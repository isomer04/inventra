# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 1.0.x (pre-release) | ✅ Active development |

## Reporting a Vulnerability

**Please do not open a public GitHub issue for security vulnerabilities.**

Report security issues privately via one of these channels:

1. **GitHub Private Vulnerability Reporting** (preferred):
   Go to [Security → Report a vulnerability](../../security/advisories/new)
   on this repository. Your report will be visible only to maintainers.

2. **Email:** isomer008@gmail.com
   Use the subject line: `[SECURITY] Inventra — <brief description>`

### What to include

- A clear description of the vulnerability
- Steps to reproduce (curl commands, test cases, screenshots)
- The potential impact and affected versions
- Any suggested remediation (optional but appreciated)

### Response timeline

| Stage | Target |
|-------|--------|
| Acknowledgement | Within 48 hours |
| Initial assessment | Within 5 business days |
| Fix or mitigation | Within 30 days for Critical/High |
| Public disclosure | Coordinated with reporter after fix is deployed |

### Scope

In scope: authentication, authorisation, multi-tenant isolation, data
exposure, injection vulnerabilities, session management.

Out of scope: denial-of-service via excessive legitimate traffic,
theoretical attacks without a practical proof of concept,
issues in dependencies that have an available upstream patch.

## Security Hardening Notes

See the Security section of [`README.md`](README.md) for a summary of
the security controls implemented in Inventra.
