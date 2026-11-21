# MangaScroll

An Android reader for long-strip manga and webtoons. Import pages and they're joined into one seamless vertical scroll.

## Features
- **Library home page**: cover grid of imported manga, a "Continue reading" card, long-press to rename or delete
- **Import** from images, a folder (sub-folders become chapters), or ZIP/CBZ archives (each becomes a chapter)
- **Natural page ordering**: `2.jpg` comes before `10.jpg`
- **Seamless reader**: tall pages are sliced internally so huge strips scroll smoothly without memory issues
- Pinch or double-tap zoom, tap to show bars, page slider, previous/next chapter
- Saves your position in each chapter, tracks read/unread, keeps the screen on while reading

Kotlin + Jetpack Compose, minimum Android 8.0 (API 26).

## Building
```
./gradlew assembleDebug
```
For a signed release, create `keystore.properties` in the project root (it's git-ignored):
```
storeFile=mangascroll.jks
storePassword=...
keyAlias=mangascroll
keyPassword=...
```
then run `./gradlew assembleRelease`. Without that file the release build is unsigned.
