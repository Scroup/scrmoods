# scrmoods

A personal mood and wellness tracker for Android, modelled on eMoods: a daily check-in with the same questions and layout, your own custom tracking points, medications, notes, a calendar, and graphs. Everything is saved in a single XML file.

## Getting the APK (GitHub builds it for you)

1. Create a new repository on GitHub and upload/push the contents of this folder (including the hidden `.github` folder and `.gitattributes`).
2. GitHub starts building automatically. Watch it under the **Actions** tab (takes about 5 minutes).
3. When it's green, open the **Releases** section on the repo's main page → **latest** → download `scrmoods.apk`.
   (It's also attached to each run under Actions → the run → *Artifacts*, as a zip.)
4. Open the APK on your phone. Android will ask you to allow installing from your browser/file manager; allow it once.

Every push builds a new version. Installing a newer APK over the old one keeps your data, because all builds are signed with the same key (`app/signing/scrmoods.jks`, password `scrmoods`). That key is only meant for this personal app. Don't reuse it for anything published on Google Play.

If the build fails, open the failed run in the Actions tab and copy the red error lines; that's all that's needed to fix it.

## What's in the app

- **Check-in**: daily questions grouped like eMoods (Daily Check-in, Health and Self Care, Mental and Social Stimulation), sleep slider, custom tracking points, medications, today's note and timestamped notes. Use the arrows to move between days; tap the date to jump back to today. Tap a selected answer again to clear it.
- **Calendar**: month view with a coloured dot per logged day (red → green by overall outlook). Tap a day to open it.
- **Graph**: choose any tracking point and a period (7 days to 1 year) to see a chart, the average, the yes %, how often each answer was picked, and a summary of all points.
- **Data**: export the XML file (backup, or to analyse on a computer) and import it back.
- **⚙ Tracking points**: add your own points (scale with your own options, yes/no, number slider, or text), edit, reorder, or remove any point, built-in ones included. Removed points are hidden but their history is kept (and they can be restored); *Delete forever* also erases their answers.

## The data file

Everything lives in one XML file (`scrmoods.xml` inside the app; use **Data → Export** to get a copy):

```xml
<scrmoods version="1">
  <sections> <section id="daily" title="Daily Check-in"/> ... </sections>
  <points>
    <point id="outlook" section="daily" type="scale" label="How was your overall outlook today?" archived="false">
      <option>Negative</option><option>Neutral</option><option>Positive</option>
    </point>
    <point id="sleep_hours" section="health" type="number" label="Last night's sleep" min="0.0" max="16.0" step="0.5" unit="hrs" archived="false"/>
    ...
  </points>
  <entries>
    <entry date="2026-10-11">
      <value point="outlook" text="Positive">2</value>
      <value point="sleep_hours" text="7.5 hrs">7.5</value>
      <value point="exercise" text="Yes">1</value>
      <note>Today's note</note>
      <timednote time="2026-10-11 23:52">A timestamped note</timednote>
    </entry>
  </entries>
</scrmoods>
```

Each answer is stored as a number (scale = option position starting at 0, Yes = 1 / No = 0, number points as-is) **and** its readable text, so it's easy to both compute with and read.

XML was chosen over plain text because custom points come and go over time: each value names its point, so adding or removing questions never shifts or breaks old data, and notes with commas or line breaks are stored safely.

### Turning it into a spreadsheet

`tools/scrmoods_to_csv.py` converts an export into a CSV (one row per day, one column per point) and prints averages:

```
python tools/scrmoods_to_csv.py scrmoods-2026-10-11.xml
python tools/scrmoods_to_csv.py scrmoods-2026-10-11.xml --text   # "Positive" instead of 2
```

## iPhone version

The iPhone version is a separate web app in its own public repository, **scrmoods-web** (https://scroup.github.io/scrmoods-web/). It uses the same XML file format, so you can export from one app and import into the other.

## Building on your own computer (optional)

Open this folder in Android Studio and press Run, or run `gradlew assembleRelease`. The APK ends up in `app/build/outputs/apk/release/`.

Tech: Kotlin, Jetpack Compose, Android 8.0+ (minSdk 26). No internet permission; the data stays on the phone.
