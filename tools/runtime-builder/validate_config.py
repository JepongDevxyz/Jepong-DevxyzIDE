#!/usr/bin/env python3
import re

REQUIRED = ("applicationId", "nativeBuilderRepo", "nativeBuilderCommit", "nativeAbi", "gradleVersion", "androidApi")
COMMIT_RE = re.compile(r"^[0-9a-f]{40}$")
EXPECTED_APP_ID = "com.jepongdevxyz.idebuild"
EXPECTED_REPO = "https://github.com/appdevforall/terminal-packages.git"


def load_properties(path):
    data = {}
    with open(path, "r", encoding="utf-8") as fh:
        for raw in fh:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            if "=" not in line:
                raise ValueError("invalid property line: %s" % line)
            key, value = line.split("=", 1)
            data[key.strip()] = value.strip()
    return data


def _version_tuple(value):
    parts = value.strip().split(".")
    if not parts or any(not p.isdigit() for p in parts):
        raise ValueError("invalid version: %s" % value)
    return tuple(int(p) for p in parts)


def validate(config):
    missing = [k for k in REQUIRED if not config.get(k)]
    if missing:
        raise ValueError("missing required keys: %s" % ", ".join(missing))
    if config["applicationId"] != EXPECTED_APP_ID:
        raise ValueError("unexpected applicationId")
    if config["nativeBuilderRepo"] != EXPECTED_REPO:
        raise ValueError("unexpected native builder repository")
    if not COMMIT_RE.match(config["nativeBuilderCommit"]):
        raise ValueError("nativeBuilderCommit must be a pinned 40-character lowercase SHA")
    if config["nativeAbi"] != "aarch64":
        raise ValueError("only aarch64 is supported in this milestone")
    if _version_tuple(config["gradleVersion"]) < (8, 9):
        raise ValueError("Gradle 8.9 or newer is required")
    try:
        api = int(config["androidApi"])
    except ValueError:
        raise ValueError("androidApi must be numeric")
    if api < 35:
        raise ValueError("Android API 35 or newer is required")
    return config
