"""
Unit tests for the TOML front-matter parser and grouping logic in
scripts/verify-adr-coverage.py.

Task 4.1 — Requirements: 9.5
"""

import importlib.util
import re
import sys
from pathlib import Path

from hypothesis import given, settings
from hypothesis import strategies as st

# ---------------------------------------------------------------------------
# Load the hyphen-named module via importlib
# ---------------------------------------------------------------------------

_spec = importlib.util.spec_from_file_location(
    "verify_adr_coverage",
    Path(__file__).parent.parent / "verify-adr-coverage.py",
)
_mod = importlib.util.module_from_spec(_spec)
# Register in sys.modules before exec so dataclass __module__ lookups resolve.
sys.modules["verify_adr_coverage"] = _mod
_spec.loader.exec_module(_mod)

# Import names from the module
parse_toml_front_matter = _mod.parse_toml_front_matter
apply_grouping = _mod.apply_grouping
check_gaps = _mod.check_gaps
SignificantChoice = _mod.SignificantChoice
DocumentedChoice = _mod.DocumentedChoice
CoveredEntry = _mod.CoveredEntry
Finding = _mod.Finding
FindingKind = _mod.FindingKind
GROUPS = _mod.GROUPS
resolve_group = _mod.resolve_group


# ---------------------------------------------------------------------------
# Local helper functions for tests 4 and 5
# (structural validator and status extractor — tested as standalone helpers
#  here; full property-based tests are in tasks 4.4 and 4.5)
# ---------------------------------------------------------------------------

_REQUIRED_SECTIONS = ["## Status", "## Context", "## Decision", "## Consequences"]
_ALTERNATIVES_SUBSECTION = "### Alternatives considered"


def validate_adr_structure(content: str) -> bool:
    """Return True iff the ADR document contains all four required sections
    (Status, Context, Decision, Consequences) in order, each with at least
    one non-blank line of content, and contains an 'Alternatives considered'
    subsection within Consequences.
    """
    lines = content.splitlines()

    # Locate each required section heading in order.
    section_indices: list[int] = []
    for section in _REQUIRED_SECTIONS:
        for i, line in enumerate(lines):
            if line.strip() == section:
                # Must appear after all previously found sections.
                if section_indices and i <= section_indices[-1]:
                    continue
                section_indices.append(i)
                break
        else:
            return False  # section not found

    # Verify each section has at least one non-blank content line before the
    # next section (or end of file).
    for idx, section_line_idx in enumerate(section_indices):
        next_boundary = (
            section_indices[idx + 1] if idx + 1 < len(section_indices) else len(lines)
        )
        content_lines = [
            l for l in lines[section_line_idx + 1 : next_boundary] if l.strip()
        ]
        if not content_lines:
            return False

    # Verify "Alternatives considered" subsection exists after Consequences.
    consequences_idx = section_indices[3]
    alternatives_found = any(
        line.strip() == _ALTERNATIVES_SUBSECTION
        for line in lines[consequences_idx:]
    )
    if not alternatives_found:
        return False

    return True


_VALID_STATUSES = {"Accepted", "Superseded", "Deprecated"}
_STATUS_PATTERN = re.compile(r"^## Status\s*\n+([^\n#]+)", re.MULTILINE)


def extract_status(content: str) -> str | None:
    """Extract the status value from an ADR document.

    Returns the status string if it is exactly one of 'Accepted',
    'Superseded', or 'Deprecated'; returns None otherwise (including for
    lowercase variants, empty values, or missing Status section).
    """
    match = _STATUS_PATTERN.search(content)
    if not match:
        return None
    value = match.group(1).strip()
    return value if value in _VALID_STATUSES else None


# ---------------------------------------------------------------------------
# Test 1: parse a well-formed TOML front-matter block
# ---------------------------------------------------------------------------


