# 0.1.0-alpha.5 — Clear console progress and safe diagnostics

All collectors, Pipeline and Freestyle steps explain progress, coverage, policy, report locations and elapsed time. Optional `detailedLogging` adds bounded cache verification and operational details. CLI v0.11.0 provides payload-free diagnostic codes for actionable API errors. Expected malformed-inventory and workspace-path failures produce concise build errors. Raw child output, API bodies and credentials are never forwarded. Scanner behavior, permissions, policy semantics and managed Syft 1.54.1 are unchanged.

# Awarely Scan for Jenkins 0.1.0-alpha.4

The build sidebar now shows Awarely Scan with a bundled shield-and-A icon. The previous icon referenced an unavailable Jenkins symbol and displayed a fallback X. The new SVG follows the Jenkins theme, works offline and adds no plugin dependency.

This is a presentation-only correction to alpha.3. Inventory collection, API checks, synchronization, preflight, build policies and build comparison behavior are unchanged. The pinned CLI remains **v0.10.1**, with managed Syft **1.54.1**. English and Romanian guides identify the sidebar icon and link to the updated preview.

Requires Jenkins 2.580.1 or newer, JDK 21 on the tested baseline, and isolated non-root Linux amd64/arm64 agents. This is a GitHub preview HPI, not a Jenkins Update Center release. Verify its public provenance and checksum and test on staging before production use.

[English walkthrough](https://github.com/awarelyeu/awarely-scan-plugin/blob/main/docs/how-to.md) · [Ghid în română](https://github.com/awarelyeu/awarely-scan-plugin/blob/main/docs/how-to.ro.md) · [Release verification](https://github.com/awarelyeu/awarely-scan-plugin/blob/main/docs/releases.md)
