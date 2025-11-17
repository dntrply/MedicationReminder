# Medication Reminder – Human Testing Checklist

This document describes areas where a human tester should exercise the app and how to do it. It is intended for non‑technical testers as well as developers organizing manual QA sessions.

---

## 1. Setup

- Install the latest `app-release.apk` following the steps in `TESTER_INSTRUCTIONS.md`.
- Ensure app notifications are allowed and battery usage is set to **Unrestricted** for Medication Reminder.
- Have a stable internet connection only if specifically asked; most testing should work offline.
- Optionally, be ready to adjust the device time to simulate upcoming reminders.

When reporting issues, always include:
- What you were trying to do
- What happened instead (the problem)
- Screenshots, if possible
- Phone model and Android version

---

## 2. Basic Medication Reminders

**Goal:** Verify that adding medications and receiving reminders works end‑to‑end.

Steps:
- Add 1–2 medications with:
  - Name and dosage
  - Photo of the medication
  - Simple daily times (e.g., within the next 5–10 minutes)
- Confirm they appear on the Home / timeline view at the correct times.
- Wait for the reminder time:
  - Check that a notification appears with the correct medication name and time.
  - Tap the notification and confirm the app opens to the relevant screen and shows the due dose.
  - Mark the dose as **Taken** and verify the Home screen and history later reflect this.
- For a second dose:
  - Dismiss the notification without opening the app, or mark it as **Skipped** (if available).
  - Confirm that the dose eventually shows as missed/overdue and that any follow‑up behavior looks reasonable.

Things to note:
- Any reminders that don’t arrive on time
- Wrong medication names or times in notifications
- App not opening to a sensible screen when tapping the notification

---

## 3. Grouped / Multiple Notifications

**Goal:** Check behavior when several medications are due around the same time.

Steps:
- Create at least 3 medications scheduled within the same 5–10 minute window.
- When that time comes:
  - Observe how notifications appear (grouped, stacked, or separate).
  - Open each notification and confirm the list of due medications is correct.
- Mark some medications as **Taken**, leave others untouched:
  - Confirm the timeline shows only remaining meds as overdue/outstanding.
  - Watch for repeat notifications (based on the repeat interval in Settings) for those not taken.

Things to note:
- Confusing or duplicated notifications
- Medications missing from notifications even though they are due

---

## 4. Profiles (Multi‑User Support)

**Goal:** Ensure profiles keep data separate and switching is clear.

Steps:
- From the profile area (usually near the top of the home screen), create two profiles, e.g. **Self** and **Parent**.
- While on **Profile A**:
  - Add 1–2 medications and schedule reminders.
- Switch to **Profile B**:
  - Confirm medications from Profile A are not visible.
  - Add a different set of medications for Profile B.
- Switch between profiles several times:
  - Verify that each profile shows only its own medications, history, and reports.
- Trigger a few reminders for each profile and pay attention to how they are identified.

Things to note:
- Any mixing of medications or history between profiles
- Confusion about which profile a notification belongs to

---

## 5. History Screen

**Goal:** Validate the history views “By Date” and “By Medication”.

Steps:
- After taking and skipping a few doses over at least one day, open the **History** screen.
- Switch between:
  - **By Date** – check that entries are grouped under the correct dates, including labels like “Today” and “Yesterday”.
  - **By Medication** – pick a medication and verify that its entries match your actual actions for that medication.
- Use the **Load previous 3 days** action:
  - Confirm older days appear without duplicates.

Things to note:
- Entries under the wrong day
- Missing or duplicated history records
- Incorrect labels or times

---

## 6. Reports Screen (Summary, Calendar, Trends, By Medication)

**Goal:** Ensure reports match real behavior.

Steps:
- Open **Reports** from the menu and explore all tabs:
  - **Summary** – check overall adherence percentages, taken/missed counts, and any streaks match your recent behavior.
  - **Calendar** – confirm day colors (e.g., green/yellow/red/gray) reflect how many doses you took or missed on each day. Pay attention to Sundays and mixed‑behavior days.
  - **Trends** – look at graphs and confirm they change sensibly as you take or miss doses over several days.
  - **By Medication** – for each medication, check counts and percentages against the History screen.
- If there is a setting for the default report tab, change it in Settings and reopen Reports to confirm it opens on the chosen tab.
- Repeat a quick check after switching profiles to confirm reports show data for the active profile only.

