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

## How partners and admins get accounts

- **Admins:** never public sign-up. The first SUPER_ADMIN comes from `BOOTSTRAP_ADMIN_*`. After that, a SUPER_ADMIN
  invites colleagues from "فريق VORNX" (`/api/admin/team`):
  - The invitee gets an email link (3 days, works once) to `/accept-invite` on the admin dashboard, chooses a password and is logged in.
  - A SUPER_ADMIN can change roles, suspend (ends their sessions and cancels a pending invite) and resend an invite.
  - Nobody can change themselves, and the last active SUPER_ADMIN can't be demoted or suspended.
- **Partners apply on the website ("انضم كشريك"):**
  - The form asks for company, CR number, sector, city and a contact. It posts to `POST /api/public/partner-applications`,
    protected by a honeypot field, 3 per IP per hour and 3 per email per day, and one open application per CR.
  - The applicant gets a confirmation email, and admins get a notification. The application waits in the admin
    dashboard under "طلبات الانضمام", with a badge.
  - **Approve:** creates the partner (ONBOARDING, with you as account manager) and emails the contact a PARTNER_ADMIN
    invite to set a password. Then add the contract under "الجهات".
  - **Reject:** a reason is required and is emailed to the applicant.

## Demo data (to present the user flow)

`DEMO_SEED=true` creates, once, three partners with real-looking data: a hotel group (2 hotels, rooms, guest keys),
a sports club (plans, an active and a pending membership), a conference center (2 published events). Plus contracts,
6 months of invoices (one overdue), staff, 3 customers with cards, wallet money, tickets, and 7 days of door activity.

| Variable (API) | Value |
|---|---|
| `DEMO_SEED` | `true` for one deploy, then remove it (a second run does nothing anyway) |
| `DEMO_EMAIL` | **your** inbox. Demo accounts use aliases of it (`you+khalid@gmail.com`), so customer login codes reach you |
| `DEMO_PASSWORD` | the password of every demo account (10+ characters; a secret, only in Railway) |

Accounts: partners `+hotel` (PARTNER_ADMIN), `+frontdesk` (FRONT_DESK), `+club`, `+events`; customers `+khalid`,
`+noura`, `+sarah`. Everything runs in one transaction; invoices are created for the demo contracts only.

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

## Customer app (`frontend/customer`)

The card holder's web app, from the `index_76.html` design: light by default, sidebar on desktop, bottom tab bar
on phones. Sign up / log in, the VORNX card (report lost), permits with "open the door from my phone",
subscriptions and the plan catalog, events and tickets, wallet (top-up, transfers), access log, account and devices.

```bash
cd frontend/customer
npm install
npm run dev        # http://localhost:5175
```

Railway: a fourth service, **Root Directory** `/frontend/customer`, **Config file** `/frontend/customer/railway.json`,
variable `VITE_API_URL=https://<api domain>`. Add its domain to the API's `CORS_ALLOWED_ORIGINS` and set `CUSTOMER_URL`.

**Arabic / English (customer app).** Every text goes through `tr()` (`src/i18n`), with the Arabic text as the key:
`tr('تسجيل الدخول')`, variables as `tr('قبل {0} د', { 0: m })`. The English is in `src/i18n/en.ts`; a missing
translation shows the Arabic and warns in the dev console. The "English / العربية" button saves the choice on the
device and reloads; the page flips to left-to-right (the CSS uses logical properties). The terms and privacy pages have
separate English versions in `LegalPages.tsx`: change both languages together.

All three frontends share one refresh token per browser (localStorage) and refresh it under a Web Lock, so several
open tabs never log each other out.

## Card payments (Moyasar)

Wallet top-up by card goes through Moyasar's **hosted invoice page**: the backend creates an invoice, the app sends
the customer to Moyasar (mada, Visa, Mastercard, Apple Pay), and Moyasar sends them back to `/wallet?topup=TU-…`.
Card details never touch VORNX. Plans and events are then paid from the wallet.

