#!/usr/bin/env python3
import argparse, hashlib, os


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def verify(path, expected_sha256, expected_size):
    actual_size = os.path.getsize(path)
    actual_sha = sha256_file(path)
    if actual_size != int(expected_size):
        raise ValueError("size mismatch: expected %s got %s" % (expected_size, actual_size))
    if actual_sha != expected_sha256.lower():
        raise ValueError("sha256 mismatch")
    return True


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
