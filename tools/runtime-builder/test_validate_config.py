import unittest
from validate_config import validate

VALID = {
    "applicationId": "com.jepongdevxyz.idebuild",
    "nativeBuilderRepo": "https://github.com/appdevforall/terminal-packages.git",
    "nativeBuilderCommit": "610af608b4a3b1127244e90edf8b7be2bf94fafa",
    "nativeAbi": "aarch64",
    "gradleVersion": "8.9",
    "androidApi": "35",
}


class ConfigValidationTest(unittest.TestCase):
    def test_valid_config(self):
        self.assertEqual(validate(dict(VALID))["nativeAbi"], "aarch64")

    def test_wrong_application_id_rejected(self):
        cfg = dict(VALID); cfg["applicationId"] = "com.termux"
        with self.assertRaises(ValueError): validate(cfg)

    def test_unpinned_commit_rejected(self):
        cfg = dict(VALID); cfg["nativeBuilderCommit"] = "main"
        with self.assertRaises(ValueError): validate(cfg)

    def test_unsupported_abi_rejected(self):
        cfg = dict(VALID); cfg["nativeAbi"] = "x86_64"
        with self.assertRaises(ValueError): validate(cfg)

    def test_old_gradle_rejected(self):
        cfg = dict(VALID); cfg["gradleVersion"] = "8.8"
        with self.assertRaises(ValueError): validate(cfg)

    def test_old_android_api_rejected(self):
        cfg = dict(VALID); cfg["androidApi"] = "34"
        with self.assertRaises(ValueError): validate(cfg)


if __name__ == "__main__":
    unittest.main()
