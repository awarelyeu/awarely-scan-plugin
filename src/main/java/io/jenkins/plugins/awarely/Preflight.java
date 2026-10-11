package io.jenkins.plugins.awarely;

import hudson.remoting.VirtualChannel;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import jenkins.MasterToSlaveFileCallable;

/** A job execution on its real agent, not a controller HTTP endpoint or an API dry-run. */
final class Preflight {
    private Preflight() {}

    static final class Check extends MasterToSlaveFileCallable<List<String>> {
        private static final long serialVersionUID = 1L;
        private final String home, input, collector;
        private final Properties pins;
        private final boolean downloads;

        Check(String home, String input, String collector, Properties pins, boolean downloads) {
            this.home = home;
            this.input = input;
            this.collector = collector;
            this.pins = pins;
            this.downloads = downloads;
        }

        @Override
        @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
                value = "DMI_HARDCODED_ABSOLUTE_FILENAME",
                justification =
                        "Linux-only prerequisite: validate the same trusted /usr/bin/env used by execution, never project PATH.")
        public List<String> invoke(File work, VirtualChannel channel) throws IOException {
            AgentFiles.privateDirectory(work.toPath());
            String arch = PinnedTools.architecture();
            if (!Files.isExecutable(Path.of("/usr/bin/env")))
                throw new IOException(
                        "Install the distribution's coreutils package: /usr/bin/env is required on the agent.");
            List<String> messages = new ArrayList<>();
            messages.add(
                    "PASS: non-root Linux " + arch + " agent, private output directory and workspace-contained input.");
            checkInput(Path.of(input), collector, messages);
            checkArchive(Path.of(home), pins, arch, downloads, messages);
            if (List.of("npm-syft", "python-syft", "java", "other").contains(collector)) {
                messages.add(
                        "NOT TESTED: managed Syft cache and artifact contents. The verified CLI checks these during collection; no Syft or project code runs in this preflight.");
                messages.add(
                        downloads
                                ? "INFO: the real scan may download its pinned Syft if missing; Node.js, Python, Java, gh and Cosign are not scanner prerequisites."
                                : "ACTION: downloads are disabled. An administrator must prepare the CLI's verified Syft cache before the real scan.");
            }
            return messages;
        }
    }

    static void checkArchive(Path home, Properties pins, String arch, boolean downloads, List<String> messages)
            throws IOException {
        Path archive = home.resolve("awarely-scan_" + pins.getProperty("version") + "_linux_" + arch + ".tar.gz");
        if (Files.exists(archive, LinkOption.NOFOLLOW_LINKS)) {
            PinnedTools.verify(archive, pins.getProperty(arch + ".archive"), 40 * 1024 * 1024);
            messages.add("PASS: cached CLI archive matches this plugin's pinned SHA-256. Execution is not tested.");
        } else if (downloads) {
            messages.add(
                    "PENDING: CLI is not cached. The real scan will download and verify the pinned release. Network availability is not tested here.");
        } else {
            throw new IOException(
                    "Pinned CLI is missing and downloads are disabled. Ask an administrator to enable verified tool downloads or prepare the pinned archive cache, then rerun this check.");
        }
    }

    static void checkInput(Path input, String collector, List<String> messages) throws IOException {
        if (!Files.isReadable(input))
            throw new IOException("Selected input is not readable by the agent user; review its permissions.");
        if (collector.equals("npm")) {
            boolean lock = readableChild(input, "package-lock.json") || readableChild(input, "npm-shrinkwrap.json");
            if (!lock && !readableChild(input, "package.json"))
                throw new IOException(
                        "No npm manifest found. Choose the directory containing package-lock.json, npm-shrinkwrap.json or package.json; check out the application first.");
            messages.add(
                    lock
                            ? "PASS: npm lockfile is readable. Its format and package versions are validated during scanning."
                            : "INFO: only package.json was found. Add an npm lockfile for resolved dependencies; coverage may be partial.");
        } else if (collector.equals("python")) {
            if (!readableChild(input, "requirements.txt"))
                throw new IOException(
                        "requirements.txt is missing or unreadable. Correct the path, or choose Python installed environment after creating the virtual environment.");
            messages.add(
                    "PASS: requirements.txt is readable. This collector produces a partial inventory; sync cannot use it.");
        } else if (collector.equals("existing")) {
            var result = ScanResult.local(
                    "preflight", "existing", "local", "", AgentFiles.read(input, 2 * 1024 * 1024), false);
            messages.add(
                    result.isIncomplete()
                            ? "INFO: existing Awarely inventory has partial or unspecified coverage; synchronization will be refused."
                            : "PASS: existing inventory has the expected envelope and declared complete selected-input coverage. The CLI validates component contents during the actual operation.");
        } else if (collector.equals("import")) {
            var json = BoundedJson.read(AgentFiles.read(input, BoundedJson.LIMIT));
            if (!"CycloneDX".equals(json.path("bomFormat").asText())
                    || !json.path("components").isArray())
                throw new IOException(
                        "Choose a CycloneDX JSON file with a components array. Component support is validated during import.");
            messages.add("PASS: readable CycloneDX JSON envelope. Component support is checked during import.");
        } else {
            messages.add(
                    "PASS: selected path is readable. Built artifacts, installed dependencies and host package support are checked during collection.");
        }
    }

    private static boolean readableChild(Path directory, String name) throws IOException {
        Path child = directory.resolve(name);
        if (!Files.exists(child, LinkOption.NOFOLLOW_LINKS)) return false;
        if (!Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(child))
            throw new IOException("Selected manifest must be a readable regular file, not a symbolic link.");
        return true;
    }
}
