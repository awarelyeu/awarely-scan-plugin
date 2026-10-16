package io.jenkins.plugins.awarely;

import static org.junit.jupiter.api.Assertions.*;

import hudson.ProxyConfiguration;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ProxyDownloadTest {
    @Test
    void controllerUsesConfiguredProxyAndKeepsFailureSafe(JenkinsRule j) throws Exception {
        try (var proxy = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                var executor = Executors.newSingleThreadExecutor()) {
            proxy.setSoTimeout(10000);
            var received = executor.submit(() -> {
                try (var socket = proxy.accept()) {
                    socket.setSoTimeout(10000);
                    var input = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                    String first = input.readLine();
                    for (String line; (line = input.readLine()) != null && !line.isEmpty(); ) {}
                    socket.getOutputStream()
                            .write(("HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                                    .getBytes(StandardCharsets.US_ASCII));
                    return first;
                }
            });
            j.jenkins.proxy = new ProxyConfiguration(proxy.getInetAddress().getHostAddress(), proxy.getLocalPort());
            var failure = assertThrows(
                    IOException.class,
                    () -> ReleaseDownload.fetch(URI.create(
                            "https://github.com/awarelyeu/awarely-sbom-scanner/releases/download/test?private-query")));
            assertEquals("CONNECT github.com:443 HTTP/1.1", received.get(10, TimeUnit.SECONDS));
            assertEquals("Pinned release download failed; check controller HTTPS/proxy access", failure.getMessage());
            assertNull(failure.getCause());
        }
    }

    @Test
    void configuredNoProxyHostsAreRespected(JenkinsRule j) throws Exception {
        j.jenkins.proxy = new ProxyConfiguration("127.0.0.1", 12345, null, null, "*.githubusercontent.com");
        try (var client = ProxyConfiguration.newHttpClientBuilder().build()) {
            var selector = client.proxy().orElseThrow();
            assertEquals(
                    List.of(Proxy.NO_PROXY),
                    selector.select(URI.create("https://release-assets.githubusercontent.com/file")));
            assertEquals(
                    Proxy.Type.HTTP,
                    selector.select(URI.create("https://github.com/file"))
                            .getFirst()
                            .type());
        }
    }
}
