# University Schedule System

A modern scheduling management application for university lecturers and administrators, built with Kotlin and Jetpack Compose.

## Features

- **Administrator Panel:**
    - **Smart Excel Import:** Upload course and lecturer lists from Excel. The system prevents duplicates and selectively merges new data.
    - **Multi-Block Scheduling:** Assign courses to multiple time slots (e.g., 3-hour lectures) on a visual calendar.
    - **Lecturer Management:** View detailed credentials and assigned courses for all lecturers.
    - **Audit Logs:** Track system changes and administrative actions.
    - **Database Cleanup:** Reset the system for fresh testing.

- **Lecturer Panel:**
    - **Secure Login:** Personalized accounts with automated Turkish character normalization (e.g., `halit_bakir`).
    - **Availability Management:** Toggle weekly availability (Available/Busy) with a color-coded calendar.
    - **Personal Schedule:** View assigned courses and individual timetables.

- **Modern UI/UX:**
    - Developed with **Jetpack Compose** and **Material 3**.
    - Adaptive layout support.
    - Stylish purple theme with soft visual elements.
    - Professional branding with custom icons.

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Database:** Room Persistence Library (SQLite)
- **Processing:** KSP (Kotlin Symbol Processing)
- **Excel Support:** Apache POI
- **Architecture:** MVVM (ViewModel, LiveData/State)

## Getting Started

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/yourusername/university-schedule-system.git
    ```
2.  **Open in Android Studio:** Use Android Studio Ladybug (2024.2.1) or higher.
3.  **Sync Gradle:** Allow the project to download all necessary dependencies.
4.  **Run:** Deploy to an emulator or physical device.

## License

This project is licensed under the MIT License - see the LICENSE file for details.
