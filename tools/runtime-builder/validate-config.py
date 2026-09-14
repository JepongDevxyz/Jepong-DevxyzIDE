#!/usr/bin/env python3
import sys
from validate_config import load_properties, validate


def main():
    if len(sys.argv) != 2:
        raise SystemExit("usage: validate-config.py <runtime-pack.properties>")
    validate(load_properties(sys.argv[1]))
    print("runtime builder configuration valid")


if __name__ == "__main__":
    main()
