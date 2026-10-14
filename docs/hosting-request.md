# Hosting request — Awarely Scan

Submitted on 2026-10-14: [Jenkins hosting request #5383](https://github.com/jenkins-infra/repository-permissions-updater/issues/5383). Awaiting hosting-team review; this is not an approval or an Update Center release. The owner confirmed the required initial Jira and Artifactory sign-ins for Jenkins username `awarely_ro`.

## Repository URL

https://github.com/awarelyeu/awarely-scan-plugin

## New Repository Name

awarely-scan-plugin

## Description

Awarely Scan provides Jenkins Pipeline and Freestyle integration for local CycloneDX inventory, explicit Awarely Monitor CVE checks and scoped source synchronization on Linux amd64/arm64 agents. It supports npm, Python and Java applications, NuGet libraries, Go modules, host packages, other application ecosystems through managed Syft, and existing SBOMs. NuGet library and Go module assessment does not cover the .NET runtime or Go toolchain/standard library.

The integration combines native collectors and pinned verified CLI/Syft tools with Awarely Monitor's scoped check/sync API and explicit inventory/assessment coverage policies. Local inventory needs no Monitor account; remote actions require the customer's Monitor Pro access and scoped credentials. Its specific purpose is integrating Jenkins with Awarely's inventory and vulnerability service: source-scoped synchronization, explicit check coverage and build policies for version-confirmed findings. It does not claim to evaluate every ecosystem.

The plugin uses administrator allowlists for remote actions, host inventory and trusted jobs/nodes; refuses privileged collection and credential use by change-request builds; bounds/parses untrusted reports; and keeps managed tool versions under maintainer control. English/Romanian user documentation, Apache-2.0 license, root Jenkinsfile, Incrementals and Jenkins Security Scan are included. Source history has been extracted into this standalone repository, which publishes provenance-attested HPI releases, including stable 0.1.0 following manual staging acceptance and 0.2.0 with automated NuGet/Go acceptance. The original alpha.1 remains at its unchanged monorepo release URL.

The default-branch [Build](https://github.com/awarelyeu/awarely-scan-plugin/actions/runs/38480581323) and [Jenkins Security Scan](https://github.com/awarelyeu/awarely-scan-plugin/actions/runs/38480581851) passed for commit `a710386ffe72055d7e428819e8cdb811d4f27643`. No open GitHub CodeQL alerts were reported when preparing this request.

Official Jenkins CD is prepared for manual triggering after successful Jenkins CI, with activation gated on hosting/CD review and Jenkins-provisioned credentials. See [hosting readiness](https://github.com/awarelyeu/awarely-scan-plugin/blob/main/docs/hosting.md).

Repository handoff: if possible, please use a coordinated transfer rather than deleting the original repository after a fork. This repository already contains six releases with HPI assets and provenance bundles, plus pull-request review history. We would like to preserve those assets, links and review records while making `jenkinsci/awarely-scan-plugin` canonical. We will follow the hosting team's instructions for the handoff and subsequent repository metadata changes.

## GitHub users to have commit permission

@awarelyeu

## Jenkins project users to have release permission

awarely_ro

## Automated release via GitHub Actions (recommended)

Yes
