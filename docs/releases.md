# Plugin releases and verification

The plugin now has its own repository. It is not currently listed in the Jenkins Update Center. The tested preview below was published before extraction and remains immutable in the scanner repository. Its tag, checksum and attestation identity must not be changed to the new repository name.

Future Jenkins-hosted releases will be published to the Jenkins Maven repository through the official Jenkins CD workflow, which feeds Jenkins update sites. Incrementals provide development previews; a GitHub prerelease flag alone does not publish to any Jenkins update site. See [hosting readiness and activation](hosting.md).

The first preview is **0.1.0-alpha.1**, built for CLI **v0.10.0** and managed Syft **1.54.1**. Download all four files below from its [release page](https://github.com/awarelyeu/awarely-sbom-scanner/releases/tag/jenkins-v0.1.0-alpha.1) into a new empty directory:

- `awarely-scan-jenkins-0.1.0-alpha.1.hpi`
- `awarely-scan-jenkins-0.1.0-alpha.1.hpi.sigstore.jsonl`
- `awarely-scan-jenkins-0.1.0-alpha.1.cdx.json`
- `SHA256SUMS`

Before installing controller code, run this in that directory with an up-to-date GitHub CLI:

```sh
gh attestation verify awarely-scan-jenkins-0.1.0-alpha.1.hpi \
  --bundle awarely-scan-jenkins-0.1.0-alpha.1.hpi.sigstore.jsonl \
  --repo awarelyeu/awarely-sbom-scanner \
  --signer-workflow awarelyeu/awarely-sbom-scanner/.github/workflows/jenkins-release.yml \
  --source-ref refs/tags/jenkins-v0.1.0-alpha.1 \
  --deny-self-hosted-runners
sha256sum --check SHA256SUMS
```

On macOS, replace the last command with `shasum -a 256 -c SHA256SUMS`. Public bundles do not require a GitHub account; trust-root updates still need internet access. Stop if provenance or digests fail. If `gh` is unavailable, follow the official [GitHub CLI installation guide](https://github.com/cli/cli#installation); it is a verifier for this administrator step, not a scanner runtime dependency. The attestation identifies the build source, not a guarantee that code has no vulnerabilities.

Each release contains the HPI, a CycloneDX dependency inventory, checksum list and public attestation bundles. The dependency inventory includes provided Jenkins/plugin dependencies, not only JARs embedded in the HPI. The HPI carries Maven license metadata; this plugin is Apache-2.0 and its Jenkins dependencies retain their own licenses.

## Maintainers

1. Authenticate a published CLI release with `python3 scripts/pin-cli.py --version vVERSION`. The CLI remains in `awarelyeu/awarely-sbom-scanner`; that verification identity is intentionally unchanged. No CLI binary is executed during pin generation.
2. Review pins, both walkthroughs and changes to authority, dependencies or release workflows. The plugin ID remains `awarely-scan` so Jenkins recognizes future updates.
3. Build from the repository root with `mvn -B -ntp verify`. Runtime changes require appropriate Pipeline/Freestyle acceptance tests on isolated Linux amd64/arm64 agents. The repository extraction preserves the already completed tests; it does not require new cloud machines.
4. Before the first Jenkins-hosted release, complete [hosting approval and CD activation](hosting.md). The prepared CD workflow is manual, disabled outside the canonical Jenkins repository, and defaults to validation only. Jenkins infrastructure must confirm its CI/release permissions first.
5. Publish a new version through Jenkins CD, then verify and stage that version before promotion. Never replace the original preview, rewrite its tag or reuse its version for a different HPI.

The POM and `.mvn/` files use the standard Jenkins changelist/Incrementals scheme. Local snapshots are not release artifacts. The previous monorepo packaging script is retained in Git history; it is superseded by Jenkins Maven publication.

Updating CLI pins is a plugin release decision. Build jobs cannot run arbitrary versions or automatic updates. A compromised release requires withdrawal/advisory and a new version, not silent replacement.
