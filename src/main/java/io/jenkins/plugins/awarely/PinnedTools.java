package io.jenkins.plugins.awarely;

import hudson.remoting.VirtualChannel;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Properties;
import jenkins.MasterToSlaveFileCallable;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

/** The plugin release authenticates fixed archive and executable digests. No job controls versions or URLs. */
final class PinnedTools {
    private PinnedTools() {}

    static Properties manifest() throws IOException {
        Properties p = new Properties();
        try (InputStream in = PinnedTools.class.getResourceAsStream("/io/jenkins/plugins/awarely/tools.properties")) {
            if (in == null) throw new IOException("Missing release tool manifest");
            p.load(in);
        }
        if (!p.getProperty("version", "").matches("v[0-9]+\\.[0-9]+\\.[0-9]+(?:-alpha\\.[0-9]+)?"))
            throw new IOException("Invalid pinned tool version");
        for (String arch : ListHolder.ARCHES)
            for (String kind : new String[] {"archive", "binary"})
                if (!p.getProperty(arch + "." + kind, "").matches("[0-9a-f]{64}"))
                    throw new IOException(
                            "This development plugin has no authenticated tool pins; use a published release");
        return p;
    }

    private static final class ListHolder {
        static final String[] ARCHES = {"amd64", "arm64"};
    }

