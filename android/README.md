# MangaScroll for Android

Kotlin + Jetpack Compose, minimum Android 8.0 (API 26).

Phone extras: pinch or double-tap zoom, tap to show the controls, the screen stays on while reading.

## Building
From this folder:
```
./gradlew assembleDebug
```
For a signed release, create `keystore.properties` here (it's git-ignored):
```
storeFile=mangascroll.jks
storePassword=...
keyAlias=mangascroll
keyPassword=...
```
then run `./gradlew assembleRelease`. Without that file the release build is unsigned.
Updates must be signed with the same key to install over an existing copy.

Tests: `./gradlew testReleaseUnitTest`
