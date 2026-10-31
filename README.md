# CoachPad

Match notes for football coaches and players: record goals, assists and cards by voice on a
Wear OS watch during the match, then review, correct and export them on the phone.

## How it works

- **Watch** (Wear OS): a match clock with pauses, a shift timer that vibrates when it is time for
  substitutions, and a microphone button. Each tap records a short voice note with the match time.
  The watch works offline and does not transcribe anything.
- **Phone** (Android): syncs the match from the watch on demand, plays the recordings back and turns
  them into events (goal, disallowed goal, yellow or red card, note) with the team and the players.
  - Team lineups per match, entered before or after the match.
  - Optional speech recognition with OpenAI Whisper, using your own API key: the recognised text
    fills in the event type, team and players from the lineup.
  - Export as shareable text, CSV or JSON.
  - A match can also be recorded on the phone alone, live or typed in afterwards from notes.
- Languages: English, Polish and German.

## Modules

- `wear` — the watch app (Jetpack Compose for Wear OS, Room).
- `mobile` — the phone app (Jetpack Compose, Room).
- `shared` — plain Kotlin shared by both: event types, play time with pauses, sync messages.

The watch and the phone talk over the Wear OS Data Layer, so both apps share one application ID.
There is no backend.

## Building

Requires Android Studio with its bundled JDK, Android SDK 37; the apps run on Android 11 (API 30)
and newer.

```bash
./gradlew assembleDebug   # build both apps
./gradlew test            # unit tests
```

Install each app on its own device by serial, as both have the same application ID:

```bash
ANDROID_SERIAL=<watch serial> ./gradlew :wear:installDebug
ANDROID_SERIAL=<phone serial> ./gradlew :mobile:installDebug
```

Speech recognition is optional and off until an OpenAI API key is entered in the phone app's
settings; the key is stored encrypted with the Android Keystore.

## License

All rights reserved. The source is published for reference only; no license is granted to use,
copy, modify or distribute it.
