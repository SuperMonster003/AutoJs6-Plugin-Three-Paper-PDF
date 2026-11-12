# Staged binary artifacts

This directory holds the exact, hash-locked binary artifacts the plugin consumes. Gradle never resolves
them from sibling repositories or from `mavenLocal()`: `app/build.gradle.kts` reads `../locks/host-api-aars.lock`
and fails configuration when a staged file is missing, listed with an invalid digest or different from its
locked SHA-256.

## Host contract AARs (lock `../locks/host-api-aars.lock`)

Release artifacts of the AutoJs6 host, consumed with `implementation` because the plugin runs in its own
process and needs the AIDL stubs and constants at run time.

| File | Host module | Content | SHA-256 |
| --- | --- | --- | --- |
| `common-plugin-api.aar` | `plugin-api/common-plugin-api` | `PluginInfo`, `IPluginInfoProvider`, `PluginActions`, `PluginCapabilityKeys`, `AutoJs6HostSettingsContract`, `PluginSettingsTransferProvider` | `9849b15091dcb255cd1c47140790081eebc074aec2e3e9b28c18f12bf9ef4a6a` |
| `explorer-action-api.aar` | `plugin-api/explorer-action-api` | `IExplorerActionPlugin` and the archive session interfaces, `ExplorerActionProtocol` (version 28), the catalog, target, session and output keys | `d387e72888214898dde23105fa0ae86d723a77dffe739ac5925c744268d87b87` |
| `pdf-api.aar` | `plugin-api/pdf-api` | `IPdfPlugin`, `IPdfDocument`, `IPdfViewerSession`, `IPdfProgressCallback`, `IPdfViewerCallback`, `PdfActions`, `PdfIds`, `PdfContract`, `PdfCapabilityKeys`, `PdfErrorCodes`, `PdfOps` | `e6bdb5895c3af87aa1f07ac182e5156db9a0202c9367c49862d89bf6109e90bb` |

Provenance (2026-11-12, roadmap P0.1):

- `common-plugin-api.aar` (63,915 B) is the 1.1.1 release build of host commit `1676e7489d` (unsigned), the
  copy 3-Slate Studio, 3-Prism Camera and 3-Stove Agent stage, byte for byte. Later host commits only add the
  `AutoJs6CreateScriptContract` the plugin does not use.
- `explorer-action-api.aar` (67,325 B) is the release build of the module at host commit `e0eda70330`, the last
  commit that touched it (protocol version 28), byte-identical to the copy AutoJs4 Runtime 1.1.0 stages.
- `pdf-api.aar` (40,584 B) is the 1.0.0 release build (`:plugin-api:pdf-api:assembleRelease`, unsigned) of host
  commit `4e872a8741` (roadmap P1.1, contract version 1, protocol `docs/dev/pdf-plugin-protocol-v1.md`). It
  depends on `common-plugin-api` and does not embed its classes. It replaced the temporary stand-in the skeleton
  was first built with (37,380 B, assembled from the roadmap appendix C draft).

Do not commit locally assembled debug AARs or rename debug outputs to bypass this policy.

To update: assemble `:plugin-api:<module>:assembleRelease` in the host, copy the AAR here, record its
lowercase SHA-256 in the lock and in this table, and run `py .python\generate_third_party_notices.py`.
