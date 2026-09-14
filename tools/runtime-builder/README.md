# DevxyzIDE runtime builder

This directory builds reproducible runtime artifacts for DevxyzIDE on the isolated `feature/modern-runtime-packs` branch.

Targets for the first milestone:

- DevxyzIDE Android-native bootstrap for `aarch64`
- Android-host JDK 17+ compatible runtime
- Android-native `aapt2`
- Gradle 8.9 component pack
- Android SDK platform 35 component pack
- deterministic SHA-256/size manifest

All inputs are pinned or downloaded from official upstream sources. Final artifacts are structurally verified in GitHub Actions before they can be considered for a release catalog. CI success does not by itself prove ARM64 execution on a real Android device.

Local validation:

```bash
cd tools/runtime-builder
python3 -m unittest -v
python3 validate-config.py config/runtime-pack.properties
```
