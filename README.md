# MangaScroll

A reader for long-strip manga and webtoons. Import pages and they're joined into one seamless vertical scroll.

| Folder | Version | Built with |
|---|---|---|
| [`android/`](android/) | Android app (APK), Android 8.0+ | Kotlin + Jetpack Compose |
| [`windows/`](windows/) | Windows app (portable `MangaScroll.exe`) | Kotlin + Compose for Desktop |

Each folder is its own Gradle project with its own `gradlew`; open or build them separately.
Both versions share the same look and features:

- **Library home page**: cover grid, search, sort by Recent / A–Z / Unread / Added, long-press a cover to rename or delete
- **Import** images, a folder (sub-folders become chapters), or ZIP/CBZ archives (each becomes a chapter)
- **Natural page ordering**: `2.jpg` comes before `10.jpg`
- **Seamless reader**: tall pages are sliced internally so huge strips scroll smoothly
- Zoom, page slider, previous/next chapter, saved position per chapter, read/unread tracking

## Credits
- UI typeface: [Outfit](https://fonts.google.com/specimen/Outfit) via Google Fonts, SIL Open Font License (see `licenses/Outfit-OFL.txt`)
- Icons: Material Symbols (Rounded)