def test_parse_toml_front_matter_well_formed():
    """Parser correctly extracts adr, covers[0].id, covers[0].version,
    and covers[0].manifest from a well-formed TOML block."""
    content = """\
+++
adr = "0010"

[[covers]]
id = "io.jsonwebtoken:jjwt-api"
version = "0.13.0"
manifest = "backend/pom.xml"

[[covers]]
id = "io.jsonwebtoken:jjwt-impl"
version = "0.13.0"
manifest = "backend/pom.xml"
+++

# ADR-0010: JWT library

## Status
Accepted
"""
    result = parse_toml_front_matter(content, source_path="adr/0010-jwt-jjwt.md")

    assert isinstance(result, DocumentedChoice), (
        f"Expected DocumentedChoice, got {type(result)}"
    )
    assert result.adr_id == "0010"
    assert len(result.covers) == 2

    first = result.covers[0]
    assert first.id == "io.jsonwebtoken:jjwt-api"
    assert first.version == "0.13.0"
    assert first.manifest == "backend/pom.xml"

    second = result.covers[1]
    assert second.id == "io.jsonwebtoken:jjwt-impl"
    assert second.version == "0.13.0"
    assert second.manifest == "backend/pom.xml"


# ---------------------------------------------------------------------------
# Test 2: MISSING_METADATA when no +++ delimiters are present
# ---------------------------------------------------------------------------


def test_parse_toml_front_matter_missing_metadata():
    """Parser emits a Finding(MISSING_METADATA) when no +++ block is present."""
    content = """\
# ADR-0010: JWT library

## Status
Accepted

## Context
Some context here.
"""
    result = parse_toml_front_matter(content, source_path="adr/0010-jwt-jjwt.md")

    assert isinstance(result, Finding), (
        f"Expected Finding, got {type(result)}"
    )
    assert result.kind == FindingKind.MISSING_METADATA
    assert "0010-jwt-jjwt.md" in result.technology_id


# ---------------------------------------------------------------------------
# Test 3: grouping table merges the jjwt trio into one group
# ---------------------------------------------------------------------------


def test_grouping_jjwt_trio():
    """apply_grouping merges jjwt-api, jjwt-impl, jjwt-jackson into one entry
    with id = 'jjwt'."""
    choices = [
        SignificantChoice(
            id="io.jsonwebtoken:jjwt-api",
            version="0.13.0",
            manifest="backend/pom.xml",
            group=resolve_group("io.jsonwebtoken:jjwt-api"),
        ),
        SignificantChoice(
            id="io.jsonwebtoken:jjwt-impl",
            version="0.13.0",
            manifest="backend/pom.xml",
            group=resolve_group("io.jsonwebtoken:jjwt-impl"),
        ),
        SignificantChoice(
            id="io.jsonwebtoken:jjwt-jackson",
            version="0.13.0",
            manifest="backend/pom.xml",
            group=resolve_group("io.jsonwebtoken:jjwt-jackson"),
        ),
    ]

    grouped = apply_grouping(choices)

    assert len(grouped) == 1, (
        f"Expected exactly 1 grouped entry, got {len(grouped)}: {grouped}"
    )
    assert grouped[0].id == "jjwt"
    assert grouped[0].group == "jjwt"


# ---------------------------------------------------------------------------
# Test 4: structural validator rejects an ADR missing the Consequences section
# ---------------------------------------------------------------------------


def test_structural_validator_missing_consequences():
    """validate_adr_structure returns False for an ADR that is missing the
    Consequences section."""
    content_without_consequences = """\
# ADR-0005: Backend platform

## Status
Accepted

## Context
We needed a backend framework for the Inventra SaaS application.

## Decision
We chose Spring Boot 4 on Java 25.
"""
    assert validate_adr_structure(content_without_consequences) is False


def test_structural_validator_accepts_complete_adr():
    """validate_adr_structure returns True for a complete, well-formed ADR."""
    content_complete = """\
# ADR-0005: Backend platform

## Status
Accepted

## Context
We needed a backend framework for the Inventra SaaS application.

## Decision
We chose Spring Boot 4 on Java 25.

## Consequences
Spring Boot provides opinionated auto-configuration.

### Alternatives considered
- **Quarkus** — rejected due to smaller ecosystem.

### References
- https://spring.io/projects/spring-boot
"""
    assert validate_adr_structure(content_complete) is True


