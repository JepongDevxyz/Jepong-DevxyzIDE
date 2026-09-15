#!/usr/bin/env python3
"""Fail-closed overrides for pinned upstream source metadata.

These overrides exist only for cases where a pinned upstream recipe points at a
mutable/generated source archive whose bytes changed without a version bump.
Each override asserts the exact expected recipe before changing anything.
"""
from pathlib import Path

FOOT_1250_OLD_SHA256 = "442a42d576ec72dd50f2d3faea8a664230a47bac79dc1eb6e7c9125ee76c130f"
FOOT_1250_NEW_SHA256 = "ee9d0e51295945157ecb33119cb2c79b276093d0fd342d959d78d772d505571c"
FOOT_1250_VERSION = 'TERMUX_PKG_VERSION="1.25.0"'
FOOT_1250_SOURCE = 'TERMUX_PKG_SRCURL=https://codeberg.org/dnkl/foot/archive/${TERMUX_PKG_VERSION}.tar.gz'


def patch_foot_1250_checksum(recipe_path):
    path = Path(recipe_path)
    text = path.read_text(encoding="utf-8")

    required = (
        FOOT_1250_VERSION,
        FOOT_1250_SOURCE,
        "TERMUX_PKG_SHA256=" + FOOT_1250_OLD_SHA256,
    )
    missing = [item for item in required if text.count(item) != 1]
    if missing:
        raise ValueError(
            "Pinned foot 1.25.0 recipe no longer matches expected upstream contents"
        )

    old = "TERMUX_PKG_SHA256=" + FOOT_1250_OLD_SHA256
    new = "TERMUX_PKG_SHA256=" + FOOT_1250_NEW_SHA256
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("recipe")
    args = parser.parse_args()
    patch_foot_1250_checksum(args.recipe)
