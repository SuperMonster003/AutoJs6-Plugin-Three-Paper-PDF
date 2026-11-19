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
| `explorer-action-api.aar` | `plugin-api/explorer-action-api` | `IExplorerActionPlugin` and the archive session interfaces, `ExplorerActionProtocol` (version 28), the catalog, target, session and output keys, `ExplorerActionHostEngineIds` | `2b0b21cd63c5f9bb1012d53ec8441a3a0c23bc42835f65b4a9fadd3c70db78cb` |
| `pdf-api.aar` | `plugin-api/pdf-api` | `IPdfPlugin`, `IPdfDocument`, `IPdfViewerSession`, `IPdfProgressCallback`, `IPdfViewerCallback`, `PdfActions`, `PdfIds`, `PdfContract`, `PdfCapabilityKeys`, `PdfErrorCodes`, `PdfOps` (contract version 2) | `116c2b7737ba2f62e68e1cb3fb8afd9dbc549c020a0d9c6de1b7ba54def9aa9a` |

Provenance (2026-11-12, roadmap P0.1):

- `common-plugin-api.aar` (63,915 B) is the 1.1.1 release build of host commit `1676e7489d` (unsigned), the
  copy 3-Slate Studio, 3-Prism Camera and 3-Stove Agent stage, byte for byte. Later host commits only add the
  `AutoJs6CreateScriptContract` the plugin does not use.
- `explorer-action-api.aar` (67,382 B) is the release build (`:plugin-api:explorer-action-api:assembleRelease`) of
  host commit `2b93a8ea3` (protocol version 28), staged on 2026-11-19. It adds the host command id
  `ExplorerActionHostEngineIds.PDF_MAKE_SEARCHABLE` (`pdf-make-searchable`, roadmap D67 / P6.11), which the plugin
  declares as a `hostEngine` action; the module has not changed since. It replaced the release build of host commit
  `e0eda70330` (67,325 B, sha256 `d387e728...268d87b87`), byte-identical to the copy AutoJs4 Runtime
  1.1.0 stages.
- `pdf-api.aar` (48,213 B) is the 1.1.0 release build (`:plugin-api:pdf-api:assembleRelease`, unsigned) of host
  commit `19ab55f80` (contract version 2, roadmap P6.1 / P6.2 row 1, D58; protocol
  `docs/dev/pdf-plugin-protocol-v1.md`, section "Version 2"), staged on 2026-11-19. It adds the optional text stamp
  field `pageOffset` (`PdfContract.FIELD_PAGE_OFFSET`, at most `MAX_PAGE_OFFSET` either way, roadmap Q211 (a)) to the
  first contract version 2 build of host commit `0f679d6b1` (48,160 B, sha256 `a463870d...9c04fdd`, staged on
  2026-11-18), appended in place before the plugin published version 2. It depends on `common-plugin-api` and does not embed
  its classes. The baseline `PdfContract.CONTRACT_VERSION` stays 1 (the plugin declares it as `pdfContractVersion`)
  and `CONTRACT_VERSION_2` is the plugin's `pdfMaxContractVersion`; `IPdfDocument` appends `getFormFields`,
  `PdfOps` adds `formFields` and the six version-2 run operations, `PdfContract` the payload input, the keys,
  completion keys, ceilings and value sets of version 2. It replaced the 1.0.0 release of host commit `c37a23258d`
  (40,772 B, sha256 `9d5714fe...af35cc49`, contract version 1), re-staged on 2026-11-14 for the packaged-app entry
  (`PdfActions` adds `PACKAGED_SERVICE_ACTION`, `PACKAGED_PERMISSION` and `PACKAGED_VIEWER_ACTIVITY_ACTION`, roadmap
  Q145 (b), D43), which replaced the 2026-11-13 release of host commit `465d7a141f` (40,670 B, sha256
  `0bb80366...ff169e94`), in which `PdfContract` added `KEY_DISPLAY_NAMES` (merge), `KEY_JAVA_SCRIPT` and
  `DEFAULT_JAVA_SCRIPT` (fromHtml), roadmap appendix D Q31, Q35, Q60 and Q61; that one replaced the P1.1 release of
  host commit `4e872a8741` (40,584 B, sha256 `e6bdb589...e90bb`), which had replaced the temporary stand-in the
  skeleton was first built with (37,380 B, assembled from the roadmap appendix C draft).

Do not commit locally assembled debug AARs or rename debug outputs to bypass this policy.

To update: assemble `:plugin-api:<module>:assembleRelease` in the host, copy the AAR here, record its
lowercase SHA-256 in the lock and in this table, and run `py .python\generate_third_party_notices.py`.