Things to note:
- Calendar days that stay uncolored even when you took or missed doses
- Percentages or counts that obviously don’t match history
- Any tab that doesn’t seem to update when data changes

---

## 7. Export and Data Tools

**Goal:** Confirm data export works and produces usable files.

Steps:
- Go to **Settings → Export / Reports**.
- Run **Export history CSV** (or similarly named option):
  - Watch for an “exporting” or progress state followed by a success message.
  - Open the **Downloads** folder and confirm that a new file was created.
  - If possible, open the file on a computer and check:
    - Columns such as date, time, medication name, profile, and status
    - Several rows match what you see in the History/Reports screens
- Open the **Debug Data** or **Debug** screen (if available):
  - Try the **export debug data** feature and confirm you see a success/failure message and a file is created.
  - Avoid destructive actions such as “delete history” until you are done with other testing, or only use them when explicitly requested.

Things to note:
- Export operations that never finish
- Missing or obviously wrong data in exported files

---

## 8. Settings & Preset Times

**Goal:** Verify that settings actually change app behavior.

Steps:
- **Language**
  - Switch between English and at least one other language (Hindi, Marathi, Gujarati, or Hinglish).
  - Confirm main screens (Home, Add Medication, History, Reports, Settings) update their text properly and nothing essential becomes unreadable.
- **Preset times**
  - Expand the preset times section (morning, lunch, evening, bedtime).
  - Change each preset to a different time.
  - Add a new medication using these presets and confirm the default reminder times match your new presets.
- **Notification repeat interval**
  - Change the repeat interval (for example from 10 minutes to 5).
  - Leave a dose un‑taken and confirm follow‑up notifications arrive at the new interval.
- **Report defaults and other options**
  - Toggle any settings related to default report tab, summaries, or calendar behavior.
  - Navigate back to the relevant screens and confirm behavior matches the description.

Things to note:
- Settings that appear to save but do not change behavior
- Language changes that leave key screens untranslated or partly broken

---

## 9. Audio Notes and Transcription

**Goal:** Confirm recording and optional transcription flows.

Steps:
- When adding or editing a medication, record an audio note (e.g., “Take after breakfast with water”).
- Confirm:
  - The app requests microphone permission the first time you record, and recording works afterward.
  - The recorded note can be played back later from the medication details.
- If transcription is available:
  - Look for a **transcription consent** dialog and verify that you can accept or decline.
  - After recording, check whether a text transcription appears and roughly matches what you said.
- Try deleting or re‑recording an audio note and confirm the displayed text and playback update accordingly.

Things to note:
- Crashes or freezes during recording or playback
- Transcriptions that never appear or are clearly tied to the wrong recording

---

## 10. Reliability & Edge Cases

**Goal:** Check how the app behaves in less common but realistic situations.

Steps:
- **App restart**
  - Force close the app and reopen it.
  - Ensure medications, profiles, history, and settings are all preserved.
- **Device reboot**
  - Schedule a reminder, then reboot the phone before it fires.
  - After reboot, confirm future reminders still arrive without needing to open the app first.
- **Time and time zone changes**
  - Temporarily change the device time or time zone.
  - Check upcoming reminders, calendar, and history. Confirm days and times still make sense (no obvious duplication or missing days).
- **Large data sets**
  - Add many medications (e.g., 10–15) and generate history over several days.
  - Scroll through Home, History, Reports, and profile menus to check performance and layout.

Things to note:
- Reminders that disappear or get duplicated after time or device changes
- Noticeable slowdowns, stutters, or layout breakage with many medications

---

## 11. UX, Accessibility, and Overall Feel

**Goal:** Capture qualitative feedback beyond pure correctness.

Questions for testers:
- Is it obvious how to:
  - Add a new medication?
  - Mark a dose as taken or skipped?
  - See what you missed in the past?
  - View your progress over time?
- Are text sizes and colors easy to read, especially for older users?
- Are important buttons and touch targets large enough and easy to tap?
- How does the app feel when:
  - System font size is increased?
  - Dark mode (if supported) is enabled?

Encourage testers to write down anything confusing, surprising, or delightful. These notes are valuable even if nothing is “broken.”

---

## 12. How to Report Issues

For each issue, please provide:
- Short title (e.g., “Sunday not marked in Calendar view”)
- Steps you followed (1, 2, 3…)
- What you expected to happen
- What actually happened
- Screenshots or screen recordings, if possible
- Phone model and Android version
- App version (if visible in Settings → About)

This information helps developers quickly reproduce and fix problems discovered during human testing.

