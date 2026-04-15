# sp-today-widget

Personal Android lockscreen widget that shows today's Super Productivity tasks.

## How it works

Reads from a ContentProvider exposed by a **signature-matched companion fork**
of Super Productivity at [hjy-good/super-productivity](https://github.com/hjy-good/super-productivity)
(branch `feat/android-widget`).

```
SP fork (NgRx state)
  -> KeyValStore (SQLite)
  -> content://com.superproductivity.superproductivity.today
  -> THIS widget
  -> Samsung Good Lock / LockStar
```

The SP fork's `READ_TODAY_TASKS` permission is `protectionLevel="signature"`,
so **only apps signed with the same keystore** may read. This widget must be
installed as an APK signed by the exact same keystore as the SP fork.

## ⚠️ Cannot coexist with upstream Super Productivity

The SP fork and upstream SP share the **same package ID**
(`com.superproductivity.superproductivity`) and would both try to register
the same ContentProvider authority (`com.superproductivity.superproductivity.today`
in the fork, absent in upstream). Android allows only one app to own a given
package ID on a device at a time, so:

1. **Uninstall the Play Store Super Productivity first.**
2. Then install the fork APK from
   [hjy-good/super-productivity Releases](https://github.com/hjy-good/super-productivity/releases).
3. Re-login to Super Sync to pull your data back.

Keeping both installed is not possible. If you need to roll back to upstream,
uninstall the fork, reinstall Play Store SP, and re-login to Super Sync.

## Security notes

- No network permission requested. The widget reads local data only.
- No persistent cache — every render pulls fresh cursor rows via IPC.
- No logging of task content (follows SP's privacy convention).
- Backup and device-transfer excluded via `data_extraction_rules.xml`.
- `allowBackup="false"` on `<application>`.

## Install

1. Grab the latest release APK from
   [Releases](https://github.com/hjy-good/sp-today-widget/releases).
2. Install via Obtainium (recommended) or `adb install`.
3. Add the widget to the home screen via the launcher.
4. Samsung: use Good Lock → LockStar to drag the home widget onto the
   lockscreen.

## Build locally

Requires:
- JDK 21
- Android SDK with API 35
- The release keystore (same one used by the SP fork)

```bash
./gradlew :app:assembleDebug
# or, with the keystore env vars set:
RELEASE_KEYSTORE_PASSWORD=... \
RELEASE_KEYSTORE_ALIAS=... \
RELEASE_KEY_PASSWORD=... \
./gradlew :app:assembleRelease
```

CI produces signed release APKs on every push to `main`; see
`.github/workflows/build-widget-apk.yml`.
