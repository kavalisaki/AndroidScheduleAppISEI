# 📱 Schedule App (ISEU Schedule)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A clean, fast, and simple Android app for university class schedules.

Built by students for students. Instead of opening a slow browser page on your phone, zooming in on tiny tables, and dealing with bad mobile connectivity, this app saves the schedule right to your device and works seamlessly offline.

---

## ⚡ Features

- 📴 **Works Offline:** Loads your schedule once and keeps it saved on your device. Check your classes even in basement lecture halls with no cellular service.
- ⏱️ **Live Class Timer:** Displays a real-time countdown to the end of the current class or break right on the main screen.
- 🔍 **Schedule Change Highlights:** Automatically detects and highlights if a class was rescheduled, moved to a different room, or replaced.
- 📍 **Prominent Room Badges:** Classroom and campus building numbers are displayed large and bold so you can spot them at a glance while walking.
- 👥 **Course & Subgroup Filters:** Supports 1st through 5th-year students with quick filtering between subgroups (1st, 2nd, or entire group).
- 📅 **Intuitive Calendar:** Effortless week switching, automatic rollover to the next week on Sundays, and Saturday support.
- 📲 **Home Screen Widget:** Glance at today's classes, rooms, and times directly from your home screen.
- 🌙 **Dark & Light Themes:** Clean Material You design that matches system colors without visual clutter.
- 🪶 **Lightweight (~2 MB):** Fast startup, zero ads, no background battery drain, and no tracking.

---

## 📥 Download & Install

1. Go to the **[Releases](https://github.com/kavalisaki/AndroidScheduleAppISEI/releases)** section.
2. Download `app-release.apk`.
3. Open the file on your Android phone and install.

---

## 🛠️ Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Architecture:** MVVM + Clean Architecture
- **Networking:** OkHttp 4 + Jsoup (with rate limiting to avoid overloading university servers)
- **Cache:** `kotlinx.serialization` (JSON on disk)

---

## 💻 Building from Source

To build the APK yourself:

1. Clone the repository:
   ```bash
   git clone git@github.com:kavalisaki/AndroidScheduleAppISEI.git
   ```
2. Open the project in **Android Studio**.
3. Or build the release APK via command line:
   ```bash
   ./gradlew assembleRelease
   ```
   The APK will be located in `app/build/outputs/apk/release/`.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE) — feel free to use, modify, and share it.
