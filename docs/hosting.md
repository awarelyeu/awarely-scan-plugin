# Jenkins project hosting readiness

Status: standalone repository prepared; hosting request not submitted. Jenkins maintainers decide acceptance. The existing preview is not an Update Center release.

## Prepared in this repository

- Public standalone repository: https://github.com/awarelyeu/awarely-scan-plugin
- Stable identity: `io.jenkins.plugins:awarely-scan`, name `Awarely Scan`, packaging `hpi`.
- Apache-2.0 license in `LICENSE` and `pom.xml`; existing dependency licenses retained.
- Plugin source, tests, English/Romanian how-to, security boundaries and contributor guide at the repository root.
- Root `Jenkinsfile` using `buildPlugin()` with Java 21/Linux.
- GitHub build checks for Linux amd64/arm64, secret scanning and Jenkins Security Scan (CodeQL).
- Incrementals extension/configuration and changelist-based POM versioning.
- Official Jenkins CD reusable workflow and Release Drafter configuration, with controlled activation below.
- [Hosting request draft](hosting-request.md) with repository, identity, purpose and maintainer fields.

## Before sending the hosting request

1. Finish the owner's final manual acceptance of the [current verified preview](releases.md). GitHub previews are built and attested in this repository; the original alpha.1 remains unchanged in the scanner repository.
2. Create a Jenkins community account at https://accounts.jenkins.io, then sign in to https://issues.jenkins.io and https://repo.jenkins-ci.org as required by the hosting form. The owner confirmed that this account does not yet exist. Replace the clearly marked account field in the request draft; GitHub and Jenkins usernames are independent.
3. Review the default-branch Jenkins Security Scan alerts, build results and metadata. Submit the prepared draft using the [official hosting form](https://github.com/jenkins-infra/repository-permissions-updater/issues/new?template=1-hosting-request.yml) only when authorized. Request official GitHub-based releases; do not provide personal Maven tokens in an issue.

## Jenkins-controlled handoff and release activation

The hosting team forks or transfers the entire repository into `jenkinsci/awarely-scan-plugin`, grants maintainers access and arranges artifact permissions. They may request changes. No repository configuration can pre-approve this step.

After they confirm the canonical repository, update the `gitHubRepo` POM property and repository links/badges/reporting URLs to `jenkinsci/awarely-scan-plugin` in the hosting review. Follow the team's fork-network instructions; do not delete the original repository before their explicit handoff instructions and preservation of its auxiliary data. Historical CLI/preview URLs stay under `awarelyeu/awarely-sbom-scanner` permanently.

The official CD workflow is already in `.github/workflows/cd.yml`. It:

- only accepts manual `workflow_dispatch` runs;
- defaults to `validate_only: true`;
- requires the exact canonical repository, `main`, and repository variable `JENKINS_CD_APPROVED=true`;
- uses only Jenkins-provisioned `MAVEN_USERNAME` and `MAVEN_TOKEN`;
- delegates CI-status verification and publication to the official Jenkins reusable workflow.

Leave `JENKINS_CD_APPROVED` unset until Jenkins reviews this workflow and the corresponding repository-permissions-updater CD configuration, provisions credentials and enables successful `ci.jenkins.io` builds. Then run validation first; a subsequent manual release run may clear `validate_only`. Never bypass the Jenkins CI verification. The POM already uses `${changelist}` with Jenkins's standard Incrementals configuration, so no runtime implementation rewrite is needed.

Jenkins-hosted releases go to the Jenkins Maven repository, from which update sites obtain plugins. After hosting, development previews should use Incrementals; the experimental update center is deprecated. The pre-hosting `preview-release.yml` is restricted to `awarelyeu/awarely-scan-plugin` and produces verified HPI downloads for manual staging tests. A GitHub alpha/beta release alone is not publication to Jenkins.

## Official references

- [Hosting process](https://www.jenkins.io/doc/developer/publishing/requesting-hosting/)
- [Preparation and account requirements](https://www.jenkins.io/doc/developer/publishing/preparation/)
- [Naming convention](https://www.jenkins.io/doc/developer/publishing/style-guides/)
- [Jenkins CI / buildPlugin](https://www.jenkins.io/doc/developer/publishing/continuous-integration/)
- [Jenkins Security Scan](https://www.jenkins.io/doc/developer/security/scan/)
- [Incrementals](https://www.jenkins.io/doc/developer/plugin-development/incrementals/)
- [Official CD setup and review](https://www.jenkins.io/doc/developer/publishing/releasing-cd/)
- [Experimental release status](https://www.jenkins.io/doc/developer/publishing/releasing-experimental-updates/)
