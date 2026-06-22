# Google Sheets sync — one-time setup

The app can auto-append every trip (summary + per-sample detail + events) to a Google
Sheet in your Drive, which opens directly in Excel / Microsoft 365. Because Google sign-in
checks the app's signing certificate, a CI-built APK needs a **stable signing key**, and you
must register that key + the app in Google Cloud. This is a one-time setup.

There are two parts: **(A) a stable signing key** and **(B) Google Cloud OAuth**.

---

## Part A — Stable signing key

Google ties sign-in to your app's package name + signing certificate SHA-1. The default CI
debug key changes every build, so we sign with a key you control.

### A1. Generate a keystore (once)

You need a JDK's `keytool` (comes with Android Studio / any JDK). In a terminal:

```bash
keytool -genkeypair -v -keystore stable.jks -storetype JKS -alias cartrip \
  -keyalg RSA -keysize 2048 -validity 10000
```

It asks for a keystore password, your name/org (anything), and a key password (you can
press Enter to reuse the keystore password). **Remember the passwords and the alias
(`cartrip`).**

### A2. Get the SHA-1 (you'll need it in Part B)

```bash
keytool -list -v -keystore stable.jks -alias cartrip
```

Copy the line under *Certificate fingerprints* → **SHA1:** (format `AB:CD:...`).
(The CI build also prints this SHA-1 in the "Re-sign with stable key" step's log.)

### A3. Base64-encode the keystore for GitHub

- **Windows (PowerShell):**
  ```powershell
  [Convert]::ToBase64String([IO.File]::ReadAllBytes("stable.jks")) | Out-File -Encoding ascii stable.b64
  ```
- **macOS / Linux:**
  ```bash
  base64 -w0 stable.jks > stable.b64   # on macOS use: base64 stable.jks > stable.b64
  ```

### A4. Add GitHub repository secrets

In your repo: **Settings → Secrets and variables → Actions → New repository secret**. Add:

| Secret name | Value |
|---|---|
| `KEYSTORE_BASE64` | the full contents of `stable.b64` |
| `KEYSTORE_PASSWORD` | your keystore password |
| `KEY_ALIAS` | `cartrip` |
| `KEY_PASSWORD` | your key password (same as keystore if you reused it) |

Re-run the **Build APK** workflow. The APK is now signed with your stable key. Install
**this** APK on the S25 (uninstall the old one first, since the signature changed).

---

## Part B — Google Cloud OAuth

### B1. Project + APIs
1. Go to <https://console.cloud.google.com/> and create (or pick) a project.
2. **APIs & Services → Library** → enable **Google Sheets API** and **Google Drive API**.

### B2. OAuth consent screen
1. **APIs & Services → OAuth consent screen** → User type **External** → Create.
2. Fill app name, your email for support + developer contact.
3. **Scopes** → Add: `.../auth/spreadsheets` and `.../auth/drive.file`.
4. **Test users** → add your own Google account (the one you'll sign in with).
5. Leave publishing status as **Testing** (fine for personal use; no Google review needed).

### B3. OAuth client ID (Android)
1. **APIs & Services → Credentials → Create credentials → OAuth client ID**.
2. Application type: **Android**.
3. Package name: `com.cartrip.analyzer`
4. SHA-1: the fingerprint from **A2**.
5. Create. (No client secret or code change is needed — Google recognizes the app by its
   package + signature.)

---

## Use it

1. Open the app → **Connect Google account** → pick your account → approve the Sheets/Drive
   permission.
2. Drive trips automatically after each trip ends (toggle "Auto-sync" off to pause).
3. A spreadsheet named **"Car Trip Analyzer Log"** appears in your Google Drive with tabs:
   - **Summary** — one appended row per trip.
   - **Samples** — per-second cleaned rows (time, lat, lon, speed, longitudinal & lateral accel).
   - **Events** — each hard brake/accel/corner.
4. Open it in Excel / 365 (File → Open, or in Drive: Open with → Google Sheets, then download
   as .xlsx). New trips keep appending to the same sheet.

A native **.xlsx** is also written per trip on the phone (under the app's files), and the
trip detail screen has a **Share Excel** button plus **Open in Google Maps** buttons.

---

## Simpler alternative (no GitHub secrets)

If you'd rather not set up CI signing, build the app in **Android Studio** instead — its
debug key is stable on your machine. Register that SHA-1 in step **B3**:

```bash
# Windows
keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
# macOS / Linux
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

Then Run/Build from Android Studio and install that APK.

---

## Notes
- The app still builds and records trips fine **without** any of this — cloud sync is
  optional and simply stays disconnected until you complete the setup.
- If sign-in fails with code 10 (`DEVELOPER_ERROR`), the SHA-1 / package in Part B doesn't
  match the APK you installed — recheck A2/B3 and that you installed the stable-signed APK.
