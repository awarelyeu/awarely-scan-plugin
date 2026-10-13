package io.jenkins.plugins.awarely;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SafeDiagnosticsTest {
    @Test
    void emitsOnlyFixedMessagesAndDiscardsLongOrInjectedOutput() throws Exception {
        try (var out = new SafeDiagnostics()) {
            out.write("token=secret\n\u001b[31mRemote operation failed: API returned HTTP 429 (RATE_LIMITED)\n"
                    .getBytes(StandardCharsets.UTF_8));
            assertNull(out.message());
            out.write(("x".repeat(100000) + "Remote operation failed: API returned HTTP 429 (RATE_LIMITED)\n")
                    .getBytes(StandardCharsets.UTF_8));
            assertNull(out.message());
            out.write(
                    "Remote operation failed: API returned HTTP 429 (RATE_LIMITED)\n".getBytes(StandardCharsets.UTF_8));
            assertTrue(out.message().startsWith("Monitor rate limit reached."));
            assertFalse(out.message().contains("secret"));
        }
    }

    @Test
    void structuredDiagnosticsNeverForwardPayloadsAndRespectDetailLevel() throws Exception {
        for (boolean detailed : new boolean[] {false, true}) {
            var bytes = new java.io.ByteArrayOutputStream();
            var listener = new hudson.util.StreamTaskListener(bytes, StandardCharsets.UTF_8);
            try (var out = new SafeDiagnostics(listener, detailed)) {
                out.write(("AWARELY_EVENT_V1 COLLECTING\nAWARELY_EVENT_V1 SYFT_CACHE_VERIFIED\n"
                                + "AWARELY_EVENT_V1 SECRET\nAWARELY_ERROR_V1 INSUFFICIENT_SCOPE SECRET\n"
                                + "\u001bAWARELY_ERROR_V1 INSUFFICIENT_SCOPE\n")
                        .getBytes(StandardCharsets.UTF_8));
                assertNull(out.message());
                out.write("AWARELY_ERROR_V1 INSUFFICIENT_SCOPE\n".getBytes(StandardCharsets.UTF_8));
                assertTrue(out.message().startsWith("INSUFFICIENT_SCOPE:"));
                assertFalse(out.message().contains("SECRET"));
                String logs = bytes.toString(StandardCharsets.UTF_8);
                assertTrue(logs.contains("Collecting selected inputs"));
                assertEquals(detailed, logs.contains("Syft archive found in cache"));
                assertFalse(logs.contains("SECRET"));
                for (int i = 0; i < 1000; i++)
                    out.write("AWARELY_EVENT_V1 STILL_WORKING\n".getBytes(StandardCharsets.UTF_8));
                assertTrue(bytes.size() < 10000, "Diagnostic stream must remain bounded");
            }
        }
    }

    @Test
    void userErrorsUseOnlyExactLocallyAuthoredMessages() {
        assertNull(UserErrors.message(new java.io.IOException("secret token and path")));
        assertTrue(UserErrors.message(new java.io.IOException("Invalid normalized inventory"))
                .contains("components array"));
    }
}
