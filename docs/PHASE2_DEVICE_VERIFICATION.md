# Phase 2 — On-Device Verification Runbook

**Why this exists:** CI confirms the code compiles, packages, and its own Robolectric tests
pass. It does not confirm the Accessibility Service actually detects a foreground app change,
launches the interception screen, or that the countdown/crisis-exit UI behaves correctly on a
real device. This is the one remaining gap in Phase 2's exit criteria (ROADMAP.md §2/§4).

I can't run this myself — no Android SDK, emulator, or adb in my sandbox, and my network
egress doesn't include Google's SDK repositories. This runbook is written so you can run the
whole thing from your phone via Termux in one sitting, using the same pull-zip-to-phone
workflow already documented in ROADMAP.md §Phase 0.5.

---

## 0. Pre-flight — static checks already done for you

Before burning device time, I read through the manifest, layout, and both enforcement classes
looking for the category of bug a device install (not CI) would catch:

- **View IDs**: every `findViewById` call in `MissionInterceptionActivity` (`voiceLineText`,
  `countdownText`, `returnToMissionButton`, `breakCommitmentButton`, `stabilityControlButton`,
  `breakReasonInput`) has a matching `android:id` in `activity_mission_interception.xml`. No
  mismatch — this class of bug (right ID string, wrong screen) would crash on `onCreate()` and
  is now ruled out statically.
- **Manifest**: `MissionAccessibilityService` and `MissionInterceptionActivity` are both
  declared correctly; `BIND_ACCESSIBILITY_SERVICE`/`exported=true` is the standard, required
  pattern (not a security relaxation). No `PACKAGE_USAGE_STATS` or `SYSTEM_ALERT_WINDOW`
  leaked in, matching the documented scope boundary.
- **Accessibility service config XML**: `canRetrieveWindowContent="false"` matches the actual
  code (`onAccessibilityEvent` only reads `event.packageName`) — the "we don't read screen
  content" claim in the description string is true, not just asserted.
- **Stability control closures**: `userId` is correctly captured as a real parameter into
  `renderStabilityControl`, not leaking from an outer scope — this looked suspicious on first
  read but checks out.
- One stale comment, non-blocking: `app/build.gradle.kts`'s top comment still says "no app
  code beyond a launcher-less Application class," which is no longer true as of this phase.
  Cosmetic only — worth a one-line fix whenever you're next in that file.

None of this replaces an actual run. Here's that.

---

## 1. Get the current code onto your phone

Follow the exact process ROADMAP.md §Phase 0.5 already documents — summarized here:

```bash
# On your phone, in Termux:
cd ~/projects
mv disciplineos disciplineos-old-backup
unzip ~/storage/downloads/<latest-zip-name>.zip -d .
mv <unzipped-folder-name> disciplineos
cp -r disciplineos-old-backup/.git disciplineos/
cd disciplineos
git status   # confirm the diff is what you expect before building
```

## 2. Build the debug APK on-device (or pull it from CI instead)

You have two options — pick whichever is less friction:

**Option A — build locally in Termux** (needs `termux-android-tools`/a JDK + Android SDK
command-line tools set up in Termux, which is a heavier one-time setup):
```bash
./gradlew :app:assembleDebug
```

**Option B — grab the artifact GitHub Actions already built** (simpler, since CI is already
green): go to the repo's Actions tab → the latest successful `build-and-test` run → download
the build output, or add an `actions/upload-artifact` step for `app/build/outputs/apk/debug/`
if the workflow doesn't already upload the APK (it currently only uploads test reports — see
note at the bottom of this doc).

## 3. Install on a real device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If you don't have `adb` in Termux, `pkg install android-tools` gets you there.

## 4. Enable the Accessibility Service

This step alone is worth doing carefully — it's the one permission grant a friend/family
member using this app will also have to walk through by hand:

1. Settings → Accessibility → find "DisciplineOS" (uses `accessibility_service_summary`
   string) → enable it.
