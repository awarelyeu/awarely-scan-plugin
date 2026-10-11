package io.jenkins.plugins.awarely;

import static org.junit.jupiter.api.Assertions.*;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SecretBytes;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import hudson.model.Result;
import java.nio.charset.StandardCharsets;
import org.jenkinsci.plugins.plaincredentials.impl.FileCredentialsImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
@EnabledOnOs(OS.LINUX)
class PreflightAgentTest {
    @Test
    void approvedSyncPreflightDoesNotExecuteDownloadUploadOrMutateSource(JenkinsRule j) throws Exception {
        var agent = j.createOnlineSlave();
        var job = j.createFreeStyleProject("preflight-only");
        job.setAssignedNode(agent);
        var workspace = agent.getWorkspaceFor(job);
        workspace.mkdirs();
        workspace.child("package-lock.json").write("{}", "UTF-8");
        var config = AwarelyConfiguration.get();
        config.setAllowToolDownloads(true);
        config.setRemoteJobs(job.getFullName());
        config.setSyncJobs(job.getFullName());
        config.setTrustedAgents(agent.getNodeName());
        config.setApiOrigins("https://api.example.invalid");
        String token = "awscan_" + "a".repeat(32) + "_" + "b".repeat(43);
        String json = """
            {"schemaVersion":1,"apiUrl":"https://api.example.invalid","token":"%s",
             "applicationId":"11111111-1111-1111-1111-111111111111","sourceId":"22222222-2222-2222-2222-222222222222"}
            """.formatted(token);
        SystemCredentialsProvider.getInstance()
                .getCredentials()
                .add(new FileCredentialsImpl(
                        CredentialsScope.GLOBAL,
                        "monitor",
                        "",
                        "credential.json",
                        SecretBytes.fromBytes(json.getBytes(StandardCharsets.UTF_8))));
        var step = new AwarelyScanBuilder("npm");
        step.setMode("sync");
        step.setCredentialsId("monitor");
        step.setPreflightOnly(true);
        job.getBuildersList().add(step);
        var build = j.buildAndAssertSuccess(job);
        j.assertLogContains("no scan, download or Monitor request", build);
        j.assertLogContains("NOT TESTED: server connectivity", build);
        assertFalse(JenkinsRule.getLog(build).contains(token));
        assertTrue(build.getArtifacts().isEmpty());
        assertNull(build.getAction(AwarelyBuildAction.class));
        assertTrue(
                agent.getRootPath().child(".awarely-scan").list().isEmpty(), "No downloaded tools or leftover session");
        // Removing the sync approval must deny preflight just as it denies the real operation.
        config.setSyncJobs("");
        var denied = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
        j.assertLogContains("separately approve", denied);
        assertFalse(JenkinsRule.getLog(denied).contains("Secret file is accessible"));
    }

    @Test
    void missingPathFailsWithRecoveryAndWorkspaceEscapeIsRefused(JenkinsRule j) throws Exception {
        var agent = j.createOnlineSlave();
        var job = j.createFreeStyleProject();
        job.setAssignedNode(agent);
        var step = new AwarelyScanBuilder("npm");
        step.setPreflightOnly(true);
        step.setPath("missing");
        job.getBuildersList().add(step);
        var missing = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
        j.assertLogContains("Selected path does not exist", missing);
        var workspace = agent.getWorkspaceFor(job);
        workspace.child("escape").symlinkTo("/tmp", hudson.model.TaskListener.NULL);
        step.setPath("escape");
        var escaped = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
        j.assertLogContains("escapes the build workspace", escaped);
    }
}
