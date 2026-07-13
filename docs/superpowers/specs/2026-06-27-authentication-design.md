# Authentication design

## Context

InvestLog currently has no authentication: `FixedCurrentUserProvider` hardcodes a single
dev user looked up by `google_sub = "dev-user"`, and every `/private/*` endpoint is open.
`system.users` already has a `google_sub` column (`NOT NULL UNIQUE`), modeled Google-first
but never wired to a real OAuth flow.

This design adds:
1. Local users (email + password), created via self-registration or by an admin.
2. Mandatory TOTP two-factor authentication for local users.
3. Role-based access (`ADMIN` / `USER`).
4. Admin-approval gating for new accounts (local signups and first-time Google logins).
5. Google OAuth2 login, toggled and configured via environment variables.
6. A login UI ported from the existing Claude Design project (`login.jsx` / the `.auth-*`
   CSS block already present in `InvestLog Dashboard.html`), plus navbar logout/logo
   changes and a new "Usuários locais" admin page.

## Out of scope

- Backup codes for 2FA recovery (admin can force a TOTP reset instead — see below).
- Password reset via email (no email-sending flow for auth; `spring-boot-starter-mail`
  exists in the project but wiring a reset-link flow is not part of this design).
- Multi-tenancy / organizations — this is still a single-household app with multiple
  individual user accounts.

## Data model

All changes are new Liquibase changesets under `db/changelog/changes/**`, **except** one
explicit edit to `14-1050-seed-dev-data.xml` (the user explicitly asked to break the
"never edit migrations" convention for this one file, to rename the seeded dev user to a
real admin account before any real deployment uses it).

New changeset altering `system.users`:
- `google_sub` — drop `NOT NULL` (keep `UNIQUE`); local users have no Google subject.
- `password_hash TEXT` — nullable; null for Google-only accounts.
- `auth_provider TEXT NOT NULL DEFAULT 'GOOGLE'` — `'LOCAL'` or `'GOOGLE'`.
- `role TEXT NOT NULL DEFAULT 'USER'` — `'ADMIN'` or `'USER'`.
- `status TEXT NOT NULL DEFAULT 'PENDING'` — `'PENDING'` or `'APPROVED'`.
- `totp_secret TEXT` — nullable; set on 2FA enrollment.
- `totp_enabled BOOLEAN NOT NULL DEFAULT false`.

Edit to `14-1050-seed-dev-data.xml`:
- `name = 'Administrador'`, `email = 'admin@admin.com'`, `auth_provider = 'LOCAL'`,
  `role = 'ADMIN'`, `status = 'APPROVED'`, `password_hash` = BCrypt hash of the
  `ADMIN_DEFAULT_PASSWORD` env var (default `admin` in `.env.example`).
- `totp_enabled = false` — the seeded admin still goes through mandatory TOTP enrollment
  on first login, same as any other local user.

## Server

### Module layout

New `auth` module (`rest/{controllers,payloads}` + `domain/{services,repositories}`,
matching `profile`/`typelists`):
- `AuthController` — `POST /private/v1/auth/login`, `POST /private/v1/auth/register`,
  `POST /private/v1/auth/logout`, `POST /private/v1/auth/totp/enroll`,
  `POST /private/v1/auth/totp/verify`, `GET /private/v1/auth/config` (public; returns
  `{ googleAuthEnabled: boolean }`), `GET /private/v1/auth/session` (current
  authenticated user + role + status, used by the client router guard).
- `UsersAdminController` — `GET/PATCH/DELETE /private/v1/users` (admin-only): list all
  users (status/role included), approve/reject, change role, delete, reset TOTP
  (`PATCH .../totp-reset` clears `totp_secret`/`totp_enabled`, forcing re-enrollment).

### Security configuration

- `spring-boot-starter-security` + `spring-boot-starter-oauth2-client` added to
  `build.gradle.kts`.
- Session-cookie auth (Spring Security default), not JWT — single deployment, no
  cross-service token requirement.
- `SecurityFilterChain`: `/private/v1/auth/**` public except the admin user-management
  endpoints; everything else under `/private/**` requires an authenticated session with
  `status = APPROVED`.
- Custom `AuthenticationProvider` for local login: verifies BCrypt password, then (if
  `totp_enabled`) requires a valid TOTP code in the same request before issuing a
  session; if `totp_enabled = false`, the response signals "needs enrollment" instead of
  a session.
