# Slice 2A clean-reinstall audit (test only)

Standalone Android test app, **org.beesearch.bindingaudit**. Not included in the
production Gradle graph. Build-generated copies of current production repository
classes are compiled directly; no fork of binding logic or dependencies is maintained.
Only the install-state directory constant is supplied by the harness.

Build from repository root with the existing wrapper:

    .\gradlew.bat -p tools/repository-binding-audit assembleDebug --offline

Set ANDROID_HOME to the installed SDK if needed. Verify APK metadata before install.
No user research data, DEV settings/Room, Stable or Beta are linked to this APK.

Commands on a fresh owned root use one UUID runId:

    adb -s <serial> shell am start -n org.beesearch.bindingaudit/.AuditActivity --es runId <uuid> --es command initialize
    adb -s <serial> shell am start -n org.beesearch.bindingaudit/.AuditActivity --es runId <uuid> --es command probe
    adb -s <serial> shell am start -n org.beesearch.bindingaudit/.AuditActivity --es runId <uuid> --es command adopt

Initialize/adopt explicitly request the exact disposable SAF tree. Probe never
initializes/adopts. Initialize also publishes a tiny synthetic JPEG and tests duplicate ingest.
No command deletes external data. Uninstall is allowed only for this confirmed
disposable package, after preserving log/header/blob evidence. After reinstall:
probe must be UNBOUND; header/media must remain; explicit adopt needs a fresh grant.
Do not uninstall/clear any Bee Search field/Beta/DEV or old PoC package.

Build outputs and generated sources stay ignored; no fixture bytes belong in Git.

## Observed audit, 2026-10-06

Samsung SM-S938B / API36 / RFCY90MBYVZ. Run
`a55ab34b-f43f-4f93-be89-7aeaef8b4930`, repository UUID
`b5a04173-d5aa-4e48-863f-0fc65ff2fd24`.

Initialize/publication/duplicate PASS; force-stop/relaunch BOUND; clean uninstall of
only this disposable package preserved external header/blob; reinstall probe UNBOUND;
explicit adopt restored original binding with unchanged public bytes.

Synthetic JPEG: 759 bytes; SHA-256
`270c0ca16088b645bbc07143f8bece6c799e101204828f2f29b75ffe34a036fb`.
Header SHA-256 `b94648381eeec755aa2afad7baa1b1c3fc0d73379f79f83d98c7db290adf5941`.
Initial launch crashed with missing Main dispatcher before binding. Added matching
coroutines-android 1.9.0 only here; corrected APK passed. Production remains unchanged.
This does not prove a crash exactly mid-persistence-write.
