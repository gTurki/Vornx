# VORNX — One Card, Connected Access

Backend API for VORNX: one card (and phone) that holds many permits — hotel keys, workplace access,
club memberships, parking, transit, events — plus the wallet, subscriptions and the portals around them.

**Stack:** Java 21 · Spring Boot 4 · Spring Security (JWT + refresh tokens, API keys for readers) ·
Spring Data JPA / Hibernate · Flyway · MySQL 8 / MariaDB · Thymeleaf (emails) · springdoc (Swagger)

## Portals served by this API

| Path | Who | Auth |
|---|---|---|
| `/api/public/**` | anyone (login, sign-up, password reset) | none |
| `/api/admin/**` | VORNX team (admin dashboard) | JWT, `TYPE_ADMIN` |
| `/api/partner/**` | partner staff (hotels, clubs, venues...) | JWT, `TYPE_PARTNER_USER` |
| `/api/customer/**` | card holders (customer app) | JWT, `TYPE_CUSTOMER` |
| `/api/devices/**` | readers and locks | `X-API-Key` header |

## Modules (`src/main/java/com/example/vornx`)

`security` (login, sessions, refresh tokens, passwords) · `platform` (admins) · `partner` · `access`
(sites, access points, permits, **the access decision engine**) · `customer` (customers, cards) · `wallet` ·
`subscription` · `events` · `billing` · `support` · `notification` · `audit` (activity log) · `email` (outbox)

## Run locally

1. Start MySQL/MariaDB (e.g. XAMPP) and create the database `vornx_db`.
2. Create `src/main/resources/application-local.properties` (it is git-ignored):
   ```properties
   spring.datasource.username=root
   spring.datasource.password=
   vornx.jwt.secret=<base64, at least 32 bytes>
   vornx.bootstrap.admin-email=admin@vornx.sa
   vornx.bootstrap.admin-password=<choose one>
   vornx.mail.mode=preview
   vornx.wallet.simulated-top-up-enabled=true
   springdoc.api-docs.enabled=true
   springdoc.swagger-ui.enabled=true
   ```
3. Run `VornxApplication` (or `./mvnw spring-boot:run`). Flyway creates all tables.
4. Swagger UI: http://localhost:8080/swagger-ui.html

## Deploy (Railway)

Built from the `Dockerfile`; health check `/actuator/health` (see `railway.json`).

| Variable | Example / note |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL` | `jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?allowPublicKeyRetrieval=true&useSSL=false` |
| `DB_USERNAME` / `DB_PASSWORD` | `${{MySQL.MYSQLUSER}}` / `${{MySQL.MYSQLPASSWORD}}` |
| `JWT_SECRET` | random, base64, ≥ 32 bytes — **never reuse the local one** |
| `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` | first SUPER_ADMIN (created only if there is none) |
| `CORS_ALLOWED_ORIGINS` | the frontends' URLs, comma-separated |
| `MAIL_MODE` | `brevo` (HTTPS API, recommended on Railway), `smtp`, or `preview` |
| `BREVO_API_KEY`, `MAIL_FROM` | Brevo API key (`xkeysib-...`) and a sender verified in Brevo, e.g. `VORNX <no-reply@vornx.sa>` |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | only for `MAIL_MODE=smtp` |
| `ADMIN_URL`, `PARTNER_URL`, `CUSTOMER_URL` | used in email links |
| `API_DOCS_ENABLED` | `false` in production (default) |

## Admin dashboard (`frontend/admin`)

React 19 + TypeScript + Vite, TanStack Query, React Router. Arabic (RTL) UI from the `index 55` design, light/dark.

```bash
cd frontend/admin
npm install
npm run dev        # http://localhost:5173 (calls the API on http://localhost:8080)
npm run build      # type-check + production build into dist/
```

Railway: a second service from this repo with **Root Directory** `/frontend/admin` and
**Config file** `/frontend/admin/railway.json`; variable `VITE_API_URL=https://<api domain>`.
Served by nginx with a strict Content-Security-Policy. Then set the API's `CORS_ALLOWED_ORIGINS`
and `ADMIN_URL` to the dashboard's domain.

## Partner portal (`frontend/partner`)

Same stack and look as the admin dashboard, from the `hotel.html` design. For partner staff: overview,
live door activity (refreshes every 5 s), permits / digital keys, subscription requests, sites and access
points, plans, events and tickets, contract and invoices, team and device API keys, support, activity log.
Buttons are hidden by role (`PARTNER_ADMIN`, `MANAGER`, `FRONT_DESK`, `VIEWER`); the API enforces the same rules.

```bash
cd frontend/partner
npm install
npm run dev        # http://localhost:5174
```

Pages opened from emails: `/accept-invite?token=…` (new staff), `/reset-password?token=…`, `/billing`.

Railway: a third service, **Root Directory** `/frontend/partner`, **Config file** `/frontend/partner/railway.json`,
variable `VITE_API_URL=https://<api domain>`. Then add its domain to the API's `CORS_ALLOWED_ORIGINS`
(comma-separated) and set `PARTNER_URL` to it, so invite and reset links point to the portal.
