package io.jenkins.plugins.awarely;

import hudson.model.Action;
import hudson.model.Run;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.RunAction2;

public final class AwarelyBuildAction implements Action, RunAction2, org.kohsuke.stapler.StaplerProxy {
    private transient Run<?, ?> run;
    private final List<ScanResult> results = new ArrayList<>();

    public synchronized void add(ScanResult result) {
        // Parallel or repeated identical steps have no unambiguous baseline.
        if (result.comparisonScope() != null) {
            for (ScanResult other : results) {
                if (!other.getArtifactPath().equals(result.getArtifactPath())
                        && result.comparisonScope().equals(other.comparisonScope())) {
                    var unavailable = BuildComparison.unavailable(
                            "Multiple matching steps in this build; give each application a distinct label/path.");
                    other.setComparison(unavailable);
                    result.setComparison(unavailable);
                }
            }
        }
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).getArtifactPath().equals(result.getArtifactPath())) {
                results.set(i, result);
                return;
            }
        }
        if (results.size() >= 100)
            throw new IllegalStateException("A build may retain at most 100 Awarely scan results");
        results.add(result);
    }

    public synchronized List<ScanResult> getResults() {
        return List.copyOf(results);
    }

    public Run<?, ?> getRun() {
        return run;
    }

    @Override
    public void onAttached(Run<?, ?> r) {
        run = r;
    }

    @Override
    public void onLoad(Run<?, ?> r) {
        run = r;
    }

    @Override
    public String getIconFileName() {
        return "symbol-awarely-scan plugin-awarely-scan";
    }

    @Override
    public String getDisplayName() {
        return "Awarely Scan";
    }

    @Override
    public String getUrlName() {
        return "awarely-scan";
    }

    @Override
    public Object getTarget() {
        checkPermission();
        return this;
    }

    public void checkPermission() {
        run.checkPermission(hudson.model.Item.READ);
    }
}
