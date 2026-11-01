# Notes

Futuristic HUD notes app for Android. Package: `com.mux.notes`

Note types: text, task list, list (shopping, bullet, numbered), drawing and handwriting (stylus, pressure, eraser, zoom),
and a spreadsheet with formulas (monthly budget template included).
Also: search, pin, color labels, archive, trash, PIN lock, share, backup and restore.

## Build
Push to GitHub -> Actions -> "Build APK" -> download artifact `Notes-debug-apk`.

## Releasing an update
1. Change `appVersionName` in `app/build.gradle.kts` (and `version` in `metadata.json`).
2. Push, wait for the build, download the APK.
3. GitHub -> Releases -> new release, tag `v1.0.1` (same number), attach the APK, publish.

The app checks `releases/latest` of `mahfuz0712/Notes` on launch and from SYS -> CHECK FOR UPDATES.
`app/debug.keystore` keeps the signature identical on every build, so new APKs install over old ones.

## Spreadsheet formulas
Cells starting with `=` are formulas. Operators `+ - * / ^ %`, comparisons `= <> < > <= >=`, references `A1`, `$A$1`, ranges `A1:B5`.
Functions: SUM, AVERAGE (AVG), MIN, MAX, COUNT, ROUND, ABS, SQRT, IF, SUMIF, COUNTIF.
Example: `=SUMIF($B$2:$B$32,"Food",$E$2:$E$32)`.

Author: Mohammad Mahfuz Rahman, mahfuzrahman0712@gmail.com, https://github.com/mahfuz0712
