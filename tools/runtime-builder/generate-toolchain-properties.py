#!/usr/bin/env python3
import argparse
import pathlib
from generate_toolchain_properties import build_properties


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
