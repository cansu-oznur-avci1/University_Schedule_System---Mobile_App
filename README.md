# University Schedule System - Phase 2 (Firestore Edition)

A professional scheduling management application for university lecturers and administrators, fully integrated with **Firebase Firestore** for real-time data synchronization and cloud persistence.

## 🚀 Phase 2 Features (Firestore Integration)

- **Cloud Database Architecture:** Fully migrated from local Room to **Firebase Firestore** for global data access.
- **Real-time Synchronization:** Instant updates across all devices for course assignments, availability changes, and administrative data.
- **Secure Multi-Role Authentication:**
    - Unified **Login Screen** as the application entry point.
    - **Admin Role:** Full system access including data management, scheduling, and audit logs.
    - **Lecturer Role:** Personalized experience with access limited to Home and Calendar.
- **Forced Password Security:** New lecturers are immediately prompted to change their auto-generated passwords upon first login.
- **Department Isolation:** Users work within a dedicated department context, ensuring data privacy and organizational clarity.

## 🛠 Features

### 🏛 Administrator
- **Classroom Management:** View, manually add, or batch import classrooms via Excel (supports Room Code and Capacity).
- **Course & Lecturer Import:** Intelligent Excel processor that merges data from multiple files while preventing duplicates.
- **Advanced Assignment System:**
    - Link Courses, Lecturers, and Classrooms to specific time slots.
    - **Conflict Prevention:** Automatic detection and blocking of double-booked lecturers or classrooms.
    - **Availability Awareness:** Visual warnings when attempting to schedule a course in a lecturer's unavailable (Red) slot.
- **Data Governance:** real-time **Audit Logs** and full database reset capabilities.

### 🎓 Lecturers
- **Personalized Home Dashboard:**
    - Title-aware welcome messages (e.g., *Welcome, Prof. Dr. Halit Bakır*).
    - Total weekly assigned courses summary.
- **Dynamic Availability Management:** Set weekly availability (Red/Green) with a single tap in the calendar.
- **Rich Calendar View:**
    - Displays Course Code, Course Name, and **Assigned Room Code** in each slot.
    - Visual distinction between empty, unavailable, and occupied slots.

## 🎨 Design & UI

- **Stylish Purple Theme:** A modern, soft purple aesthetic with elegant gradients.
- **Adaptive Navigation:** Implemented `NavigationSuiteScaffold` for seamless transition between different screen sizes.
- **Enhanced UX:** Vertical and horizontal scrolling in the calendar to accommodate full 08:00 - 17:00 schedules without text clipping.

## 📦 Tech Stack

- **Kotlin:** Coroutines, Flow, StateFlow.
- **Jetpack Compose:** Material 3, Adaptive Navigation.
- **Firebase:** Firestore (NoSQL Cloud DB), Analytics.
- **Apache POI:** Robust Excel (.xlsx) processing.
- **Architecture:** MVVM (Model-View-ViewModel) with Repository Pattern.

## Getting Started

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/cansu-oznur-avci1/University_Schedule_System---Mobile_App.git
    ```
2.  **Firebase Configuration:**
    - Place your `google-services.json` file in the `app/` directory.
    - Ensure Firestore is enabled in your Firebase project.
3.  **Build & Run:** Open the project in Android Studio (Ladybug 2024.2.1 or newer) and deploy to your device.

---
*Prepared as part of the University Mobile Programming Lab Project.*
