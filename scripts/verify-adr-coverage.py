#!/usr/bin/env python3
"""
verify-adr-coverage.py — ADR coverage-verification script for Inventra.

Scans source manifests (pom.xml, package.json, Dockerfiles, docker-compose
files, CI workflow) and compares the declared significant technology choices
against the documented ADR set. Exits 0 when all choices are documented,
versions match, and no orphaned entries exist; exits 1 otherwise.

Requires Python 3.11+ (uses tomllib from the standard library).
"""

from __future__ import annotations

import sys
import tomllib
from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path
from typing import Union


@dataclass
class SignificantChoice:
    id: str            # canonical technology identifier
    version: str       # version string as declared in the manifest
    manifest: str      # repository-relative path to the source manifest
    group: str | None  # logical group name if this choice is part of a group


@dataclass
class CoveredEntry:
    id: str       # canonical technology identifier
    version: str  # version string as documented in the ADR front-matter
    manifest: str # source manifest path as documented in the ADR front-matter


@dataclass
class DocumentedChoice:
    adr_id: str              # zero-padded four-digit ADR number, e.g. "0005"
    covers: list[CoveredEntry] = field(default_factory=list)


class FindingKind(Enum):
    GAP = "GAP"
    VERSION_DRIFT = "VERSION_DRIFT"
    ORPHAN = "ORPHAN"
    MISSING_METADATA = "MISSING_METADATA"


@dataclass
class Finding:
    kind: FindingKind
    technology_id: str
    detail: str  # human-readable description of the finding


GROUPS: dict[str, str] = {
    # jjwt trio → one ADR
    "io.jsonwebtoken:jjwt-api":     "jjwt",
    "io.jsonwebtoken:jjwt-impl":    "jjwt",
    "io.jsonwebtoken:jjwt-jackson": "jjwt",
    # Lombok + MapStruct → one ADR
    "org.projectlombok:lombok":                   "lombok-mapstruct",
    "org.mapstruct:mapstruct":                    "lombok-mapstruct",
    "org.mapstruct:mapstruct-processor":          "lombok-mapstruct",
    "org.projectlombok:lombok-mapstruct-binding": "lombok-mapstruct",
    # Testcontainers trio → one ADR
    "org.testcontainers:testcontainers":     "testcontainers",
    "org.testcontainers:mysql":              "testcontainers",
    "org.testcontainers:junit-jupiter":      "testcontainers",
    "org.testcontainers:testcontainers-bom": "testcontainers",  # BOM entry
    # Bootstrap + icons + Popper → one ADR
    "bootstrap":       "bootstrap-ui",
    "bootstrap-icons": "bootstrap-ui",
    "@popperjs/core":  "bootstrap-ui",
    # Chart.js + ng2-charts → one ADR
    "chart.js":   "charting",
    "ng2-charts": "charting",
    # Angular CLI + @angular/build → one ADR
    "@angular/cli":   "angular-build-tooling",
    "@angular/build": "angular-build-tooling",
    # ESLint + angular-eslint + typescript-eslint → one ADR
    "eslint":            "eslint-tooling",
    "angular-eslint":    "eslint-tooling",
    "typescript-eslint": "eslint-tooling",
    "@eslint/js":        "eslint-tooling",
    # Vitest + coverage → one ADR
    "vitest":              "vitest-testing",
    "@vitest/coverage-v8": "vitest-testing",
    # Testing Library → one ADR
    "@testing-library/angular": "testing-library",
    "@testing-library/dom":     "testing-library",
    # SpotBugs + FindSecBugs → one ADR (already ADR-0001)
    "com.github.spotbugs:spotbugs-maven-plugin":    "spotbugs-findsecbugs",
    "com.h3xstream.findsecbugs:findsecbugs-plugin": "spotbugs-findsecbugs",
    # PITest + junit5 plugin → one ADR
    "org.pitest:pitest-maven":         "pitest",
    "org.pitest:pitest-junit5-plugin": "pitest",
    # Docker base images → one ADR (ADR-0035)
    # These match the bare image names (before the colon)
    "maven":                       "docker-base-images",
    "eclipse-temurin":             "docker-base-images",
    "node":                        "docker-base-images",
    "nginxinc/nginx-unprivileged": "docker-base-images",
    # Docker images with tags (full references from Dockerfiles)
    "maven:3-eclipse-temurin-26":         "docker-base-images",
    "eclipse-temurin:25-jre":             "docker-base-images",
    "node:26-alpine":                     "docker-base-images",
    "nginxinc/nginx-unprivileged:alpine": "docker-base-images",
    # MySQL images from docker-compose
    "mysql:8.4": "mysql-database",
    "mysql":     "mysql-database",
    # GitHub Actions (with and without SHAs) → one ADR per action
    "actions/checkout":    "github-actions",
    "actions/setup-java":  "github-actions",
    "actions/setup-node":  "github-actions",
    "actions/upload-artifact": "github-actions",
    "actions/setup-python": "github-actions",
    # Maven build plugins → not documented (internal build tooling)
    "org.springframework.boot:spring-boot-maven-plugin": "maven-build-plugins",
    "org.apache.maven.plugins:maven-compiler-plugin":    "maven-build-plugins",
}

