import pathlib, tempfile, unittest
from generate_manifest import generate
from verify_pack import sha256_file, verify


class ManifestTest(unittest.TestCase):
    def test_generates_exact_size_and_hash(self):
        with tempfile.TemporaryDirectory() as td:
            root = pathlib.Path(td)
            p = root / "gradle-8.9.devxyz-toolchain.zip"
            p.write_bytes(b"abc")
            out = root / "runtime-manifest.properties"
            generate([p], out)
            text = out.read_text(encoding="utf-8")
            self.assertIn("artifact.gradle.size=3", text)
            self.assertIn("artifact.gradle.sha256=%s" % sha256_file(p), text)
            self.assertNotIn("http://", text)
            self.assertNotIn("example.invalid", text)
            self.assertTrue(verify(p, sha256_file(p), 3))

    def test_wrong_size_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            p = pathlib.Path(td) / "gradle-8.9.devxyz-toolchain.zip"
            p.write_bytes(b"abc")
            with self.assertRaises(ValueError): verify(p, sha256_file(p), 4)


if __name__ == "__main__":
    unittest.main()
