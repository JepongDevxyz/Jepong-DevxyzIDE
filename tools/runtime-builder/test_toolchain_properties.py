import pathlib, tempfile, unittest
from generate_toolchain_properties import build_properties


class ToolchainPropertiesTest(unittest.TestCase):
    def make_payload(self):
        td = tempfile.TemporaryDirectory()
        root = pathlib.Path(td.name)
        payload = root / "toolchains" / "gradle-8.9" / "bin"
        payload.mkdir(parents=True)
        (payload / "gradle").write_text("#!/bin/sh\n", encoding="utf-8")
        return td, root

    def test_gradle_metadata(self):
        td, root = self.make_payload()
        try:
            text = build_properties(root, "gradle", "8.9", "all")
            self.assertIn("component=gradle", text)
            self.assertIn("version=8.9", text)
            self.assertIn("abi=all", text)
            self.assertRegex(text, r"sha256\.toolchains/gradle-8\.9/bin/gradle=[0-9a-f]{64}")
        finally:
            td.cleanup()

    def test_android_sdk_metadata(self):
        with tempfile.TemporaryDirectory() as td:
            root = pathlib.Path(td)
            platform = root / "toolchains" / "android-sdk" / "platforms" / "android-35"
            platform.mkdir(parents=True)
            (platform / "android.jar").write_bytes(b"jar")
            text = build_properties(root, "android-sdk-platform", "35", "all")
            self.assertIn("component=android-sdk-platform", text)
            self.assertRegex(text, r"sha256\.toolchains/android-sdk/platforms/android-35/android\.jar=[0-9a-f]{64}")

    def test_unknown_component_rejected(self):
        td, root = self.make_payload()
        try:
            with self.assertRaises(ValueError):
                build_properties(root, "unknown", "1", "all")
        finally:
            td.cleanup()


if __name__ == "__main__":
    unittest.main()
