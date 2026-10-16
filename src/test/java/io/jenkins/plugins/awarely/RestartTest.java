package io.jenkins.plugins.awarely;

import static org.junit.jupiter.api.Assertions.*;

import hudson.console.LineTransformationOutputStream;
import hudson.model.Result;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.log.TaskListenerDecorator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension;

class RestartTest {
    @RegisterExtension
    final RealJenkinsExtension controller = new RealJenkinsExtension().withTimeout(120);

    @Test
    void interruptedActualStepFailsAfterRestartWithoutReplay() throws Throwable {
        controller.startJenkins();
        controller.run(j -> {
            // Pause the actual builder at its first diagnostic, before any agent or Monitor access.
            j.jenkins.getExtensionList(TaskListenerDecorator.Factory.class).add(owner -> new PauseAwarely());
            var job = j.createProject(WorkflowJob.class, "restart-awarely");
            job.setDefinition(new CpsFlowDefinition(
                    "node { awarelyScan collector: 'npm', mode: 'sync', credentialsId: 'synthetic-never-read'; echo 'DEPLOY_MUST_NOT_RUN' }",
                    true));
            var build = job.scheduleBuild2(0).waitForStart();
            j.waitForMessage("RESTART_TEST_PAUSED", build);
            assertTrue(build.isBuilding());
        });
        controller.stopJenkinsForcibly();
        controller.startJenkins();
        controller.run(j -> {
            var build = j.jenkins
                    .getItemByFullName("restart-awarely", WorkflowJob.class)
                    .getLastBuild();
            j.assertBuildStatus(Result.FAILURE, j.waitForCompletion(build));
            String log = String.join("\n", build.getLog(1000));
            assertTrue(log.contains("SynchronousResumeNotSupportedException"), log);
            assertFalse(log.contains("DEPLOY_MUST_NOT_RUN"), log);
            assertFalse(log.contains("Synchronizing only"), log);
            assertNull(build.getAction(AwarelyBuildAction.class));
            assertEquals(1, log.split("RESTART_TEST_PAUSED", -1).length - 1, log);
        });
    }

    private static final class PauseAwarely extends TaskListenerDecorator {
        private static final long serialVersionUID = 1L;

        @Override
        public OutputStream decorate(OutputStream out) {
            return new LineTransformationOutputStream() {
                @Override
                protected void eol(byte[] bytes, int length) throws IOException {
                    out.write(bytes, 0, length);
                    if (new String(bytes, 0, length, StandardCharsets.UTF_8)
                            .contains("[Awarely] Validating job permissions")) {
                        out.write("RESTART_TEST_PAUSED\n".getBytes(StandardCharsets.UTF_8));
                        out.flush();
                        try {
                            new CountDownLatch(1).await();
                        } catch (InterruptedException error) {
                            Thread.currentThread().interrupt();
                            throw new java.io.InterruptedIOException("Test controller stopped");
                        }
                    }
                }
            };
        }
    }
}