# Prefix-based group rules: any id starting with a prefix maps to a group.
# Checked after the exact GROUPS lookup.
GROUP_PREFIXES: list[tuple[str, str]] = [
    ("@angular/", "angular-framework"),
    ("actions/checkout@", "github-actions"),      # Match any SHA
    ("actions/setup-java@", "github-actions"),
    ("actions/setup-node@", "github-actions"),
    ("actions/upload-artifact@", "github-actions"),
    ("actions/setup-python@", "github-actions"),
]


def resolve_group(technology_id: str) -> str | None:
    """Return the group name for a technology ID, or None if standalone."""
    if technology_id in GROUPS:
        return GROUPS[technology_id]
    for prefix, group in GROUP_PREFIXES:
        if technology_id.startswith(prefix):
            return group
    return None


_DELIMITER = "+++"


def parse_toml_front_matter(
    content: str,
    source_path: str = "<unknown>",
) -> Union[DocumentedChoice, Finding]:
    """Parse the TOML front-matter block from an ADR file's content.

    The front-matter block is delimited by ``+++`` on its own line at the very
    start of the file and a matching closing ``+++`` line.

    Returns:
        A ``DocumentedChoice`` when the block is present and valid.
        A ``Finding(MISSING_METADATA, ...)`` when no ``+++`` block is found.

    Exits with code 1 when a ``+++`` block is present but contains malformed
    TOML (consistent with the error-handling policy in the design document).
    """
    lines = content.splitlines()

    # The file must start with the opening delimiter.
    if not lines or lines[0].strip() != _DELIMITER:
        return Finding(
            kind=FindingKind.MISSING_METADATA,
            technology_id=source_path,
            detail=(
                f"ADR file '{source_path}' has no TOML front-matter block "
                f"(expected '+++' on the first line)"
            ),
        )

    # Find the closing delimiter.
    closing_index: int | None = None
    for i, line in enumerate(lines[1:], start=1):
        if line.strip() == _DELIMITER:
            closing_index = i
            break

    if closing_index is None:
        print(
            f"ERROR: ADR '{source_path}' front-matter parse error: "
            f"opening '+++' found but no closing '+++' delimiter",
            file=sys.stderr,
        )
        sys.exit(1)

    toml_text = "\n".join(lines[1:closing_index])

    try:
        data = tomllib.loads(toml_text)
    except tomllib.TOMLDecodeError as exc:
        print(
            f"ERROR: ADR '{source_path}' front-matter parse error: {exc}",
            file=sys.stderr,
        )
        sys.exit(1)

    # Extract adr identifier.
    adr_id: str = str(data.get("adr", ""))

    # Extract covers list.
    raw_covers: list[dict] = data.get("covers", [])
    covers: list[CoveredEntry] = []
    for entry in raw_covers:
        covers.append(
            CoveredEntry(
                id=str(entry.get("id", "")),
                version=str(entry.get("version", "")),
                manifest=str(entry.get("manifest", "")),
            )
        )

    return DocumentedChoice(adr_id=adr_id, covers=covers)



