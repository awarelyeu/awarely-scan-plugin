package io.jenkins.plugins.awarely;

import com.fasterxml.jackson.databind.JsonNode;
import hudson.model.Run;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Bounded observations against the immediately preceding build of this job, never a remediation verdict. */
public final class BuildComparison implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String message, findingMessage;
    private final int baselineBuild, addedComponents, removedComponents, addedMatches, removedMatches;
    private final List<Change> changes;

    private BuildComparison(
            String message,
            String findingMessage,
            int baselineBuild,
            int addedComponents,
            int removedComponents,
            int addedMatches,
            int removedMatches,
            List<Change> changes) {
        this.message = message;
        this.findingMessage = findingMessage;
        this.baselineBuild = baselineBuild;
        this.addedComponents = addedComponents;
        this.removedComponents = removedComponents;
        this.addedMatches = addedMatches;
        this.removedMatches = removedMatches;
        this.changes = List.copyOf(changes);
    }

    static BuildComparison unavailable(String reason) {
        return new BuildComparison(reason, "No CVE comparison.", 0, -1, -1, -1, -1, List.of());
    }

    static String scope(String... fields) throws IOException {
        return PinnedTools.digest(pack(fields).getBytes(StandardCharsets.UTF_8));
    }

    static String scope(
            String collector,
            String mode,
            String path,
            String label,
            boolean allPackages,
            String node,
            String cli,
            String source)
            throws IOException {
        return scope("v1", collector, mode, path, label, Boolean.toString(allPackages), node, cli, source);
    }

    static BuildComparison compare(Run<?, ?> run, ScanResult current, byte[] inventory, byte[] report)
            throws InterruptedException {
        Run<?, ?> previous = run.getPreviousBuild();
        if (previous == null) return unavailable("First retained build; no previous baseline.");
        if (previous.isBuilding())
            return unavailable("Previous build is still running; no comparison with an unfinished build.");
        var action = previous.getAction(AwarelyBuildAction.class);
        if (action == null) return unavailable("Previous build has no Awarely result with comparison metadata.");
        var candidates = action.getResults().stream()
                .filter(r -> current.comparisonScope().equals(r.comparisonScope()))
                .toList();
        if (candidates.size() != 1)
            return unavailable(
                    "Previous build has no unique completed scan with the same collector, path, label, scope, CLI and destination.");
        ScanResult prior = candidates.get(0);
        try {
            return calculate(
                    previous.getNumber(),
                    prior,
                    current,
                    artifact(previous, prior, "inventory.cdx.json"),
                    inventory,
                    report == null ? null : artifact(previous, prior, "check-result.json"),
                    report);
        } catch (IOException | IllegalArgumentException e) {
            // Artifact retention and external artifact stores may make a prior result unavailable.
            // Do not echo exceptions containing untrusted paths or report content.
            return unavailable(
                    "Previous artifacts are unavailable, invalid or exceed comparison limits. Current assessment and build policy are unchanged.");
        }
    }

    private static byte[] artifact(Run<?, ?> run, ScanResult result, String file) throws IOException {
        if (!result.getArtifactPath().matches("awarely/[A-Za-z0-9-]{1,80}"))
            throw new IOException("Invalid artifact reference");
        try (InputStream in = run.getArtifactManager()
                .root()
                .child(result.getArtifactPath() + "/" + file)
                .open()) {
            byte[] bytes = in.readNBytes(BoundedJson.LIMIT + 1);
            if (bytes.length > BoundedJson.LIMIT) throw new IOException("Artifact too large");
            return bytes;
        }
    }

    static BuildComparison calculate(
            int previousBuild,
            ScanResult prior,
            ScanResult current,
            byte[] previousInventory,
            byte[] inventory,
            byte[] previousReport,
            byte[] report)
            throws IOException {
        var oldLocal = ScanResult.local("comparison", "existing", "local", "", previousInventory, false);
        var newLocal = ScanResult.local("comparison", "existing", "local", "", inventory, false);
        if (oldLocal.isIncomplete() || newLocal.isIncomplete())
            return unavailable("Component comparison requires complete selected-input coverage in both inventories.");
        var oldBom = BoundedJson.read(previousInventory);
        var newBom = BoundedJson.read(inventory);
        if (!inventoryScope(oldBom).equals(inventoryScope(newBom)))
            return unavailable("Selected inventory scope changed; no component or CVE comparison.");
        Map<String, String> before = components(oldBom), after = components(newBom);
        List<Change> changes = new ArrayList<>();
        int added = difference(after, before, "Component added", changes);
        int removed = difference(before, after, "Component removed", changes);
        int addedMatches = -1, removedMatches = -1;
        String findingMessage = "No CVE comparison: this action does not assess vulnerabilities.";
        if (report != null && previousReport != null) {
            // Validate the full report, not the bounded findings preview.
            var oldCheck = ScanResult.check("comparison", "existing", "", previousReport, "REPORT_ONLY");
            var newCheck = ScanResult.check("comparison", "existing", "", report, "REPORT_ONLY");
            if (oldCheck.getComponents() != oldLocal.getComponents()
                    || newCheck.getComponents() != newLocal.getComponents())
                throw new IOException("Inventory and assessment counts differ");
            JsonNode oldJson = BoundedJson.read(previousReport), newJson = BoundedJson.read(report);
            if (oldCheck.isIncomplete() || newCheck.isIncomplete()) {
                findingMessage =
                        "CVE comparison unavailable: at least one assessment is partial or has unevaluated components.";
            } else if (!hasCatalogContext(oldJson) || !hasCatalogContext(newJson)) {
                findingMessage = "CVE comparison unavailable: catalog snapshot metadata is missing or invalid.";
            } else if (!window(oldJson).equals(window(newJson))) {
                findingMessage = "CVE comparison unavailable: assessment window lengths differ.";
            } else {
                var oldMatches = matches(oldJson);
                var newMatches = matches(newJson);
                addedMatches = difference(newMatches, oldMatches, "Match newly observed", changes);
                removedMatches = difference(oldMatches, newMatches, "Match no longer observed", changes);
                boolean changedContext = !assessmentContext(oldJson).equals(assessmentContext(newJson));
                findingMessage = (changedContext
                                ? "Catalog snapshot or assessment dates changed. "
                                : "Same reported catalog snapshot and dates. ")
                        + "Match differences are observations, not proof of introduction or remediation. Package versions and precision are part of match identity.";
            }
        }
        return new BuildComparison(
                "Compared with build #" + previousBuild + ". Version changes appear as a removal and an addition.",
                findingMessage,
                previousBuild,
                added,
                removed,
                addedMatches,
                removedMatches,
                changes);
    }

    private static String inventoryScope(JsonNode bom) throws IOException {
        String scope = null;
        for (JsonNode p : bom.path("metadata").path("properties")) {
            if ("awarely:scope".equals(p.path("name").asText())) {
                if (scope != null) throw new IOException("Duplicate inventory scope");
                scope = BoundedJson.text(p, "value", 2048);
            }
        }
        if (scope == null || scope.isBlank()) throw new IOException("Missing inventory scope");
        return scope;
    }

    private static Map<String, String> components(JsonNode bom) throws IOException {
        Map<String, String> values = new TreeMap<>();
        for (JsonNode c : bom.path("components")) {
            String purl = BoundedJson.text(c, "purl", 4096);
            if (!purl.startsWith("pkg:") || values.put(purl, purl) != null)
                throw new IOException("Ambiguous component identity");
        }
        return values;
    }

    private static Map<String, String> matches(JsonNode report) throws IOException {
        List<String> identities = new ArrayList<>();
        List<String> descriptions = new ArrayList<>();
        for (JsonNode c : report.path("components")) {
            List<String> fields = new ArrayList<>();
            for (String f : List.of(
                    "ecosystem",
                    "name",
                    "version",
                    "packageType",
                    "distribution",
                    "distributionVersion",
                    "architecture",
                    "sourcePackage",
                    "sourceVersion")) {
                fields.add(c.has(f) ? BoundedJson.text(c, f, 2048) : "");
            }
            identities.add(pack(fields.toArray(String[]::new)));
            descriptions.add(fields.get(0) + " " + fields.get(1) + "@" + fields.get(2)
                    + (fields.get(4).isEmpty()
                            ? ""
                            : " [" + fields.get(4) + " " + fields.get(5) + " " + fields.get(6) + "]"));
        }
        Map<String, String> values = new TreeMap<>();
        for (JsonNode m : report.path("matches")) {
            String id = BoundedJson.text(m, "cveId", 40);
            for (JsonNode hit : m.path("components")) {
                int index = BoundedJson.count(hit, "componentIndex", identities.size() - 1);
                String precision = BoundedJson.text(hit, "precision", 16);
                String key = pack(id, identities.get(index), precision);
                if (values.size() >= 100000
                        || values.put(key, id + " | " + descriptions.get(index) + " | " + precision) != null)
                    throw new IOException("Ambiguous or excessive match identities");
            }
        }
        return values;
    }

    private static String pack(String... values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) out.append(value.length()).append(':').append(value);
        return out.toString();
    }

    private static boolean hasCatalogContext(JsonNode report) {
        try {
            Instant.parse(report.path("coverage").path("catalogGeneratedAt").asText());
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static Duration window(JsonNode report) {
        var cov = report.path("coverage");
        return Duration.between(
                Instant.parse(cov.path("from").asText()),
                Instant.parse(cov.path("to").asText()));
    }

    private static String assessmentContext(JsonNode report) {
        var cov = report.path("coverage");
        return pack(
                cov.path("from").asText(),
                cov.path("to").asText(),
                cov.path("catalogGeneratedAt").asText(),
                cov.path("distributionCatalogs").toString());
    }

    private static int difference(
            Map<String, String> left, Map<String, String> right, String kind, List<Change> preview) {
        int count = 0;
        for (var entry : left.entrySet()) {
            if (!right.containsKey(entry.getKey())) {
                count++;
                if (preview.size() < 100) preview.add(new Change(kind, entry.getValue()));
            }
        }
        return count;
    }

    public String getMessage() {
        return message;
    }

    public String getFindingMessage() {
        return findingMessage;
    }

    public int getBaselineBuild() {
        return baselineBuild;
    }

    public int getAddedComponents() {
        return addedComponents;
    }

    public int getRemovedComponents() {
        return removedComponents;
    }

    public int getAddedMatches() {
        return addedMatches;
    }

    public int getRemovedMatches() {
        return removedMatches;
    }

    public boolean isAvailable() {
        return baselineBuild > 0;
    }

    public boolean isMatchesAvailable() {
        return addedMatches >= 0;
    }

    public List<Change> getChanges() {
        return changes;
    }

    public static final class Change implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String kind, identity;

        Change(String kind, String identity) {
            this.kind = kind;
            this.identity = identity;
        }

        public String getKind() {
            return kind;
        }

        public String getIdentity() {
            return identity;
        }
    }
}