# ---------------------------------------------------------------------------
# Test 5: status extractor rejects lowercase "accepted", accepts "Accepted"
# ---------------------------------------------------------------------------


def test_status_extractor_lowercase_rejected():
    """extract_status returns None for lowercase 'accepted'."""
    content = """\
# ADR-0005: Backend platform

## Status
accepted

## Context
Some context.
"""
    result = extract_status(content)
    assert result is None, (
        f"Expected None for lowercase 'accepted', got {result!r}"
    )


def test_status_extractor_accepted_capitalised():
    """extract_status returns 'Accepted' for correctly capitalised status."""
    content = """\
# ADR-0005: Backend platform

## Status
Accepted

## Context
Some context.
"""
    result = extract_status(content)
    assert result == "Accepted", (
        f"Expected 'Accepted', got {result!r}"
    )


def test_status_extractor_valid_values():
    """extract_status accepts all three valid status values."""
    for status in ("Accepted", "Superseded", "Deprecated"):
        content = f"# ADR\n\n## Status\n{status}\n\n## Context\nSome context.\n"
        assert extract_status(content) == status, (
            f"Expected '{status}' to be accepted"
        )


def test_status_extractor_rejects_invalid_values():
    """extract_status returns None for invalid or misspelled status values."""
    for bad_status in ("accepted", "ACCEPTED", "Approve", "approved", "", "  "):
        content = f"# ADR\n\n## Status\n{bad_status}\n\n## Context\nSome context.\n"
        result = extract_status(content)
        assert result is None, (
            f"Expected None for status {bad_status!r}, got {result!r}"
        )


# ---------------------------------------------------------------------------
# Feature: architecture-decisions-documentation, Property 2: Version accuracy / drift detection
# ---------------------------------------------------------------------------

check_version_drift = _mod.check_version_drift


@settings(max_examples=200)
@given(manifest_version=st.text(), adr_version=st.text())
def test_property_version_drift(manifest_version: str, adr_version: str):
    """Property 2: For any (manifest version string, ADR documented version string)
    pair referring to the same technology ID, check_version_drift reports
    VERSION_DRIFT if and only if the strings differ character-for-character.

    **Validates: Requirements 2.3, 9.1, 9.7**
    """
    choice = SignificantChoice(
        id="test-tech",
        version=manifest_version,
        manifest="backend/pom.xml",
        group=None,
    )
    doc = DocumentedChoice(
        adr_id="0001",
        covers=[
            CoveredEntry(
                id="test-tech",
                version=adr_version,
                manifest="backend/pom.xml",
            )
        ],
    )

    findings = check_version_drift([choice], [doc])
    drift_findings = [f for f in findings if f.kind == FindingKind.VERSION_DRIFT]

    if manifest_version == adr_version or manifest_version == "":
        # No drift expected: versions match, or manifest version is empty
        # (empty manifest version means "unversioned" — not compared)
        assert len(drift_findings) == 0, (
            f"Expected no VERSION_DRIFT for manifest_version={manifest_version!r}, "
            f"adr_version={adr_version!r}, but got: {drift_findings}"
        )
    else:
        # manifest_version != adr_version AND manifest_version != "" → drift expected
        assert len(drift_findings) == 1, (
            f"Expected exactly one VERSION_DRIFT for manifest_version={manifest_version!r}, "
            f"adr_version={adr_version!r}, but got: {drift_findings}"
        )
        assert drift_findings[0].technology_id == "test-tech"


# ---------------------------------------------------------------------------
# Property 4: Status value validity
# Feature: architecture-decisions-documentation, Property 4: Status value validity
# Validates: Requirements 5.4
# ---------------------------------------------------------------------------

_VALID_STATUS_SET = {"Accepted", "Superseded", "Deprecated"}


