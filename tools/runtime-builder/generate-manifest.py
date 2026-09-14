#!/usr/bin/env python3
import argparse, hashlib, os, pathlib

PROVENANCE = {
    "devxyz-bootstrap-aarch64.zip": ("bootstrap", "android-native", "aarch64", "https://github.com/appdevforall/terminal-packages.git", "610af608b4a3b1127244e90edf8b7be2bf94fafa"),
    "gradle-8.9.devxyz-toolchain.zip": ("gradle", "8.9", "all", "https://services.gradle.org/distributions/gradle-8.9-bin.zip", "8.9"),
    "android-sdk-35.devxyz-toolchain.zip": ("android-sdk-platform", "35", "all", "https://dl.google.com/android/repository/repository2-1.xml", "35"),
}


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def generate(paths, out_path):
    records = []
    sums = []
    for raw in sorted(paths, key=lambda p: pathlib.Path(p).name):
        p = pathlib.Path(raw)
        if p.name not in PROVENANCE:
            raise ValueError("unknown artifact: %s" % p.name)
        component, version, abi, source, source_ref = PROVENANCE[p.name]
        digest = sha256_file(p)
        size = p.stat().st_size
        if source.startswith("http://") or "example.invalid" in source or digest == "0" * 64:
            raise ValueError("unsafe manifest metadata")
        key = component.replace("-", "_")
        records.extend([
            "artifact.%s.component=%s" % (key, component),
            "artifact.%s.version=%s" % (key, version),
            "artifact.%s.abi=%s" % (key, abi),
            "artifact.%s.filename=%s" % (key, p.name),
            "artifact.%s.size=%d" % (key, size),
            "artifact.%s.sha256=%s" % (key, digest),
            "artifact.%s.source=%s" % (key, source),
            "artifact.%s.sourceRef=%s" % (key, source_ref),
        ])
        sums.append("%s  %s" % (digest, p.name))
    out = pathlib.Path(out_path)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(records) + "\n", encoding="utf-8")
    (out.parent / "SHA256SUMS").write_text("\n".join(sums) + "\n", encoding="utf-8")
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("files", nargs="+")
    args = ap.parse_args()
    print(generate(args.files, args.out))


if __name__ == "__main__":
    main()
