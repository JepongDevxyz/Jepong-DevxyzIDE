#!/usr/bin/env python3
import argparse
from verify_pack import verify


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--file", required=True)
    ap.add_argument("--sha256", required=True)
    ap.add_argument("--size", required=True, type=int)
    args = ap.parse_args()
    verify(args.file, args.sha256, args.size)
    print("verified %s" % args.file)


if __name__ == "__main__":
    main()
