package io.jenkins.plugins.awarely;

import static org.junit.jupiter.api.Assertions.*;

import hudson.FilePath;
import hudson.model.TaskListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
@EnabledOnOs(OS.LINUX)
class PinnedToolsAgentTest {
    @Test
    void transferredArchiveAndCachedExecutableAreVerifiedOnTheAgent(JenkinsRule j) throws Exception {
        var agent = j.createOnlineSlave();
        FilePath root = agent.getRootPath();
        FilePath workspace = root.child("workspace");
        workspace.mkdirs();
        var session = workspace.act(new AgentFiles.Prepare(root.getRemote(), ".", "npm"));
        byte[] binary = "synthetic test executable, never launched".getBytes(StandardCharsets.UTF_8);
        byte[] archive = archive(binary);
        Properties pins = pins(archive, binary);
        FilePath work = new FilePath(workspace, session.work);
        String executable = work.act(new PinnedTools.Prepare(pins, session.home, archive, TaskListener.NULL, false));
        assertArrayEquals(binary, new FilePath(work, executable).act(new AgentFiles.Read()));
        assertEquals(0700, new FilePath(work, executable).mode() & 0777);
        var cached = workspace.act(new AgentFiles.Prepare(root.getRemote(), ".", "npm"));
        // No network or replacement bytes are needed when the public archive is already cached.
        PinnedTools.prepare(new FilePath(workspace, cached.work), pins, cached.home, false, TaskListener.NULL, false);
        new FilePath(workspace, cached.home)
                .child("awarely-scan_v0.12.0_linux_" + PinnedTools.architecture() + ".tar.gz")
                .write("modified", StandardCharsets.UTF_8.name());
        var modified = workspace.act(new AgentFiles.Prepare(root.getRemote(), ".", "npm"));
        assertThrows(
                IOException.class,
                () -> PinnedTools.prepare(
                        new FilePath(workspace, modified.work), pins, modified.home, true, TaskListener.NULL, false));
    }

    @Test
    void transferWithWrongArchiveOrExecutableDigestCannotPrepareATool(JenkinsRule j) throws Exception {
        var agent = j.createOnlineSlave();
        FilePath root = agent.getRootPath();
        FilePath workspace = root.child("workspace");
        workspace.mkdirs();
        byte[] binary = "synthetic".getBytes(StandardCharsets.UTF_8);
        byte[] archive = archive(binary);
        Properties pins = pins(archive, binary);
        var session = workspace.act(new AgentFiles.Prepare(root.getRemote(), ".", "npm"));
        var work = new FilePath(workspace, session.work);
        assertThrows(
                IOException.class,
                () -> work.act(new PinnedTools.Prepare(
                        pins,
                        session.home,
                        "wrong archive".getBytes(StandardCharsets.UTF_8),
                        TaskListener.NULL,
                        false)));
        assertFalse(work.child("awarely-scan").exists());
        pins.setProperty(PinnedTools.architecture() + ".binary", "0".repeat(64));
        assertThrows(
                IOException.class,
                () -> work.act(new PinnedTools.Prepare(pins, session.home, archive, TaskListener.NULL, false)));
        assertEquals(0, work.child("awarely-scan").mode() & 0111);
    }

    private static Properties pins(byte[] archive, byte[] binary) throws IOException {
        var pins = new Properties();
        pins.setProperty("version", "v0.12.0");
        for (String architecture : new String[] {"amd64", "arm64"}) {
            pins.setProperty(architecture + ".archive", PinnedTools.digest(archive));
            pins.setProperty(architecture + ".binary", PinnedTools.digest(binary));
        }
        return pins;
    }

    private static byte[] archive(byte[] binary) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var tar = new TarArchiveOutputStream(new GzipCompressorOutputStream(bytes))) {
            var entry = new TarArchiveEntry("awarely-scan");
            entry.setSize(binary.length);
            tar.putArchiveEntry(entry);
            tar.write(binary);
            tar.closeArchiveEntry();
        }
        return bytes.toByteArray();
    }
}