The wallet is credited only after the backend **fetches the invoice from Moyasar with the secret key** and it is
`paid` with the exact amount and currency - never from the browser's URL or the callback's body. Three triggers
(return page, Moyasar's callback, a job every minute) can check at once; a row lock makes it credit once.

| Variable (API service) | Value |
|---|---|
| `MOYASAR_SECRET_KEY` | `sk_test_…` while testing, `sk_live_…` when going live. Empty = card top-up off |
| `MOYASAR_CALLBACK_URL` | `https://<api domain>/api/public/payments/moyasar/callback` |

Local development without a key keeps the instant simulated top-up (`vornx.wallet.simulated-top-up-enabled=true`).

## Real-time updates (WebSocket)

`wss://<api>/ws`: the three apps keep one WebSocket open while logged in. The first message carries the access
token (never the URL); the server closes the socket when the token expires (code 4401) and the app refreshes
and reconnects. Pushes are sent only after the database commit:

| Event | Who gets it | What the app does |
|---|---|---|
| `notification` | the recipient (customer / partner team / all admins) | toast + reload what's on screen |
| `access-event` | the partner's team | "التشغيل المباشر" updates instantly |

Handshakes are accepted only from the CORS origins. The hub is in memory: fine for one API instance;
with several, add Redis pub/sub between `RealtimeRelay` and `RealtimeHub`.

## Public website (`frontend/site`)

The landing page from `index_77 معدل.html`, as plain HTML/CSS/JS (no build tool), served by nginx with the same
strict CSP. Its contact form posts to `POST /api/public/inquiries` (honeypot + 5/hour per IP + 3/day per email);
inquiries appear live in the admin dashboard under "رسائل الموقع".

Railway: a fifth service, **Root Directory** `/frontend/site`, **Config file** `/frontend/site/railway.json`, variables
`VITE_API_URL` (the API), `VORNX_APP_URL` (customer app) and `VORNX_PARTNER_URL` (partner portal).
Add the site's domain to the API's `CORS_ALLOWED_ORIGINS`.

## Two-step customer login

After the right password, the customer gets a 6-digit code (by email for now) and types it in the app.

1. `POST /api/public/auth/customer/login`: **202** + `challengeToken` (a code was sent), or 200 + tokens when turned off.
2. `POST /api/public/auth/customer/login/verify` `{challengeToken, code}` gives the tokens.
3. `POST /api/public/auth/customer/login/resend` `{challengeToken}` sends a new code (once a minute, 3 sends max).

Rules: a code works for 5 minutes, and only once. After 5 wrong codes the customer must enter the password again. An account can start at most 5 logins per 15 minutes. Only hashes are stored.

Railway variable `CUSTOMER_LOGIN_CODE`: `sms`, `email` (default) or `off`.
In development (`vornx.dev.expose-tokens=true`) the email code is also returned as `devCode` and prefilled in the app.

### SMS codes (Firebase phone auth)

With `sms`, the challenge has `channel: SMS` and the customer's full `phoneNumber`.
- The app asks Firebase to text the code; the customer types it.
- The app sends Firebase's ID token to `POST /api/public/auth/customer/login/verify-phone`.
- The API checks the token's Google signature, project, freshness (5 min) and that its phone is the account's.
- `/resend` switches the same login to email: one tap, or automatically when SMS can't be sent.

Variables:
- API: `CUSTOMER_LOGIN_CODE=sms`, `FIREBASE_PROJECT_ID` (the API refuses to start in sms mode without it).
- Customer app (build time; without them the app falls back to email):
  `VITE_FIREBASE_API_KEY`, `VITE_FIREBASE_AUTH_DOMAIN`, `VITE_FIREBASE_PROJECT_ID`, `VITE_FIREBASE_APP_ID`.

Firebase console:
- Blaze plan.
- Phone sign-in on.
- SMS region policy: allow Saudi Arabia only.
- The customer domain under Authorized domains.
- A budget alert in Google Cloud Billing.

## Sign-up: terms, email code, WhatsApp code

1. `POST /customer/register` needs `acceptTerms: true` (the version, `customer.Legal.TERMS_VERSION`, and the time
   are stored). It answers **202** with a `SIGNUP` challenge: the code is emailed; `/customer/login/verify` confirms
   the email and logs in. Until the email is confirmed, every login asks for an email code (even with
   `CUSTOMER_LOGIN_CODE=off`); a password reset also confirms it.
2. With `CUSTOMER_PHONE_VERIFICATION=whatsapp`, the customer then confirms the mobile with a code sent by WhatsApp
   through [UltraMsg](https://ultramsg.com): `POST /api/customer/phone-verification`, then `/confirm`
   (`/resend` once a minute). Until then the other `/api/customer/**` endpoints answer **403 PHONE_NOT_VERIFIED**
   and the app shows only that step. A mobile code can never log in, and a login code can never confirm a mobile.

The terms and privacy policy are pages of the customer app: `/terms` and `/privacy`.

Variables (API):
- `CUSTOMER_PHONE_VERIFICATION`: `whatsapp` or `off` (default).
- `ULTRAMSG_INSTANCE_ID`, `ULTRAMSG_TOKEN` (the token is secret). The API refuses to start with `whatsapp` and
  without them (in development the message is only logged and the code returned as `devCode`).

## Authenticator app (optional, per customer)

In "حسابي" the customer can turn on Google / Microsoft Authenticator (TOTP, RFC 6238). Then the login's second step
is the app's code instead of the email code, and 10 one-time recovery codes are shown once.
- `/api/customer/security/totp`: `GET` status, `POST /setup` (secret + `otpauth://` URI, drawn as a QR code in
  the browser), `POST /enable {code}` (returns the recovery codes), `POST /disable {password}`.
- A code works once (the last used 30-second step is stored); a recovery code works once.
- The secret is stored encrypted (AES-GCM). Key: `TOTP_ENCRYPTION_KEY` (base64 of 32 random bytes, e.g.
  `openssl rand -base64 32`); without it, a key derived from `JWT_SECRET`. **Choose it once**: changing it later
  (or changing `JWT_SECRET` while it is unset) breaks the apps already set up.

## Face ID / fingerprint sign-in (passkeys, optional, per customer)

In "حسابي" > الأمان the customer adds the device ("إضافة هذا الجهاز"): Face ID / Touch ID / fingerprint / Windows Hello
creates a key pair on the phone, and only the PUBLIC key is stored (`passkeys`). The login page then shows
"الدخول بـ Face ID": no email, no password and no email code (the passkey already proves the phone AND the person,
and can't be phished: the phone only signs for our domain). Yubico's `webauthn-server-core` checks everything.
- Add: `POST /api/customer/passkeys/options` -> `navigator.credentials.create()` -> `POST /api/customer/passkeys`.
  `GET` lists the devices, `DELETE /{id}` removes one. Adding or removing sends a security email.
- Sign in: `POST /api/public/auth/customer/passkey/options` -> `navigator.credentials.get()` ->
  `POST /api/public/auth/customer/passkey/login` (tokens). A challenge works once, for 5 minutes; 30 per IP / 10 min.
- **Domain:** a passkey works only on the domain it was made on. By default that's the host of `CUSTOMER_URL`.
  When the customer app moves to its own domain (e.g. `my.vornx.sa`), set `PASSKEY_RP_ID` / `PASSKEY_ORIGINS`;
  passkeys made on the old domain stop working, and customers add their device again after a password sign-in.
