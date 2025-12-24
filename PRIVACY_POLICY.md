# Privacy Policy for Swyp Launcher

**Last Updated**: December 23, 2025

## Overview

Swyp Launcher ("the App") is committed to protecting your privacy. This privacy policy explains our approach to data collection and usage.

## Our Privacy Commitment

**We do not collect, store, transmit, or share any personal data.**

Swyp Launcher is designed with privacy as a core principle. The app operates entirely offline on your device, and no information ever leaves your device.

## Data Collection

### What We DON'T Collect

- ❌ Personal information (name, email, phone number, etc.)
- ❌ Device identifiers (IMEI, Android ID, advertising ID, etc.)
- ❌ Location data
- ❌ Usage analytics or statistics
- ❌ Crash reports
- ❌ App usage patterns
- ❌ Search queries or voice recordings
- ❌ Installed apps list
- ❌ Any other personal or non-personal data

### What Stays on Your Device

The following information is stored locally on your device and never transmitted:

- **App Preferences**: Your settings (grid size, theme, default mode, etc.)
- **Hidden Apps List**: Apps you've chosen to hide from the launcher
- **Usage Statistics**: App launch frequency for smart ordering (accessed via Android's UsageStatsManager API)
- **ML Models**: Downloaded handwriting recognition models (stored in app cache)

All this data remains on your device and is deleted when you uninstall the app.

## Permissions Explained

The app requests the following permissions for functionality:

### Required Permissions

- **QUERY_ALL_PACKAGES**: To display your installed apps in the launcher
- **PACKAGE_USAGE_STATS**: To provide smart app ordering based on your usage patterns (processed locally)

### Optional Permissions

- **RECORD_AUDIO**: Only used for voice mode if you choose to enable it. Audio is processed on-device by Google ML Kit and never transmitted.

### System Permissions

- **REQUEST_DELETE_PACKAGES**: To allow you to uninstall apps from the launcher
- **BIND_VOICE_INTERACTION**: To function as Android's assistant service

## Third-Party Services

### Google ML Kit

The app uses Google ML Kit for:
- **Digital Ink Recognition** (handwriting mode)
- **Speech Recognition** (voice mode)

ML Kit processes all data **on-device**. Recognition models are downloaded to your device and run locally. No data is sent to Google servers during recognition.

**ML Kit Privacy**: https://developers.google.com/ml-kit/terms

### No Other Third Parties

The app does not integrate with:
- Analytics services (Google Analytics, Firebase Analytics, etc.)
- Crash reporting services
- Advertising networks
- Social media platforms
- Any other third-party services

## Internet Access

**The app does not require internet access** for core functionality. The only network activity is:

- **One-time ML model download**: When you first use handwriting mode, the recognition model is downloaded from Google's servers. After the initial model download, the app functions completely offline.

## Data Security

Since no data is collected or transmitted:
- There are no servers to breach
- There are no databases to hack
- There are no accounts to compromise

Your data stays on your device, protected by Android's security model and your device's lock screen.

## Changes to This Policy

If we ever change our privacy practices, we will update this policy and the "Last Updated" date. However, our core commitment to not collecting data will not change.

## Open Source Transparency

Swyp Launcher is open source. You can review the entire codebase to verify our privacy claims:

[GitHub Repository URL]

## Your Rights

Since we don't collect any data:
- There is no data to access
- There is no data to delete
- There is no data to export
- There is no data to correct

Simply uninstalling the app removes all local data from your device.

## Contact

If you have questions about this privacy policy, please:
- Open an issue on GitHub: [GitHub Issues URL]

## Summary

**Swyp Launcher is a privacy-respecting app that:**
- ✅ Operates completely offline
- ✅ Processes all data on your device
- ✅ Never transmits personal information
- ✅ Never tracks your behavior
- ✅ Never shares data with third parties
- ✅ Is fully open source and transparent

Your privacy is not just protected, it's guaranteed by design.
