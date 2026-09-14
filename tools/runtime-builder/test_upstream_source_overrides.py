import pathlib
import tempfile
import unittest


class UpstreamSourceOverrideTest(unittest.TestCase):
    def test_foot_1250_archive_checksum_is_pinned_to_observed_bytes(self):
        from upstream_source_overrides import patch_foot_1250_checksum

        old = """TERMUX_PKG_VERSION=\"1.25.0\"\nTERMUX_PKG_SRCURL=https://codeberg.org/dnkl/foot/archive/${TERMUX_PKG_VERSION}.tar.gz\nTERMUX_PKG_SHA256=442a42d576ec72dd50f2d3faea8a664230a47bac79dc1eb6e7c9125ee76c130f\n"""
        expected = "ee9d0e51295945157ecb33119cb2c79b276093d0fd342d959d78d772d505571c"
        with tempfile.TemporaryDirectory() as td:
            recipe = pathlib.Path(td) / "build.sh"
            recipe.write_text(old, encoding="utf-8")
            patch_foot_1250_checksum(recipe)
            text = recipe.read_text(encoding="utf-8")
            self.assertIn("TERMUX_PKG_SHA256=" + expected, text)
            self.assertNotIn("SKIP_CHECKSUM", text)

    def test_unexpected_recipe_is_rejected(self):
        from upstream_source_overrides import patch_foot_1250_checksum

        with tempfile.TemporaryDirectory() as td:
            recipe = pathlib.Path(td) / "build.sh"
            recipe.write_text("TERMUX_PKG_VERSION=\"1.25.0\"\nTERMUX_PKG_SHA256=unexpected\n", encoding="utf-8")
            with self.assertRaises(ValueError):
                patch_foot_1250_checksum(recipe)


if __name__ == "__main__":
    unittest.main()
