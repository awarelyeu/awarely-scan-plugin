package io.jenkins.plugins.awarely;

import java.io.IOException;
import java.util.Map;

/** Only locally authored messages may become concise build errors. Never echo arbitrary exceptions. */
final class UserErrors {
    private UserErrors() {}

    private static final Map<String, String> MESSAGES = Map.ofEntries(
            Map.entry(
                    "Invalid normalized inventory",
                    "Invalid Awarely inventory. Select inventory.cdx.json with CycloneDX 1.6 and a components array; use the import collector for an external SBOM."),
            Map.entry(
                    "Selected input escapes the build workspace",
                    "Selected input escapes the build workspace. Choose a directory inside this job's workspace; links to external directories are not allowed."),
            Map.entry(
                    "Inventory input must not be a symbolic link",
                    "Inventory input must not be a symbolic link. Copy the inventory file into this job's workspace."),
            Map.entry(
                    "Choose an existing inventory file in the workspace",
                    "Choose an existing inventory file in the workspace."),
            Map.entry(
                    "Choose an existing application directory in the workspace",
                    "Choose an existing application directory in the workspace."),
            Map.entry(
                    "Selected path does not exist. Use a path relative to the workspace and check out/build the application before Awarely.",
                    "Selected path does not exist. Use a path relative to the workspace and check out/build the application before Awarely."),
            Map.entry(
                    "Selected path is not accessible to the agent user. Review the directory permissions.",
                    "Selected path is not accessible to the agent user. Review the directory permissions."),
            Map.entry(
                    "Pinned tool verification failed; nothing was executed",
                    "Pinned tool verification failed; nothing was executed. Ask an administrator to inspect the private tool cache."),
            Map.entry(
                    "Pinned scanner is absent. Ask an administrator to enable verified tool downloads or prepopulate the documented cache",
                    "Pinned scanner is absent. Ask an administrator to enable verified tool downloads or prepopulate the documented cache"),
            Map.entry(
                    "Pinned release download failed; check controller HTTPS/proxy access",
                    "Pinned release download failed; check the controller's HTTPS access and Jenkins proxy settings for GitHub."),
            Map.entry(
                    "Release download limit exceeded",
                    "Pinned CLI download exceeded its deadline. Review the controller's HTTPS/proxy connectivity and retry."),
            Map.entry(
                    "Invalid check report",
                    "Monitor returned an invalid check report. No successful assessment is reported; retry later or contact support."),
            Map.entry(
                    "Invalid sync receipt",
                    "Monitor's synchronization receipt could not be validated. Inspect the source in Monitor before retrying."));

    static String message(IOException error) {
        return MESSAGES.get(error.getMessage());
    }
}
