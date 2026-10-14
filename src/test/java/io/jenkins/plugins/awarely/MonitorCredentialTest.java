package io.jenkins.plugins.awarely;

import static org.junit.jupiter.api.Assertions.*;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SecretBytes;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.jenkinsci.plugins.plaincredentials.impl.FileCredentialsImpl;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class MonitorCredentialTest {
    private static final String APP = "11111111-1111-4111-8111-111111111111";
    private static final String SOURCE = "22222222-2222-4222-8222-222222222222";
    private static final String ORIGIN = "https://api.example.invalid";

    private static byte[] addCredential(String id, String origin, String source, String token) {
        byte[] json = ("{\"schemaVersion\":1,\"apiUrl\":\"" + origin + "\",\"applicationId\":\"" + APP
                        + "\",\"sourceId\":\"" + source + "\",\"token\":\"" + token + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        SystemCredentialsProvider.getInstance()
                .getCredentials()
                .add(new FileCredentialsImpl(
                        CredentialsScope.GLOBAL,
                        id,
                        "Synthetic Monitor credential",
                        "monitor.json",
                        SecretBytes.fromBytes(json)));
        return json;
    }

    @Test
    void sourceIdentityExcludesTokenAndPreservesPersistedWriterBindings(JenkinsRule j) throws Exception {
        String firstToken = "awscan_" + "a".repeat(32) + "_" + "b".repeat(43);
        String rotatedToken = "awscan_" + "c".repeat(32) + "_" + "d".repeat(43);
        String canonicalEquivalent = "https://API.EXAMPLE.INVALID:443";
        AwarelyConfiguration.get().setApiOrigins(ORIGIN + "\n" + canonicalEquivalent);
        byte[] original = addCredential("original", ORIGIN, SOURCE, firstToken);
        addCredential("rotated", canonicalEquivalent, SOURCE, rotatedToken);
        addCredential("other-source", ORIGIN, "33333333-3333-4333-8333-333333333333", rotatedToken);
        var run = j.buildAndAssertSuccess(j.createFreeStyleProject("writer"));
        var competing = j.buildAndAssertSuccess(j.createFreeStyleProject("competing"));
        String expected = "e3e36a710fea6befbc5e0b3cdbfe6f7a64ca0b83d85318931759590d6c1b76a5";
        try (var credential = MonitorCredential.load(run, "original")) {
            assertEquals(expected, credential.sourceKey);
            assertArrayEquals(original, credential.input().readAllBytes());
            try (var lease = SyncState.acquire(credential.sourceKey, run)) {
                assertNotNull(lease);
            }
            var result = ScanResult.local(
                    "app", "npm", "local", "awarely/test", BuildComparisonTest.inventory("1.0.0"), false);
            result.complete(
                    BuildComparison.scope("npm", "check", ".", "app", false, "", "v0.12.0", credential.sourceKey));
            var action = new AwarelyBuildAction();
            action.add(result);
            run.addAction(action);
            run.save();
        }
        var state = j.jenkins.getExtensionList(SyncState.class).get(0);
        String persistedState = Files.readString(j.jenkins.getRootDir().toPath().resolve(state.getId() + ".xml"));
        assertTrue(persistedState.contains(expected));
        assertFalse(persistedState.contains(firstToken));
        assertFalse(persistedState.contains(rotatedToken));
        String persistedBuild = Files.readString(run.getRootDir().toPath().resolve("build.xml"));
        assertFalse(persistedBuild.isBlank());
        assertFalse(persistedBuild.contains(firstToken));
        assertFalse(persistedBuild.contains(rotatedToken));
        state.load();
        try (var rotated = MonitorCredential.load(run, "rotated")) {
            assertEquals(expected, rotated.sourceKey);
            assertThrows(java.io.IOException.class, () -> SyncState.acquire(rotated.sourceKey, competing));
            try (var lease = SyncState.acquire(rotated.sourceKey, run)) {
                assertNotNull(lease);
            }
        }
        try (var other = MonitorCredential.load(run, "other-source")) {
            assertNotEquals(expected, other.sourceKey);
        }
        var closed = MonitorCredential.load(run, "original");
        var retainedBuffer = closed.input();
        closed.close();
        assertArrayEquals(new byte[original.length], retainedBuffer.readAllBytes());
    }
}
