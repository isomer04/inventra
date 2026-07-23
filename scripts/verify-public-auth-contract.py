#!/usr/bin/env python3
"""Fail when frontend and backend public-auth endpoint contracts drift."""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA_CONTRACT = ROOT / "backend/src/main/java/com/inventra/api/security/SecurityPaths.java"
TS_CONTRACT = ROOT / "frontend/src/app/core/contracts/public-auth-endpoints.contract.ts"
ENDPOINT_PATTERN = re.compile(r"[\"'](/api/v1/auth/[^\"']+)[\"']")


def read_endpoints(path: Path) -> list[str]:
    endpoints = ENDPOINT_PATTERN.findall(path.read_text(encoding="utf-8"))
    if not endpoints:
        raise ValueError(f"No public auth endpoints found in {path.relative_to(ROOT)}")
    if len(endpoints) != len(set(endpoints)):
        raise ValueError(f"Duplicate public auth endpoint in {path.relative_to(ROOT)}")
    return endpoints


try:
    backend = set(read_endpoints(JAVA_CONTRACT))
    frontend = set(read_endpoints(TS_CONTRACT))
except (OSError, ValueError) as error:
    print(f"[ERROR] {error}", file=sys.stderr)
    sys.exit(1)

if backend != frontend:
    print("[ERROR] Public auth endpoint contract drift detected.", file=sys.stderr)
    print(f"  Backend only: {sorted(backend - frontend)}", file=sys.stderr)
    print(f"  Frontend only: {sorted(frontend - backend)}", file=sys.stderr)
    sys.exit(1)

print(f"Public auth endpoint contract is synchronized ({len(backend)} paths).")
