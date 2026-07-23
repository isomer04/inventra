# Privacy Notes — Inventra

_This is a portfolio/demo project released under the MIT License. The notes below describe how the application handles personal data if you choose to self-host it._

---

## Data collected

| Category | Data | Purpose |
|----------|------|---------|
| User accounts | Email, first name, last name, role | Authentication and access control |
| Customer records | Name, email, phone, address, notes | Order management |
| Audit log | Actor email (anonymised after 365 days), action type | Security accountability |
| Session tokens | JWT (in-memory), refresh token (sessionStorage) | Authentication |

No payment data, government IDs, biometrics, or special-category data is collected.

---

## Data retention

| Data | Retention |
|------|-----------|
| User accounts | Until deleted by an ADMIN |
| Customer records | Until deleted (blocked if orders exist) |
| Audit log actor_email | Anonymised after 365 days by a nightly job |
| Expired refresh tokens | Purged every 6 hours |

---

## Key privacy design decisions

- Tokens stored in `sessionStorage` — cleared on tab close, never written to cookies.
- No third-party analytics, tracking, or advertising scripts.
- GDPR right-to-erasure implemented: `DELETE /api/v1/tenant` pseudonymises all user/customer PII and suspends the tenant.
- Passwords hashed with BCrypt; refresh tokens stored as SHA-256 hashes.

---

For security vulnerability reports, see [SECURITY.md](SECURITY.md).
