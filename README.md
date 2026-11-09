# BURHAN NEXUS – BALL BOUNCE

A fast, neon-styled 2D arcade game for Android by **Burhan Nexus**.
Keep the ball bouncing with your paddle. One life. No second chances.

![App icon](branding/app-icon-512.png)

| | |
|---|---|
| Version | 1.0.0 (versionCode 1) |
| Package | `com.burhannexus.ballbounce` |
| Android | 8.0+ (minSdk 26), target SDK 35, designed for Android 10 and newer |
| Orientation | Portrait |
| Permissions | **None** – works fully offline, no internet permission |
| Engine | Kotlin + custom Canvas 2D engine (no game libraries, no ads) |
| APK size | ~1 MB |

## Features

- **Gameplay** – the ball falls under gravity; slide your finger anywhere to move the paddle. Each paddle hit = +1.
- **Angle physics** – centre hits bounce straight up, edge hits bounce at sharp angles (up to ~55°).
  Fixed 240 Hz physics sub-steps stop the ball from ever passing through the paddle.
- **Difficulty** – speed grows in small steps with **time survived** (+2% every 6 s) and **every 10 hits** (+7%),
  capped at 1.85× so it stays playable. The paddle shrinks slightly every 10 hits (minimum 68%).
  “SPEED UP! LEVEL n” shows on each level.
- **One life** – a single miss ends the game immediately. No continues.
- **Scores** – personal best + TOP 3 leaderboard (gold / silver / bronze), saved on the device with
  SharedPreferences (`commit()`), so they survive closing, restarting and rebooting.
- **Game over** – final score, personal best, NEW HIGH SCORE animation with confetti, TOP 3 achievement,
  plus **NEW GAME**, **MAIN MENU** and **EXIT GAME** buttons (buttons are locked for 0.8 s to prevent accidental taps).
- **Pause** – pause button / back key / leaving the app pauses; resume gives a 3-2-1 countdown.
- **Menu** – Burhan Nexus logo, PLAY, TOP 3 HIGH SCORES, HOW TO PLAY, ABOUT, SOUND ON/OFF, animated background ball.
- **About** – original Burhan Nexus logo (unmodified), clickable email (opens the mail app) and
  mobile number (opens the dialer – the user must press Call), version and copyright.
- **Sounds** – bounce, game over, button click, high-score fanfare (all generated, royalty free). ON/OFF is remembered.

## Download

Every push to `main` builds the APKs in **GitHub Actions** and puts them on the
[`apk-builds`](https://github.com/Zia046/Buhan-Nexus-Ball-Bounce/tree/apk-builds) branch:

- [BurhanNexus-BallBounce-v1.0.0-release.apk](https://github.com/Zia046/Buhan-Nexus-Ball-Bounce/raw/apk-builds/BurhanNexus-BallBounce-v1.0.0-release.apk) – install this one.
- [BurhanNexus-BallBounce-v1.0.0-debug.apk](https://github.com/Zia046/Buhan-Nexus-Ball-Bounce/raw/apk-builds/BurhanNexus-BallBounce-v1.0.0-debug.apk) – testing build, installs side by side as “Ball Bounce (Debug)”.

Publishing a GitHub Release (e.g. tag `v1.0.0`) also attaches both APKs to the release automatically.

## Automated checks

- **Unit tests** (18): physics, paddle angles, no tunnelling at max speed, difficulty steps and cap,
  one-life game over, fresh NEW GAME, TOP 3 sorting/ties/persistence.
- **Emulator UI check** on Android 10 and Android 15: opens every screen, plays, forces a miss,
  restarts the app to confirm saved scores, taps PLAY and EXIT GAME with real touches, fails on any crash.
  Screenshots are pushed to the `ci-screenshots-api29` / `ci-screenshots-api35` branches.

## Release signing (one-time setup)

The release keystore is stored **encrypted** in `signing/release.jks.enc`.
Add one repository secret so GitHub can sign the release APK:

1. GitHub → this repo → **Settings → Secrets and variables → Actions → New repository secret**
2. Name: `BN_SIGNING_PASSWORD` – Value: the store password from your private key backup file.
3. Re-run the workflow (Actions → *Build Android APK* → *Run workflow*).

Without the secret the release APK is signed with a temporary debug key (installs fine, but is not
update-compatible). Keep the backup (`burhan-nexus-release.jks` + password) in two safe places –
without it you cannot publish updates that install over the existing app.

## Build it yourself (Android Studio)

1. Install Android Studio (Ladybug or newer) with JDK 17.
2. *File → Open* → select this folder. Let Gradle sync.
3. Run on a phone/emulator with ▶, or build APKs:

```bash
./gradlew testDebugUnitTest      # unit tests (physics, difficulty, score saving)
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk
```

To sign a local release build with the real key, set before building:
`BN_KEYSTORE_PATH`, `BN_KEYSTORE_PASSWORD`, `BN_KEY_PASSWORD`, `BN_KEY_ALIAS=burhannexus`.

## Project structure

```
app/src/main/java/com/burhannexus/ballbounce/
  MainActivity.kt        fullscreen activity, back/exit, email + dialer intents
  GameView.kt            Canvas renderer, all screens, input, effects
  engine/GameWorld.kt    physics, collision, difficulty (pure Kotlin, unit tested)
  engine/ScoreBook.kt    personal best + TOP 3 logic (pure Kotlin, unit tested)
  PrefsStore.kt          SharedPreferences storage
  SoundManager.kt        SoundPool sound effects
  Particles.kt           particle effects
  Brand.kt               official Burhan Nexus name, email, phone
app/src/main/res/        icon, logo (drawable-nodpi/bn_logo.png), sounds (raw/)
app/src/main/assets/fonts  Tektur + Outfit (SIL Open Font License)
branding/                original logo + 512px app icon
signing/release.jks.enc  encrypted release key
```

## Credits

Developed by **Burhan Nexus** – ziaullah.046@gmail.com – 03475093446
Fonts: Tektur and Outfit, SIL Open Font License 1.1 (license files in `app/src/main/assets/fonts`).

© 2026 Burhan Nexus. All rights reserved.
