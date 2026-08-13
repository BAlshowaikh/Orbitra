# Orbitra Frontend — Architecture Reference

One place to read *why* `orbitra-fe`'s structure and design decisions look the way they do — consolidated
from `CLAUDE.md`'s "Architecture (orbitra-fe)" section, cross-checked against the actual code (not just
copied) as of this writing. `CLAUDE.md` keeps its own copy of this same content for Claude Code's guidance;
this file is the human-facing reference. If the two drift, treat the code itself as the tiebreaker, not
either doc. Companion to `docs/architecture&logic.md` (the backend equivalent) and `docs/phase-tracker.md`
(build progress, not design rationale — tracked there, not duplicated here).

---

## Stack and conventions at a glance

Angular 21, standalone components (no NgModules), Angular Material + Tailwind CSS, signals for state (no
NgRx), Reactive Forms, Vitest for unit tests, Playwright for e2e (deferred to its own phase). Zoneless by
default — no `zone.js` dependency. Talks to exactly one backend surface: `api-gateway`
(`http://localhost:8080` in dev), never an individual service directly.

---

## Folder structure

Not package-by-layer like the backend services — Angular's own idiomatic split instead:

- **`core/`** — app-wide singletons: services, guards, interceptors, models. Anything that exists exactly
  once for the whole app.
- **`layout/`** — used-once shell pieces: navbar, footer. Distinct from `shared/` since these aren't
  reused across features, they compose the app shell itself.
- **`shared/`** — reusable-but-not-singleton building blocks, split further by *kind of thing*:
  `components/`, `directives/`, `pipes/`.
