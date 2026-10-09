# Awarely Scan for Jenkins 0.1.0-alpha.2

This preview is built and published from the dedicated `awarelyeu/awarely-scan-plugin` repository, with updated repository metadata, CI actions and English/Romanian installation guides. The HPI, dependency SBOM and checksums have public provenance attestations tied to this repository and release tag. The plugin runtime source and fixed CLI/Syft versions are unchanged from 0.1.0-alpha.1.

Collect an application SBOM during a build, check it against Awarely Monitor and explicitly synchronize a configured source after deployment. The plugin supports Linux agents, native npm/Python manifests, managed Syft for npm/Python/Java/other ecosystems, external CycloneDX application imports and existing Awarely inventories.

Pipeline and Freestyle jobs share the same CLI, private outputs, credential handling and reports. Administrators approve exact jobs, agent nodes and API origins; synchronization and Linux host inventory require separate approval. Severity gates use version-confirmed matches; partial inventories and unevaluated components have an independent policy. API failures remain failures and preserve the local SBOM.

Jenkins 2.580.1 or newer and Linux amd64/arm64 agents are required. JDK 21 is the tested runtime. Use isolated agents for trusted jobs. This preview is distributed as a provenance-attested HPI in this repository, not through the Jenkins Update Center. Verify the artifact before installation.

[English walkthrough](https://github.com/awarelyeu/awarely-scan-plugin/blob/main/docs/how-to.md) · [Ghid în română](https://github.com/awarelyeu/awarely-scan-plugin/blob/main/docs/how-to.ro.md) · [Release verification](https://github.com/awarelyeu/awarely-scan-plugin/blob/main/docs/releases.md)
