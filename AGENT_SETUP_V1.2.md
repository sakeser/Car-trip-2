# Task for Claude in Chrome — v1.2 update, Google setup, signing & APK build

**Goal:** Update the existing GitHub repo to v1.2, set up Google Sheets sync (signing key +
GitHub secrets + Google Cloud OAuth), build a stable-signed APK, and give the user a download
link.

You (the Chrome agent) can do everything **in the browser**: GitHub uploads/commits, GitHub
secrets, Google Cloud Console, and watching the Actions build. A few steps must be done by the
**user in a terminal** (generating a keystore) or by **dragging files** — these are marked
**PAUSE — ASK THE USER**. Do those handoffs clearly, wait for the values, then continue.

Local project folder on the user's PC:
`C:\Users\sinan\Claude\Projects\car trip analyzer`
App package name (needed later): `com.cartrip.analyzer`

Do the steps in order.

---

## Step 1 — Push the v1.2 code to GitHub

1. Open the existing repo: `https://github.com/<username>/car-trip-analyzer`
   (if it doesn't exist yet, create it: New repo → name `car-trip-analyzer`, Private, no README).
2. Go to `https://github.com/<username>/car-trip-analyzer/upload/main`.
3. **PAUSE — ASK THE USER:** "Drag the **contents** of `C:\Users\sinan\Claude\Projects\car
   trip analyzer` (the files/subfolders inside it — including the `app`, `.github`, `cloud`/
   `export` source folders) into this upload box. Uploading overwrites the changed files. Tell
   me when they all appear."
4. Verify the upload includes `app/build.gradle.kts`, `.github/workflows/build-apk.yml`, and the
   new source folders under
   `app/src/main/java/com/cartrip/analyzer/` (`cloud/`, `export/`). If `.github` is missing,
   tell the user.
5. Commit message: `v1.2 — Sheets sync, Excel export, Maps`. Commit changes.

---

## Step 2 — Get a stable signing key (USER does this in a terminal)

**PAUSE — ASK THE USER** to run these on their PC and send back the results. (Needs a JDK's
`keytool`; it ships with Android Studio. Tell them to run it in a folder they'll remember.)

Generate the keystore:
```
keytool -genkeypair -v -keystore stable.jks -storetype JKS -alias cartrip -keyalg RSA -keysize 2048 -validity 10000
```
(They choose a keystore password, fill in name/org, and a key password — Enter reuses the
keystore password.)

Show the SHA-1:
```
keytool -list -v -keystore stable.jks -alias cartrip
```

Base64-encode the keystore (Windows PowerShell):
```
[Convert]::ToBase64String([IO.File]::ReadAllBytes("stable.jks")) | Out-File -Encoding ascii stable.b64
```

**Ask the user to give you:**
- the **SHA-1** fingerprint (format `AB:CD:EF:...`),
- the entire contents of **stable.b64** (one long base64 string),
- the **keystore password**, the **key password**, and confirm the alias is `cartrip`.

> Handle these as sensitive. You'll paste them into GitHub secrets in Step 3 and the SHA-1 into
> Google Cloud in Step 4.

---

## Step 3 — Add GitHub repository secrets

Go to `https://github.com/<username>/car-trip-analyzer/settings/secrets/actions`.
Create four **repository secrets** (New repository secret) with the values from Step 2:

| Name | Value |
|---|---|
| `KEYSTORE_BASE64` | the full stable.b64 string |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | `cartrip` |
| `KEY_PASSWORD` | key password |

---

## Step 4 — Google Cloud OAuth

Open `https://console.cloud.google.com/`. The user must be logged into the Google account they
will sign in with on the phone (ask them to log in if needed).

1. **Create/select a project** (top project picker → New project → name it e.g. "Car Trip
   Analyzer" → Create → select it).
2. **Enable APIs:** go to `APIs & Services → Library`, search and **Enable** both:
   - **Google Sheets API**
   - **Google Drive API**
3. **OAuth consent screen** (`APIs & Services → OAuth consent screen`):
   - User type **External** → Create.
   - App name (anything), user support email = the user's email, developer contact = same.
   - **Scopes:** Add scopes → add `.../auth/spreadsheets` and `.../auth/drive.file` → Update → Save.
   - **Test users:** add the user's Google account.
   - Keep publishing status **Testing**. Save through to the end.
4. **OAuth client ID** (`APIs & Services → Credentials → Create credentials → OAuth client ID`):
   - Application type: **Android**.
   - Package name: `com.cartrip.analyzer`
   - SHA-1 certificate fingerprint: the SHA-1 from Step 2.
   - Create. (No client secret or code change needed.)

---

## Step 5 — Build the stable-signed APK

1. Go to the repo **Actions** tab.
2. Trigger a fresh run **after** the secrets exist (Step 3): open **Build APK → Run workflow →
   branch main → Run**. (A run from the Step 1 commit happened before secrets were added and
   would be debug-signed; you need a new run.)
3. Open the running job, wait for it to finish (~4–6 min). Confirm the **"Re-sign with stable
   key"** step ran (it appears only when the secrets are set) and printed a SHA-1 — it should
   match Step 2. If any step fails, open it, copy the full log, and show the user.
4. On success: run's **Artifacts → car-trip-analyzer-debug** → download.

Report back: the run URL, confirmation the re-sign step ran, and that the artifact downloaded
(it's a `.zip` containing `app-debug.apk`).

---

## Step 6 — Tell the user how to finish

Give the user these final instructions:
- Unzip `car-trip-analyzer-debug.zip` → `app-debug.apk`.
- **Uninstall any previous version** on the S25 first (the signing key changed), then install
  this APK.
- Open the app → **Connect Google account** → approve the Sheets/Drive permission.
- Drive a trip; it auto-appends to the "Car Trip Analyzer Log" sheet in their Google Drive.
- If sign-in fails with **code 10 (DEVELOPER_ERROR)**, the SHA-1/package in Step 4 doesn't match
  the installed APK — recheck that the same keystore's SHA-1 was registered and that the
  stable-signed APK (from the Step 5 re-run) was installed.

---

### Notes for the agent
- GitHub secret values can't be read back after saving — if unsure, delete and re-create.
- In Google Cloud, if a screen asks to "Configure consent screen" before creating credentials,
  do the consent screen first (Step 4.3), then the client ID (Step 4.4).
- Don't paste the keystore/passwords anywhere except the GitHub secret fields.
