# TVX Browser

Android TV / Google TV browser project targeting ARM64 APK builds.

## Current status

This repository starts from an empty baseline. The initial application will provide TV-remote navigation, tabs, downloads, mouse cursor controls, and basic ad blocking using Android WebView.

**Chrome Web Store compatibility is not implemented in this initial version.** Android WebView does not provide the Chrome Extensions runtime. Full compatibility requires a maintained Chromium-based engine and extension APIs; we will not claim direct Chrome Web Store support until implemented and tested.

## Build

GitHub Actions builds a debug APK and uploads it as an artifact on pushes to `main` or via manual workflow dispatch.
