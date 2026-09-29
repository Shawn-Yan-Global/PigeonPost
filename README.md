# PigeonPost 📮

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-13%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-purple.svg)](https://kotlinlang.org)
[![Version](https://img.shields.io/badge/Version-1.0.0-orange.svg)](https://github.com/yourusername/PigeonPost)

[中文文档](README_ZH.md) | **English**

An Android application for monitoring SMS messages and automatically forwarding them to email addresses based on customizable templates.

## ✨ Features

### 🎯 Core Features
- **SMS Monitoring**: Continuously listens for incoming SMS messages
- **Email Forwarding**: Automatically forwards matched SMS to configured email addresses
- **Template Configuration**: Keyword matching, case sensitivity, AND/OR logic
- **Email Configuration**: SMTP server configuration with quick setup presets (Gmail, QQ Mail, 163 Mail)
- **Forward Records**: View recent SMS records with manual retry capability and pull to refresh

### 🚀 Advanced Features
- **Active Time Period**: Configure high-priority monitoring periods (e.g., 8:00-9:00 daily)
- **Permission Monitoring**: Real-time permission status monitoring with alerts
- **Auto-start**: Automatically starts after device reboot or app updates
- **Log Management**: View, export, and auto-cleanup logs, with explicit feedback on how many files a cleanup removed
- **Record Management**: Auto-cleanup old records with configurable retention periods
- **Internationalization**: Full support for English and Chinese
- **No Filter Mode**: Option to forward all SMS without filtering

### 🎨 User Experience
- **Material Design 3**: Modern and beautiful UI
- **Dark Mode Support**: Automatically adapts to system theme
- **Language Settings**: In-app language switching
- **Notification Control**: Granular control over notification types
- **Quick Actions**: Stop monitoring from notification bar
- **Save Confirmation**: Settings that overwrite stored configuration ask before committing

## 🏗️ Architecture

The project is a multi-module Gradle build. Kotlin sources live under `src/main/kotlin` in every
module, and a shared `ktlint` convention plugin keeps formatting consistent.

```
PigeonPost/
├── app/                                  # Application module
│   └── src/main/kotlin/com/octopus/pigeon/post/
│       ├── PigeonPostApplication.kt      # Startup, module wiring, settings bridge
│       ├── service/
│       │   ├── SmsMonitorService.kt      # Foreground SMS monitoring service
│       │   ├── PermissionMonitorService.kt
│       │   ├── SmsMatchingService.kt     # Template matching
│       │   ├── TemplateService.kt
│       │   ├── EmailService.kt           # SMTP delivery
│       │   ├── CleanupService.kt         # One-off cleanup requests
│       │   ├── CleanupWorker.kt          # Scheduled cleanup (WorkManager)
│       │   ├── SmsKeepAliveWorker.kt     # Keep-alive safety net (WorkManager)
│       │   └── CleanupActions.kt         # Shared cleanup logic
│       ├── receiver/
│       │   ├── SmsReceiver.kt            # SMS reception
│       │   └── BootReceiver.kt           # Boot completed
│       ├── data/
│       │   ├── database/                 # Room database, DAOs, converters
│       │   ├── repository/               # Data repositories
│       │   └── model/                    # Data models
│       ├── ui/
│       │   ├── activity/                 # Activities
│       │   ├── screen/                   # Compose screens
│       │   ├── viewmodel/                # ViewModels
│       │   ├── component/                # Shared Compose components
│       │   └── theme/                    # Theme configuration
│       ├── permission/                   # Permission helpers
│       └── util/                         # Utility classes
├── core/
│   ├── logging/                          # Logging + log management UI
│   ├── locale/                           # Runtime language switching
│   └── ui/                               # Shared design system (AppSpacing)
├── buildSrc/                             # ktlint convention plugin
└── gradle/                               # Version catalog, wrapper
```

### 📦 Modules

#### App Module (`:app`)
The main application module containing:
- UI components built with Jetpack Compose
- Background services for SMS monitoring and email forwarding
- Data persistence using Room and DataStore
- Permission management and monitoring

#### Core Modules

**Logging Module** (`:core:logging`)
- Structured logging system built on Log4j
- Log file management, viewing and export
- Manual and scheduled log cleanup
- Injected into the app through `LoggingConfig.initialize()`

**Locale Module** (`:core:locale`)
- Centralized language management
- Runtime language switching, persisted in DataStore
- `LocaleAwareComponentActivity` base class that applies the saved locale

**UI Module** (`:core:ui`)
- Shared design-system values, currently `AppSpacing`
- Keeps hardcoded spacing out of the feature modules

## 🔧 Technical Stack

### Core Technologies
| | |
|---|---|
| **Language** | Kotlin 2.4.20 |
| **Build System** | Gradle 9.7.1 with Kotlin DSL, AGP 9.4.1 |
| **JDK** | 21 |
| **Compile SDK** | 37 |
| **Min SDK** | 33 (Android 13) |
| **Target SDK** | 37 |

### UI Framework
- **Jetpack Compose** (BOM 2026.09.00) with Material Design 3
- **Compose Navigation** 2.10.2

### Architecture Components
- **ViewModel**: MVVM pattern
- **StateFlow**: Reactive state management
- **Room** 2.8.5: Android local database
- **DataStore**: configuration storage
- **KSP** 2.3.6 for annotation processing

### Background Processing
- **Coroutines** for in-process async work
- **WorkManager** for everything that must survive process death and reboots:
  - `SmsKeepAliveWorker` restores the monitoring service if the system kills it
  - `CleanupWorker` runs the scheduled log/record cleanup once every 24 hours
- **Foreground Services** (`dataSync` + `specialUse`) for reliable SMS monitoring

There is no `AlarmManager` or `JobScheduler` scheduling: the old wake-up chain was removed because
WorkManager already covers both cases more reliably.

### Email
- **JavaMail** (Android compatible) over SMTP

### Other Libraries
- **Log4j** for logging
- **Process Phoenix** for app restart

## 🏗️ Building

```bash
# Everything: assemble, lint, unit tests (this is what CI should run)
./gradlew build

# Debug build
./gradlew assembleDebug

# Release build (R8 minified + resource shrinking)
./gradlew :app:assembleRelease

# Unit tests
./gradlew :app:testDebugUnitTest

# Lint only
./gradlew :app:lintDebug
```

`ktlint` runs automatically before every `assemble` and `test` task, so sources are formatted
without anyone having to remember it. To run it explicitly:

```bash
./gradlew ktlintCheck    # report violations
./gradlew ktlintFormat   # fix them
```

## 📱 Getting Started

### Prerequisites
- Android Studio with AGP 9.4.1 support
- JDK 21
- Android device or emulator running Android 13 or higher

### Installation

1. Clone the repository
```bash
git clone https://github.com/yourusername/PigeonPost.git
cd PigeonPost
```

2. Open the project in Android Studio

3. Sync Gradle and build the project

4. Run on your device or emulator

### Configuration

1. **Grant Permissions**: Allow SMS read and notification permissions
2. **Configure Template**: Set up keywords and matching logic
3. **Configure Email**: Set up SMTP server and credentials
4. **Optional - Active Time**: Configure high-priority monitoring periods
5. **Start Service**: Enable SMS monitoring

### Debug Menu Password

The debug menu is reached from the drawer, under Advanced options, or by
long pressing the version number three times. Either way it asks for a
password.

The password is asked once. After that the menu stays unlocked, including
across restarts, so it is not a per-visit prompt. Changing the password locks
it again, so the next visit has to use the new one. Clearing the app's data or
uninstalling also locks it.

Put it in `local.properties`, which is gitignored:

```properties
pigeonpost.debug.password=your password here
```

The build hashes it and packages only the hash, so the password itself is not
in the APK and cannot be read out of it with `strings`. If the key is missing,
the build still succeeds and generates a random password for that build only,
printed near the top of the output. CI therefore never needs the key, and
never ships a password anyone else knows.

The salt lives in `gradle.properties` as `pigeonpost.debug.password.salt`. It
is not a secret, but it must not change once released: the app re-salts what
you type before comparing it to the stored verifier, so a new salt would lock
out anyone who already set their own password.

## 📋 Permissions

The app requests the following permissions:

| Permission | Purpose |
|------------|---------|
| `READ_SMS` | Read SMS content for forwarding |
| `RECEIVE_SMS` | Receive SMS broadcasts |
| `INTERNET` | Send emails via SMTP |
| `FOREGROUND_SERVICE` | Keep monitoring service alive |
| `FOREGROUND_SERVICE_DATA_SYNC` | Service type specification |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Service type for SMS/permission monitoring |
| `RECEIVE_BOOT_COMPLETED` | Auto-start after reboot |
| `VIBRATE` | Alert users about permission issues |
| `POST_NOTIFICATIONS` | Show notifications (Android 13+) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Reliable background operation |

`androidx.work` additionally contributes `WAKE_LOCK` and `ACCESS_NETWORK_STATE` for its own internal
scheduling; the app does not use them directly.

## 🔐 Privacy & Security

- **No Data Collection**: All data stays on your device
- **Local Storage**: SMS records and logs are stored locally
- **Secure Credentials**: Email credentials are stored in DataStore
- **Obfuscation**: Release builds apply R8 with aggressive shrinking, control-flow obfuscation and resource name adaptation
- **Open Source**: Fully transparent code for security audit
- **No Analytics**: No third-party tracking or analytics

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

Before opening a PR, please make sure `./gradlew build` passes.

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

```
Copyright 2025-2026 PigeonPost Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## 👥 Authors

- **PigeonPost Team** - *Initial work*

## 🙏 Acknowledgments

- Thanks to all contributors who have helped improve this project
- Built with [Jetpack Compose](https://developer.android.com/jetpack/compose)
- Email functionality powered by [JavaMail API](https://javaee.github.io/javamail/)

## 📞 Support

If you have any questions or issues, please:
1. Check the [Issues](https://github.com/yourusername/PigeonPost/issues) page
2. Create a new issue if your problem isn't already listed
3. Provide detailed information about your problem

---

**Made with ❤️ for Android developers who need reliable SMS forwarding**
