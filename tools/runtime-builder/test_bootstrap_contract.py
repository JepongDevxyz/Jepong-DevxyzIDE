import io, pathlib, tempfile, unittest, zipfile

EXPECTED_APP = "applicationId=com.jepongdevxyz.idebuild"
FORBIDDEN = ("/data/data/com.termux/", "/data/data/com.itsaky.androidide/")


def verify_bootstrap(path):
    with zipfile.ZipFile(path, "r") as zf:
        names = zf.namelist()
        if "devxyz-bootstrap.properties" not in names:
            raise ValueError("missing devxyz-bootstrap.properties")
        meta = zf.read("devxyz-bootstrap.properties").decode("utf-8")
        if "format=1" not in meta or EXPECTED_APP not in meta or "arch=aarch64" not in meta:
            raise ValueError("invalid bootstrap identity")
        joined = "\n".join(names)
        for bad in FORBIDDEN:
            if bad in joined:
                raise ValueError("foreign package prefix in archive")
        if not any(n.endswith("usr/bin/aapt2") or n == "usr/bin/aapt2" for n in names):
            raise ValueError("aapt2 missing")
        if not any("usr/lib/jvm/" in n and n.endswith("/bin/java") for n in names):
            raise ValueError("JDK java executable missing")
        if "SYMLINKS.txt" in names:
            links = zf.read("SYMLINKS.txt").decode("utf-8", errors="replace")
            for bad in FORBIDDEN:
                if bad in links:
                    raise ValueError("foreign package prefix in symlinks")
    return True


class BootstrapContractTest(unittest.TestCase):
    def build_fixture(self, bad=False):
        td = tempfile.TemporaryDirectory()
        p = pathlib.Path(td.name) / "bootstrap.zip"
        with zipfile.ZipFile(p, "w") as zf:
            zf.writestr("devxyz-bootstrap.properties", "format=1\napplicationId=com.jepongdevxyz.idebuild\narch=aarch64\n")
            zf.writestr("usr/bin/aapt2", b"bin")
            zf.writestr("usr/lib/jvm/java-21-openjdk/bin/java", b"bin")
            zf.writestr("SYMLINKS.txt", "/data/data/com.termux/files/usr/bin/bad←link\n" if bad else "bin/sh←usr/bin/sh\n")
        return td, p

    def test_valid_fixture(self):
        td, p = self.build_fixture(False)
        try: self.assertTrue(verify_bootstrap(p))
        finally: td.cleanup()

    def test_foreign_prefix_rejected(self):
        td, p = self.build_fixture(True)
        try:
            with self.assertRaises(ValueError): verify_bootstrap(p)
        finally: td.cleanup()


if __name__ == "__main__":
    import sys
    if len(sys.argv) == 2:
        verify_bootstrap(sys.argv[1]); print("bootstrap contract valid")
    else:
        unittest.main()
