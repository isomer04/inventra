+++
adr = "0040"
# This ADR documents an architectural/security decision (client-side token storage)
# rather than a specific technology choice, so it declares no manifest coverage.
+++

# ADR-0040: Refresh Token Stored in `sessionStorage` (Not an `HttpOnly` Cookie)

## Status
Accepted (with a defined migration trigger — see Consequences)

## Context
The frontend receives two credentials from `POST /api/v1/auth/login`:

- an **access token** — JWT, 15-minute lifetime, sent as `Authorization: Bearer`;
- a **refresh token** — opaque UUID, 7-day lifetime, exchanged at `/api/v1/auth/refresh`.

Both are currently kept in `sessionStorage` (`AuthService.storeTokens`). Anything readable
from JavaScript is readable by an XSS payload, and of the two the refresh token is the more
valuable: it is long-lived and can be exchanged for a fresh access token repeatedly.

Three options were considered:

1. **Both tokens in `localStorage`** — survives tab close and browser restart; readable by
   any script on the origin; the longest-lived exposure.
2. **Both tokens in `sessionStorage`** (current) — scoped to one tab, cleared when the tab
   closes, not shared across tabs; still readable by any script on the origin.
3. **Refresh token in an `HttpOnly; Secure; SameSite=Strict` cookie scoped to
   `/api/v1/auth/refresh`, access token in memory only** — the refresh token becomes
   unreadable from JavaScript, so XSS can no longer exfiltrate a long-lived credential.

Option 3 is the industry-standard pattern and is strictly stronger. It is also a
cross-cutting change: it requires a server-side `Set-Cookie` on login/refresh, CSRF
protection on the refresh endpoint (a cookie is sent automatically, so `SameSite` alone is
not the whole answer), cookie-domain configuration that works both under the nginx reverse
proxy and against the Angular dev server, and a frontend that survives a page reload with
no access token in memory.

## Decision
Keep both tokens in `sessionStorage` for now, and record this as a **known, accepted
weakness** rather than an oversight.

Supporting controls that make the residual risk acceptable at this stage:

- A CSP with `script-src 'self'` and no `unsafe-inline`, which is the primary defence —
  it targets the XSS that this threat model depends on.
- `sessionStorage` over `localStorage`: per-tab and cleared on tab close, so the exposure
  window is a single session rather than indefinite.
- Refresh tokens are hashed (SHA-256) at rest, single-use, and rotated on every refresh.
- Reuse of an already-consumed refresh token is treated as a theft signal and revokes the
  entire token family (`AuthService.refresh` → `revokeTokenFamily`), so a stolen token
  yields at most one rotation before every session for that user is invalidated.

## Consequences
- **Gained:** No cookie/CSRF/proxy-domain work; login state is simple to reason about and
  identical in dev and prod.
- **Risk accepted:** A successful XSS can read the 7-day refresh token and mint access
  tokens until the family is revoked or the token expires. The CSP is the control standing
  between an injected script and this credential.
- **Bounded by:** Family revocation on reuse detection, so an attacker refreshing in
  parallel with the legitimate user trips the alarm and loses access.
- **Migration trigger:** Move to option 3 before this application handles real customer
  data or is exposed to untrusted users. The decision above is defensible for a portfolio
  deployment; it is not the right answer for a production tenant.
- **Reference:** OWASP Cheat Sheet — "HTML5 Security: Local Storage"; OAuth 2.0 for
  Browser-Based Applications (draft-ietf-oauth-browser-based-apps), which recommends
  keeping refresh tokens out of JavaScript-readable storage.
