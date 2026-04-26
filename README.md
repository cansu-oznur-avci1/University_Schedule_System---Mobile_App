# University Schedule System - Phase 2 (Firestore)

A professional scheduling management application for university lecturers and administrators, fully integrated with **Firebase Firestore** for real-time data synchronization.

## 🚀 Phase 2 Features (Firestore Integration)

- **Cloud Database:** Fully migrated from local Room to **Firebase Firestore**.
- **Real-time Sync:** Instant updates across devices for course assignments and availability.
- **Secure Authentication:**
    - Entry-point **Login Screen** for all users.
    - Role-based navigation:
        - **Admin:** Full access (Home, Calendar, Data, Settings).
        - **Lecturer:** Limited access (Home + Calendar only).
    - **Forced Password Change:** New lecturers must change their auto-generated password on first login.

## 🛠 Features

- **Administrator:**
    - **Multi-Block Scheduling:** Assign a single course to multiple time slots visually.
    - **Data Management:** Export sample templates, import data, and view real-time **Audit Logs**.
    - **Conflict Prevention:** Automatic detection of double-booked classrooms or lecturers.
- **Lecturers:**
    - **Availability Management:** Set weekly availability with a single tap.
    - **Visual Feedback:** Unavailable slots are marked in **Red**, while available ones are **Green**.
    - **Personalized Calendar:** View and manage assigned courses in a modern grid view.

## 🎨 Design & UI

- **Modern UI:** Built with **Jetpack Compose** and **Material 3**.
- **Navigation:** Implemented `NavigationSuiteScaffold` for adaptive navigation (rail/bottom bar).
- **Security:** Password fields are masked for privacy.

## 📦 Tech Stack

- **Kotlin** (Coroutines, Flow, StateFlow)
- **Jetpack Compose** (Material 3)
- **Firebase Firestore** (Real-time Cloud Database)
- **Firebase Analytics**
- **Apache POI** (Excel Processing)
- **MVVM Architecture**

## Getting Started

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/cansu-oznur-avci1/University_Schedule_System---Mobile_App.git
    ```
2.  **Firebase Setup:**
    - Add your `google-services.json` to the `app/` directory.
    - Enable Firestore in the Firebase Console.
3.  **Run:** Open in Android Studio (Ladybug or newer) and deploy to a device.

---
*Prepared as part of the University Mobile Programming Lab Project.*
