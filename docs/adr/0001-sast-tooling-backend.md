+++
adr = "0001"

[[covers]]
id = "spotbugs-findsecbugs"
version = "4.9.8.3"
manifest = "backend/pom.xml"
+++

# ADR-0001: Add SpotBugs + Find Security Bugs to Backend Build

## Status
Accepted

## Context
The backend Maven build has OWASP Dependency-Check (SCA) but no SAST plugin. A multi-tenant Spring Boot application handling authentication, RBAC, and financial-adjacent data should have at minimum source-level static analysis for injection sinks, null-dereference paths, and concurrency bugs. The Java compiler and Spring's own checks do not cover these.

Find Security Bugs (a SpotBugs plugin) specifically targets Spring Security, JNDI injection, XSS, SQL injection, and other OWASP Top 10 patterns in Java code.

## Decision
Add `spotbugs-maven-plugin` (4.8.6.4) with `findsecbugs-plugin` (1.13.0) bound to the `verify` phase. Threshold: Low (report everything; fail only on High and above in CI). Initial baseline suppressions are acceptable where the finding is a confirmed false positive, but every suppression requires a dated comment with justification.

## Consequences
- **Gained:** Automated detection of injection sinks, deserialization issues, Spring Security misuse, and other CWE-relevant patterns on every `mvn verify`.
- **Cost:** Build time increases by ~30–60 seconds on first run (index build). Subsequent runs are faster.
- **Initial noise:** First run will likely surface some warnings that need triage. These become the suppressions baseline documented in `backend/spotbugs-exclude.xml`.
- **Alternatives considered:**
  - PMD (rejected — weaker security rules for Spring, better for style/complexity).
  - SonarQube (rejected — requires separate server infrastructure; overkill for solo project at this stage).
- **Reference:** https://find-sec-bugs.github.io/ · https://spotbugs.github.io/


