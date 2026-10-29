# Browser authentication storage

Access tokens are held in an in-memory store subscribed to by React's
`AuthProvider` (`useSyncExternalStore`). No token or user session is written to
localStorage/sessionStorage. The old `alloca.session` entry is removed on startup;
existing sessions from the old client must sign in again.

## HTTP contract

- Login returns access token and user JSON; the refresh token is only in
  `Set-Cookie: alloca_refresh=...; Path=/api/v1/auth; HttpOnly; Secure; SameSite=Lax`.
  Max-Age follows the refresh token lifetime (default 7 days).
- Refresh POST reads that cookie, with no body required. Missing/invalid/expired
  cookies return 401 and are expired. Active-user and credentials-version checks
  still run in AuthService. Refresh does not extend the cookie's original expiry.
- Logout POST expires the cookie using the same name, path and flags, even with
  no access token. The client clears memory and shows a retry if the cookie could
  not be cleared. Logout does not revoke copies of an otherwise valid JWT.
- All three POSTs require `X-Requested-With: Alloca` and an exact trusted `Origin`.
  Missing, null or untrusted origins receive 403. Browser fetch supplies Origin;
  CLI/API-test clients must provide it explicitly.
- CORS allows only `CORS_ALLOWED_ORIGINS`, credentials and specified headers.
  The non-simple header plus Origin check protects these cookie-based operations
  against CSRF, including login CSRF. Other API endpoints require explicit Bearer
  authentication, never the refresh cookie. Do not add cookie-authenticated
  mutations outside this guard without equivalent CSRF protection.

## Local and deployment configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `AUTH_COOKIE_SECURE` | `true` | HTTPS cookies; set `false` only for local HTTP development |
| `AUTH_COOKIE_SAME_SITE` | `Lax` | `Lax`/`Strict` for same-site deployments; `None` for cross-site HTTPS |
| `CORS_ALLOWED_ORIGINS` | localhost ports 5173, 3000, 8080 | Comma-separated exact trusted frontend origins; no wildcards |

Set `AUTH_COOKIE_SECURE=false` in your local backend environment if running HTTP.
Do not set it to false in production. `SameSite=None` with Secure disabled fails
startup rather than silently producing an unusable cookie.

The frontend uses relative `/api/...` requests and `credentials: include`.
Vite already proxies `/api` to localhost:8080. For Vercel/Render, configure a
same-origin reverse proxy for `/api` and preserve Origin and Set-Cookie headers;
allow the actual frontend origin in the backend. Cookies are host-only (no
Domain attribute), so they work through the proxy. Set Secure=true in production.

If a future deployment calls Render directly from a different site, adapt the
frontend API base URL and configure SameSite=None + Secure with exact CORS origins.
Third-party-cookie blocking can still prevent that arrangement; a same-origin
proxy avoids that dependency. HttpOnly prevents JS from reading the refresh
token, but does not make an XSS-compromised page safe to use.

## Lifecycle and verification

On reload, startup calls refresh and then /auth/me; routing waits until complete.
No cookie means signed out. Network/server errors show Retry instead of silently
pretending the session expired. Concurrent 401s share one refresh. A session
generation counter prevents late refresh/login responses restoring an old account
after logout or a new login.

- Backend: `./backend/mvnw -f backend/pom.xml test` (cookie flags, CSRF, CORS and regressions).
- Frontend: Node 22.18+ or 24+, `npm test` in frontend (native Node TypeScript tests;
  no browser launched), then `npm run build` and `npm run lint`.
- With Docker running: `./backend/mvnw -f backend/pom.xml verify` for PostgreSQL AuthIT.
- Manual browser check: sign in; verify no tokens in Web Storage and an HttpOnly
  cookie with expected flags; reload a protected URL; sign out; reload and confirm
  signed-out state. Confirm expired access refreshes without a redirect loop.