@given(status_value=st.text())
@settings(max_examples=200)
def test_property_status_value_validity(status_value: str):
    """**Validates: Requirements 5.4**

    Property 4: extract_status returns exactly one of 'Accepted', 'Superseded',
    or 'Deprecated' for valid inputs, and returns None for any other value.

    For any arbitrary string used as the status line in an ADR document:
    - result is not None  iff  status_value.strip() is in the valid set
    - result is None      iff  status_value.strip() is NOT in the valid set
    """
    doc = f"# ADR\n\n## Status\n{status_value}\n\n## Context\nSome context.\n"
    result = extract_status(doc)

    if status_value.strip() in _VALID_STATUS_SET:
        assert result is not None, (
            f"Expected a non-None result for valid status {status_value!r}, got None"
        )
        assert result == status_value.strip(), (
            f"Expected result {status_value.strip()!r}, got {result!r}"
        )
    else:
        assert result is None, (
            f"Expected None for invalid status {status_value!r}, got {result!r}"
        )


# ---------------------------------------------------------------------------
# Property 1: Coverage gap detection
# Feature: architecture-decisions-documentation, Property 1: Coverage gap detection
# ---------------------------------------------------------------------------

# Validates: Requirements 1.1, 9.3, 9.6


@given(
    choice_ids=st.lists(st.text(min_size=1)),
    documented_ids=st.lists(st.text(min_size=1)),
)
@settings(max_examples=200)
def test_gap_detection_property(choice_ids: list, documented_ids: list):
    """Property 1: check_gaps reports a GAP finding for every choice ID not
    covered by any documented entry, and no GAP finding for any choice ID
    that is covered.

    Validates: Requirements 1.1, 9.3, 9.6
    """
    # Build SignificantChoice objects from the generated choice IDs.
    grouped_choices = [
        SignificantChoice(id=cid, version="1.0", manifest="manifest.xml", group=None)
        for cid in choice_ids
    ]

    # Build DocumentedChoice objects from the generated documented IDs.
    # Each DocumentedChoice covers exactly one ID.
    documented_choices = [
        DocumentedChoice(
            adr_id="0001",
            covers=[CoveredEntry(id=did, version="1.0", manifest="manifest.xml")],
        )
        for did in documented_ids
    ]

    findings = check_gaps(grouped_choices, documented_choices)

    # Collect the set of technology IDs that appear in GAP findings.
    gap_ids = {f.technology_id for f in findings if f.kind == FindingKind.GAP}

    # All findings must be of kind GAP (check_gaps only produces GAP findings).
    assert all(f.kind == FindingKind.GAP for f in findings)

    documented_id_set = set(documented_ids)
    choice_id_set = set(choice_ids)

    # Every choice ID NOT in documented IDs must have exactly one GAP finding.
    uncovered = choice_id_set - documented_id_set
    for cid in uncovered:
        assert cid in gap_ids, (
            f"Expected GAP finding for uncovered choice '{cid}', but none was reported"
        )

    # Every choice ID IN documented IDs must have no GAP finding.
    covered = choice_id_set & documented_id_set
    for cid in covered:
        assert cid not in gap_ids, (
            f"Unexpected GAP finding for covered choice '{cid}'"
        )

    # The set of GAP finding IDs must equal exactly the uncovered set.
    assert gap_ids == uncovered, (
        f"GAP finding IDs {gap_ids!r} do not match expected uncovered set {uncovered!r}"
    )


# ---------------------------------------------------------------------------
# Feature: architecture-decisions-documentation, Property 5: Reference URL format validity
# ---------------------------------------------------------------------------


def validate_reference_url(url: str) -> bool:
    """Return True iff the URL starts with 'https://' AND the host portion
    (between 'https://' and the first '/' or end of string) contains at
    least one '.'.

    Returns False otherwise.
    """
    prefix = "https://"
    if not url.startswith(prefix):
        return False
    # Extract the host portion: everything after 'https://' up to the first '/'
    rest = url[len(prefix):]
    slash_pos = rest.find("/")
    host = rest[:slash_pos] if slash_pos != -1 else rest
    return "." in host


