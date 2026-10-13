# Awarely Scan for Jenkins 0.1.0

First stable GitHub release, promoting the runtime tested in 0.1.0-alpha.5. CLI **v0.11.0** and managed Syft **1.54.1** remain pinned; collector behavior, permissions, policy and synchronization semantics are unchanged.

## Available workflows

- Pipeline and Freestyle inventory for npm, Python, Java, Linux agent packages, other Syft ecosystems, and existing/imported SBOMs.
- Local CycloneDX export, explicit Monitor CVE checks, or synchronization of one configured source.
- Configuration preflight, readable console progress, optional bounded detailed logging, complete JSON artifacts, and comparison with compatible previous builds.
- Version-confirmed severity policies with report-only, UNSTABLE or FAILURE outcomes; incomplete assessment coverage remains explicit.
- Administrator job/node/origin approvals, private tool/results directories, verified pinned downloads, and one trusted writer job per synchronized source.

## Validation and installation

Manual acceptance covered Pipeline/Freestyle, Linux amd64/arm64 agents, baseline and updated applications, policy outcomes and deployment gating, synchronization, and denied job/scope/path/partial-inventory/competing-writer cases. Remote acceptance used an isolated staging tenant and clearly identified synthetic CVE fixtures. These tests do not imply that a successful check proves an application secure.

Release CI rebuilds and tests the plugin on Linux amd64 and arm64, validates artifact versions and CLI pins, and publishes provenance attestations for the HPI, dependency SBOM and checksums. Release refs must target a GitHub-verified commit; unsigned annotated tags are rejected. Published versions are never replaced.

Requires **Jenkins 2.580.1+**, JDK 21 on the tested baseline, and isolated non-root Linux amd64/arm64 agents. Verify the HPI, back up your controller and validate your setup on staging before upgrading. Existing alpha installations keep the same `awarely-scan` plugin ID.

**Distribution:** this stable release is available as a manually installed HPI on GitHub. Jenkins project hosting and Update Center publication are pending; this release does not claim Jenkins project approval.

[Download and verification](https://github.com/awarelyeu/awarely-scan-plugin/blob/main/docs/releases.md) · [English walkthrough](https://github.com/awarelyeu/awarely-scan-plugin/blob/main/docs/how-to.md) · [Ghid în română](https://github.com/awarelyeu/awarely-scan-plugin/blob/main/docs/how-to.ro.md)
