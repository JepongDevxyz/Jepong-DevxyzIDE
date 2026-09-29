# DevxyzIDE

Native Android IDE by Jepong Devxyz.

## Reference UI and workspace
- Splash screen and DevxyzIDE logo treatment
- Project explorer with a collapsible Android project tree
- Source and layout editor tabs, syntax-colored code, and line numbers
- Build log, APK install action, and project folder details
- Project tools, utilities, resource helpers, and editable settings
- Dark navy/cyan colors and compact five-section mobile navigation

## Project workflows
- Import Gradle ZIPs in the background with staging, Zip Slip protection, size/depth limits, and Gradle root validation
- Create a launchable Kotlin Android project with a Gradle 8.10.2 wrapper
- Edit and save source, search project files, export/backup project ZIPs, and inspect toolchain status
- Build and install debug APKs with the embedded JDK/Android SDK toolchain

Android CI runs lint, unit tests, assembles a debug APK, checks the package and signature. APK artifact publishing is held until the approved reference UI has been explicitly reviewed.
