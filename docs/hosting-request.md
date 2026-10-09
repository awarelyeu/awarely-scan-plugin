# Hosting request draft — Awarely Scan

Not submitted. Complete the Jenkins account field after account creation and final manual acceptance. This maps to the official repository-permissions-updater hosting form.

## Repository URL

https://github.com/awarelyeu/awarely-scan-plugin

## New Repository Name

awarely-scan-plugin

## Description

Awarely Scan provides Jenkins Pipeline and Freestyle integration for local CycloneDX inventory, explicit Awarely Monitor CVE checks and scoped source synchronization on Linux amd64/arm64 agents. It supports npm, Python and Java applications, host packages, other application ecosystems through managed Syft, and existing SBOMs.

The integration combines native collectors and pinned verified CLI/Syft tools with Awarely Monitor's scoped check/sync API and explicit inventory/assessment coverage policies. Local inventory needs no Monitor account; remote actions require the customer's Monitor credentials. This is the integration point with Awarely's inventory and vulnerability service, rather than a general replacement for SBOM producers or a claim to evaluate every ecosystem.

The plugin uses administrator allowlists for remote actions, host inventory and trusted jobs/nodes; refuses privileged collection and credential use by change-request builds; bounds/parses untrusted reports; and keeps managed tool versions under maintainer control. English/Romanian user documentation, Apache-2.0 license, root Jenkinsfile, Incrementals and Jenkins Security Scan are included. Source history has been extracted into this standalone repository, which publishes provenance-attested HPI previews for manual staging tests. The original alpha.1 remains at its unchanged monorepo release URL.

## GitHub users to have commit permission

@awarelyeu

## Jenkins project users to have release permission

PENDING: create and verify the owner's Jenkins community account, then enter its username here without @. Do not assume it is the GitHub handle.

## Automated release via GitHub Actions (recommended)

Yes — official Jenkins CD, manually triggered after successful Jenkins CI. The workflow is prepared but activation is gated until hosting/CD review and Jenkins-provisioned permissions/credentials. See [hosting readiness](hosting.md).