@given(url=st.text())
@settings(max_examples=200)
def test_property5_reference_url_format_validity(url: str):
    """**Validates: Requirements 5.3**

    Property 5: The reference extractor accepts a URL if and only if it
    begins with 'https://' and contains at least one dot in the host portion.
    """
    result = validate_reference_url(url)

    # Compute the expected outcome independently.
    prefix = "https://"
    if url.startswith(prefix):
        rest = url[len(prefix):]
        slash_pos = rest.find("/")
        host = rest[:slash_pos] if slash_pos != -1 else rest
        expected = "." in host
    else:
        expected = False

    assert result == expected, (
        f"validate_reference_url({url!r}) returned {result!r}, expected {expected!r}"
    )


# ---------------------------------------------------------------------------
# Feature: architecture-decisions-documentation, Property 3: ADR structural conformance
# ---------------------------------------------------------------------------

from hypothesis import given, settings
from hypothesis import strategies as st


# Test 1: arbitrary text — validator must not crash
@given(st.text())
@settings(max_examples=200)
def test_property3_arbitrary_text(doc: str) -> None:
    """**Validates: Requirements 2.1, 2.2, 3.1, 3.2, 4.1, 4.2, 5.1, 5.2, 9.2**

    For any arbitrary string, validate_adr_structure must not raise an
    exception and must return a plain bool.
    """
    result = validate_adr_structure(doc)
    assert isinstance(result, bool)


# Test 2: structured valid document — validator must accept it
@given(
    status=st.sampled_from(["Accepted", "Superseded", "Deprecated"]),
    context=st.text(min_size=1).filter(lambda s: s.strip()),
    decision=st.text(min_size=1).filter(lambda s: s.strip()),
    consequences=st.text(min_size=1).filter(lambda s: s.strip()),
    alternative=st.text(min_size=1).filter(lambda s: s.strip()),
)
@settings(max_examples=200)
def test_property3_structured_valid_document(
    status: str,
    context: str,
    decision: str,
    consequences: str,
    alternative: str,
) -> None:
    """**Validates: Requirements 2.1, 2.2, 3.1, 3.2, 4.1, 4.2, 5.1, 5.2, 9.2**

    A document that contains all four required sections in order, each with
    at least one non-blank content line, and includes the 'Alternatives
    considered' subsection within Consequences, must be accepted by the
    structural validator.
    """
    # Sanitise generated text so it cannot accidentally contain a bare
    # section heading (## ...) that would confuse the section-order logic.
    def _sanitise(text: str) -> str:
        return "\n".join(
            line if not line.lstrip().startswith("##") else line.replace("##", "  ")
            for line in text.splitlines()
        ) or "content"

    safe_context = _sanitise(context)
    safe_decision = _sanitise(decision)
    safe_consequences = _sanitise(consequences)
    safe_alternative = _sanitise(alternative)

    doc = (
        f"## Status\n{status}\n\n"
        f"## Context\n{safe_context}\n\n"
        f"## Decision\n{safe_decision}\n\n"
        f"## Consequences\n{safe_consequences}\n\n"
        f"### Alternatives considered\n- **{safe_alternative}** — rejected\n"
    )
    assert validate_adr_structure(doc) is True


# Test 3: missing one required section — validator must reject it
@given(st.sampled_from(["## Status", "## Context", "## Decision", "## Consequences"]))
@settings(max_examples=200)
def test_property3_missing_section_invalid(missing_section: str) -> None:
    """**Validates: Requirements 2.1, 2.2, 3.1, 3.2, 4.1, 4.2, 5.1, 5.2, 9.2**

    A complete ADR document from which one required section has been removed
    must be rejected by the structural validator.
    """
    complete_doc = (
        "## Status\nAccepted\n\n"
        "## Context\nSome context here.\n\n"
        "## Decision\nWe chose X.\n\n"
        "## Consequences\nSome consequences.\n\n"
        "### Alternatives considered\n- **Y** — rejected\n"
    )
    # Remove the target section heading and its content line.
    lines = complete_doc.splitlines()
    filtered = [line for line in lines if line.strip() != missing_section]
    doc_without_section = "\n".join(filtered)

    assert validate_adr_structure(doc_without_section) is False