- `oauth2Login()` for Google, registered only when `GOOGLE_AUTH_ENABLED=true`. Success
  handler upserts by `google_sub` (creating `role=USER, status=PENDING, auth_provider=
  GOOGLE` on first login), then redirects to the SPA — the SPA's session check shows the
  pending-approval screen if `status != APPROVED`.
- `CurrentUserProvider` replaced: reads the authenticated principal from
  `SecurityContextHolder` instead of the fixed dev lookup.
- `GlobalExceptionHandler` gains 401/403 `ProblemDetail` mappings.

### TOTP

`dev.samstevens.totp` library: generates the secret + QR code payload on enroll, verifies
6-digit codes on login. No backup codes (out of scope) — lockout recovery is the admin
TOTP-reset endpoint.

### Environment configuration

`.env` additions (mirrored into `application*.yaml` via `spring.security.oauth2.client.
registration.google.*`):

| Variable | Default | Purpose |
|---|---|---|
| `GOOGLE_AUTH_ENABLED` | `false` | Registers the Google OAuth2 client + shows the button client-side |
| `GOOGLE_CLIENT_ID` | _(empty)_ | From Google Cloud Console OAuth 2.0 Client ID |
| `GOOGLE_CLIENT_SECRET` | _(empty)_ | From Google Cloud Console |
| `ADMIN_DEFAULT_PASSWORD` | `admin` | Password for the seeded admin account — change after first login |

README gets a new section documenting the Google Cloud Console setup (OAuth consent
screen, Web application client, authorized redirect URI
`http(s)://<host>/private/login/oauth2/code/google`).

## Client

- `LoginView.vue` ported from the design's `login.jsx`: brand aside (gradient panel,
  headline, bullet points) + form card with `Entrar`/`Criar conta` segmented tabs,
  email/password fields, "Esqueci a senha" placeholder (no-op — out of scope), submit
  button, divider, "Continuar com Google" button (hidden when `googleAuthEnabled` is
  false per `GET /auth/config`).
- New TOTP enrollment view/modal (not in the design source — built consistent with the
  existing `Modal` component): QR code image + 6-digit confirmation input, shown when
  login responds "needs enrollment".
- New "pending approval" view: shown when the session check returns `status = PENDING`.
- `TheNavbar.vue`: brand-mark icon swapped from the `trending-up` mdi icon to the ported
  `LogoMark` SVG (the design's "ascending ledger" mark); add a logout icon button next to
  the user avatar block (matches the design's `Navbar` in `views.jsx`, which already
  includes an `onLogout` icon button).
- `styles.css` gains the `.auth-*` rule block (already fully written in the design's
  `InvestLog Dashboard.html`) ported verbatim, plus new rules for the local-users admin
  card grid (modeled on the existing `.wallet-grid`/`.wallet-card` pattern, styled to
  match the reference Tabler "Users" screenshot: name, role/status badges, approve/
  reject/delete/reset-2FA actions).
- New `SettingsView` section "Usuários locais", admin-only.
- New `auth` Pinia store: session state, login/logout/register/enroll actions.
- Router: global guard redirects unauthenticated → `/login`; `/settings` nav item and
  route hidden entirely when `role = USER`; delete actions (wallets, holdings, types)
  hidden/disabled for `role = USER`.

## Process / sequencing

Work happens on `feature/authentication`, branched from `main`. Per the user's
instruction, this branch itself gets a PR into `main` once complete, but the
implementation inside it is split into four sequential PRs **targeting
`feature/authentication`** (not `main`) so each is independently reviewable:

1. **Foundation** — schema changes, seed-admin edit, Spring Security skeleton (local
   login/logout, roles, route protection, no 2FA yet), `LoginView` port, navbar
   logout/logo, router guard. End state: the app requires a local login to use anything.
2. **Mandatory 2FA** — TOTP enrollment/verification for local users, both server and the
   enrollment UI.
3. **Self-registration + approval** — signup endpoint, `PENDING`/`APPROVED` gating,
   "Usuários locais" admin page (approve/reject/role/delete/reset-2FA), pending-approval
   client view.
4. **Google OAuth2** — env-driven client registration, success-handler provisioning,
   approval gate reuse, Google button visibility via `/auth/config`, README setup docs.

## Testing

- Server: `BaseIntegrationTest`-style controller tests per new endpoint, covering
  unauthenticated/forbidden/pending/approved paths. A test-only `ADMIN_DEFAULT_PASSWORD`
  and disabled `GOOGLE_AUTH_ENABLED` keep the existing Testcontainers setup deterministic.
- Client: Vitest coverage for the auth store (login/enroll/register state transitions)
  and the router guard.
