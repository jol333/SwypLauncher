# Swyp Launcher

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" alt="Swyp Launcher Icon" width="120"/>
</p>

An Android digital assistant that can be used as an app launcher!
Swyp Launcher is a modern Android assistant application that serves as a launcher to launch apps quickly. The app provides four distinct interaction modes (handwriting, index, keyboard, and voice) optimized for single-handed operation. All UI elements are positioned in the bottom half of the screen for easy thumb access. It's completely free and all four modes run locally without any internet connection.


## Features

### Four Interaction Modes

- **Handwriting Mode**: Draw letters or gestures to find apps using on-device ML Kit recognition
- **Index Mode**: Browse apps alphabetically with letter-based grid navigation
- **Keyboard Mode**: Traditional text search with real-time filtering and calculator support
- **Voice Mode**: Hands-free app launching with speech recognition

### Smart Features

- **Usage-Based Ranking**: Apps automatically ordered by frequency and recency
- **App Shortcuts**: Quick access to specific apps by searching pre-defined terms
- **Hidden Apps**: Hide certain apps from appearing in the main app list
- **Customizable UI**: Adjust grid size, icon shapes, background blur and sort order
- **System Integration**: Works as Android's default assistant (swipe from corner or long-press power button)

### Privacy First

- **100% Offline**: All processing happens on-device
- **No Tracking**: Zero data collection or analytics
- **No Network**: No internet permissions required for core functionality
- **Open Source**: Fully transparent codebase

## Requirements

- Android 14 (API 34) or higher
- Approximately 20MB storage space
- Optional: Microphone permission for voice mode

## Installation

### From GitHub Releases

1. Download the latest APK from [Releases](https://github.com/jol333/swyplauncher/releases)
2. Enable "Install from Unknown Sources" in your device settings
3. Install the APK
4. Follow the setup wizard

### From Source

1. Clone the repository:
```bash
git clone https://github.com/jol333/swyplauncher.git
cd swyplauncher
```

2. Build and install:
```bash
./gradlew assembleDebug installDebug
```

3. Launch the app and follow the setup wizard to:
   - Set Swyp Launcher as your default digital assistant
   - Grant usage stats permission for smart app ordering
   - Configure your preferences

## Usage

### Launching the Assistant

- **Gesture**: Swipe diagonally from the bottom corners of your screen
- **Power Button**: Long-press the power button
- **App Icon**: Long press app icon and select an interaction mode directly

### Switching Modes

Tap the mode icons or swipe horizontally to switch between handwriting, index, keyboard, and voice modes. Your last-used mode is remembered next time when you open the assistant.

## Project Structure

```
app/src/main/java/com/joyal/swyplauncher/
├── data/              # Repository implementations and data sources
├── di/                # Hilt dependency injection modules
├── domain/            # Business logic, use cases, and models
├── service/           # Android VoiceInteractionService implementations
├── ui/                # Compose screens, ViewModels, and components
└── util/              # Utility classes and extensions
```

## Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

### Quick Start for Contributors

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Test thoroughly on Android 14+
4. Submit a pull request

## License

This project is licensed under the Apache License, Version 2.0 - see the [LICENSE](LICENSE.md) file for details.

## Privacy Policy

See [PRIVACY_POLICY](PRIVACY_POLICY.md) for our complete privacy policy.

**TL;DR**: We don't collect, store, or transmit any user data. Everything runs offline on your device.

## Support

For issues, questions, or feature requests, please open an issue on GitHub.

## Documentation

- **[CONTRIBUTING.md](CONTRIBUTING.md)** - Contribution guidelines
- **[CHANGELOG.md](CHANGELOG.md)** - Version history
- **[PRIVACY_POLICY.md](PRIVACY_POLICY.md)** - Privacy policy
- **[LICENSE](LICENSE.md)** - Apache License, Version 2.0