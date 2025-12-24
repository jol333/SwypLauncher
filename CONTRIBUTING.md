# Contributing to Swyp Launcher

Thank you for your interest in contributing to Swyp Launcher! This document provides guidelines and instructions for contributing.

## Code of Conduct

- Be respectful and inclusive
- Focus on constructive feedback
- Help maintain a welcoming environment for all contributors

## How to Contribute

### Reporting Bugs

Before creating a bug report:
1. Check existing issues to avoid duplicates
2. Test on the latest version
3. Gather relevant information (device, Android version, steps to reproduce)

When creating a bug report, include:
- Clear, descriptive title
- Detailed steps to reproduce
- Expected vs actual behavior
- Screenshots or videos if applicable
- Device information and Android version
- Relevant logs or error messages

### Suggesting Features

Feature requests are welcome! Please:
- Check if the feature has already been requested
- Clearly describe the feature and its benefits
- Explain how it fits with the app's privacy-first philosophy
- Consider implementation complexity

### Pull Requests

1. **Fork the repository** and create a feature branch
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Follow the architecture guidelines** (see `.kiro/steering/structure.md`)
   - Maintain Clean Architecture layers
   - Use MVVM pattern
   - Follow existing naming conventions

3. **Write minimal, focused code**
   - Keep changes small and focused
   - Don't add unnecessary features
   - Follow Kotlin idioms and conventions

4. **Test your changes**
   - Test on multiple devices if possible
   - Verify all four modes still work
   - Check assistant integration
   - Test with ProGuard enabled

5. **Commit with clear messages**
   ```
   feat: Add new feature
   fix: Fix specific bug
   refactor: Refactor component
   docs: Update documentation
   ```

6. **Submit the pull request**
   - Reference related issues
   - Describe what changed and why
   - Include screenshots for UI changes

## Development Setup

### Prerequisites
- Android Studio (latest stable version)
- JDK 11 or higher
- Android SDK with API 34+
- Git

### Building the Project

1. Clone the repository:
   ```bash
   git clone https://github.com/jol333/swyplauncher.git
   cd swyplauncher
   ```

2. Open in Android Studio or build via command line:
   ```bash
   ./gradlew assembleDebug
   ```

3. Install on device:
   ```bash
   ./gradlew installDebug
   ```

### Project Structure

```
app/src/main/java/com/joyal/swyplauncher/
├── data/          # Repository implementations, data sources
├── di/            # Hilt dependency injection
├── domain/        # Business logic, use cases, models
├── service/       # Android services
├── ui/            # Compose UI, ViewModels, screens
└── util/          # Utilities
```

See `.kiro/steering/structure.md` for detailed architecture guidelines.

## Coding Standards

### Kotlin Style
- Use `val` over `var` when possible
- Prefer immutable collections
- Use trailing commas in multi-line declarations
- Follow official Kotlin coding conventions

### Architecture Rules
- **Presentation → Domain → Data** (strict layer separation)
- ViewModels depend on use cases, NOT repositories
- Domain layer has NO Android dependencies
- Use Hilt for all dependency injection

### Compose Guidelines
- Keep Composables small and focused
- Extract reusable components
- Use `remember` for expensive computations
- Use `LaunchedEffect` for side effects

### Naming Conventions
- Use Cases: `<Verb><Noun>UseCase`
- Repositories: `<Entity>Repository` (interface), `<Entity>RepositoryImpl` (impl)
- ViewModels: `<Feature>ViewModel`
- Screens: `<Mode>ModeScreen`
- UI State: `<Feature>UiState`

## Testing Guidelines

- Test on minimum SDK (Android 14)
- Test all four interaction modes
- Verify assistant integration works
- Test with ProGuard/R8 enabled
- Check for memory leaks
- Verify offline functionality

## Privacy Requirements

All contributions MUST maintain the app's privacy guarantees:
- ✅ No data collection or tracking
- ✅ All processing on-device
- ✅ No network requests (except ML model downloads)
- ✅ No third-party analytics or ads
- ❌ Do NOT add any telemetry or analytics
- ❌ Do NOT add network dependencies

## Documentation

When adding features:
- Update README.md if user-facing
- Update CHANGELOG.md
- Add inline code comments for complex logic
- Update steering files if architecture changes

## Questions?

- Open an issue for questions
- Check existing documentation in `.kiro/steering/`
- Review the codebase for examples

## License

By contributing, you agree that your contributions will be licensed under the Apache License, Version 2.0

---

Thank you for contributing to Swyp Launcher! 🚀
