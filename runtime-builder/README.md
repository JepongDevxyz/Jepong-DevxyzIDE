# DevxyzIDE Android runtime builder

DevxyzIDE cannot safely consume a terminal/bootstrap compiled for another Android application id. The maintained Code On The Go terminal-package builder explicitly accepts `-p <package-name>` and rewrites the Termux package prefix for that app.

`build-devxyz-terminal-runtime.sh` pins the verified upstream commit used when this source snapshot was prepared and always passes:

```text
-p com.jepongdevxyz.idebuild
```

The script produces upstream bootstrap/APT output; distribution/hosting is intentionally separate because release packs need a real signing key and HTTPS host. Never commit a private GPG key.

`../tools/runtime/make_toolchain_pack.py` packages an already prepared JDK, SDK, aapt2, NDK or CMake directory into the SHA-256-verified format accepted by DevxyzIDE's `ToolchainPackInstaller`.
