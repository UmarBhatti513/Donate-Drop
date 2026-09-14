# DonateDrop

### Connecting blood donors and recipients through an Android application

DonateDrop helps users create blood requests, find nearby donors, and coordinate donations. Built with **Kotlin and Firebase**, it brings request management, location-based discovery, donation history, and administration into one mobile application.

Developed as a BS Information Technology final-year project at **The Shaikh Ayaz University Shikarpur**.

## Features

- **Account management:** Email/password registration, sign-in, password recovery, profile editing, and account deletion.
- **Blood requests:** Create requests with blood group, quantity, urgency, and contact details; respond to requests and track their progress.
- **Nearby donor search:** Find users through GeoFirestore location queries and filter by blood group.
- **Donation history:** Review completed donations and request details.
- **Notifications:** View and manage in-app notifications stored in Firestore.
- **Profile images:** Upload images through Cloudinary.
- **Admin dashboard:** Manage users and requests, review completed requests, and view donation analytics.
- **Email integration:** A Firebase Cloud Function sends a requester an email through SendGrid when a request changes to pending, once the backend is configured.

## Request workflow

| Stage | Meaning |
| --- | --- |
| Open | A blood request is available for a donor to respond. |
| Pending | A donor has responded and coordination is underway. |
| Completed | The donation has been recorded as completed. |

## Technology stack

| Area | Technologies |
| --- | --- |
| Android application | Kotlin, Android Studio, XML layouts, View Binding |
| Interface | Material Components, RecyclerView, AndroidX Navigation |
| Presentation logic | Activities, Fragments, ViewModels, LiveData |
| Authentication and data | Firebase Authentication, Cloud Firestore |
| Location | Google Play Services Location, GeoFirestore |
| Images and networking | Cloudinary, Glide, OkHttp, Kotlin Coroutines |
| Charts | MPAndroidChart |
| Email backend | Node.js, Firebase Cloud Functions, Firebase Admin SDK, SendGrid |
| Build system | Gradle Kotlin DSL, Gradle version catalog |

## Getting started

### Requirements

- Android Studio compatible with Android Gradle Plugin **8.10.1**.
- A compatible Gradle JDK, with **JDK 17** as the minimum for Android Gradle Plugin 8.x. See the [Android Java build documentation](https://developer.android.com/build/jdks).
- Android SDK **35**; an emulator or device running Android **7.0 / API 24** or later.
- Your own Firebase project and Cloudinary account.
- For the optional email backend: Node.js **22**, npm, Firebase CLI, and a SendGrid account.

The repository includes its Gradle wrapper. Kotlin is configured at **2.0.21**, and the application targets SDK **35**.

### 1. Clone the repository

```bash
git clone https://github.com/UmarBhatti513/Donate-Drop.git
cd Donate-Drop
```

Open this folder in Android Studio.

### 2. Configure Firebase

1. Create a project in the [Firebase Console](https://console.firebase.google.com/).
2. Register an Android application with package name `com.example.donatedrop`.
3. Download your project's `google-services.json` and place it inside `app/`.
4. Enable **Email/Password** in Firebase Authentication.
5. Create a Cloud Firestore database and configure access rules for your application.

`google-services.json` is excluded from this repository. Each developer must supply their own copy. Preserve collection and field spelling used in the code; the main collections include `users`, `Admin`, `Blood Requests`, `DonationHistory`, and `Notifications`.

The uploaded project does not include Firestore rules for reproducing its access policy. Configure and validate authorization before using real data; client-side admin navigation alone does not enforce database permissions.

### 3. Configure Cloudinary

Use your own cloud name and unsigned upload preset in:

- `app/src/main/java/com/example/donatedrop/CloudinaryUploader.kt`
- `app/src/main/java/com/example/donatedrop/MyApp.kt`

Apply suitable limits to the unsigned preset. Never place a Cloudinary API secret in the Android application.

### 4. Run the application

Sync Gradle, choose an emulator or connected device, and click **Run**. Grant location permission and enable device location when using nearby search.

To build a debug APK from the project root on Windows:

```powershell
.\gradlew.bat assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk` after a successful build.

### Optional: email backend

The backend lives in `functions/`. Install its dependencies with:

```bash
cd functions
npm install
cd ..
```

Before deploying, merge the duplicate `scripts` objects in `functions/package.json`, select your own Firebase project instead of the original `.firebaserc` mapping, and configure a verified SendGrid sender.

The current function reads `sendgrid.key`, `sendgrid.from`, and `sendgrid.from_name` through legacy `functions.config()`, while `firebase.json` disallows legacy runtime configuration. Update the function to a supported configuration mechanism and keep the SendGrid key server-side. Firebase documents the migration in its [environment configuration guide](https://firebase.google.com/docs/functions/config-env).

Backend deployment requires those configuration changes; installing dependencies alone does not enable email delivery.

## Project organization

| Path | Purpose |
| --- | --- |
| `app/src/main/java/com/example/donatedrop/` | Application classes and screens |
| `app/src/main/java/com/example/donatedrop/adapters/` | RecyclerView adapters |
| `app/src/main/java/com/example/donatedrop/models/` | Data models |
| `app/src/main/java/com/example/donatedrop/ui/` | Dashboard sections, fragments, and ViewModels |
| `app/src/main/res/` | Layouts, graphics, menus, navigation, and themes |
| `functions/` | Firebase email notification function |
| `gradle/` | Version catalog and Gradle wrapper |
| `firebase.json` | Firebase deployment configuration |

## Development status

DonateDrop is an academic project. The following items were identified in the reviewed source and should be addressed before production use:

- Remove the `"Password" to password` entries in `SignupScreen.kt`. Firebase Authentication should handle passwords; do not duplicate them in Firestore. Remove any previously stored password fields separately.
- Replace hardcoded email-based admin routing with an authorization design enforced by backend rules or trusted server logic.
- Merge the duplicate backend `scripts` sections and migrate its legacy configuration as described above.

Build and deployment success depend on your local toolchain and service configuration. This README does not certify a production deployment.

## Contributing

Issues and pull requests are welcome. Describe the problem or proposed improvement, keep changes focused, and include relevant validation details. Use test accounts and sample data when sharing screenshots or bug reports.

## Author

**Muhammad Umar Bhatti**  
BS Information Technology — The Shaikh Ayaz University Shikarpur

[GitHub profile](https://github.com/UmarBhatti513) · [Project repository](https://github.com/UmarBhatti513/Donate-Drop)

## License

No license is declared in the reviewed project. A license should be added to specify permissions for reuse and distribution.
