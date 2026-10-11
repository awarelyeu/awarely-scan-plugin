package io.jenkins.plugins.awarely;

import static org.junit.jupiter.api.Assertions.*;

import hudson.model.Run;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ComparisonPersistenceTest {
    private ScanResult add(Run<?, ?> run, String scope, String artifacts, String version) throws Exception {
        var result = ScanResult.local("app", "npm", "local", artifacts, BuildComparisonTest.inventory(version), false);
        result.complete(scope);
        var action = run.getAction(AwarelyBuildAction.class);
        if (action == null) {
            action = new AwarelyBuildAction();
            run.addAction(action);
        }
        action.add(result);
        @SuppressWarnings("deprecation")
        Path root = run.getArtifactsDir().toPath().resolve(artifacts);
        Files.createDirectories(root);
        Files.write(root.resolve("inventory.cdx.json"), BuildComparisonTest.inventory(version));
        run.save();
        return result;
    }

    @Test
    void previousBuildUsesMatchingScopeAndRetainedArtifacts(JenkinsRule j) throws Exception {
        var job = j.createFreeStyleProject();
        var first = j.buildAndAssertSuccess(job);
        add(first, "same", "awarely/first", "1.0.0");
        var second = j.buildAndAssertSuccess(job);
        var current = add(second, "same", "awarely/second", "2.0.0");
        var comparison = BuildComparison.compare(second, current, BuildComparisonTest.inventory("2.0.0"), null);
        assertEquals(1, comparison.getRemovedComponents());
        current.setComparison(comparison);
        second.save();
        // Run data roundtrip preserves the bounded UI model without storing the full report.
        var serialized = Run.XSTREAM2.toXML(second.getAction(AwarelyBuildAction.class));
        var restored = (AwarelyBuildAction) Run.XSTREAM2.fromXML(serialized);
        assertEquals(1, restored.getResults().getFirst().getComparison().getAddedComponents());
        var third = j.buildAndAssertSuccess(job);
        var different = add(third, "other-source", "awarely/third", "2.0.0");
        assertFalse(BuildComparison.compare(third, different, BuildComparisonTest.inventory("2.0.0"), null)
                .isAvailable());
        @SuppressWarnings("deprecation")
        Path old = first.getArtifactsDir().toPath().resolve("awarely/first/inventory.cdx.json");
        Files.delete(old);
        assertFalse(BuildComparison.compare(second, current, BuildComparisonTest.inventory("2.0.0"), null)
                .isAvailable());
    }

    @Test
    void duplicateStepsInvalidateBothComparisons(JenkinsRule j) throws Exception {
        var run = j.buildAndAssertSuccess(j.createFreeStyleProject());
        var first = add(run, "same", "awarely/one", "1.0.0");
        var second = add(run, "same", "awarely/two", "1.0.0");
        assertFalse(first.getComparison().isAvailable());
        assertFalse(second.getComparison().isAvailable());
        var next = j.buildAndAssertSuccess(run.getParent());
        var current = add(next, "same", "awarely/three", "2.0.0");
        assertFalse(BuildComparison.compare(next, current, BuildComparisonTest.inventory("2.0.0"), null)
                .isAvailable());
    }

    @Test
    void comparisonTextIsEscapedInReport(JenkinsRule j) throws Exception {
        var run = j.buildAndAssertSuccess(j.createFreeStyleProject());
        var result = add(run, "same", "awarely/current", "1.0.0");
        byte[] malicious = BuildComparisonTest.bytes(
                new String(BuildComparisonTest.inventory("2.0.0"), java.nio.charset.StandardCharsets.UTF_8)
                        .replace("pkg:npm/demo@2.0.0", "pkg:npm/<img id='comparison-injected' src='x'>@2.0.0"));
        result.setComparison(
                BuildComparisonTest.compare(BuildComparisonTest.inventory("1.0.0"), malicious, null, null));
        run.save();
        var page = j.createWebClient().goTo(run.getUrl() + "awarely-scan/");
        assertTrue(page.getByXPath("//*[@id='comparison-injected']").isEmpty());
        assertTrue(page.asNormalizedText().contains("<img id='comparison-injected'"));
    }
}
