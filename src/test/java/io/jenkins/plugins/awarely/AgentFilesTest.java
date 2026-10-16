package io.jenkins.plugins.awarely;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs({OS.LINUX, OS.MAC})
class AgentFilesTest {
    @TempDir
    Path directory;

    @Test
    void boundedReadsRejectLinksAndDirectories() throws Exception {
        Path input = Files.writeString(directory.resolve("input"), "inventory");
        assertArrayEquals("inventory".getBytes(java.nio.charset.StandardCharsets.UTF_8), AgentFiles.read(input, 9));
        assertThrows(IOException.class, () -> AgentFiles.read(input, 8));
        assertThrows(IOException.class, () -> AgentFiles.read(directory, 100));
        Path link = Files.createSymbolicLink(directory.resolve("symbolic"), input);
        assertThrows(IOException.class, () -> AgentFiles.read(link, 100));
        Path hard = Files.createLink(directory.resolve("hard"), input);
        assertThrows(IOException.class, () -> AgentFiles.read(hard, 100));
        assertThrows(IOException.class, () -> AgentFiles.read(input, 100));
    }

    @Test
    void copiesArePrivateAndNeverReplaceExistingDestinations() throws Exception {
        Path input = Files.writeString(directory.resolve("input"), "inventory");
        Path output = directory.resolve("output");
        new AgentFiles.CopyInventory(output.toString()).invoke(input.toFile(), null);
        assertEquals("inventory", Files.readString(output));
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(output));
        Files.writeString(output, "keep");
        assertThrows(
                IOException.class, () -> new AgentFiles.CopyInventory(output.toString()).invoke(input.toFile(), null));
        assertEquals("keep", Files.readString(output));
        Path link = Files.createSymbolicLink(directory.resolve("link"), output);
        assertThrows(
                IOException.class, () -> new AgentFiles.CopyInventory(link.toString()).invoke(input.toFile(), null));
        assertEquals("keep", Files.readString(output));
    }
}