def scan_pom_xml(path: str) -> list[SignificantChoice]:
    """Scan backend/pom.xml and return SignificantChoice records.

    Collects all <dependency>, <plugin>, and explicitly-versioned
    <dependencyManagement>/<dependency> entries.
    """
    import xml.etree.ElementTree as ET

    p = Path(path)
    if not p.exists():
        print(f"ERROR: manifest not found: {path}", file=sys.stderr)
        sys.exit(1)

    try:
        tree = ET.parse(p)
    except ET.ParseError as exc:
        print(f"ERROR: failed to parse {path}: {exc}", file=sys.stderr)
        sys.exit(1)

    root = tree.getroot()
    ns = "http://maven.apache.org/POM/4.0.0"
    N = lambda tag: f"{{{ns}}}{tag}"

    choices: list[SignificantChoice] = []

    def _text(elem: ET.Element | None) -> str:
        if elem is None:
            return ""
        return (elem.text or "").strip()

    # 1. Direct <dependencies>/<dependency> entries (not inside <dependencyManagement>)
    # Use a targeted path from the root to avoid matching managed deps
    direct_deps_parent = root.find(f"{N('dependencies')}")
    if direct_deps_parent is not None:
        for dep in direct_deps_parent.findall(N("dependency")):
            group_id = _text(dep.find(N("groupId")))
            artifact_id = _text(dep.find(N("artifactId")))
            version = _text(dep.find(N("version")))
            if group_id and artifact_id:
                canonical_id = f"{group_id}:{artifact_id}"
                choices.append(SignificantChoice(
                    id=canonical_id,
                    version=version,
                    manifest=path,
                    group=resolve_group(canonical_id),
                ))

    # 2. <build>/<plugins>/<plugin> entries
    for plugin in root.findall(f".//{N('build')}/{N('plugins')}/{N('plugin')}"):
        group_id = _text(plugin.find(N("groupId")))
        artifact_id = _text(plugin.find(N("artifactId")))
        version = _text(plugin.find(N("version")))
        if group_id and artifact_id:
            canonical_id = f"{group_id}:{artifact_id}"
            choices.append(SignificantChoice(
                id=canonical_id,
                version=version,
                manifest=path,
                group=resolve_group(canonical_id),
            ))

    # 3. <dependencyManagement>/<dependencies>/<dependency> entries with explicit <version>
    for dep in root.findall(
        f".//{N('dependencyManagement')}/{N('dependencies')}/{N('dependency')}"
    ):
        version_elem = dep.find(N("version"))
        if version_elem is None:
            continue  # no explicit version — skip
        version = _text(version_elem)
        group_id = _text(dep.find(N("groupId")))
        artifact_id = _text(dep.find(N("artifactId")))
        if group_id and artifact_id:
            canonical_id = f"{group_id}:{artifact_id}"
            choices.append(SignificantChoice(
                id=canonical_id,
                version=version,
                manifest=path,
                group=resolve_group(canonical_id),
            ))

    return choices


