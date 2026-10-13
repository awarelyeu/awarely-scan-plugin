package io.jenkins.plugins.awarely;

import static org.junit.jupiter.api.Assertions.*;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.DataBoundConstructor;

@WithJenkins
class PolicyPipelineTest {
    @Test
    void failureStopsFollowingStepsWhileUnstableContinues(JenkinsRule j) throws Exception {
        for (String policy : new String[] {"FAILURE", "UNSTABLE"}) {
            var job = j.createProject(WorkflowJob.class, "policy-" + policy);
            job.setDefinition(new CpsFlowDefinition(
                    "node { step([$class: 'PolicyFixture', policy: '" + policy + "']); echo 'DEPLOYMENT_REACHED' }",
                    true));
            var run = j.assertBuildStatus(Result.fromString(policy), job.scheduleBuild2(0));
            j.assertLogContains("Awarely policy: version-confirmed matches", run);
            assertEquals(policy.equals("UNSTABLE"), JenkinsRule.getLog(run).contains("DEPLOYMENT_REACHED"));
        }
    }

    @Test
    void traversalFailsBeforeAgentAccessWithoutBindingStackTrace(JenkinsRule j) throws Exception {
        var job = j.createProject(WorkflowJob.class, "traversal");
        job.setDefinition(new CpsFlowDefinition(
                "node { awarelyScan collector: 'npm', path: '../outside'; echo 'UNSAFE_REACHED' }", true));
        var run = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
        j.assertLogContains("Parent traversal", run);
        String log = JenkinsRule.getLog(run);
        assertFalse(log.contains("UNSAFE_REACHED"));
        assertFalse(log.contains("InvocationTargetException"));
        assertFalse(log.contains("java.lang.IllegalArgumentException"));
        assertFalse(log.contains("Validating job permissions"));
    }

    public static final class PolicyFixture extends Builder implements SimpleBuildStep {
        private final String policy;

        @DataBoundConstructor
        public PolicyFixture(String policy) {
            this.policy = policy;
        }

        public String getPolicy() {
            return policy;
        }

        @Override
        public void perform(Run<?, ?> run, FilePath workspace, EnvVars env, Launcher launcher, TaskListener listener)
                throws IOException {
            var result = ScanResult.check(
                    "app",
                    "npm",
                    "awarely/test",
                    ScanResultTest.report("version", "HIGH", "complete", "[]").getBytes(StandardCharsets.UTF_8),
                    "HIGH");
            AwarelyScanBuilder.applyPolicy(run, result, true, policy, listener);
        }

        @TestExtension
        @Symbol("awarelyPolicyFixture")
        public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
            @Override
            public boolean isApplicable(Class<? extends AbstractProject> type) {
                return true;
            }

            @Override
            public String getDisplayName() {
                return "Policy fixture";
            }
        }
    }
}
