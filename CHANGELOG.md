# Changelog

All notable changes to Swyp Launcher will be documented in this file.

## [1.7.0] - 2026-11-19

### Added
- Icon pack support: apply any installed third-party icon pack (ADW/Nova format), with pack previews, coverage stats and fallback styling (mask, background, scale) for apps the pack doesn't cover
- Per-app custom icons: long-press an app and choose "Change icon" to pick an icon from any installed pack
- "Newest" sort order that lists recently installed apps first
- Option to include hidden apps and shortcuts in search results, with an unhide action in their context menu
- Redesigned home-screen widget with responsive layouts (compact, button row, search bar, column and bento grid) that follow the system light/dark theme and update when the enabled modes change

### Changed
- Much faster assistant cold start: apps are shown from an on-disk snapshot in the first frame, rendered icons are cached on disk, and app scanning, usage stats, ML Kit setup and icon decoding run off the main thread
- Release builds are now optimized and shrunk with R8 and ship a baseline profile, making the app smaller and quicker to start
- Custom gestures that match no visible app now fall back to a handwriting text search
- Search-related toggles are grouped under a new "Search results" section in settings
- Polished icon picker, context menus (now kept within screen bounds and scrollable) and handwriting layout
- Upgraded build tools, Kotlin, Compose, Material 3 and AndroidX libraries

### Fixed
- ANR (app not responding) on low-end phones while drawing the mode buttons for the first time

---

## [1.6.0] - 2026-07-06

### Added
- App shortcut search: find and run app shortcuts directly from search results, with icons and a context menu
- App shortcuts can be added to shortcut groups, hidden, and assigned to custom gestures
- Time zone conversion (e.g. "Chile time now", "14:58 in Japan")
- Option to load all apps when the assistant opens
- Swipe between adjacent letters in Index mode when it is the only enabled mode
- Shimmer loading grid in Index mode

### Fixed
- Memory leaks and ANRs; debug logs are stripped from release builds

---

## [1.5.0] - 2026-06-03

### Added
- Custom gestures: draw your own shapes in handwriting mode to show chosen apps
- Currency conversion at live rates, including popular cryptocurrencies
- Unit conversion across 18 categories, with settings to choose which conversion categories are enabled
- Background blur for the assistant and settings screens

### Changed
- The assistant bottom sheet now respects navigation-bar and keyboard insets
- Faster app-change observation and voice interaction service startup

---

## [1.4.1] - 2026-02-24

### Added
- Implemented copy and share options for empty search, with localization updates
- Added responsive widget layouts and improved dialog scrollability

### Changed
- Standardized secondary text color and adjusted default app icon corner radius
- Reorganized settings cards layout and refined spatial animations

---

## [1.4.0] - 2026-02-12

### Added
- Added home screen widget and quick settings tile support

---

## [1.3.0] - 2026-02-09

### Added
- Added multi-language support (Portuguese, Spanish, Chinese, Hindi, German, Malayalam)
- Implemented per-app language selection with a dedicated settings UI

### Changed
- Implemented app caching layer and optimized app list retrieval, including cache invalidation on language change
- Upgraded Gradle, AGP, Kotlin, and KSP versions

---

## [1.2.0] - 2026-01-08

### Added
- Added dynamic jelly wobble effect and morph effect for pop-ups in settings screen
- Added baseline profile generation module to optimize build performance

### Changed
- Refactored blur effect handling and improved dynamic layout sizing

---

## [1.1.0] - 2025-12-30

### Changed
- Removed restrictions on landscape mode for digital assistant bottom sheet
- Digital assistant bottom sheet UI is now optimized for landscape mode
- Changed the color of dynamic shortcut icons to make it visible on both dark and light color context menu

---

## [1.0.0] - 2025-12-24

### Initial Release

#### Added
- **Four Interaction Modes**
  - Handwriting Mode with ML Kit Digital Ink Recognition
  - Index Mode with alphabetical grid navigation
  - Keyboard Mode with real-time text search and calculator support
  - Voice Mode with speech recognition
- **Smart Features**
  - Usage-based app ranking (frequency and recency)
  - App shortcuts via long-press
  - Hidden apps configuration
  - Customizable grid size and layout
- **System Integration**
  - Android assistant service integration
  - Launch via gesture (swipe from corner) or power button
  - System-wide app launcher functionality
- **Privacy & Performance**
  - 100% offline operation
  - On-device ML processing
  - No data collection or tracking
  - Optimized with ProGuard/R8
- **Customization**
  - Material 3 theming with Expressive design
  - Adjustable icon shapes and sizes
  - Blur effects configuration
  - Default mode selection

#### Technical Details
- Minimum SDK: Android 14 (API 34)
- Target SDK: Android 14+ (API 36)
- Architecture: Clean Architecture with MVVM
- Dependency Injection: Hilt
- UI Framework: Jetpack Compose
- ML Processing: Google ML Kit

#### Known Issues
- Shortcut Editor may require double-tap after swipe-down dismissal
- Full-screen flick scroll may initially scroll in opposite direction

---

## Future Releases

### Planned Features
- Widget support for quick app access
- Custom gesture shortcuts
- App categories and folders
- Backup and restore settings
- Additional ML model languages
- Tablet-optimized layouts

### Under Consideration
- Custom icon packs support
- App usage statistics dashboard
- Quick settings integration
- Accessibility improvements

---

For detailed technical changes, see the [commit history](https://github.com/jol333/swyplauncher/commits/main).
