# University Schedule System - Final Version (Firestore Edition)

A professional scheduling management application for university lecturers and administrators, fully integrated with **Firebase Firestore** for real-time data synchronization and cloud persistence.

## 🚀 Key Features

- **Cloud Database Architecture:** Fully migrated from local Room to **Firebase Firestore** for global data access.
- **Real-time Synchronization:** Instant updates across all devices for course assignments, availability changes, and administrative data.
- **Secure Multi-Role Authentication:**
    - Unified **Login Screen** with input validation.
    - **Admin Role:** Full system access including data management, scheduling, and audit logs.
    - **Lecturer Role:** Personalized experience with access limited to Home and Calendar.
- **Forced Password Security:** New lecturers are immediately prompted to change their auto-generated passwords upon first login (Minimum 4 characters).
- **Department Isolation:** Users work within a dedicated department context, ensuring data privacy and organizational clarity.

## 🏛 Administrator Dashboard

- **Quick Summary Panels:** Real-time visibility on the Home screen:
    - **Unassigned Lecturers:** List of lecturers with no scheduled courses.
    - **Unassigned Courses:** List of courses not yet placed in the schedule.
    - **Available Classrooms:** List of rooms with free time slots.
- **Classroom Management:** 
    - View and manually add classrooms.
    - **Robust Excel Import:** Supports "Room Code" and "Capacity" headers with automatic column detection and numeric type handling.
- **Course & Lecturer Import:** 
    - **Intelligent Merging:** Merges data from multiple files while preventing duplicate lecturer records.
    - **Advanced Title Parsing:** Accurately separates academic titles (Assist. Prof., Assoc. Prof., Prof. Dr., etc.) even from complex formats like `profdr_name_surname`.
- **Advanced Assignment System:**
    - **Conflict Prevention:** Automatic detection and blocking of double-booked lecturers or classrooms.
    - **Availability Awareness:** Visual warnings for scheduling in a lecturer's unavailable (Red) slot.
- **Data Governance:** 
    - Real-time **Audit Logs** for tracking assignments.
    - **Independent Clearing:** Separate reset buttons for Classrooms and Lecturers/Courses.

## 🎓 Lecturer Features

- **Personalized Home Dashboard:**
    - Title-aware welcome messages.
    - Weekly assignment summary count.
- **Dynamic Availability Management:** Set weekly availability (Red/Green) with a single tap in the calendar.
- **Rich Calendar View:**
    - Displays Course Code, Course Name, and **Assigned Room Code**.
    - Visual distinction between empty, unavailable, and occupied slots.

## 🎨 Design & UI

- **Modern Aesthetic:** Stylish purple theme with elegant gradients and Material 3 components.
- **Adaptive Navigation:** `NavigationSuiteScaffold` for seamless transition across different screen sizes.
- **Enhanced UX:** Full 08:00 - 17:00 schedule support with smooth horizontal and vertical scrolling.

## 📦 Tech Stack

- **Kotlin:** Coroutines, Flow, StateFlow.
- **Jetpack Compose:** Material 3, Adaptive Navigation.
- **Firebase:** Firestore (NoSQL Cloud DB).
- **Apache POI:** Robust Excel (.xlsx) processing.
- **Architecture:** MVVM (Model-View-ViewModel) with Repository Pattern.

---
*Prepared as part of the University Mobile Programming Lab Project.*
