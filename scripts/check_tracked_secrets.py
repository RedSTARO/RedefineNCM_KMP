#!/usr/bin/env python3
"""Reject tracked runtime dumps and JWT credentials without printing secret values."""

from __future__ import annotations

import base64
import json
import re
import subprocess
import sys
from pathlib import Path


FORBIDDEN_PATHS = {"dump.rdb", "out.kt", "desktopApp/webview.dll"}
JWT_CANDIDATE = re.compile(
    rb"(?<![A-Za-z0-9_-])"
    rb"([A-Za-z0-9_-]{10,})\.([A-Za-z0-9_-]{10,})\.([A-Za-z0-9_-]{16,})"
    rb"(?![A-Za-z0-9_-])",
)


def decode_json_segment(segment: bytes) -> object | None:
    try:
        padded = segment + b"=" * (-len(segment) % 4)
        return json.loads(base64.urlsafe_b64decode(padded))
    except (ValueError, UnicodeDecodeError, json.JSONDecodeError):
        return None


def is_jwt(match: re.Match[bytes]) -> bool:
    header = decode_json_segment(match.group(1))
    payload = decode_json_segment(match.group(2))
    return (
        isinstance(header, dict)
        and isinstance(header.get("alg"), str)
        and isinstance(payload, dict)
    )


def tracked_paths() -> list[str]:
    output = subprocess.check_output(["git", "ls-files", "-z"])
    return [entry.decode("utf-8") for entry in output.split(b"\0") if entry]


def scan() -> int:
    paths = tracked_paths()
    forbidden = sorted(
        path for path in FORBIDDEN_PATHS.intersection(paths) if Path(path).exists()
    )
    if forbidden:
        print("Runtime/debug artifacts are tracked:", file=sys.stderr)
        for path in forbidden:
            print(f"  {path}", file=sys.stderr)
        return 1

    findings: list[tuple[str, int]] = []
    for relative_path in paths:
        path = Path(relative_path)
        try:
            content = path.read_bytes()
        except (FileNotFoundError, PermissionError, OSError):
            continue
        for candidate in JWT_CANDIDATE.finditer(content):
            if is_jwt(candidate):
                line = content.count(b"\n", 0, candidate.start()) + 1
                findings.append((relative_path, line))

    if findings:
        print("JWT-shaped credentials are present in tracked source:", file=sys.stderr)
        for path, line in findings:
            print(f"  {path}:{line}", file=sys.stderr)
        return 1
    return 0


def self_test() -> None:
    def segment(value: object) -> bytes:
        raw = json.dumps(value, separators=(",", ":")).encode("utf-8")
        return base64.urlsafe_b64encode(raw).rstrip(b"=")

    token = b".".join(
        (
            segment({"alg": "HS256", "typ": "JWT"}),
            segment({"sub": "test", "exp": 1}),
            b"signature0123456789",
        ),
    )
    assert is_jwt(JWT_CANDIDATE.search(token))  # type: ignore[arg-type]
    assert JWT_CANDIDATE.search(b"NSNotificationCenter.defaultCenter.postNotificationName") is not None
    assert not is_jwt(  # type: ignore[arg-type]
        JWT_CANDIDATE.search(b"NSNotificationCenter.defaultCenter.postNotificationName"),
    )


if __name__ == "__main__":
    if "--self-test" in sys.argv:
        self_test()
    raise SystemExit(scan())
