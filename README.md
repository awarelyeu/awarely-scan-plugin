# Awarely Scan for Jenkins

**Preview: 0.1.0-alpha.2. Test on a staging controller before production use.**

[Download and verify the HPI](docs/releases.md). Pins: CLI v0.10.0 and managed Syft 1.54.1.

Generate an SBOM during a build, check it with Awarely Monitor, and explicitly synchronize one inventory source when appropriate. The plugin uses the same CLI and managed Syft as the terminal workflow. It does not build your application or install its dependencies.

[English walkthrough](docs/how-to.md) · [Ghid în română](docs/how-to.ro.md) · [Security boundaries](SECURITY.md)

This is the dedicated plugin repository. The [Awarely CLI](https://github.com/awarelyeu/awarely-sbom-scanner) has its own source and release cycle. Jenkins project hosting has not yet been approved; the plugin is not currently in the Jenkins Update Center. See [hosting readiness](docs/hosting.md).

[![Build](https://github.com/awarelyeu/awarely-scan-plugin/actions/workflows/build.yml/badge.svg)](https://github.com/awarelyeu/awarely-scan-plugin/actions/workflows/build.yml)
[![Jenkins Security Scan](https://github.com/awarelyeu/awarely-scan-plugin/actions/workflows/jenkins-security-scan.yml/badge.svg)](https://github.com/awarelyeu/awarely-scan-plugin/actions/workflows/jenkins-security-scan.yml)

Download the current **0.1.0-alpha.2** preview from [this repository's releases](https://github.com/awarelyeu/awarely-scan-plugin/releases/tag/v0.1.0-alpha.2). The historical **0.1.0-alpha.1** preview and its original signatures remain in the scanner repository; see [release verification](docs/releases.md).

## Supported workflows

| Collector | Input in the build workspace | Collection |
| --- | --- | --- |
| `npm` | Directory with an npm lockfile | Native manifest collection |
| `npm-syft` | Built/installed Node.js directory | Managed Syft |
| `python` | Directory with requirements.txt | Native, partial inventory |
| `python-syft` | Directory containing an installed virtual environment | Managed Syft |
| `java` | Built Java artifacts and their dependencies | Managed Syft |
| `other` | NuGet, Go, Composer, RubyGems or Cargo application files | Managed Syft; CVE assessment currently unevaluated |
| `linux` | The Linux Jenkins agent | Focused packages, or all installed packages |
| `import` | An external CycloneDX application SBOM | Validated application-package import |
| `existing` | A previously generated Awarely inventory | Reuse without rescanning |

Actions are `local` (default), `check` and `sync`. Linux collection inventories the **agent**, not a container image or production server. See the CLI [coverage contract](https://github.com/awarelyeu/awarely-sbom-scanner/blob/main/docs/coverage.md).

## Build from source

Use JDK 21 and Maven with the Jenkins baseline declared in `pom.xml`:

```sh
mvn -B -ntp verify
```

The output is `target/awarely-scan.hpi`. Development tool manifests deliberately fail closed until release archive and executable digests have been authenticated. Do not replace pins with arbitrary downloads or bypass verification.

Future Jenkins releases use the Jenkins Incrementals/CD version scheme. A local development build is a snapshot, not a replacement for the published preview. See [contributing](CONTRIBUTING.md) and [release verification](docs/releases.md).
