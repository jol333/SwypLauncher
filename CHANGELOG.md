# Changelog

All notable changes to Swyp Launcher will be documented in this file.

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