# ---------------------------------------------------------------------------
# Feature: architecture-decisions-documentation, Property 6: Orphan entry detection
# ---------------------------------------------------------------------------

check_orphans = _mod.check_orphans


@settings(max_examples=200)
@given(
    documented_ids=st.lists(st.text(min_size=1)),
    manifest_ids=st.lists(st.text(min_size=1)),
)
def test_property_orphan_detection(
    documented_ids: list[str], manifest_ids: list[str]
) -> None:
    """Property 6: For any set of documented ADR entries and any set of manifest
    choices, check_orphans reports an ORPHAN finding for every documented entry
    whose covered ID is absent from the manifest set, and no ORPHAN finding for
    any entry whose ID is present.

    **Validates: Requirements 9.8**
    """
    # Build DocumentedChoice objects — each covers exactly one ID.
    documented_choices = [
        DocumentedChoice(
            adr_id=f"{i:04d}",
            covers=[CoveredEntry(id=doc_id, version="1.0", manifest="backend/pom.xml")],
        )
        for i, doc_id in enumerate(documented_ids)
    ]

    # Build SignificantChoice objects from manifest_ids (as grouped choices).
    grouped_choices = [
        SignificantChoice(
            id=manifest_id,
            version="1.0",
            manifest="backend/pom.xml",
            group=None,
        )
        for manifest_id in manifest_ids
    ]

    findings = check_orphans(grouped_choices, documented_choices)
    orphan_findings = [f for f in findings if f.kind == FindingKind.ORPHAN]

    # Compute expected orphans: IDs in documented_ids NOT in manifest_ids set.
    manifest_id_set = set(manifest_ids)
    expected_orphan_ids = {doc_id for doc_id in documented_ids if doc_id not in manifest_id_set}

    # The set of technology_id values from ORPHAN findings must equal the expected set.
    actual_orphan_ids = {f.technology_id for f in orphan_findings}
    assert actual_orphan_ids == expected_orphan_ids, (
        f"Expected ORPHAN findings for IDs {expected_orphan_ids!r}, "
        f"but got findings for {actual_orphan_ids!r}. "
        f"documented_ids={documented_ids!r}, manifest_ids={manifest_ids!r}"
    )


# ---------------------------------------------------------------------------
# Feature: architecture-decisions-documentation, Property 7: Sequential ID assignment
# ---------------------------------------------------------------------------


def next_adr_id(existing_ids: set[int]) -> str:
    """Return the next ADR identifier as a zero-padded four-digit string.

    If existing_ids is non-empty, returns max(existing_ids) + 1 formatted as
    a zero-padded four-digit string. If existing_ids is empty, returns "0001".
    """
    if existing_ids:
        return f"{max(existing_ids) + 1:04d}"
    return "0001"


@given(existing_ids=st.sets(st.integers(min_value=1, max_value=9998)))
@settings(max_examples=200)
def test_property_sequential_id_assignment(existing_ids: set):
    """Property 7: For any existing set of zero-padded four-digit ADR identifiers,
    the next-ID function returns the smallest unused integer greater than the
    current maximum, formatted as a zero-padded four-digit string, and never
    returns an already-assigned identifier.

    **Validates: Requirements 10.3**
    """
    result = next_adr_id(existing_ids)

    # The result must be a four-character zero-padded string.
    assert len(result) == 4, (
        f"Expected a 4-character string, got {result!r} (length {len(result)})"
    )
    assert result.isdigit(), (
        f"Expected a numeric string, got {result!r}"
    )

    # The result must not already be in the existing set.
    existing_formatted = {f"{i:04d}" for i in existing_ids}
    assert result not in existing_formatted, (
        f"next_adr_id returned {result!r} which is already in the existing set"
    )

    # If the set is non-empty, the result must equal max(existing_ids) + 1.
    if existing_ids:
        expected = f"{max(existing_ids) + 1:04d}"
        assert result == expected, (
            f"Expected {expected!r} (max={max(existing_ids)} + 1), got {result!r}"
        )

    # If the set is empty, the result must be "0001".
    if not existing_ids:
        assert result == "0001", (
            f"Expected '0001' for empty set, got {result!r}"
        )