- **`features/<name>/`** — one folder per product area, itself split by *role within that feature*:
  `pages/` (routable screens — what a route actually points at), `components/` (reusable pieces used by
  those pages, not routed to directly), `data-access/` (the feature's own API-calling service),
  `state/` (only if the feature genuinely needs state shared across sibling components — not created
  preemptively), `<name>.routes.ts` (the feature's own routes, lazy-loaded from the main router).

`AuthService` is a deliberate exception to the `data-access/` pattern — it stays in `core/auth/`, combining
its own API calls (`login()`/`register()`) with the session state they update, rather than splitting into a
separate `AuthApiService`. The reasoning: unlike Hotel/Flight/Booking API calls (consumed by many different,
unrelated screens), nothing else in the app has any use for a raw login response except `AuthService`
itself — splitting it would add a file with little real decoupling benefit. Hotel/Flight/Booking, once
built, get their own `features/<name>/data-access/*ApiService`, since their data does serve many different
consumers.

---

## Styling — Angular Material + Tailwind together

Material handles complex interactive components (pickers, tables, dialogs, form fields); Tailwind handles
layout/spacing/utility styling everywhere else.

Tailwind's **Preflight** (its built-in CSS reset) is deliberately disabled — left in place, it would strip
the browser-default base styles Material's own components are designed on top of, breaking their spacing
and appearance. Disabling it means writing the three-import form in `styles.css` (theme + utilities layers)
instead of the single default `@import 'tailwindcss'`, omitting the `preflight.css` layer.

Material's own theme lives in a **separate file**, `material-theme.scss`, rather than merged into
`styles.css`. This wasn't a stylistic choice so much as a discovered constraint: Material's theming API
(`mat.theme()`) still requires Sass (`@use`/`@include` mixins), even though the project's own default
stylesheet choice — picked at `ng new` time — is plain CSS/Tailwind, not Sass. The `sass` package had to
be added as its own dev dependency specifically to
compile this one file; the gap was only caught by checking `node_modules` directly rather than trusting the
Material schematic had wired everything correctly on its own.

**Brand palette and type** (decided 2026-08-11, after exploring options in an interactive artifact):
"Dusk Departure" direction — primary `#6E3FA3` (violet), tertiary `#2F5D8A` (cobalt), generated into a
real Material 3 tonal palette via `ng generate @angular/material:m3-theme` (not hand-picked shades —
this runs Google's actual Material Color Utilities algorithm, the same one behind Android's Material You).
**Sora** (via `@fontsource/sora`) is the display face, applied only to `h1`/`h2`/`h3` — a deliberate
"restraint" choice, Material's own components (buttons, form fields) stay on Roboto for legibility rather
than every piece of UI text switching fonts. A role-based accent-color scheme (different color per
Traveler/Partner/Admin) was explored and explicitly dropped in favor of one unified theme.

**Single source of truth for neutrals**: page background/surface/border/muted-text colors all come from
Material's own generated system tokens (`--mat-sys-surface`, `--mat-sys-on-surface`,
`--mat-sys-outline-variant`, `--mat-sys-on-surface-variant`) rather than maintaining a second, hand-picked
neutral palette in parallel — Material's M3 algorithm already generates neutrals harmonized with the
primary/tertiary colors, so a separate hand-authored set would risk visibly clashing with it instead of
adding anything.

These are registered once as real Tailwind color tokens, in `styles.css`'s `@theme` block
(`--color-surface: var(--mat-sys-surface);`, etc.), rather than every component repeating
`var(--mat-sys-...)` inline via Tailwind's arbitrary-value syntax — that pattern was tried first and
correctly called out as unmaintainable (a system-wide concern shouldn't need repeating on every element).
Components now just use plain Tailwind classes: `bg-surface`, `border-line`, `text-muted`. This aliasing is
safe specifically because the Tailwind token name and the Material variable name are different strings —
the risk flagged earlier (a `@theme` token colliding with a same-named plain CSS custom property) only
applies when both sides share one name, which isn't the case here. `font-display`
(`--font-display: 'Sora', sans-serif;`) is registered the same way, for non-heading elements (like the
navbar wordmark) that still need the display face without hardcoding an inline style.

---

## Session state — signals, not NgRx

`AuthService` is the reference pattern for state in this app: a single **private**, mutable
`signal<string | null>` holds the raw JWT, seeded from and kept in sync with `localStorage`. Everything
else — `currentUser`, `isExpired`, `isAuthenticated`, and each role check — is exposed as a **read-only
`computed()`** value derived from that one signal. Nothing outside `AuthService` can mutate session state
directly; every consumer (guards, interceptors, the navbar) can only read it.

**No refresh tokens** — a deliberate simplicity call, not an oversight. The existing 1-hour access token
is accepted as-is; a session simply expires an hour after login, regardless of activity (JWTs carry no
server-side session to track idleness, so there's no such thing as an "idle timeout" here, only a fixed
expiry). Adding a refresh-token/rotation/cookie subsystem to the backend purely to smooth over this was
considered and explicitly rejected. The JWT itself lives in plain `localStorage` — matching "no refresh
token," since there's no companion cookie-based flow that would call for a different storage strategy.

---

## HTTP layer — functional interceptors

Registered via `provideHttpClient(withInterceptors([authInterceptor, errorInterceptor]))` — functional
interceptors (plain functions), not the older class-based `HttpInterceptor` pattern.

**`authInterceptor`** attaches the JWT as a `Bearer` token to every outgoing request, unconditionally,
whenever one exists. It doesn't need its own copy of the Gateway's public-route allowlist — the Gateway
already ignores the header on public routes regardless of whether it's present, so there's nothing to gain
by duplicating that logic on the frontend.

**`errorInterceptor`** does two things on any failed request:
1. Surfaces the backend's actual error message via `NotificationService` (a thin `MatSnackBar` wrapper) —
   specifically `error.error.message` (the real backend-authored message), never Angular's own generic
   `error.message` (always some variant of "Http failure response for...").
2. On a `401` specifically, forces `AuthService.logout()` and redirects to `/login`. This is safe to do
   unconditionally because `401` has exactly one meaning in this system, confirmed against `auth-service`'s
   own exception handling: an invalid/expired JWT. Bad login credentials throw `IllegalArgumentException`,
   which maps to `400`, not `401` — so a wrong-password login attempt can never be mistaken for "your
   session expired" and trigger this path.

After handling both, the interceptor **re-throws** the same error rather than swallowing it — components
still need their own error handlers for things the interceptor can't know about, like resetting a local
`isSubmitting` flag so a loading spinner doesn't spin forever, or screen-specific reactions (highlighting a
form field, refreshing stale data).

---

## Route guards — UX-only, not a security boundary

Every guard is a functional `CanActivateFn` (or a factory returning one, for parameterized checks). Real
authorization is already fully enforced server-side — the Gateway's defense-in-depth JWT check plus each
service's own role/ownership checks — so a guard bypass here is a bad UX path, not a vulnerability.

- **`authGuard`** — must be logged in (and not expired). Redirects to `/login` if not.
- **`roleGuard(role)`** / **`partnerTypeGuard(type)`** — factories, since the check is parameterized per
  route (`canActivate: [roleGuard('ADMIN')]`). Not authenticated at all → `/login`. Authenticated but the
  wrong role/type → a friendly `/error?status=403&message=...` page instead — redirecting to `/login`
  wouldn't make sense there, since the user is already logged in and logging in again changes nothing.

**Two distinct "something went wrong" surfaces, not one**: the error interceptor's toast is for "you tried
to *do* something and it failed" — you're still on a valid screen, nothing forces you off it. The `/error`
page (query-param driven, not yet built) is for "you can't even *be* on this screen at all" — a
guard-blocked navigation, or a `404` — where a full-page takeover makes sense since there's no legitimate
"current screen" left to toast over.

---

## Environments hold no secrets

`environment.ts` (production) / `environment.development.ts` (dev) carry exactly one value so far:
`apiBaseUrl`, the Gateway's URL. Unlike backend `.env` files — read server-side only, gitignored, safe to
hold real secrets — Angular's environment files get compiled directly into the JavaScript bundle sent to
every visitor's browser, and are typically committed to git rather than gitignored. Nothing genuinely
secret (API keys meant to stay private, credentials) can ever live here; only values that are fine for
literally anyone to see, since anyone can already inspect them via browser dev tools regardless.

---

## Testing

**Vitest**, not Jasmine/Karma — this surprised an earlier planning assumption made before the project
existed; confirmed as Angular 21's actual default via the real `ng new` scaffold, not assumed from older
Angular knowledge. **Playwright** is deferred to its own dedicated e2e phase at the end of the frontend
build, rather than built alongside every individual feature.

---

## Deferred / not yet built

Tracked in detail in `docs/phase-tracker.md`'s Frontend section — noted here only so this doc doesn't read
as though these already exist:

- **`/login`, `/register`, `/error`, and every feature route** — guards and the navbar already reference
  these paths, but none exist as real routed pages yet (Phase 3 onward).
- **Partner/Admin `data-access/` API services** (Hotel/Flight/Booking) — not built until those features are.
- **Dockerization** (`orbitra-fe/Dockerfile`, `docker-compose.yml` entry) — last phase, matching how every
  backend service was dockerized only after its own core build was verified.
