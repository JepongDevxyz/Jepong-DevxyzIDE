# DevxyzIDE v0.6 AIDE Test Edition — AIDE parser/dependency fix

Fresh checks performed after the reported AIDE 331-error failure:

- Removed Java 8 lambda (`->`) and method-reference (`::`) syntax from Android sources.
- Removed AndroidX, Material Components, and Sora Editor Maven dependencies from the AIDE-only edition.
- Replaced AppCompatActivity/Material/Sora widgets with Android platform Activity/Button/EditText equivalents.
- Replaced AndroidX FileProvider with a small app-local read-only APK ContentProvider.
- Set AIDE legacy build profile: AGP 3.2.1, Gradle 4.6, compile/target SDK 28, Java 7 source.
- App name remains `DevxyzIDE`.
- Application ID remains `com.jepongdevxyz.idebuild`.

Verification:

- Host self-tests: 23/23 PASS
- Source contract tests (AIDE Test Edition): PASS
- Runtime pack tool tests: PASS
- Bootstrap stamp tests: PASS
- AIDE legacy source compatibility regression test: PASS
- Android resource/manifest XML parsing: PASS
- Pure Java non-Android source compile under Java 8 compiler: PASS
- ZIP integrity: checked after packaging

Limit: this environment does not contain an Android SDK/AIDE runtime, so this is not an on-device AIDE APK-build claim. The edition specifically removes the parser/dependency root causes visible in the supplied AIDE screenshots.