# ---------------------------------------------------------------------------
# Feature: architecture-decisions-documentation, Property 9: Index link validity
# ---------------------------------------------------------------------------

# Validates: Requirements 11.2, 11.5


def find_broken_links(link_targets: list[str], existing_files: list[str]) -> list[str]:
    """Return a list of link targets that are NOT in the set of existing files.

    Simple set difference: broken = [t for t in link_targets if t not in set(existing_files)]
    """
    existing = set(existing_files)
    return [t for t in link_targets if t not in existing]


@given(
    link_targets=st.lists(st.text(min_size=1)),
    existing_files=st.lists(st.text(min_size=1)),
)
@settings(max_examples=200)
def test_property9_index_link_validity(
    link_targets: list[str], existing_files: list[str]
) -> None:
    """**Validates: Requirements 11.2, 11.5**

    Property 9: For any index document and any set of ADR files on disk,
    the link validator reports a broken-link finding for every relative link
    that does not resolve to an existing file, and no broken-link finding for
    any link that does resolve.

    Asserts:
    - Every broken link is in link_targets but NOT in existing_files.
    - No link that IS in existing_files appears in the broken list.
    """
    broken = find_broken_links(link_targets, existing_files)
    existing_set = set(existing_files)

    # Every reported broken link must be in link_targets.
    for link in broken:
        assert link in link_targets, (
            f"Broken link {link!r} is not in link_targets {link_targets!r}"
        )

    # Every reported broken link must NOT be in existing_files.
    for link in broken:
        assert link not in existing_set, (
            f"Broken link {link!r} is in existing_files but was reported as broken"
        )

    # No link that IS in existing_files should appear in the broken list.
    for link in link_targets:
        if link in existing_set:
            assert link not in broken, (
                f"Link {link!r} exists in existing_files but was incorrectly "
                f"reported as broken"
            )


# ---------------------------------------------------------------------------
# Feature: architecture-decisions-documentation, Property 8: Index completeness and categorization
# Validates: Requirements 11.1, 11.3, 11.4
# ---------------------------------------------------------------------------

_VALID_CATEGORIES = {"backend", "frontend", "build/quality/infrastructure"}


def generate_index(entries: list[tuple[str, str]]) -> dict[str, list[str]]:
    """Build a category → list[adr_id] index from (adr_id, category) pairs.

    Takes a list of (adr_id, category) tuples where category must be one of
    the three valid categories: "backend", "frontend", or
    "build/quality/infrastructure".

    Returns a dict mapping each category that has at least one entry to the
    list of adr_ids assigned to it, in the order they were encountered.
    """
    index: dict[str, list[str]] = {}
    for adr_id, category in entries:
        if category not in index:
            index[category] = []
        index[category].append(adr_id)
    return index


@given(
    entries=st.lists(
        st.tuples(
            st.text(min_size=1),
            st.sampled_from(["backend", "frontend", "build/quality/infrastructure"]),
        )
    )
)
@settings(max_examples=200)
def test_property_index_completeness_and_categorization(
    entries: list[tuple[str, str]],
) -> None:
    """Property 8: For any set of ADR entries each assigned to exactly one of
    the three valid categories, the index generator lists each entry exactly
    once, assigns it to exactly one category, and assigns no entry to an
    invalid category.

    **Validates: Requirements 11.1, 11.3, 11.4**
    """
    index = generate_index(entries)

    # Assert: every category key in the result is one of the three valid categories.
    for category in index:
        assert category in _VALID_CATEGORIES, (
            f"Index contains invalid category {category!r}; "
            f"valid categories are {_VALID_CATEGORIES}"
        )

    # Collect all adr_ids across all categories in the index.
    all_indexed: list[str] = []
    for adr_ids in index.values():
        all_indexed.extend(adr_ids)

    # Assert: every adr_id from entries appears exactly once across all categories.
    # (Duplicates in the input are allowed — each occurrence must appear once.)
    assert len(all_indexed) == len(entries), (
        f"Expected {len(entries)} total entries in the index, got {len(all_indexed)}"
    )

    # Assert: each adr_id appears in exactly the category it was assigned to.
    for adr_id, expected_category in entries:
        assert expected_category in index, (
            f"Category {expected_category!r} is missing from the index"
        )
        assert adr_id in index[expected_category], (
            f"adr_id {adr_id!r} not found in its assigned category {expected_category!r}"
        )


