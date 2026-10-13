package io.jenkins.plugins.awarely;

import com.fasterxml.jackson.databind.JsonNode;
import hudson.model.TaskListener;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Recognizes fixed CLI failure lines without retaining or exposing arbitrary child output. */
final class SafeDiagnostics extends OutputStream {
    private static final Map<String, String> MESSAGES = Map.of(
            "Remote operation failed: API returned HTTP 429 (RATE_LIMITED)",
            "Monitor rate limit reached. Stagger checks for this account and retry later; no successful assessment is reported.",
            "Remote operation failed: API returned HTTP 409 (INVENTORY_CONFLICT)",
            "The source changed during synchronization. Review its current inventory before starting a new build.",
            "Remote operation failed: API returned HTTP 409 (IDEMPOTENCY_CONFLICT)",
            "Monitor rejected a conflicting synchronization request. Inspect the source before retrying.",
            "Remote operation failed: API returned HTTP 503 (DISTRIBUTION_CATALOG_INVALID)",
            "The distribution catalog is temporarily unavailable or invalid. Retry the check later; no successful assessment is reported.");
    private static final JsonNode CATALOG = catalog();

    private static JsonNode catalog() {
        try (var in = SafeDiagnostics.class.getResourceAsStream("/io/jenkins/plugins/awarely/diagnostics.json")) {
            if (in == null) throw new java.io.IOException("Missing diagnostics catalog");
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(in);
        } catch (java.io.IOException e) {
            throw new ExceptionInInitializerError("Invalid bundled diagnostics catalog");
        }
    }

    private final TaskListener listener;
    private final boolean detailed;
    private final Set<String> seen = new HashSet<>();
    private int emitted;

    SafeDiagnostics() {
        this(TaskListener.NULL, false);
    }

    SafeDiagnostics(TaskListener listener, boolean detailed) {
        this.listener = listener;
        this.detailed = detailed;
    }

    private void accept(String value) {
        String legacy = MESSAGES.get(value);
        if (legacy != null && message == null) message = legacy;
        // No JSON/body parsing or free-text forwarding. Only exact, payload-free
        // v1 lines can select messages compiled into this plugin release.
        for (String kind : new String[] {"ERROR", "EVENT"}) {
            String prefix = "AWARELY_" + kind + "_V1 ";
            if (!value.startsWith(prefix)) continue;
            String code = value.substring(prefix.length());
            JsonNode item =
                    CATALOG.path(kind.equals("ERROR") ? "errors" : "events").get(code);
            if (item == null) return;
            if (kind.equals("ERROR")) {
                message = code + ": " + item.asText();
                return;
            }
            if (item.path("detailed").asBoolean() && !detailed) return;
            if (emitted >= 64 || (!code.equals("STILL_WORKING") && !seen.add(code))) return;
            emitted++;
            listener.getLogger().println("[Awarely] " + item.path("message").asText());
        }
    }

    private final byte[] line = new byte[512];
    private int length;
    private boolean overflow;
    private String message;

    @Override
    public synchronized void write(int b) {
        if (b == '\n') {
            if (!overflow) {
                accept(new String(line, 0, length, StandardCharsets.UTF_8));
            }
            Arrays.fill(line, (byte) 0);
            length = 0;
            overflow = false;
        } else if (length < line.length) line[length++] = (byte) b;
        else overflow = true;
    }

    synchronized String message() {
        return message;
    }

    @Override
    public synchronized void close() {
        Arrays.fill(line, (byte) 0);
        length = 0;
    }
}
