package io.jenkins.plugins.awarely;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreflightTest {
    @TempDir
    Path directory;

    @Test
    void missingManifestExplainsTheActualRecovery() throws Exception {
        var messages = new ArrayList<String>();
        var error = assertThrows(IOException.class, () -> Preflight.checkInput(directory, "npm", messages));
        assertTrue(error.getMessage().contains("package-lock.json"));
        Files.writeString(directory.resolve("package.json"), "{}");
        Preflight.checkInput(directory, "npm", messages);
        assertTrue(messages.getFirst().contains("coverage may be partial"));
        assertThrows(IOException.class, () -> Preflight.checkInput(directory, "python", new ArrayList<>()));
    }

    @Test
    void manifestSymlinkCannotReadAnOutsideFile() throws Exception {
        Path external = Files.createTempFile("awarely-outside-", ".json");
        try {
            Files.createSymbolicLink(directory.resolve("package-lock.json"), external);
            assertThrows(IOException.class, () -> Preflight.checkInput(directory, "npm", new ArrayList<>()));
        } finally {
            Files.deleteIfExists(external);
        }
    }

    @Test
    void downloadPermissionIsNotReportedAsCachedOrNetworkVerified() throws Exception {
        Properties pins = new Properties();
        pins.setProperty("version", "v0.10.0");
        pins.setProperty("amd64.archive", "0".repeat(64));
        var messages = new ArrayList<String>();
        Preflight.checkArchive(directory, pins, "amd64", true, messages);
        assertTrue(messages.getFirst().startsWith("PENDING:"));
        assertEquals(0, directory.toFile().list().length, "Preflight cannot download or create an archive");
        assertThrows(IOException.class, () -> Preflight.checkArchive(directory, pins, "amd64", false, messages));
        Files.writeString(directory.resolve("awarely-scan_v0.10.0_linux_amd64.tar.gz"), "tampered");
        assertThrows(
                IOException.class,
                () -> Preflight.checkArchive(directory, pins, "amd64", true, messages),
                "Download permission cannot bypass a failed hash");
    }

    @Test
    void invalidExistingInventoryCannotPassPreflight() throws Exception {
        Path input = directory.resolve("bad.json");
        Files.writeString(input, "{\"bomFormat\":\"CycloneDX\",\"specVersion\":\"1.6\"}");
        assertThrows(IOException.class, () -> Preflight.checkInput(input, "existing", new ArrayList<>()));
    }
}