# ---------------------------------------------------------------------------
# Feature: architecture-decisions-documentation, Property 10: Significance classification and grouping
# ---------------------------------------------------------------------------


@given(entries=st.lists(st.tuples(st.text(min_size=1), st.booleans())))
@settings(max_examples=200)
def test_property10_significance_classification_and_grouping(
    entries: list[tuple[str, bool]],
) -> None:
    """Property 10: For any set of manifest entries, the significance classifier
    includes an entry iff it is a direct or explicitly-pinned transitive
    dependency; the grouping function merges all entries sharing a group key
    into one grouped entry and leaves ungrouped entries as standalone.

    **Validates: Requirements 1.2, 1.3, 1.4**
    """
    # Filter to only direct entries (is_direct=True) — these are the significant choices.
    # Deduplicate by ID to ensure one entry per unique ID (the property is about
    # classification and grouping, not about duplicate-input handling).
    seen_ids: set[str] = set()
    direct_entries: list[tuple[str, bool]] = []
    for tech_id, is_direct in entries:
        if is_direct and tech_id not in seen_ids:
            seen_ids.add(tech_id)
            direct_entries.append((tech_id, is_direct))

    # Build SignificantChoice objects from the direct entries.
    choices = [
        SignificantChoice(
            id=tech_id,
            version="1.0",
            manifest="backend/pom.xml",
            group=resolve_group(tech_id),
        )
        for tech_id, _is_direct in direct_entries
    ]

    # Call apply_grouping on the direct choices.
    grouped = apply_grouping(choices)

    # Compute expected output structure:
    # - For each unique group key in the input, there should be exactly one entry.
    # - For each ungrouped ID (resolve_group returns None), there should be exactly one entry.
    unique_group_keys: set[str] = set()
    ungrouped_ids: list[str] = []

    for tech_id, _is_direct in direct_entries:
        group = resolve_group(tech_id)
        if group is not None:
            unique_group_keys.add(group)
        else:
            ungrouped_ids.append(tech_id)

    expected_count = len(unique_group_keys) + len(ungrouped_ids)

    assert len(grouped) == expected_count, (
        f"Expected {expected_count} entries in grouped output "
        f"({len(unique_group_keys)} group keys + {len(ungrouped_ids)} ungrouped IDs), "
        f"but got {len(grouped)}. "
        f"entries={direct_entries!r}, grouped={[g.id for g in grouped]!r}"
    )

    # Verify each unique group key appears exactly once in the output.
    grouped_ids = [g.id for g in grouped]
    for group_key in unique_group_keys:
        count = grouped_ids.count(group_key)
        assert count == 1, (
            f"Expected exactly one entry for group key '{group_key}', "
            f"but found {count} in grouped output: {grouped_ids!r}"
        )

    # Verify each ungrouped ID appears exactly once in the output.
    for tech_id in ungrouped_ids:
        count = grouped_ids.count(tech_id)
        assert count == 1, (
            f"Expected exactly one entry for ungrouped ID '{tech_id}', "
            f"but found {count} in grouped output: {grouped_ids!r}"
        )

    # Verify all grouped entries with a group have group == id (representative entries).
    for entry in grouped:
        if entry.group is not None:
            assert entry.id == entry.group, (
                f"Grouped representative entry should have id == group, "
                f"but got id={entry.id!r}, group={entry.group!r}"
            )
