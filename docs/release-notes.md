# Awarely Scan for Jenkins 0.2.0

NuGet/.NET libraries and Go modules now support Monitor CVE checks through the existing Syft collector. The plugin pins CLI **v0.12.0** and managed Syft **1.54.1**. The collector label and help explain the preparation required, supported assessment and remaining coverage gaps.

NuGet and Go findings use the existing per-component version precision, severity gate, incomplete-assessment policy and build comparison. Backend checks require exact ecosystem/package identity and ecosystem-specific version ordering. Unknown versions and advisory syntax remain visible; Go toolchain/stdlib assessment is outside this module check. Job permissions, credential isolation, source ownership and tool verification remain enforced.

[English walkthrough](https://github.com/awarelyeu/awarely-scan-plugin/blob/main/docs/how-to.md#nugetnet-libraries-and-go-modules) · [Ghid în română](https://github.com/awarelyeu/awarely-scan-plugin/blob/main/docs/how-to.ro.md) · [Verify the release](https://github.com/awarelyeu/awarely-scan-plugin/blob/main/docs/releases.md)

This is a stable GitHub HPI release. Jenkins Update Center publication remains pending community hosting approval.