def scan_package_json(path: str) -> list[SignificantChoice]:
    """Scan frontend/package.json and return SignificantChoice records.

    Collects all keys from 'dependencies' and 'devDependencies'.
    """
    import json

    p = Path(path)
    if not p.exists():
        print(f"ERROR: manifest not found: {path}", file=sys.stderr)
        sys.exit(1)

    try:
        data = json.loads(p.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        print(f"ERROR: failed to parse {path}: {exc}", file=sys.stderr)
        sys.exit(1)

    choices: list[SignificantChoice] = []

    for section in ("dependencies", "devDependencies"):
        for pkg_name, version in data.get(section, {}).items():
            choices.append(SignificantChoice(
                id=pkg_name,
                version=str(version),
                manifest=path,
                group=resolve_group(pkg_name),
            ))

    return choices


def scan_dockerfile(path: str) -> list[SignificantChoice]:
    """Scan a Dockerfile and return SignificantChoice records for FROM images.

    Extracts each FROM image reference, stripping any digest (@sha256:...)
    but keeping the tag.
    """
    import re

    p = Path(path)
    if not p.exists():
        print(f"ERROR: manifest not found: {path}", file=sys.stderr)
        sys.exit(1)

    choices: list[SignificantChoice] = []
    from_pattern = re.compile(r"^\s*FROM\s+(\S+)", re.IGNORECASE)

    for line in p.read_text(encoding="utf-8").splitlines():
        m = from_pattern.match(line)
        if not m:
            continue
        image_ref = m.group(1)
        # Strip AS alias (e.g. "image:tag AS build")
        image_ref = image_ref.split()[0] if " " in image_ref else image_ref
        # Strip digest (@sha256:...)
        if "@" in image_ref:
            image_ref = image_ref.split("@")[0]
        # Derive the bare image name (without tag) for group lookup
        image_name = image_ref.split(":")[0]
        choices.append(SignificantChoice(
            id=image_ref,
            version="",
            manifest=path,
            group=resolve_group(image_name),
        ))

    return choices


def scan_docker_compose(path: str) -> list[SignificantChoice]:
    """Scan a docker-compose file and return SignificantChoice records.

    Uses a line-based parser to extract 'image:' values without requiring
    PyYAML (which is not in the standard library).
    """
    import re

    p = Path(path)
    if not p.exists():
        print(f"ERROR: manifest not found: {path}", file=sys.stderr)
        sys.exit(1)

    choices: list[SignificantChoice] = []
    image_pattern = re.compile(r"^\s*image:\s*(.+)$")

    for line in p.read_text(encoding="utf-8").splitlines():
        m = image_pattern.match(line)
        if not m:
            continue
        image_value = m.group(1).strip().strip("'\"")
        if not image_value:
            continue
        # Derive the bare image name (without tag) for group lookup
        image_name = image_value.split(":")[0]
        choices.append(SignificantChoice(
            id=image_value,
            version="",
            manifest=path,
            group=resolve_group(image_name),
        ))

    return choices


def scan_ci_workflow(path: str) -> list[SignificantChoice]:
    """Scan a GitHub Actions workflow file and return SignificantChoice records.

    Extracts each 'uses:' action reference.
    """
    import re

    p = Path(path)
    if not p.exists():
        print(f"ERROR: manifest not found: {path}", file=sys.stderr)
        sys.exit(1)

    choices: list[SignificantChoice] = []
    uses_pattern = re.compile(r"^\s*uses:\s*(.+)$")

    for line in p.read_text(encoding="utf-8").splitlines():
        m = uses_pattern.match(line)
        if not m:
            continue
        action_ref = m.group(1).strip().strip("'\"")
        # Strip inline comment (e.g. "actions/checkout@sha # v4")
        if " #" in action_ref:
            action_ref = action_ref.split(" #")[0].strip()
        if not action_ref:
            continue
        # Derive the bare action name (without @sha/version) for group lookup
        action_name = action_ref.split("@")[0]
        choices.append(SignificantChoice(
            id=action_ref,
            version="",
            manifest=path,
            group=resolve_group(action_name),
        ))

    return choices



def apply_grouping(choices: list[SignificantChoice]) -> list[SignificantChoice]:
    """Merge choices sharing a group key into a single grouped entry.

    For each group, create one representative SignificantChoice with:
    - id = the group name
    - version = "" (group has no single version)
    - manifest = the manifest of the first member
    - group = the group name

    Ungrouped entries are returned as-is (with group=None).
    """
    result: list[SignificantChoice] = []
    emitted_groups: set[str] = set()
    first_member: dict[str, SignificantChoice] = {}

    for choice in choices:
        group = resolve_group(choice.id)
        if group is not None and group not in first_member:
            first_member[group] = choice

    for choice in choices:
        group = resolve_group(choice.id)
        if group is None:
            result.append(choice)
        else:
            if group not in emitted_groups:
                emitted_groups.add(group)
                representative = SignificantChoice(
                    id=group,
                    version="",
                    manifest=first_member[group].manifest,
                    group=group,
                )
                result.append(representative)

    return result



def scan_adr_files(adr_dir: str) -> tuple[list[DocumentedChoice], list[Finding]]:
    """Scan adr/*.md files and return (documented_choices, findings).

    Skips README.md and template.md. Emits MISSING_METADATA findings for
    files without front-matter. Exits 1 on malformed front-matter.

    """
    adr_path = Path(adr_dir)
    if not adr_path.exists():
        print("ERROR: adr/ directory not found", file=sys.stderr)
        sys.exit(1)

    _SKIP = {"README.md", "template.md"}

    documented_choices: list[DocumentedChoice] = []
    findings: list[Finding] = []

    for path in sorted(adr_path.glob("*.md")):
        if path.name in _SKIP:
            continue

        content = path.read_text(encoding="utf-8")
        result = parse_toml_front_matter(content, str(path))

        if isinstance(result, Finding):
            # MISSING_METADATA — warn and count, but continue scanning
            print(
                f"WARNING: ADR {path.name} has no front-matter metadata — skipped"
            )
            findings.append(result)
        else:
            documented_choices.append(result)

    return (documented_choices, findings)



def check_gaps(
    grouped_choices: list[SignificantChoice],
    documented_choices: list[DocumentedChoice],
) -> list[Finding]:
    """Return GAP findings for choices not covered by any ADR."""
    # Build the set of all technology IDs covered by any DocumentedChoice.
    covered_ids: set[str] = set()
    for doc in documented_choices:
        for entry in doc.covers:
            covered_ids.add(entry.id)

    findings: list[Finding] = []
    for choice in grouped_choices:
        if choice.id not in covered_ids:
            findings.append(
                Finding(
                    kind=FindingKind.GAP,
                    technology_id=choice.id,
                    detail=f"No ADR documents {choice.id} (from {choice.manifest})",
                )
            )
    return findings


def check_version_drift(
    grouped_choices: list[SignificantChoice],
    documented_choices: list[DocumentedChoice],
) -> list[Finding]:
    """Return VERSION_DRIFT findings where documented version != manifest version."""
    # Build a dict mapping technology ID → (documented_version, adr_id).
    # If the same ID appears in multiple ADRs, the last one wins (edge case).
    covered_versions: dict[str, tuple[str, str]] = {}
    for doc in documented_choices:
        for entry in doc.covers:
            covered_versions[entry.id] = (entry.version, doc.adr_id)

    findings: list[Finding] = []
    for choice in grouped_choices:
        if choice.id in covered_versions and choice.version:
            documented_version, adr_id = covered_versions[choice.id]
            if choice.version != documented_version:
                findings.append(
                    Finding(
                        kind=FindingKind.VERSION_DRIFT,
                        technology_id=choice.id,
                        detail=(
                            f"ADR-{adr_id} documents version '{documented_version}' "
                            f"but manifest declares '{choice.version}'"
                        ),
                    )
                )
    return findings


def check_orphans(
    grouped_choices: list[SignificantChoice],
    documented_choices: list[DocumentedChoice],
) -> list[Finding]:
    """Return ORPHAN findings for ADR entries with no matching manifest choice."""
    # Build the set of all technology IDs present in the grouped manifest.
    manifest_ids: set[str] = {choice.id for choice in grouped_choices}

    findings: list[Finding] = []
    for doc in documented_choices:
        for entry in doc.covers:
            if entry.id not in manifest_ids:
                findings.append(
                    Finding(
                        kind=FindingKind.ORPHAN,
                        technology_id=entry.id,
                        detail=(
                            f"ADR-{doc.adr_id} covers '{entry.id}' but this "
                            f"technology is not in any source manifest"
                        ),
                    )
                )
    return findings



def report_findings(findings: list[Finding]) -> None:
    """Print findings grouped by category to stdout.

    Groups findings by kind and prints a header for each kind that has at
    least one finding, followed by a bullet for each finding's detail.
    """
    # Collect findings per kind in a stable order.
    grouped: dict[FindingKind, list[Finding]] = {kind: [] for kind in FindingKind}
    for finding in findings:
        grouped[finding.kind].append(finding)

    for kind in FindingKind:
        kind_findings = grouped[kind]
        if not kind_findings:
            continue
        count = len(kind_findings)
        noun = "finding" if count == 1 else "findings"
        print(f"{kind.value} ({count} {noun}):")
        for f in kind_findings:
            print(f"  - {f.technology_id}: {f.detail}")
        print()



def main() -> None:
    """Orchestrate scan → group → scan ADRs → compare → report.

    Exits 0 when all finding lists are empty, exits 1 otherwise.
    """
    repo_root = Path(__file__).parent.parent

    # 1. Scan all manifests.
    all_choices: list[SignificantChoice] = []
    all_choices.extend(scan_pom_xml(str(repo_root / "backend/pom.xml")))
    all_choices.extend(scan_package_json(str(repo_root / "frontend/package.json")))
    all_choices.extend(scan_dockerfile(str(repo_root / "backend/Dockerfile")))
    all_choices.extend(scan_dockerfile(str(repo_root / "frontend/Dockerfile")))
    all_choices.extend(scan_docker_compose(str(repo_root / "docker-compose.yml")))
    all_choices.extend(scan_docker_compose(str(repo_root / "docker-compose.prod.yml")))
    all_choices.extend(scan_ci_workflow(str(repo_root / ".github/workflows/ci.yml")))

    # 2. Apply grouping.
    grouped = apply_grouping(all_choices)

    # 3. Scan ADR files.
    documented, metadata_findings = scan_adr_files(str(repo_root / "docs" / "adr"))

    # 4. Run checks.
    gap_findings = check_gaps(grouped, documented)
    drift_findings = check_version_drift(grouped, documented)
    orphan_findings = check_orphans(grouped, documented)

    # 5. Combine and report.
    all_findings = metadata_findings + gap_findings + drift_findings + orphan_findings
    report_findings(all_findings)

    sys.exit(0 if not all_findings else 1)


if __name__ == "__main__":
    main()
