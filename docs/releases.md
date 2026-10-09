# Plugin releases and verification

The current GitHub preview is **0.1.0-alpha.2**, built in this dedicated plugin repository. It uses CLI **v0.10.0** and managed Syft **1.54.1**. It is not currently listed in the Jenkins Update Center; install the verified HPI on a staging controller for acceptance testing.

Download these four files from the [v0.1.0-alpha.2 release](https://github.com/awarelyeu/awarely-scan-plugin/releases/tag/v0.1.0-alpha.2) into a new empty directory:

- `awarely-scan-jenkins-0.1.0-alpha.2.hpi`
- `awarely-scan-jenkins-0.1.0-alpha.2.hpi.sigstore.jsonl`
- `awarely-scan-jenkins-0.1.0-alpha.2.cdx.json`
- `SHA256SUMS`

Before installing controller code, run this in that directory with an up-to-date GitHub CLI:

```sh
gh attestation verify awarely-scan-jenkins-0.1.0-alpha.2.hpi \
  --bundle awarely-scan-jenkins-0.1.0-alpha.2.hpi.sigstore.jsonl \
  --repo awarelyeu/awarely-scan-plugin \
  --signer-workflow awarelyeu/awarely-scan-plugin/.github/workflows/preview-release.yml \
  --source-ref refs/tags/v0.1.0-alpha.2 \
  --deny-self-hosted-runners
sha256sum --check SHA256SUMS
```

On macOS, replace the last command with `shasum -a 256 -c SHA256SUMS`. Public bundles do not require a GitHub account; trust-root updates still need internet access. Stop if provenance or digests fail. If `gh` is unavailable, follow the official [GitHub CLI installation guide](https://github.com/cli/cli#installation); it is a verifier for this administrator step, not a scanner runtime dependency.

The public attestation covers the HPI, dependency inventory and checksum list. Separate bundle files are provided for each subject; the bundle downloaded above also lets you authenticate the SBOM by repeating `gh attestation verify` with the `.cdx.json` file and the same policy flags. The attestation identifies the build source, not a guarantee that code has no vulnerabilities.

After verification, follow the [English](how-to.md) or [Romanian](how-to.ro.md) installation guide. Jenkins keeps the plugin ID `awarely-scan`, so this HPI updates an installation of the previous preview. Keep a controller backup and test on staging first.

The CycloneDX inventory includes provided Jenkins/plugin dependencies, not only JARs embedded in the HPI. The HPI carries Maven license metadata; this plugin is Apache-2.0 and its dependencies retain their own licenses.

## Historical preview

The original **0.1.0-alpha.1** remains at its [original scanner-repository release](https://github.com/awarelyeu/awarely-sbom-scanner/releases/tag/jenkins-v0.1.0-alpha.1). Its files and tag are unchanged. Verify that historical HPI using `--repo awarelyeu/awarely-sbom-scanner`, `--signer-workflow awarelyeu/awarely-sbom-scanner/.github/workflows/jenkins-release.yml` and `--source-ref refs/tags/jenkins-v0.1.0-alpha.1`, with its original bundle. Do not apply the new repository's identity to an old artifact.

## Maintainers: GitHub previews before Jenkins hosting

1. Authenticate CLI pins with `python3 scripts/pin-cli.py --check`. To deliberately update them, use `python3 scripts/pin-cli.py --version vVERSION` and review the resulting change. The CLI remains in the scanner repository.
2. Review source, both walkthroughs, release notes and publication changes through a PR. Require Linux amd64/arm64 build checks, packaging guard tests, secret scanning and Jenkins Security Scan. Runtime changes need appropriate Pipeline/Freestyle acceptance tests on isolated agents.
3. After merge, create a new `vX.Y.Z-alpha.N` tag at the reviewed commit on `main`. Never reuse a version or move an existing tag.
4. `.github/workflows/preview-release.yml` runs only in `awarelyeu/awarely-scan-plugin`. It runs the build matrix, verifies the tagged commit belongs to `main`, authenticates CLI pins, builds with explicit `changelist` and `scmTag` values, and checks the packaged HPI, SBOM and embedded pins before attesting and publishing a new GitHub prerelease.
5. Download the published assets and verify provenance and checksums against that exact repository, workflow and tag before staging the HPI. Publication fails if the release already exists; fix a defective published artifact with a new version rather than replacement.

The POM and `.mvn/` retain Jenkins's standard changelist/Incrementals configuration. Only the preview build supplies an explicit alpha version; ordinary local builds remain snapshots.

## Future Jenkins-hosted releases

Jenkins-hosted releases use the official Maven/CD workflow, which feeds Jenkins update sites. Complete [hosting approval and CD activation](hosting.md) first. The prepared `cd.yml` is manual, disabled outside the canonical Jenkins repository, and defaults to validation only. Jenkins infrastructure must confirm CI/release permissions. A GitHub prerelease does not publish to any Jenkins update site.

After hosting, use Jenkins Incrementals for development previews and official CD for releases. The pre-hosting GitHub preview workflow is restricted to the Awarely repository and will not publish from `jenkinsci`.