2. **Check:** does the on-device description shown here match
   `accessibility_service_description` in `strings.xml`, and does it read correctly to
   someone who didn't write it? This is the actual user-facing trust moment for this
   permission.
3. **Check:** no crash or ANR on enabling. This exercises `onServiceConnected()` for the
   first time outside Robolectric.

## 5. Seed a test Mission and trigger an interception

Since Phase 3 (onboarding UI) doesn't exist yet, there's no in-app way to create a Mission —
you'll need to insert one directly, either via a temporary debug hook in `AppContainer` or
directly against the Room DB. The concrete test:

1. Create a `Mission` row: `status = ACTIVE`, blocklist containing one app you have installed
   (e.g. a browser or a game), tier = whatever you want to test first (start with
   **Recruit**, then re-run for **Warden** and **Iron** — the branching logic differs
   materially between them).
2. Open the blocklisted app.
3. **Expected:** `MissionAccessibilityService` detects the foreground change within
   `notificationTimeout` (100ms) and launches `MissionInterceptionActivity` — full-screen,
   over whatever was showing, even if the device was locked.
4. **Check per tier:**
   - **Recruit**: informational copy shown (not Warden Voice), casual exit available
     immediately, no countdown gate on the buttons.
   - **Warden/Iron**: Warden Voice line displayed (via `NoOpWardenVoiceGenerator` → fallback
     bank, since there's no real backend yet — confirm a fallback line actually renders, not
     a blank `TextView`), countdown visibly ticking down, Return/Break buttons disabled until
     countdown completes.
   - **Iron specifically**: the crisis-exit stability control button is visible and tappable
     from the very first frame — not after the countdown, not requiring navigation away from
     this screen. Tap it and confirm it returns home immediately with no delay or confirmation
     dialog, and that no Violation/Ledger row gets written for that Mission (check the DB
     directly, or trust `RecordViolationUseCase`'s guard — but this is exactly the kind of
     "provably closed loop" claim worth eyeballing once for real).
5. **Check the interception screen survives rotation/process death**: since state is
   rebuilt from Intent extras + a DB read in `onCreate` rather than retained in memory, kill
   the app process (Developer Options → "Don't keep activities," or just force-stop) mid-
   countdown and confirm re-triggering the interception doesn't crash or show stale state.

## 6. Confirm the CI test-report gap flagged in ROADMAP.md §4.5(b)

The roadmap notes run #8's job-level success was confirmed but the actual per-test breakdown
in the `test-reports` artifact was never pulled to confirm all 9 `InterceptionControllerTest`
cases passed individually (vs. some subset silently skipped). Thirty-second check:

1. GitHub → Actions → the run in question → download the `test-reports` artifact.
2. Open `app/build/reports/tests/testDebugUnitTest/index.html` (or the XML if you'd rather
   grep it) and confirm all 9 cases show as passed, not skipped.

## 7. What "done" looks like for Phase 2

Update ROADMAP.md's Phase 2 checklist once all of the above pass:
- [ ] Accessibility Service enables without crash, description copy checked
- [ ] Interception triggers correctly at Recruit, Warden, and Iron (three separate passes)
- [ ] Iron crisis exit works with no delay and writes no Ledger entry
- [ ] Screen survives process death mid-countdown
- [ ] CI `test-reports` artifact confirms all 9 `InterceptionControllerTest` cases individually passed

Only then is Phase 2 honestly "done" rather than "compiles and packages."

---

### One workflow gap worth fixing before you do this

`.github/workflows/build-and-test.yml` currently uploads only `**/build/reports/tests` as an
artifact — it never uploads the actual debug APK. If you want Option B in step 2 above (pull
the APK from CI instead of building locally in Termux), add this step to the workflow:

```yaml
      - name: Upload debug APK
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: app-debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
          retention-days: 14
```

Say the word and I'll make this edit directly in the zip and hand you an updated one.
