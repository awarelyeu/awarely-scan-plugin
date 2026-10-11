package io.jenkins.plugins.awarely;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BuildComparisonTest {
    static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    static byte[] inventory(String version) {
        return bytes("""
          {"bomFormat":"CycloneDX","specVersion":"1.6","metadata":{"properties":[
           {"name":"awarely:coverage","value":"complete-for-selected-inputs"},
           {"name":"awarely:scope","value":"selected-application"}]},
           "components":[{"purl":"pkg:npm/demo@%s","name":"demo","version":"%s"}]}
          """.formatted(version, version));
    }

    static ScanResult result() throws IOException {
        return ScanResult.local("app", "npm", "local", "awarely/test", inventory("1.0.0"), false);
    }

    static byte[] report(String version, int count) {
        var matches = new java.util.ArrayList<String>();
        for (int i = 0; i < count; i++) matches.add("""
            {"cveId":"CVE-2026-%d","severity":"HIGH","components":[{"componentIndex":0,"precision":"version"}]}
            """.formatted(10000 + i));
        return bytes("""
           {"schemaVersion":1,"status":"complete","coverage":{"inventoryCoverage":"complete","componentsSubmitted":1,
           "from":"2025-01-01T00:00:00Z","to":"2026-01-01T00:00:00Z","catalogGeneratedAt":"2026-01-01T00:00:00Z","unevaluated":[]},
           "components":[{"ecosystem":"npm","name":"demo","version":"%s"}],
           "matches":[%s],"summary":{"matchedCves":%d,"componentMatches":%d}}
           """.formatted(version, String.join(",", matches), count, count));
    }

    static BuildComparison compare(byte[] oldBom, byte[] newBom, byte[] oldReport, byte[] newReport)
            throws IOException {
        return BuildComparison.calculate(1, result(), result(), oldBom, newBom, oldReport, newReport);
    }

    @Test
    void versionChangesAreAdditionsAndRemovalsNotClaimsOfFixes() throws Exception {
        var c = compare(inventory("1.0.0"), inventory("2.0.0"), report("1.0.0", 2), report("2.0.0", 0));
        assertTrue(c.isAvailable());
        assertEquals(1, c.getAddedComponents());
        assertEquals(1, c.getRemovedComponents());
        assertEquals(0, c.getAddedMatches());
        assertEquals(2, c.getRemovedMatches());
        assertTrue(c.getFindingMessage().contains("not proof"));
    }

    @Test
    void fullReportsAreComparedBeyondTheDisplayPreview() throws Exception {
        var c = compare(inventory("1.0.0"), inventory("1.0.0"), report("1.0.0", 102), report("1.0.0", 101));
        assertEquals(1, c.getRemovedMatches());
        assertTrue(c.getChanges().getFirst().getIdentity().contains("CVE-2026-10101"));
        var bounded = compare(inventory("1.0.0"), inventory("1.0.0"), report("1.0.0", 150), report("1.0.0", 0));
        assertEquals(150, bounded.getRemovedMatches());
        assertEquals(100, bounded.getChanges().size());
    }

    @Test
    void incompleteReportsAndInventoriesDoNotTurnIntoResolvedFindings() throws Exception {
        byte[] partial = bytes(new String(inventory("1.0.0"), StandardCharsets.UTF_8)
                .replace("complete-for-selected-inputs", "partial"));
        assertFalse(compare(partial, inventory("1.0.0"), null, null).isAvailable());
        byte[] unevaluated = bytes(new String(report("1.0.0", 0), StandardCharsets.UTF_8)
                .replace(
                        "\"unevaluated\":[]",
                        "\"unevaluated\":[{\"componentIndex\":0,\"reason\":\"ECOSYSTEM_NOT_EVALUATED\"}]"));
        var c = compare(inventory("1.0.0"), inventory("1.0.0"), report("1.0.0", 1), unevaluated);
        assertTrue(c.isAvailable());
        assertFalse(c.isMatchesAvailable());
        assertEquals(-1, c.getRemovedMatches());
    }

    @Test
    void catalogAndRollingPeriodChangesAreExplicit() throws Exception {
        byte[] shifted = bytes(new String(report("1.0.0", 0), StandardCharsets.UTF_8).replace("01-01T00", "01-02T00"));
        var c = compare(inventory("1.0.0"), inventory("1.0.0"), report("1.0.0", 1), shifted);
        assertTrue(c.isMatchesAvailable());
        assertTrue(c.getFindingMessage().contains("dates changed"));
        byte[] shorter = bytes(new String(report("1.0.0", 0), StandardCharsets.UTF_8).replace("2025-01", "2025-02"));
        assertFalse(compare(inventory("1.0.0"), inventory("1.0.0"), report("1.0.0", 1), shorter)
                .isMatchesAvailable());
    }

    @Test
    void distinctSourcesSettingsAndUnambiguousEncoding() throws Exception {
        assertNotEquals(BuildComparison.scope("a", "bc"), BuildComparison.scope("ab", "c"));
        assertNotEquals(BuildComparison.scope("npm", "source-a"), BuildComparison.scope("npm", "source-b"));
        byte[] changedScope = bytes(
                new String(inventory("1.0.0"), StandardCharsets.UTF_8).replace("selected-application", "whole-host"));
        assertFalse(compare(inventory("1.0.0"), changedScope, null, null).isAvailable());
    }
}
