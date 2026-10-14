package io.jenkins.plugins.awarely;

import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import jenkins.branch.MultiBranchProject;
import jenkins.model.Jenkins;
import jenkins.scm.api.mixin.ChangeRequestSCMHead;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.FileCredentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.interceptor.RequirePOST;

/** Freestyle build step and Pipeline awarelyScan step, using the same verified CLI as interactive users. */
public final class AwarelyScanBuilder extends Builder implements SimpleBuildStep {
    private final String collector;
    private String mode = "local",
            path = ".",
            applicationLabel = "application",
            credentialsId = "",
            severityThreshold = "REPORT_ONLY",
            policyResult = "UNSTABLE";
    private boolean failOnIncomplete = true, allPackages, preflightOnly, detailedLogging;

    @DataBoundConstructor
    public AwarelyScanBuilder(String collector) {
        this.collector = ScanPolicy.choice(collector, ScanPolicy.COLLECTORS, "collector");
    }

    public String getCollector() {
        return collector;
    }

    public String getMode() {
        return mode;
    }

    public String getPath() {
        return path;
    }

    public String getApplicationLabel() {
        return applicationLabel;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getSeverityThreshold() {
        return severityThreshold;
    }

    public boolean isFailOnIncomplete() {
        return failOnIncomplete;
    }

    public boolean isAllPackages() {
        return allPackages;
    }

    public String getPolicyResult() {
        // Jobs saved before this setting existed retain their original behavior.
        return policyResult == null ? "UNSTABLE" : policyResult;
    }

    public boolean isPreflightOnly() {
        return preflightOnly;
    }

    public boolean isDetailedLogging() {
        return detailedLogging;
    }

    @DataBoundSetter
    public void setDetailedLogging(boolean value) {
        detailedLogging = value;
    }

    @DataBoundSetter
    public void setPolicyResult(String v) {
        policyResult = ScanPolicy.choice(v, Set.of("UNSTABLE", "FAILURE"), "policy result");
    }

    @DataBoundSetter
    public void setPreflightOnly(boolean v) {
        preflightOnly = v;
    }

    @DataBoundSetter
    public void setMode(String v) {
        mode = ScanPolicy.choice(v, ScanPolicy.MODES, "action");
    }

    @DataBoundSetter
    public void setPath(String v) {
        // Validate at perform() before all agent access, so Pipeline reports a
        // concise AbortException instead of a data-binding stack trace.
        path = v;
    }

    @DataBoundSetter
    public void setApplicationLabel(String v) {
        applicationLabel = ScanPolicy.label(v);
    }

    @DataBoundSetter
    public void setCredentialsId(String v) {
        if (v == null || v.length() > 256 || v.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid credential ID");
        credentialsId = v;
    }

    @DataBoundSetter
    public void setSeverityThreshold(String v) {
        severityThreshold = ScanPolicy.choice(v, ScanPolicy.GATES, "severity threshold");
    }

    @DataBoundSetter
    public void setFailOnIncomplete(boolean v) {
        failOnIncomplete = v;
    }

    @DataBoundSetter
    public void setAllPackages(boolean v) {
        allPackages = v;
    }

    @Override
    public void perform(
            Run<?, ?> run, FilePath workspace, EnvVars environment, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {
        long started = System.nanoTime();
        try {
            performScan(run, workspace, environment, launcher, listener);
        } catch (IOException error) {
            if (error instanceof AbortException) throw error;
            String message = UserErrors.message(error);
            if (message != null) throw new AbortException(message);
            throw error;
        } finally {
            listener.getLogger()
                    .printf("[Awarely] Step elapsed: %.1fs%n", (System.nanoTime() - started) / 1_000_000_000.0);
        }
    }

    private void performScan(
            Run<?, ?> run, FilePath workspace, EnvVars environment, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {
        validate();
        listener.getLogger().println("[Awarely] Validating job permissions, agent and workspace-contained input.");
        Computer computer = workspace.toComputer();
        if (computer == null || computer.getNode() == null || computer.getNode() instanceof Jenkins)
            throw new AbortException("Awarely must run on a dedicated Linux agent, never on the Jenkins controller");
        Node node = computer.getNode();
        AwarelyConfiguration config = AwarelyConfiguration.get();
        boolean privileged = !mode.equals("local") || collector.equals("linux");
        if (privileged) {
            if (isChangeRequest(run.getParent()))
                throw new AbortException(
                        "Pull-request builds may collect application SBOMs locally, but cannot use credentials or inventory the agent host");
            if (!config.permitsAgent(node.getNodeName()))
                throw new AbortException("This agent is not approved for Monitor operations or host inventory");
            if (!mode.equals("local") && !config.permitsRemote(run.getParent()))
                throw new AbortException("An administrator must approve this exact job for Monitor operations");
            if (mode.equals("sync") && !config.permitsSync(run.getParent()))
                throw new AbortException(
                        "An administrator must separately approve this exact job for inventory synchronization");
            if (collector.equals("linux") && !config.permitsHost(run.getParent()))
                throw new AbortException("An administrator must approve this exact job for agent host inventory");
        }
        FilePath nodeRoot = node.getRootPath();
        if (nodeRoot == null) throw new AbortException("Agent is disconnected");
        Properties pins = PinnedTools.manifest();
        listener.getLogger()
                .println("Awarely Scan: " + collector + " / " + mode + "; CLI " + pins.getProperty("version")
                        + " (fixed by plugin release).");
        AgentFiles.Session session = workspace.act(new AgentFiles.Prepare(nodeRoot.getRemote(), path, collector));
        listener.getLogger()
                .println("[Awarely] Input validation passed; results use a private directory outside the workspace.");
        if (detailedLogging)
            listener.getLogger().printf("[Awarely] Selected input: %s; label: %s%n", path, applicationLabel);
        FilePath work = new FilePath(workspace.getChannel(), session.work);
        String artifactPrefix = "awarely/" + UUID.randomUUID();
        try {
            if (preflightOnly) {
                listener.getLogger().println("Awarely configuration check only: no scan, download or Monitor request.");
                for (String message : work.act(new Preflight.Check(
                        session.home, session.input, collector, pins, config.isAllowToolDownloads())))
                    listener.getLogger().println(message);
                if (!mode.equals("local")) {
                    try (MonitorCredential credential = MonitorCredential.load(run, credentialsId)) {
                        listener.getLogger()
                                .println(
                                        "PASS: Secret file is accessible, its JSON format is valid and its API origin is approved.");
                    }
                    listener.getLogger()
                            .println(
                                    "NOT TESTED: server connectivity, token expiry/revocation, Check/Sync scope, account quota and source state. These require the real API operation.");
                }
                listener.getLogger()
                        .println(
                                "Configuration checks passed. Turn off 'Validate configuration only' to collect and process inventory. No vulnerability assessment was performed.");
                return;
            }
            String sourceKey = "local";
            listener.getLogger()
                    .println(
                            "[Awarely] Preparing the pinned CLI; cached and downloaded tools must pass digest verification.");
            String binary = work.act(new PinnedTools.Prepare(
                    pins, session.home, config.isAllowToolDownloads(), listener, detailedLogging));
            FilePath inventory = work.child("inventory.cdx.json");
            int code;
            if (collector.equals("existing")) {
                new FilePath(workspace.getChannel(), session.input)
                        .act(new AgentFiles.CopyInventory(inventory.getRemote()));
                code = 0;
            } else {
                listener.getLogger()
                        .println(
                                "Collecting selected inputs locally. Project builds and dependency installation remain separate pipeline steps.");
                code = execute(
                        launcher,
                        work,
                        session.home,
                        binary,
                        diagnosticArguments(collectionArguments(
                                session.input, inventory.getRemote(), config.isAllowToolDownloads())),
                        null,
                        listener,
                        detailedLogging);
                if (code != 0 && code != 3) throw new AbortException(collectionError(code));
            }
            byte[] inventoryBytes = inventory.act(new AgentFiles.Read());
            ScanResult result =
                    ScanResult.local(applicationLabel, collector, "local", artifactPrefix, inventoryBytes, code == 3);
            listener.getLogger()
                    .printf(
                            "[Awarely] Inventory collected: %d components; coverage: %s for selected inputs.%n",
                            result.getComponents(), result.getCoverage());
            Map<String, String> artifacts = new LinkedHashMap<>();
            artifacts.put(artifactPrefix + "/inventory.cdx.json", "inventory.cdx.json");
            // Preserve the collected inventory even when authentication or assessment subsequently fails.
            run.pickArtifactManager()
                    .archive(work, launcher, new jenkins.util.BuildListenerAdapter(listener), artifacts);
            publishResult(run, result);
            artifacts.clear();
            if (!mode.equals("local")) {
                if (mode.equals("sync") && (result.isIncomplete() || result.getComponents() == 0))
                    throw new AbortException(
                            "Synchronization requires a complete, non-empty selected-input inventory. Use local export or check instead");
                listener.getLogger()
                        .println(
                                mode.equals("check")
                                        ? "Checking the selected inventory. Saved inventory and alerts remain unchanged."
                                        : "Synchronizing only the credential's configured source.");
                try (MonitorCredential credential = MonitorCredential.load(run, credentialsId)) {
                    sourceKey = credential.sourceKey;
                    List<String> args = new ArrayList<>(List.of(
                            mode,
                            "--input",
                            inventory.getRemote(),
                            "--credentials",
                            "-",
                            "--output",
                            work.child(mode + "-result.json").getRemote()));
                    if (mode.equals("sync")) {
                        // New invocations have new keys. The CLI retains a fixed revision/key for every transport
                        // retry.
                        args.addAll(List.of("--idempotency-key", "jenkins-" + UUID.randomUUID()));
                        try (SyncState.Lease lease = SyncState.acquire(credential.sourceKey, run)) {
                            code = execute(
                                    launcher,
                                    work,
                                    session.home,
                                    binary,
                                    diagnosticArguments(args),
                                    credential.input(),
                                    listener,
                                    detailedLogging);
                        }
                    } else
                        code = execute(
                                launcher,
                                work,
                                session.home,
                                binary,
                                diagnosticArguments(args),
                                credential.input(),
                                listener,
                                detailedLogging);
                }
                if (code != 0) throw new AbortException(remoteError(code));
                byte[] report = work.child(mode + "-result.json").act(new AgentFiles.Read());
                if (mode.equals("check"))
                    result = ScanResult.check(applicationLabel, collector, artifactPrefix, report, severityThreshold);
                else {
                    validateReceipt(report, result.getComponents());
                    result = ScanResult.local(
                            applicationLabel, collector, "sync", artifactPrefix, inventoryBytes, false);
                }
                artifacts.put(artifactPrefix + "/" + mode + "-result.json", mode + "-result.json");
            }
            // Exact allowlist only: never a credential, cache file or raw producer output.
            if (!artifacts.isEmpty())
                run.pickArtifactManager()
                        .archive(work, launcher, new jenkins.util.BuildListenerAdapter(listener), artifacts);
            result.complete(BuildComparison.scope(
                    collector,
                    mode,
                    path,
                    applicationLabel,
                    allPackages,
                    collector.equals("linux") ? node.getNodeName() : "",
                    pins.getProperty("version"),
                    sourceKey));
            result.setComparison(BuildComparison.compare(
                    run,
                    result,
                    inventoryBytes,
                    mode.equals("check") ? work.child("check-result.json").act(new AgentFiles.Read()) : null));
            publishResult(run, result);
            listener.getLogger()
                    .println("Build comparison: " + result.getComparison().getMessage());
            if (mode.equals("check"))
                listener.getLogger()
                        .printf(
                                "Awarely completed: %d components; %d matched CVEs; %d unevaluated. See Awarely Scan and the JSON artifacts.%n",
                                result.getComponents(), result.getMatchedCves(), result.getUnevaluated());
            else
                listener.getLogger()
                        .printf(
                                "Awarely completed: %d components; %s. No vulnerability assessment was performed.%n",
                                result.getComponents(),
                                mode.equals("sync") ? "source synchronized" : "local inventory archived");
            listener.getLogger()
                    .println("[Awarely] Reports: " + run.getUrl() + "awarely-scan/; JSON artifacts: " + artifactPrefix
                            + "/");
            if (mode.equals("check")) {
                listener.getLogger()
                        .printf(
                                "[Awarely] Coverage: %s for selected inputs; version-confirmed component matches: %d; unevaluated: %d.%n",
                                result.getCoverage(), result.getVersionMatches(), result.getUnevaluated());
                listener.getLogger()
                        .printf(
                                "[Awarely] Policy: severity=%s (version-confirmed only); incomplete coverage=%s; triggered result=%s.%n",
                                severityThreshold, failOnIncomplete ? "enforce" : "report only", getPolicyResult());
                listener.getLogger()
                        .println("[Awarely] No matches is not an all-clear. Review coverage and the complete report.");
            }
            if (!result.isGateExceeded() && !(failOnIncomplete && result.isIncomplete()))
                listener.getLogger()
                        .println(
                                "[Awarely] No configured policy was triggered; any earlier build result is preserved.");
            applyPolicy(run, result, failOnIncomplete, getPolicyResult(), listener);
        } finally {
            try {
                work.deleteRecursive();
            } catch (IOException e) {
                listener.getLogger()
                        .println(
                                "Private scanner files could not be removed. Clean or recycle the isolated agent before reuse.");
                run.setResult(Result.FAILURE);
            }
        }
    }

    static void applyPolicy(
            Run<?, ?> run, ScanResult result, boolean failOnIncomplete, String policyResult, TaskListener listener)
            throws AbortException {
        if (!result.isGateExceeded() && !(failOnIncomplete && result.isIncomplete())) return;
        String reason = result.isGateExceeded()
                ? "version-confirmed matches reached the configured severity threshold"
                : "inventory or vulnerability assessment is incomplete";
        if ("FAILURE".equals(policyResult)) {
            run.setResult(Result.FAILURE);
            throw new AbortException("Awarely policy: " + reason
                    + ". Build marked FAILURE; the step stops here. Inventory and report were preserved.");
        }
        run.setResult(Result.UNSTABLE);
        listener.getLogger()
                .println("Awarely policy: " + reason
                        + ". Build marked UNSTABLE. Use skipStagesAfterUnstable() to skip later Declarative stages.");
    }

    private static void publishResult(Run<?, ?> run, ScanResult result) throws IOException {
        synchronized (run) {
            AwarelyBuildAction action = run.getAction(AwarelyBuildAction.class);
            if (action == null) {
                action = new AwarelyBuildAction();
                run.addAction(action);
            }
            action.add(result);
            run.save();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static boolean isChangeRequest(Job<?, ?> job) {
        if (job.getParent() instanceof MultiBranchProject project)
            return project.getProjectFactory().getBranch(job).getHead() instanceof ChangeRequestSCMHead;
        return false;
    }

    private void validate() throws AbortException {
        try {
            ScanPolicy.choice(collector, ScanPolicy.COLLECTORS, "collector");
            ScanPolicy.choice(mode, ScanPolicy.MODES, "action");
            ScanPolicy.relativePath(path);
            ScanPolicy.label(applicationLabel);
            ScanPolicy.choice(severityThreshold, ScanPolicy.GATES, "threshold");
            ScanPolicy.choice(getPolicyResult(), Set.of("UNSTABLE", "FAILURE"), "policy result");
        } catch (IllegalArgumentException e) {
            throw new AbortException(e.getMessage());
        }
        if (!mode.equals("local") && (credentialsId == null || credentialsId.isBlank()))
            throw new AbortException("Select a Jenkins Secret file containing your Monitor credential JSON");
        if (preflightOnly && mode.equals("sync") && collector.equals("python"))
            throw new AbortException(
                    "Python requirements.txt produces a partial inventory and cannot synchronize. Choose python-syft with installed dependencies, or use local/check.");
        if (allPackages && !collector.equals("linux"))
            throw new AbortException("All packages applies only to Linux host inventory");
        if (!severityThreshold.equals("REPORT_ONLY") && !mode.equals("check"))
            throw new AbortException("A severity threshold requires the check action");
    }

    List<String> collectionArguments(String input, String output, boolean download) {
        List<String> args = new ArrayList<>();
        switch (collector) {
            case "linux" -> {
                args.add("host");
                if (allPackages) args.add("--all-packages");
            }
            case "npm", "python" -> args.addAll(List.of("app", "--ecosystem", collector, "--path", input));
            case "import" -> args.addAll(List.of("import", "--input", input));
            case "npm-syft", "python-syft", "java", "other" -> {
                args.addAll(List.of("syft", "--ecosystem", collector.replace("-syft", ""), "--path", input));
                if (download) args.add("--allow-download");
            }
            default -> throw new IllegalArgumentException("Unsupported collector");
        }
        args.addAll(List.of("--name", applicationLabel, "--output", output, "--timeout", "300"));
        return args;
    }

    static int execute(
            Launcher launcher,
            FilePath work,
            String home,
            String binary,
            List<String> args,
            InputStream credentials,
            TaskListener listener)
            throws IOException, InterruptedException {
        return execute(launcher, work, home, binary, args, credentials, listener, false);
    }

    static int execute(
            Launcher launcher,
            FilePath work,
            String home,
            String binary,
            List<String> args,
            InputStream credentials,
            TaskListener listener,
            boolean detailed)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(
                "/usr/bin/env",
                "-i",
                "HOME=" + home,
                "LANG=C.UTF-8",
                "PATH=/usr/bin:/bin",
                "AWARELY_RUN_COOKIE=" + UUID.randomUUID(),
                binary));
        command.addAll(args);
        // The fixed executable handles structured input. No shell interprets paths or labels. Child output can contain
        // untrusted data and is not logged.
        try (SafeDiagnostics diagnostics = new SafeDiagnostics(listener, detailed)) {
            Launcher.ProcStarter starter = launcher.launch()
                    // Launcher expands dollar macros even without a shell. Escape them once so paths/labels remain
                    // literal.
                    .cmds(command.stream()
                            .map(value -> value.replace("$", "$$"))
                            .toList())
                    .quiet(true)
                    .pwd(work)
                    .stdout(OutputStream.nullOutputStream())
                    .stderr(diagnostics);
            if (credentials != null) starter.stdin(credentials);
            Proc process = starter.start();
            int status;
            try {
                status = process.joinWithTimeout(360, TimeUnit.SECONDS, listener);
            } catch (IOException | InterruptedException e) {
                try {
                    process.kill();
                } catch (IOException | InterruptedException cleanup) {
                    e.addSuppressed(cleanup);
                }
                throw e;
            }
            if (status != 0 && diagnostics.message() != null) throw new AbortException(diagnostics.message());
            return status;
        }
    }

    private List<String> diagnosticArguments(List<String> arguments) {
        List<String> result = new ArrayList<>(arguments);
        result.addAll(List.of("--log-format", "events"));
        if (detailedLogging) result.add("--verbose");
        return result;
    }

    private static String collectionError(int code) {
        return switch (code) {
            case 2 ->
                "Collection failed: review the selected directory, supported manifest/artifact types and verified-tool availability. Nothing was uploaded.";
            case 4 -> "Collection could not publish a private output. Check agent disk space and permissions.";
            case 5 -> "Collection was interrupted or exceeded its deadline. Nothing was uploaded.";
            default -> "Collection failed. No inventory was uploaded; review agent health and supported inputs.";
        };
    }

    private static String remoteError(int code) {
        return switch (code) {
            case 2 ->
                "Credential or inventory validation failed. Check credential scope/format and use a valid Awarely inventory.";
            case 4 ->
                "API operation could not save its receipt. A sync may have committed; inspect the source before retrying.";
            case 5 ->
                "API operation was interrupted or timed out. A sync may have committed; inspect the source before retrying.";
            default ->
                "Monitor operation failed. Check credential validity/scope, source revision, quotas, catalog availability and HTTPS connectivity. No successful assessment is reported.";
        };
    }

    private static void validateReceipt(byte[] bytes, int components) throws IOException {
        var n = BoundedJson.read(bytes);
        if (n.path("schemaVersion").asInt() != 1
                || !"committed".equals(n.path("status").asText())
                || BoundedJson.count(n, "sourceComponentCount", 5000) != components)
            throw new IOException("Invalid sync receipt");
    }

    @Extension
    @Symbol("awarelyScan")
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> type) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Awarely Scan: inventory, check or synchronize";
        }

        public ListBoxModel doFillCollectorItems() {
            ListBoxModel items = new ListBoxModel();
            items.add("npm lockfile", "npm");
            items.add("npm installed files (Syft)", "npm-syft");
            items.add("Python requirements.txt (partial)", "python");
            items.add("Python installed environment (Syft)", "python-syft");
            items.add("Java built artifacts (Syft)", "java");
            items.add("NuGet / Go and other application ecosystems (Syft)", "other");
            items.add("Linux agent host", "linux");
            items.add("Import external CycloneDX application SBOM", "import");
            items.add("Existing Awarely SBOM", "existing");
            return items;
        }

        public ListBoxModel doFillModeItems() {
            ListBoxModel items = new ListBoxModel();
            items.add("Save local inventory only", "local");
            items.add("Check CVEs", "check");
            items.add("Synchronize one source", "sync");
            return items;
        }

        public ListBoxModel doFillSeverityThresholdItems() {
            ListBoxModel items = new ListBoxModel();
            items.add("Report only", "REPORT_ONLY");
            items.add("Critical", "CRITICAL");
            items.add("High", "HIGH");
            items.add("Medium", "MEDIUM");
            items.add("Low", "LOW");
            return items;
        }

        public ListBoxModel doFillPolicyResultItems() {
            ListBoxModel items = new ListBoxModel();
            items.add("UNSTABLE — report and continue", "UNSTABLE");
            items.add("FAILURE — stop this step", "FAILURE");
            return items;
        }

        @RequirePOST
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item) {
            if (item == null || !item.hasPermission(Item.CONFIGURE)) return new ListBoxModel();
            return new StandardListBoxModel()
                    .includeEmptyValue()
                    .includeAs(hudson.security.ACL.SYSTEM2, item, FileCredentials.class);
        }
    }
}
