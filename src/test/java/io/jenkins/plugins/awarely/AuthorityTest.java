package io.jenkins.plugins.awarely;

import static org.junit.jupiter.api.Assertions.*;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SecretBytes;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import hudson.model.Item;
import hudson.model.User;
import hudson.security.ACL;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlSelect;
import org.jenkinsci.plugins.plaincredentials.impl.FileCredentialsImpl;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.springframework.security.access.AccessDeniedException;

@WithJenkins
class AuthorityTest {
    @Test
    void credentialDropdownRequiresPostCrumbAndConfigurePermission(JenkinsRule j) throws Exception {
        var project = j.createFreeStyleProject("credential-form");
        var step = new AwarelyScanBuilder("npm");
        step.setCredentialsId("synthetic-monitor");
        project.getBuildersList().add(step);
        SystemCredentialsProvider.getInstance()
                .getCredentials()
                .add(new FileCredentialsImpl(
                        CredentialsScope.GLOBAL,
                        "synthetic-monitor",
                        "Synthetic credential",
                        "monitor.json",
                        SecretBytes.fromBytes("synthetic-secret-content".getBytes(StandardCharsets.UTF_8))));
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ)
                .everywhere()
                .to("configurator", "reader")
                .grant(Item.READ)
                .onItems(project)
                .to("configurator", "reader")
                .grant(Item.CONFIGURE)
                .onItems(project)
                .to("configurator"));
        String endpoint = project.getUrl()
                + "descriptorByName/io.jenkins.plugins.awarely.AwarelyScanBuilder/fillCredentialsIdItems";
        try (var client = j.createWebClient().login("configurator")) {
            client.setThrowExceptionOnFailingStatusCode(false);
            var url = new URL(j.getURL(), endpoint);
            assertEquals(
                    405,
                    client.getPage(new WebRequest(url, HttpMethod.GET))
                            .getWebResponse()
                            .getStatusCode());
            assertEquals(
                    405,
                    client.getPage(client.createCrumbedUrl(endpoint))
                            .getWebResponse()
                            .getStatusCode());
            assertEquals(
                    403,
                    client.getPage(new WebRequest(url, HttpMethod.POST))
                            .getWebResponse()
                            .getStatusCode());
            var invalidCrumb = new WebRequest(url, HttpMethod.POST);
            invalidCrumb.setAdditionalHeader(j.jenkins.getCrumbIssuer().getCrumbRequestField(), "invalid");
            assertEquals(403, client.getPage(invalidCrumb).getWebResponse().getStatusCode());
            var response = client.getPage(client.addCrumb(new WebRequest(url, HttpMethod.POST)))
                    .getWebResponse();
            assertEquals(200, response.getStatusCode());
            assertTrue(response.getContentAsString().contains("synthetic-monitor"));
            assertFalse(response.getContentAsString().contains("synthetic-secret-content"));

            client.setThrowExceptionOnFailingStatusCode(true);
            var page = client.goTo(project.getUrl() + "configure");
            client.waitForBackgroundJavaScript(5000);
            HtmlSelect select = page.getFirstByXPath("//select[@name='_.credentialsId']");
            assertNotNull(select);
            assertTrue(select.getOptions().stream()
                    .anyMatch(option -> option.getValueAttribute().equals("synthetic-monitor")));
            select.setSelectedAttribute("synthetic-monitor", true);
            j.submit(page.getFormByName("config"));
            assertEquals(
                    "synthetic-monitor",
                    project.getBuildersList().get(AwarelyScanBuilder.class).getCredentialsId());
        }
        try (var client = j.createWebClient().login("reader")) {
            var response = client.getPage(
                            client.addCrumb(new WebRequest(new URL(j.getURL(), endpoint), HttpMethod.POST)))
                    .getWebResponse();
            assertEquals(200, response.getStatusCode());
            assertTrue(JSONObject.fromObject(response.getContentAsString())
                    .getJSONArray("values")
                    .isEmpty());
        }
        assertTrue(j.jenkins
                .getDescriptorByType(AwarelyScanBuilder.DescriptorImpl.class)
                .doFillCredentialsIdItems(null)
                .isEmpty());
    }

    @Test
    void reportSettingsAndCredentialEnumerationRespectPermissions(JenkinsRule j) throws Exception {
        var project = j.createFreeStyleProject("private-app");
        var run = j.buildAndAssertSuccess(project);
        var action = new AwarelyBuildAction();
        run.addAction(action);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ)
                .everywhere()
                .to("viewer")
                .grant(Item.READ)
                .onItems(project)
                .to("reader")
                .grant(Jenkins.ADMINISTER)
                .everywhere()
                .to("admin"));
        var descriptor = j.jenkins.getDescriptorByType(AwarelyScanBuilder.DescriptorImpl.class);
        try (var ignored = ACL.as(User.getById("viewer", true))) {
            assertThrows(AccessDeniedException.class, action::getTarget);
            assertThrows(
                    AccessDeniedException.class,
                    () -> AwarelyConfiguration.get()
                            .configure((org.kohsuke.stapler.StaplerRequest2) null, new net.sf.json.JSONObject()));
            assertTrue(descriptor.doFillCredentialsIdItems(project).isEmpty());
        }
        try (var ignored = ACL.as(User.getById("reader", true))) {
            assertSame(action, action.getTarget());
            assertTrue(descriptor.doFillCredentialsIdItems(project).isEmpty());
        }
    }

    @Test
    void allowlistsAreExactAndWritePermissionIsSeparate(JenkinsRule j) throws Exception {
        var allowed = j.createFreeStyleProject("approved");
        var other = j.createFreeStyleProject("approved-extra");
        var cfg = AwarelyConfiguration.get();
        assertFalse(cfg.permitsRemote(allowed));
        cfg.setRemoteJobs("approved");
        cfg.setApiOrigins("https://monitor.example.invalid");
        cfg.setTrustedAgents("agent");
        assertTrue(cfg.permitsRemote(allowed));
        assertFalse(cfg.permitsRemote(other));
        assertFalse(cfg.permitsSync(allowed));
        assertFalse(cfg.permitsHost(allowed));
        assertFalse(cfg.permitsAgent("agent-other"));
        assertFalse(cfg.permitsOrigin("https://monitor.example.invalid.attacker.invalid"));
    }
}
