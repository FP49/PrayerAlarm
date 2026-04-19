# NamaazAlarm

A native Android prayer alarm app. Enter your location once — the app fetches accurate prayer times for your area for the entire month and schedules precise alarms for all five daily prayers automatically.

No timetable images. No manual entry. Works for any city in the world.

---

## Download

Go to the [Releases](https://github.com/FivePizza412079/NamaazAlarm/releases) page and download the latest `app-release.apk`.

**Before installing:**

```
Settings > Apps > Special Access > Install unknown apps
> Allow from this source
```

---

## How It Works

```
Open app > tap Fetch Prayer Times > confirm your location
> choose calculation method > tap Fetch > review times > Set All Alarms
```

Prayer times are fetched from [Aladhan.com](https://aladhan.com) — a free Islamic prayer times API that calculates accurate times using your GPS coordinates. No account or API key required. One internet connection is needed once per month to fetch the new month's times.

---

## Features

**Prayer Times**
- Fetches all 30/31 days of prayer times in a single tap
- Covers all five prayers: Fajr, Zuhr, Asr, Maghrib, Isha
- Supports 9 calculation methods including Hanafi/Karachi (default), MWL, ISNA, Umm Al-Qura, Turkey, Moonsighting Committee
- Asr always calculated using Hanafi school regardless of method
- Times can be individually corrected if your mosque differs slightly

**Alarms**
- All alarms set upfront for the entire month in one go
- Alarms fire even when the app is closed and the screen is off
- Uses `setAlarmClock()` — the same API Samsung's Clock app uses — fires reliably in Ultra Power Saving mode
- Full-screen alarm appears over the lock screen with a Stop button
- Alarm plays your chosen audio for exactly 5 minutes; white noise fills any shortfall automatically

**Reminders**
- 30-minute and 10-minute pre-prayer notifications for Zuhr, Asr, Maghrib and Isha
- Reminder notifications can be dismissed by swipe
- Full alarm notification stays on screen until Stop is tapped

**Dashboard**
- Circular countdown ring showing time until next prayer
- Today's five prayer times shown at a glance
- Current Hijri (Islamic) date
- Device city name via GPS

**Audio**
- Choose any audio file (adhan, nasheed) as the alarm sound
- Option to use a separate audio file specifically for Fajr
- App warns if file is under 5 minutes and fills remainder with white noise

**Settings**
- 12-hour (default) or 24-hour time format toggle
- Calculation method selection
- Separate Fajr azaan toggle with its own file picker

**Background**
- WorkManager job runs every 15 minutes to reschedule any alarms Samsung may have cancelled
- Daily midnight cleanup removes past days from the alarm list automatically
- Alarms are rescheduled automatically after device reboot

---

## First Install Steps

```
1. Install the APK from the Releases page
2. Open Namaaz Alarm
3. Complete Step 1: Battery Optimisation dialog
   - Tap Go to Settings
   - Change dropdown to All apps
   - Find Namaaz Alarm > Don't optimise
   - Press Back
4. Complete Step 2: Never Sleeping Apps dialog
   - Tap Open Samsung Battery Settings
   - Battery > Never sleeping apps > + > Namaaz Alarm > Add
   - Come back and tap Done
5. Tap Fetch Prayer Times
6. Allow location permission when prompted
7. Confirm your calculation method (defaults to Hanafi/Karachi)
8. Tap Fetch
9. Review extracted times — tap any time to correct it
10. Tap Set All Alarms
11. Allow Alarms and Reminders permission if prompted on Android 12+
```

---

## End of Month

When the new month begins, a banner appears on the dashboard. Tap **Fetch Prayer Times** to load the new month. Old alarms are cancelled automatically when new ones are set.

---

## Calculation Methods

| Method | Best for |
|--------|----------|
| Hanafi / Karachi (default) | UK South Asian mosques, Pakistan, India, Bangladesh |
| Muslim World League | General worldwide use |
| Umm Al-Qura | Saudi Arabia |
| ISNA | North America |
| Egyptian Authority | Egypt and nearby |
| Kuwait | Kuwait |
| Qatar | Qatar |
| Turkey (Diyanet) | Turkey |
| Moonsighting Committee | Global moonsighting-based |

---

## Samsung Battery Settings

Samsung aggressively kills background processes. Two settings are required for reliable alarms:

**Setting 1 — Battery Optimisation**
```
Settings > Apps > three dots (top right) > Special Access
> Optimise battery usage
> Change dropdown to All apps
> Find Namaaz Alarm > Don't optimise
```

**Setting 2 — Never Sleeping Apps**
```
Settings > Device Care (or Battery and Device Care) > Battery
> Never sleeping apps > + > find Namaaz Alarm > Add
```

The app guides you through both on first launch.

---

## Requirements

- Android 5.0 (API 21) minimum
- Tested on Samsung SM-T290 (Android 9)
- Internet connection required once per month to fetch prayer times
- Location permission required for GPS-based time calculation

---

## Privacy

- Location is used only to calculate prayer times. It is not stored, transmitted, or shared beyond the Aladhan API call.
- The Aladhan API receives your GPS coordinates and returns prayer times. See their [privacy policy](https://aladhan.com/privacy-policy).
- No analytics, no tracking, no ads.

---

## License

Public repository. All rights reserved.
