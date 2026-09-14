#!/usr/bin/env python3
import argparse, hashlib, os, pathlib

ALLOWED = {"gradle", "android-sdk-platform"}


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def build_properties(root, component, version, abi):
    if component not in ALLOWED:
        raise ValueError("unsupported component: %s" % component)
    lines = ["format=1", "component=%s" % component, "version=%s" % version, "abi=%s" % abi]
    root = pathlib.Path(root)
    files = sorted(p for p in root.rglob("*") if p.is_file() and p.name != "devxyz-toolchain.properties")
    if not files:
        raise ValueError("payload is empty")
    for path in files:
        rel = path.relative_to(root).as_posix()
        lines.append("sha256.%s=%s" % (rel, sha256_file(path)))
    return "\n".join(lines) + "\n"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", required=True)
    ap.add_argument("--component", required=True)
    ap.add_argument("--version", required=True)
    ap.add_argument("--abi", required=True)
    args = ap.parse_args()
    text = build_properties(args.root, args.component, args.version, args.abi)
    out = pathlib.Path(args.root) / "devxyz-toolchain.properties"
    out.write_text(text, encoding="utf-8")
    print(out)


if __name__ == "__main__":
    main()
