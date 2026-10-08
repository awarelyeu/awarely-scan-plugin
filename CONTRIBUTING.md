# Contributing

Use JDK 21 and Maven. Run `mvn -B -ntp verify` from this repository root. The output is `target/awarely-scan.hpi`; local snapshots are for development only. The Jenkins baseline and dependency BOM are in `pom.xml`.

Open a pull request against `main` with a focused description, relevant validation and documentation in both English and Romanian where user behavior changes. Do not commit tokens, private inventories, lab addresses or production configuration. Use [private reporting](SECURITY.md#reporting) for security issues.

GitHub Actions builds on Linux amd64 and arm64, checks for committed secrets, and runs the Jenkins-specific CodeQL rules. A green scan job is not proof that there are no findings: maintainers must review the Code Scanning alerts. Changes to managed tool versions must use `scripts/pin-cli.py` and preserve the authenticated source/workflow/tag/digest checks.

`Jenkinsfile` supplies the Jenkins project `buildPlugin()` entry point once hosting is approved. `.mvn/` enables Jenkins Incrementals; publication still requires infrastructure permissions. Release automation is deliberately gated as described in [hosting readiness](docs/hosting.md).

## Repository extraction

This repository preserves the two plugin commits from `awarelyeu/awarely-sbom-scanner` using `git subtree split --prefix=jenkins-plugin` at commit `ededef42ee16aea0dc351b1cf570695c283efe6f`. The extracted tip is `02532d3b27dd270151e4864b7d1b6bd6efebd435`. Source files, test files and the pinned CLI manifest are unchanged by extraction; metadata, workflows and documentation are adjusted for the standalone repository.

The historical preview `jenkins-v0.1.0-alpha.1` and CLI releases stay in their original repository so existing provenance verification and downloads remain valid. Future plugin changes belong here; CLI changes belong in the scanner repository.
