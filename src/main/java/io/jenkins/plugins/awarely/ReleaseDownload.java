package io.jenkins.plugins.awarely;

import hudson.ProxyConfiguration;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Public release transport on the controller; never sends a Monitor credential. */
final class ReleaseDownload {
    static final int LIMIT = 40 * 1024 * 1024;

    private ReleaseDownload() {}

    static byte[] fetch(URI uri) throws IOException, InterruptedException {
        HttpClient client = ProxyConfiguration.newHttpClientBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        try {
            return fetch(client, uri, Duration.ofSeconds(180));
        } finally {
            client.shutdownNow();
        }
    }

    static byte[] fetch(HttpClient client, URI uri, Duration budget) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + budget.toNanos();
        for (int redirects = 0; redirects <= 5; redirects++) {
            validate(uri);
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new IOException("Release download limit exceeded");
            var request = ProxyConfiguration.newHttpRequestBuilder(uri)
                    .timeout(Duration.ofNanos(remaining))
                    .header("Accept", "application/octet-stream")
                    .GET()
                    .build();
            var pending = client.sendAsync(request, info -> new BoundedBody(info, LIMIT));
            HttpResponse<byte[]> response;
            try {
                response = pending.get(remaining, TimeUnit.NANOSECONDS);
            } catch (TimeoutException error) {
                throw new IOException("Release download limit exceeded", error);
            } catch (ExecutionException error) {
                // Do not expose redirect query strings, proxy credentials or response bodies in build logs.
                throw new IOException("Pinned release download failed; check controller HTTPS/proxy access");
            } finally {
                if (!pending.isDone()) pending.cancel(true);
            }
            int status = response.statusCode();
            if (Set.of(301, 302, 303, 307, 308).contains(status)) {
                String location = response.headers().firstValue("Location").orElse(null);
                if (location == null) throw new IOException("Missing release redirect");
                try {
                    uri = uri.resolve(location);
                } catch (IllegalArgumentException error) {
                    throw new IOException("Unapproved release redirect");
                }
                continue;
            }
            if (status != 200)
                throw new IOException("Pinned release download failed; check controller HTTPS/proxy access");
            return response.body();
        }
        throw new IOException("Too many release redirects");
    }

    static void validate(URI uri) throws IOException {
        if (!"https".equals(uri.getScheme())
                || uri.getRawUserInfo() != null
                || uri.getRawFragment() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)
                || uri.getHost() == null
                || !Set.of("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
                        .contains(uri.getHost())) throw new IOException("Unapproved release redirect");
    }

    /** Bound bytes before the JDK subscriber buffers them, including chunked/error/redirect responses. */
    static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> body = HttpResponse.BodySubscribers.ofByteArray();
        private final int limit;
        private final boolean oversized;
        private Flow.Subscription subscription;
        private long size;
        private boolean done;

        BoundedBody(HttpResponse.ResponseInfo info, int limit) {
            this.limit = limit;
            oversized = info.headers().firstValueAsLong("Content-Length").orElse(-1) > limit;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return body.getBody();
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            if (oversized) {
                done = true;
                subscription.cancel();
                body.onError(new IOException("Release archive is too large"));
            } else body.onSubscribe(subscription);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (done) return;
            for (ByteBuffer buffer : buffers) size += buffer.remaining();
            if (size > limit) {
                done = true;
                subscription.cancel();
                body.onError(new IOException("Release archive is too large"));
            } else body.onNext(buffers);
        }

        @Override
        public void onError(Throwable error) {
            if (!done) {
                done = true;
                body.onError(error);
            }
        }

        @Override
        public void onComplete() {
            if (!done) {
                done = true;
                body.onComplete();
            }
        }
    }
}
