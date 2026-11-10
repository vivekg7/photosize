# PhotoSize

Resize and compress your photo and signature to the exact size Indian exam application
forms ask for: SSC, UPSC, IBPS, SBI, RRB, NEET, JEE Main, CUET, GATE, passport size, or
your own preset.

The Play Store apps that do this are full of ads, and the people using them are students
filling a form an hour before the deadline. PhotoSize is free, open source (MIT), with
**no ads, no tracking, no analytics, no account and no internet permission**.

## Features

- Presets bundled in [`app/src/main/assets/presets.json`](app/src/main/assets/presets.json):
  each exam lists its uploads (photo, signature, and for banks the thumb impression and
  handwritten declaration) with width × height in px, min/max KB, format and DPI.
- Custom presets for any form the list doesn't cover, kept on the device.
- Pick a picture from the system Photo Picker or take one with your camera app.
- Crop under a fixed frame with the preset's aspect ratio: drag to move, pinch to zoom,
  rotate in 90° steps. EXIF orientation is applied, so phone photos aren't sideways.
- Automatic KB fitting (see below), with the JFIF DPI written when the preset gives one.
- Signature mode: whitens unevenly lit paper and darkens the ink, with a slider for faint
  strokes.
- Optional name and date strip at the bottom of the photo.
- Shows the final size in KB, bytes and px, and the JPEG quality used, before saving.
- Save to `Pictures/PhotoSize`, or share to another app.

A note in the app reminds users that specs change with every notification and must be
checked against the official one.

## Permissions

None. The manifest declares no permissions at all:

| Need            | How, without a permission                                                                                                                                                 |
| --------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Pick a photo    | System Photo Picker (`PickVisualMedia`).                                                                                                                                  |
| Take a photo    | The user's camera app via `ACTION_IMAGE_CAPTURE`, writing to a `FileProvider` cache file. Declaring `CAMERA` would actually make Android block this intent until granted. |
| Save the result | `MediaStore` insert into `Pictures/PhotoSize`, allowed without permission since Android 10.                                                                               |
| Share           | `ACTION_SEND` with a `FileProvider` URI and a one-off read grant.                                                                                                         |
| Network         | Not declared. Nothing leaves the phone. See [PRIVACY.md](PRIVACY.md).                                                                                                     |

## Building

Requirements: Android SDK with platform 37. This machine has no system JDK; use Android
Studio's bundled JBR:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug testDebugUnitTest
./gradlew assembleRelease   # unsigned unless keystore.properties exists
```

Release signing reads an optional, git-ignored `keystore.properties` (`storeFile`,
`storePassword`, `keyAlias`, `keyPassword`). Without it the release APK is built unsigned.

## How it works, and why

**KB fitting** (`core/SizeFitter.kt`). At the preset's size, binary-search the highest JPEG
quality (95 down to 40) whose file is under the maximum: about seven encodes, not a scan.
If quality 40 is still too big and the preset allows a range of sizes, shrink the picture
by the square root of the overshoot and search again; quality is not pushed below 40
first, because a slightly smaller image looks better than a blocky one. At the smallest
allowed size it goes down to quality 1 before reporting failure. The encoder sits behind
an `ImageEncoder` interface so the whole search is unit-tested on the JVM with a fake.

**Too-small files are padded.** A clean signature at 140 × 60 px is often 5 KB even at
the top quality, while IBPS wants at least 10 KB. More quality can't add detail that isn't
there, so the file is grown with JPEG comment segments, which every decoder skips: the
picture is byte-for-byte the same and the app says so on screen.

**KB is ambiguous**, so both readings are satisfied: the minimum is taken as KB × 1024 and
the maximum as KB × 1000. A file inside that window passes a portal using either.

**DPI** is written into the JFIF header (`core/JpegMarkers.kt`), patched in place, since
some portals and printing checks read it and Android's encoder leaves it unset.

**Signature cleanup** (`core/InkCleaner.kt`). Phone photos of paper are never evenly lit,
so one global threshold either leaves a grey corner or eats strokes. Each pixel is divided
by the local paper brightness (a wide box blur from a summed-area table, re-estimated with
the ink masked out), which makes the paper uniformly white; then everything above the
threshold goes white and the ink is stretched to black with soft edges.

**Pixel sizes the notification doesn't give.** NTA (NEET, JEE, CUET) and UPSC publish only
KB limits. For those presets the app picks a sensible size and allows it to shrink, which
the portals accept. Sources and the date checked are in each preset.

**Name and date strip.** The bottom 20% of the frame becomes a white band with the name and
date. The crop screen shows that band over the photo, so what you frame is what you get.

**Own crop UI, no crop library**, to keep the app small, dependency-light and F-Droid
friendly. Decoding uses `BitmapFactory` plus an explicit EXIF matrix, which behaves the
same on every Android version.

## Presets and their sources

Checked on 10 November 2026 against: SSC CGL 2026 notice; IBPS PO/MT-XVI (2026); SBI PO
2026-27; NEET UG 2026, JEE Main 2027 and CUET UG 2026 information bulletins; GATE 2026
(IIT Guwahati) site. UPSC and RRB NTPC (CEN 06/2026) come from secondary sources because
the official sites were unreachable; treat them with more care. SSC and recent RRB forms
capture the photo live in the browser, so those presets contain only the signature.

To fix or add an exam, edit `presets.json` (same schema as custom presets: `kind` is
`photo` or `ink`; `minWidth` makes the size flexible; `dpi` and `nameDate` are optional)
and run the unit tests, which validate the file.

## License

[MIT](LICENSE)
