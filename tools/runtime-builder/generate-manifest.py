#!/usr/bin/env python3
import argparse
from generate_manifest import generate


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("files", nargs="+")
    args = ap.parse_args()
    print(generate(args.files, args.out))


if __name__ == "__main__":
    main()
