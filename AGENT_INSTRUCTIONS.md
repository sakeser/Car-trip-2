# Task for Claude in Chrome — Publish project & build the APK

**Goal:** Publish a local Android project to a new GitHub repo, let GitHub Actions build
the debug APK, and give the user a link to download the built APK.

Work through the steps below. Pause and ask the user whenever you need them to act
(logging in, dragging files). If a build step fails, copy the full error log and show it.

---

## Local project location (on the user's Windows PC)

```
C:\Users\sinan\Claude\Projects\car trip analyzer
```

## Step 1 — Log in
Go to github.com. If the user isn't logged in, ask them to log in, then continue.

## Step 2 — Create the repository
Create a **new** repository:
- Name: `car-trip-analyzer`
- Visibility: **Private**
- Do **not** add a README, .gitignore, or license (keep it empty).

Click **Create repository**.

## Step 3 — Upload the project files
Open the upload page: `https://github.com/<username>/car-trip-analyzer/upload/main`

Ask the user to drag the **contents** of the folder above into the drop area — the files
and subfolders **inside** it, not the folder itself, so the structure is preserved.

Wait until the user confirms the files appear. Verify the upload includes the required
files in **Step 5** before committing. The `.github` folder and the `app` folder must be
present.

## Step 4 — Commit
Commit message: `Initial commit`. Click **Commit changes**.

## Step 5 — Verify required files are in the repo
The build will fail unless ALL of these exist (browse the repo to confirm):

```
settings.gradle.kts
build.gradle.kts
gradle.properties
.github/workflows/build-apk.yml
app/build.gradle.kts
app/proguard-rules.pro
app/src/main/AndroidManifest.xml
app/src/main/res/...            (values/, drawable/, mipmap-anydpi-v26/)
app/src/main/java/com/cartrip/analyzer/...   (all .kt files: MainActivity, TripApp,
    data/, record/, analysis/, ui/)
```

If `.github/workflows/build-apk.yml` is missing, create it via **Add file → Create new
file**, set the path to `.github/workflows/build-apk.yml`, and paste the workflow from the
**Appendix** below. If other files are missing, tell the user which ones and ask them to
re-upload those.

## Step 6 — Run the build
Open the repo's **Actions** tab. A run named **Build APK** should start automatically on
the commit. If Actions prompts to enable workflows, ask the user to enable them, then
trigger the run via **Build APK → Run workflow** (branch `main`).

Open the running job and wait for it to finish (about 3–6 minutes). If it fails, open the
failed step, copy the **full error log**, and show it to the user.

## Step 7 — Retrieve the APK
When the run succeeds, scroll to the run's **Artifacts** section and click
**car-trip-analyzer-debug** to download it.

Report back to the user:
- the GitHub **run URL**, and
- confirmation the file downloaded (it is a `.zip`).

---

## What the user does next (FYI for the user, not the agent)

The artifact downloads as `car-trip-analyzer-debug.zip`. Unzip it to get
`app-debug.apk`. Copy that to the Samsung S25, open it with the **Files** app, allow
**install unknown apps**, install, and grant **Location** + **Notifications** on the first
trip.

Note: the artifact link only works while logged into GitHub — it's not a public direct
download URL.

---

## Appendix — `.github/workflows/build-apk.yml`

This workflow installs Gradle 8.7 directly (via `gradle/actions/setup-gradle`) and runs
`gradle assembleDebug`, so it does **not** require a committed Gradle wrapper
(`gradlew` / `gradle-wrapper.jar`). Use it exactly as written.

```yaml
name: Build APK

on:
  push:
    branches: [ main, master ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Set up Android SDK
        uses: android-actions/setup-android@v3

      - name: Install SDK packages
        run: |
          yes | sdkmanager --licenses >/dev/null 2>&1 || true
          sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '8.7'

      - name: Build debug APK
        run: gradle assembleDebug --no-daemon --stacktrace

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: car-trip-analyzer-debug
          path: app/build/outputs/apk/debug/app-debug.apk
          if-no-files-found: error
```
