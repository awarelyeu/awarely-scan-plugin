# Awarely Scan for Jenkins 0.1.0-alpha.3

This preview makes setup and build outcomes explicit:

- **Validate configuration only** checks the actual agent, paths, private output permissions, CLI cache/download policy, administrator approvals and credential format without running a scan, downloading tools or contacting Monitor. Pending downloads and untested Syft/API readiness are stated clearly. Field help includes concrete examples and recovery steps.
- **Choose UNSTABLE or FAILURE** for severity and incomplete-coverage policies. UNSTABLE remains the default for existing jobs. FAILURE preserves evidence and stops normal step execution; deliberate Pipeline error handling still applies.
- **Changes since the previous build** shows components added/removed and observed CVE-match differences from retained full reports. Scope/destination compatibility, completed baselines and coverage are required; changed catalog dates are disclosed. Missing data is not reported as remediation.

English and Romanian walkthroughs cover installation, preflight, Pipeline/Freestyle configuration, build policies and comparison limitations. The fixed CLI remains v0.10.0 with managed Syft 1.54.1. Tool verification, scoped credentials, separate host/sync approvals and sync ordering protections remain in place.

Requires Jenkins 2.580.1 or newer, JDK 21 on the tested baseline, and isolated non-root Linux amd64/arm64 agents. This is a GitHub preview HPI, not a Jenkins Update Center release. Verify its public provenance and checksum and test on staging before production use.

[English walkthrough](https://github.com/awarelyeu/awarely-scan-plugin/blob/main/docs/how-to.md) · [Ghid în română](https://github.com/awarelyeu/awarely-scan-plugin/blob/main/docs/how-to.ro.md) · [Release verification](https://github.com/awarelyeu/awarely-scan-plugin/blob/main/docs/releases.md)
