# University Schedule System - Modernized (MVVM)

A professional scheduling management application for university lecturers and administrators, optimized for high performance and ready for Phase 2 (Firestore) integration.

## 🚀 Modernization Features (Week 10 & 11 Standards)

- **Advanced Architecture:**
    - Fully migrated from Singleton to **MVVM (Model-View-ViewModel)**.
    - Implemented **Repository Pattern** to abstract data access, ensuring a seamless future transition to Firebase Firestore.
    - Robust State Management using **StateFlow** and **Sealed UiState** (Idle, Loading, Success, Error).

- **Performance & Safety:**
    - **Asynchronous Processing:** All I/O operations (Excel Import/Export) run on `Dispatchers.IO` using Kotlin Coroutines to prevent ANR crashes.
    - **Lifecycle Aware:** UI observes data streams using `collectAsStateWithLifecycle()` to optimize resource consumption.
    - **Safe Error Handling:** Proper handling and re-throwing of `CancellationException` for coroutine stability.

- **Data Integrity (Unique Data):**
    - Intelligent Excel Processor: Prevents duplicate course codes and lecturers.
    - Automatic Data Merging: Consolidates lecturer info from multiple files.
    - **Turkish Character Normalization:** Smart username generation (e.g., `halit_bakir`) and login support for Turkish characters.

## 🛠 Features

- **Administrator:**
    - **Multi-Block Scheduling:** Assign a single course to multiple time slots visually.
    - **Data Management:** Export sample templates, import data, and view real-time **Audit Logs**.
    - **Lecturer Insights:** Click on lecturer names to view credentials and their full schedule.
- **Lecturers:**
    - **Personalized Access:** Secure login with randomly generated 6-digit passwords.
    - **Availability Toggle:** Set weekly availability (Red/Green) with instant database sync.

## 🎨 Design

- **UI:** Built with **Jetpack Compose** and **Material 3**.
- **Theme:** Stylish and soft Purple Theme.
- **Branding:** Custom brand-aligned application icon and internal iconography.

## 📦 Tech Stack

- **Kotlin** (Coroutines, Flow, StateFlow)
- **Jetpack Compose** (Material 3)
- **Room Persistence Library** (SQLite with Flow support)
- **Apache POI** (Excel Processing)
- **MVVM Architecture**

## Getting Started

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/cansu-oznur-avci1/University_Schedule_System---Mobile_App.git
    ```
2.  **Open in Android Studio:** (Ladybug or newer).
3.  **Sync Gradle:** Ensure all dependencies are downloaded.
4.  **Run:** Deploy to a physical device or emulator.

---
*Prepared as part of the University Mobile Programming Lab Project.*
