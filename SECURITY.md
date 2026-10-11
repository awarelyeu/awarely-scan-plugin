# Jenkins integration security boundaries

The Jenkins plugin is controller code. Install only a reviewed, provenance-verified release and restrict Jenkins administration. The CLI's [security contract](https://github.com/awarelyeu/awarely-sbom-scanner/blob/main/SECURITY.md) still applies.

## Controller, agent and project separation

Collection and CLI processes run on Linux agents, never on the built-in controller node and never as root. Paths are relative to the selected workspace and resolved on that agent; parent traversal and escaping symlinks are rejected. Runtime commands use structured argument lists and a fixed executable, without a shell. Child environments are reset; unrelated Jenkins, cloud, Git and API credentials are not forwarded.

The controller retains only bounded report projections and explicit artifacts. Reports are treated as untrusted JSON, with byte/depth/string/count bounds and duplicate-key rejection. Display fields are escaped in Jelly. No package-supplied HTML, URL, description or terminal sequence is rendered as active content. Build results and artifacts require the job's read permission.

These are not OS sandbox controls. An attacker who already executes arbitrary code as the agent's OS user can access that user's processes, files and capabilities. Use dedicated or disposable agents with CPU, memory, disk and network limits. Keep external PRs and untrusted builds off credential-bearing agents, including across sequential builds. Jenkins administrators must enforce scheduling/trust isolation; a node label by itself is not an access-control boundary.

## Credentials and remote authority

API use requires an administrator allowlist for the exact job, trusted node and credential destination origin. Host inventory and synchronization each require separate job approvals. Multibranch change-request heads cannot use credentials or scan the host, even if the parent project is trusted. Jobs still need appropriate Jenkins credential scope/access. A repository contributor who can change a trusted pipeline must be treated as having that pipeline's authority; protect release/deployment branches and job configuration.

Credentials are Jenkins Secret files, read after local collection for API operations. Configuration-only preflight may also read and validate their bounded format on the controller, after the same exact job/agent/origin and change-request authority checks; it never transmits the credential or performs an API call. The credential bytes are bounded and passed through stdin to a fixed verified CLI; they are not stored in workspaces, command arguments, environment variables, report actions or artifacts. Arbitrary child stdout/stderr are not copied into the build log. A bounded recognizer translates a small allowlist of exact CLI errors into fixed diagnostic messages; raw child text is discarded. The CLI verifies TLS, refuses redirects/proxy discovery and sends only normalized identities, versions and evidence. Never provide production credentials to untrusted code, even check-only credentials.

## Managed tools

The published plugin fixes the CLI version, release archive digest and executable digest for both supported architectures. Maintainers authenticate those pins against the CLI's public build provenance before release. A job cannot choose another version, binary path or download URL. The original archive is rehashed on each use; only the expected regular executable is extracted into a fresh private directory and its digest is checked again. Archive expansion, download sizes, redirects, timeouts and paths are bounded. Tool preparation uses public HTTPS release endpoints without credentials.

Managed Syft follows the pinned CLI release and repeats its own archive verification. Its parser receives a minimal environment and fixed offline settings. Download permission is administrator-controlled. There is no background updater or executable lookup from the project's PATH. A checksum authenticates content only through the trusted plugin release that pins it; downloading a checksum next to an arbitrary archive would not provide this chain of trust.

## Publication and build outcomes

Only the explicit SBOM, check and sync files are archived. Generated/imported SBOMs are normalized by the CLI. An explicitly selected existing SBOM is preserved as supplied and may contain its original metadata; choose and protect that artifact accordingly. Raw Syft output and private staging directories are removed after the step. Artifacts still contain potentially sensitive software identities; restrict their readers and retention. Same-user tampering and compromised agent kernels/filesystems are outside the plugin's trust boundary.

Severity thresholds apply to version-confirmed matches. Policy results default to UNSTABLE for compatibility; FAILURE raises a step failure after preserving evidence. Pipeline authors can explicitly catch errors, so deployment controls must account for their orchestration. Incomplete collection/assessment has a separate policy. API/tool/input failures cannot become a zero-finding success. Local export and sync do not imply a vulnerability check.

Sync refuses partial and empty inventories. The plugin serializes syncs and writes a persistent high-water mark before API transmission, rejecting older builds or a different job for the same credential destination/application/source. The API applies revision/idempotency rules. Use one writer job per source and never silently reset bindings or blindly replay uncertain commits. Interruption or controller restart may leave a committed request without its receipt; inspect the source before retrying.

## Preflight and comparison

Configuration-only preflight executes as a build step on the real agent, after normal authority checks; there is no new controller validation endpoint that accesses paths, secrets or networks. It creates and cleans private staging directories but executes no tool, downloads nothing and does not acquire or modify sync ordering state. It distinguishes verified local facts from pending downloads and untested server/Syft readiness. It is not a dry run of other job steps and is not a security verdict.

Build comparison reads only bounded, explicitly named artifacts of the previous retained run in the same job. A completed scan identity binds collector, configured path, label, Linux scope/agent, pinned CLI and destination source (never the token). No comparison searches across jobs or untrusted PR boundaries; duplicates and unfinished baselines are refused. Full report projections are recomputed from archived evidence, while only counts and a bounded escaped change preview are persisted. Missing artifacts or incompatible coverage produce an unavailable explanation. No removed match is declared fixed; catalog/window changes are visible and comparison never weakens the current severity/incomplete policy.

## Reporting

Use this repository's private GitHub vulnerability reporting. Include the plugin, Jenkins, CLI and Java versions and a synthetic reproduction. Do not publish real tokens, inventories, cloud identifiers or private build logs.
