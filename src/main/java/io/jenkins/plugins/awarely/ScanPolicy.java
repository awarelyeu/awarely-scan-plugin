package io.jenkins.plugins.awarely;

/** Validated job options. Legacy Pipeline/XML values remain stable. */
public final class ScanPolicy {
    public interface Option {
        String getId();

        String getDescription();
    }

    public enum Collector implements Option {
        NPM("npm", "npm lockfile"),
        NPM_SYFT("npm-syft", "npm installed files (Syft)"),
        PYTHON("python", "Python requirements.txt (partial)"),
        PYTHON_SYFT("python-syft", "Python installed environment (Syft)"),
        JAVA("java", "Java built artifacts (Syft)"),
        OTHER("other", "NuGet / Go and other application ecosystems (Syft)"),
        LINUX("linux", "Linux agent host"),
        IMPORT("import", "Import external CycloneDX application SBOM"),
        EXISTING("existing", "Existing Awarely SBOM");
        private final String id, description;

        Collector(String id, String description) {
            this.id = id;
            this.description = description;
        }

        public String getId() {
            return id;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum Mode implements Option {
        LOCAL("local", "Save local inventory only"),
        CHECK("check", "Check CVEs"),
        SYNC("sync", "Synchronize one source");
        private final String id, description;

        Mode(String id, String description) {
            this.id = id;
            this.description = description;
        }

        public String getId() {
            return id;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum Threshold implements Option {
        REPORT_ONLY("Report only"),
        CRITICAL("Critical"),
        HIGH("High"),
        MEDIUM("Medium"),
        LOW("Low");
        private final String description;

        Threshold(String description) {
            this.description = description;
        }

        public String getId() {
            return name();
        }

        public String getDescription() {
            return description;
        }
    }

    public enum PolicyResult implements Option {
        UNSTABLE("UNSTABLE — report and continue"),
        FAILURE("FAILURE — stop this step");
        private final String description;

        PolicyResult(String description) {
            this.description = description;
        }

        public String getId() {
            return name();
        }

        public String getDescription() {
            return description;
        }
    }

    private ScanPolicy() {}

    static <E extends Enum<E> & Option> E option(String value, Class<E> type, String field) {
        for (E option : type.getEnumConstants()) if (option.getId().equals(value)) return option;
        throw new IllegalArgumentException("Unsupported " + field);
    }

    static String relativePath(String input) {
        if (input == null
                || input.isBlank()
                || input.length() > 2048
                || input.startsWith("/")
                || input.contains("\\")) {
            throw new IllegalArgumentException("Use a path relative to the build workspace");
        }
        for (String part : input.split("/", -1)) {
            if (part.equals("..") || part.isEmpty())
                throw new IllegalArgumentException("Parent traversal and empty path segments are not allowed");
        }
        if (input.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.FORMAT)) {
            throw new IllegalArgumentException("Path contains unsupported characters");
        }
        return input;
    }

    static String label(String value) {
        if (value == null
                || value.isBlank()
                || value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 120
                || value.codePoints()
                        .anyMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.FORMAT)) {
            throw new IllegalArgumentException(
                    "Application label must be 1–120 UTF-8 bytes without control characters");
        }
        return value;
    }

    static int severity(String severity) {
        return switch (severity) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }
}