    static String digest(byte[] b) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 unavailable");
        }
    }

    /** Network access stays on the controller so Jenkins proxy credentials never reach agents. */
    static String prepare(
            hudson.FilePath work,
            Properties pins,
            String home,
            boolean download,
            hudson.model.TaskListener listener,
            boolean detailed)
            throws IOException, InterruptedException {
        String missingArch = work.act(new MissingArchive(pins, home));
        byte[] archive = null;
        if (missingArch != null) {
            if (!java.util.List.of("amd64", "arm64").contains(missingArch))
                throw new IOException("Unsupported agent architecture");
            if (!download)
                throw new IOException(
                        "Pinned scanner is absent. Ask an administrator to enable verified tool downloads or prepopulate the documented cache");
            listener.getLogger()
                    .println("[Awarely] Downloading the pinned CLI through the controller's Jenkins proxy settings.");
            String version = pins.getProperty("version");
            String file = "awarely-scan_" + version + "_linux_" + missingArch + ".tar.gz";
            archive = ReleaseDownload.fetch(URI.create(
                    "https://github.com/awarelyeu/awarely-sbom-scanner/releases/download/" + version + "/" + file));
            if (!digest(archive).equals(pins.getProperty(missingArch + ".archive")))
                throw new IOException("Pinned tool verification failed; nothing was executed");
        }
        return work.act(new Prepare(pins, home, archive, listener, detailed));
    }

    private static final class MissingArchive extends MasterToSlaveFileCallable<String> {
        private static final long serialVersionUID = 1L;
        private final Properties pins;
        private final String home;

        MissingArchive(Properties pins, String home) {
            this.pins = pins;
            this.home = home;
        }

        @Override
        public String invoke(File directory, VirtualChannel channel) throws IOException {
            AgentFiles.privateDirectory(directory.toPath());
            AgentFiles.privateDirectory(Path.of(home));
            String arch = architecture();
            Path archive =
                    Path.of(home).resolve("awarely-scan_" + pins.getProperty("version") + "_linux_" + arch + ".tar.gz");
            if (!Files.exists(archive, LinkOption.NOFOLLOW_LINKS)) return arch;
            verify(archive, pins.getProperty(arch + ".archive"), 40 * 1024 * 1024);
            return null;
        }
    }

    static final class Prepare extends MasterToSlaveFileCallable<String> {
        private static final long serialVersionUID = 1L;
        private final Properties pins;
        private final String home;
        private final byte[] downloadedArchive;
        private final hudson.model.TaskListener listener;
        private final boolean detailed;

        Prepare(
                Properties pins,
                String home,
                byte[] downloadedArchive,
                hudson.model.TaskListener listener,
                boolean detailed) {
            this.pins = pins;
            this.home = home;
            this.downloadedArchive = downloadedArchive;
            this.listener = listener;
            this.detailed = detailed;
        }

        @Override
        public String invoke(File directory, VirtualChannel channel) throws IOException, InterruptedException {
            Path work = directory.toPath();
            AgentFiles.privateDirectory(work);
            String arch = architecture();
            listener.getLogger().println("[Awarely] Agent architecture: Linux " + arch + ".");
            String version = pins.getProperty("version"),
                    file = "awarely-scan_" + version + "_linux_" + arch + ".tar.gz";
            Path cache = Path.of(home);
            AgentFiles.privateDirectory(cache);
            Path archive = cache.resolve(file);
            if (!Files.exists(archive, LinkOption.NOFOLLOW_LINKS)) {
                if (downloadedArchive == null || downloadedArchive.length > 40 * 1024 * 1024)
                    throw new IOException("Verified CLI archive is unavailable; retry tool preparation");
                if (!digest(downloadedArchive).equals(pins.getProperty(arch + ".archive")))
                    throw new IOException("Pinned tool verification failed; nothing was executed");
                Path temp = Files.createTempFile(
                        cache,
                        "download-",
                        ".tmp",
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                try {
                    Files.write(temp, downloadedArchive, java.nio.file.StandardOpenOption.WRITE);
                    verify(temp, pins.getProperty(arch + ".archive"), 40 * 1024 * 1024);
                    try {
                        Files.move(temp, archive, StandardCopyOption.ATOMIC_MOVE);
                    } catch (java.nio.file.FileAlreadyExistsException concurrentDownload) {
                        verify(archive, pins.getProperty(arch + ".archive"), 40 * 1024 * 1024);
                    }
                } finally {
                    Files.deleteIfExists(temp);
                }
            }
            verify(archive, pins.getProperty(arch + ".archive"), 40 * 1024 * 1024);
            if (detailed)
                listener.getLogger().println("[Awarely] CLI archive in cache passed pinned digest verification.");
            Path binary = work.resolve("awarely-scan");
            boolean found = false;
            long expanded = 0;
            int entries = 0;
            try (InputStream in = Files.newInputStream(archive, LinkOption.NOFOLLOW_LINKS);
                    GzipCompressorInputStream gz = new GzipCompressorInputStream(in);
                    TarArchiveInputStream tar = new TarArchiveInputStream(gz)) {
                TarArchiveEntry entry;
                while ((entry = tar.getNextEntry()) != null) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    expanded += entry.getSize();
                    if (entry.getSize() < 0 || expanded > 100L * 1024 * 1024 || ++entries > 200)
                        throw new IOException("Release archive exceeds extraction limits");
                    if (!entry.getName().equals("awarely-scan")) continue;
                    if (found
                            || !entry.isFile()
                            || entry.isLink()
                            || entry.isSymbolicLink()
                            || entry.getSize() > 40L * 1024 * 1024)
                        throw new IOException("Invalid release executable entry");
                    found = true;
                    try (var out = Files.newOutputStream(
                            binary,
                            java.nio.file.StandardOpenOption.CREATE_NEW,
                            java.nio.file.StandardOpenOption.WRITE)) {
                        Files.setPosixFilePermissions(binary, PosixFilePermissions.fromString("rw-------"));
                        long copied = tar.transferTo(out);
                        if (copied != entry.getSize()) throw new IOException("Incomplete release executable");
                    }
                }
            }
            if (!found) throw new IOException("Release executable is missing");
            verify(binary, pins.getProperty(arch + ".binary"), 40 * 1024 * 1024);
            Files.setPosixFilePermissions(binary, PosixFilePermissions.fromString("rwx------"));
            if (detailed)
                listener.getLogger().println("[Awarely] Extracted CLI executable passed pinned digest verification.");
            return binary.toString();
        }
    }

    static String architecture() throws IOException {
        return switch (System.getProperty("os.arch")) {
            case "amd64", "x86_64" -> "amd64";
            case "aarch64", "arm64" -> "arm64";
            default -> throw new IOException("Unsupported agent architecture; choose a Linux amd64 or arm64 agent");
        };
    }

    static void verify(Path file, String expected, int limit) throws IOException {
        if (expected == null
                || !expected.matches("[0-9a-f]{64}")
                || !digest(AgentFiles.read(file, limit)).equals(expected))
            throw new IOException("Pinned tool verification failed; nothing was executed");
    }
}
