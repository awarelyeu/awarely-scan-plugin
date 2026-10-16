package io.jenkins.plugins.awarely;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class ReleaseDownloadTest {
    private static final URI START =
            URI.create("https://github.com/awarelyeu/awarely-sbom-scanner/releases/download/v0.12.0/file");

    @Test
    void followsApprovedHttpsRedirectsWithoutAuthenticationHeaders() throws Exception {
        var client = new ScriptedClient(
                new Reply(302, "https://release-assets.githubusercontent.com/file?signed=query"), new Reply(200, null));
        assertArrayEquals(new byte[] {1, 2}, ReleaseDownload.fetch(client, START, Duration.ofSeconds(1)));
        assertEquals(2, client.requests.size());
        for (var request : client.requests) {
            assertEquals("GET", request.method());
            assertTrue(request.headers().firstValue("Authorization").isEmpty());
            assertTrue(request.headers().firstValue("Proxy-Authorization").isEmpty());
            assertTrue(request.bodyPublisher().isEmpty());
        }
    }

    @Test
    void rejectsUnapprovedRedirectBeforeMakingAnotherRequest() {
        for (String target : List.of(
                "http://github.com/file",
                "https://evil.invalid/file",
                "https://github.com:444/file",
                "https://user:secret@github.com/file",
                "https://github.com/file#fragment",
                "https:/missing-host")) {
            var client = new ScriptedClient(new Reply(302, target));
            assertThrows(IOException.class, () -> ReleaseDownload.fetch(client, START, Duration.ofSeconds(1)), target);
            assertEquals(1, client.requests.size(), target);
        }
    }

    @Test
    void limitsRedirectsAndCancelsAnUnfinishedResponseBody() {
        var loop = new ScriptedClient(
                new Reply(302, "/again"),
                new Reply(302, "/again"),
                new Reply(302, "/again"),
                new Reply(302, "/again"),
                new Reply(302, "/again"),
                new Reply(302, "/again"));
        assertThrows(IOException.class, () -> ReleaseDownload.fetch(loop, START, Duration.ofSeconds(1)));
        assertEquals(6, loop.requests.size());
        var stalled = new ScriptedClient();
        assertThrows(IOException.class, () -> ReleaseDownload.fetch(stalled, START, Duration.ofMillis(100)));
        assertTrue(stalled.pending.isCancelled());
    }

    @Test
    void boundsDeclaredAndChunkedBodiesBeforeBuffering() {
        var declared = new ReleaseDownload.BoundedBody(new Reply(200, null, Map.of("Content-Length", List.of("5"))), 4);
        var subscription = new Subscription();
        declared.onSubscribe(subscription);
        assertTrue(subscription.cancelled);
        assertTrue(declared.getBody().toCompletableFuture().isCompletedExceptionally());
        var chunked = new ReleaseDownload.BoundedBody(new Reply(200, null), 4);
        subscription = new Subscription();
        chunked.onSubscribe(subscription);
        chunked.onNext(List.of(ByteBuffer.wrap(new byte[] {1, 2, 3})));
        assertFalse(subscription.cancelled);
        chunked.onNext(List.of(ByteBuffer.wrap(new byte[] {4, 5})));
        assertTrue(subscription.cancelled);
        assertTrue(chunked.getBody().toCompletableFuture().isCompletedExceptionally());
        var exact = new ReleaseDownload.BoundedBody(new Reply(200, null), 2);
        exact.onSubscribe(new Subscription());
        exact.onNext(List.of(ByteBuffer.wrap(new byte[] {1, 2})));
        exact.onComplete();
        assertArrayEquals(
                new byte[] {1, 2}, exact.getBody().toCompletableFuture().join());
    }

    private static final class Subscription implements Flow.Subscription {
        boolean cancelled;

        @Override
        public void request(long count) {}

        @Override
        public void cancel() {
            cancelled = true;
        }
    }

    private record Reply(int statusCode, String location, Map<String, List<String>> extraHeaders)
            implements HttpResponse<byte[]>, HttpResponse.ResponseInfo {
        Reply(int statusCode, String location) {
            this(statusCode, location, Map.of());
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(
                    location == null ? extraHeaders : Map.of("Location", List.of(location)), (a, b) -> true);
        }

        @Override
        public byte[] body() {
            return new byte[] {1, 2};
        }

        @Override
        public HttpRequest request() {
            return HttpRequest.newBuilder(START).build();
        }

        @Override
        public Optional<HttpResponse<byte[]>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return START;
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }

    private static final class ScriptedClient extends HttpClient {
        final ArrayDeque<Reply> responses = new ArrayDeque<>();
        final List<HttpRequest> requests = new ArrayList<>();
        CompletableFuture<?> pending;

        ScriptedClient(Reply... replies) {
            responses.addAll(List.of(replies));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            requests.add(request);
            var result = responses.isEmpty()
                    ? new CompletableFuture<HttpResponse<T>>()
                    : CompletableFuture.completedFuture((HttpResponse<T>) responses.removeFirst());
            pending = result;
            return result;
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest r, HttpResponse.BodyHandler<T> h, HttpResponse.PushPromiseHandler<T> p) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest r, HttpResponse.BodyHandler<T> h) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            throw new UnsupportedOperationException();
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }
    }
}
