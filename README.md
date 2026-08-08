# RK RECORDING — Android screen recorder

Native Android app. Records the screen with **no time limit**, saves to
**Movies/RK RECORDING** automatically, has 480p / 720p / 1080p quality,
internal/app audio (stereo), microphone, mixing of both, "clear voice" noise reduction,
selectable frame-rate and bitrate, an in-app gallery (play / share / delete), and a notification with a live
timer and a Stop button. App icon: **RK**.

Package: `com.rk.recording` · minSdk 29 (Android 10) · targetSdk 34.

## Features
- No time-limit screen recording, auto-saved to **Movies/RK RECORDING**.
- Quality 480p / 720p / 1080p; **frame rate** 15/24/30/60; **bitrate** Auto/4/8/12/20 Mbps.
- Audio: **internal/app sound in stereo**, microphone, or both mixed; "clear voice" noise reduction.
- Notification with live timer + Stop & save; ESC-style stop from the notification.
- **My recordings** screen inside the app: thumbnail list, tap to play, share, delete.
- **Draw on screen while recording** — a floating pen overlay (pen on/off, 5 colours,
  3 thicknesses, undo, clear, movable toolbar). Drawings are burned into the video.
  Needs the one-time "Display over other apps" permission (app opens the setting for you).
- **3-2-1 countdown** with Skip before recording starts.
- **Floating controls** on screen while recording: movable bubble with live timer,
  **Pause / Resume** and **Stop** (like the phone's built-in recorder).
- App icon: **RK**.
- Version 1.1 (versionCode 2).

### Not included (and why)
- **Region / partial-area crop**: needs a low-level GPU crop pipeline; left out to keep
  the build reliable. Android's own "Share one app" (shown when you start) already records
  just one app.
- **"Show taps and touches"** over other apps: only the system recorder can draw other
  apps' touch dots. You can turn it on phone-wide via Settings > Developer options > "Show taps".

---

## APK kai rite banavvo (3 rasta)

### Rasto 1 — GitHub par (PC ma kai install karya vagar) ✅ sauthi saheli
1. https://github.com par free account banavo.
2. "New repository" → naam `RK-RECORDING` → Create.
3. "uploading an existing file" par click karo → aa aakha folder na badha
   files/folders drag-and-drop karo (`app`, `.github`, `build.gradle`,
   `settings.gradle`, `gradle.properties`, `gradle`) → Commit.
4. Uparni "Actions" tab kholo → "Build RK Recording APK" run thay chhe (3-5 min).
5. Run puro thai jaay pachhi niche "Artifacts" ma **RK-Recording-APK** download karo.
   Andar `app-debug.apk` chhe.
6. E APK mobile ma move karo → open → "Unknown sources / Install anyway" allow
   karo → install. Bas.

### Rasto 2 — Android Studio (PC/Mac ma)
1. Free Android Studio install karo (developer.android.com/studio).
2. File → Open → aa folder pasand karo. Gradle sync thava do.
3. Build → Build Bundle(s)/APK(s) → Build APK(s).
4. "locate" click karo → `app/build/outputs/apk/debug/app-debug.apk` → mobile ma install.

### Rasto 3 — Command line (jena PC ma Android SDK chhe)
```
gradle assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

---

## Mobile ma vaparva
1. App kholo → Quality pasand karo (480/720/1080).
2. Mic ane "Clear voice" toggle jarur mujab.
3. **Start recording** → Android "Start now" mange → allow.
4. Screen record thay chhe. Notification ma **timer** ane **Stop & save** button chhe.
5. Stop dabaao → file **Movies/RK RECORDING** ma save. Gallery/Files ma dekhaay.



## Internal audio (app sound) — read this
Version 1.2 fixes the common "video records but no sound" problem. The fix: the mic now
uses the plain MIC source (earlier it used VOICE_COMMUNICATION, which put the phone in
call-mode and silenced captured media audio). Noise reduction is now done with audio
effects instead.

Tips if a specific app still has no sound:
- Turn ON the "Internal audio" switch (it is on by default).
- YouTube, most games, music and video apps: captured fine (USAGE_MEDIA).
- **Zoom / phone calls / WhatsApp calls**: Android does NOT allow any third-party app to
  capture another app's *call* audio (voice-communication) — this is an OS privacy rule,
  not an app bug. Workaround: play the call on **speaker** and turn ON **Microphone** so
  the room sound (incl. the other person) is recorded through the mic.
- Some apps (certain OTT/DRM players) mark their audio as non-capturable; their sound
  cannot be recorded by any recorder.

## Countdown & floating controls
- "3-2-1 countdown" and "Floating controls" switches are ON by default.
- They use the same "Display over other apps" permission as draw (allow once).
- After you start, a 3-2-1 countdown shows (tap "Skip countdown" to skip), then a small
  movable bubble appears with the timer, Pause/Resume and Stop.

## Draw on screen (annotate)
- "Draw on screen while recording" switch on karo → Start.
- Pehli var "Display over other apps" allow karvanu (app e setting kholi ape) → pachi Start fari dabaao.
- Recording chalu thay etle daabi baaju nano toolbar aave: **Pen** (on/off), 5 rang,
  jaadai (●), undo (↶), clear (✕). Toolbar ne ≡ handle thi khasedi shakay.
- **Pen ON** = screen par doro (te vakhate niche ni app touch nahi thay).
  **Pen Off** = niche ni app vaparo, drawing dekhati rahe.
- Je doro te recording ma save thay.

## Notes (pramanik)
- Aa native app chhe — mobile browser ni jem block nathi thati. Screen record barobar chale.
- "Clear voice" = device nu built-in echo/noise reduction (VOICE_COMMUNICATION).
  Samsung S25 na AI jetlu strong nahi, pan background awaj saras ochho thay.
- Ek j file (recording) ni koi time limit nathi — batteri/storage joi ne band karo.
- Internal audio (game/app no awaj) HOVE included chhe (Android 10+). App ma
  "Internal audio" switch on rakho. Mic + internal banne on hoy to banne mix thay.
  Nondh: aapo aap (system/app) je audio 'capture allowed' rakhe te j padse; keta
  DRM/protected app (dakhla tarike keta OTT) no awaj Android capture na kare — e OS ni limitation chhe.

## Internal audio (app sound) — read this
Version 1.2 fixes the common "video records but no sound" problem. The mic now uses the
plain MIC source (earlier VOICE_COMMUNICATION put the phone in call-mode and silenced the
captured media audio). Noise reduction is now done with audio effects instead.

- Keep the "Internal audio" switch ON (default). YouTube, games, music/video apps record fine.
- Zoom / phone / WhatsApp calls: Android does NOT let any third-party app capture another
  app's CALL audio (privacy rule). Workaround: play on speaker + turn on Microphone.
- Some DRM/OTT players mark audio non-capturable; no recorder can capture those.
