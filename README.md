# EZproperty

A cloud-based property inspection platform that enables property managers to build and maintain a persistent photo history for every property. Instead of recreating condition reports from scratch for each tenancy, managers reuse existing photos and update only what has changed — on the web today, with mobile on-site capture and AI-assisted reporting on the roadmap.

**Live:** [app.ez-property.net](https://app.ez-property.net) (frontend on Vercel, API on Railway Singapore, photos on Cloudflare R2)

## The Problem

Every time a tenancy ends and a new one begins, a property manager typically:

- Re-photographs the entire property — 200 to 400 photos per property, depending on whether it is an apartment or a house
- Repeats this even though most rooms haven't changed at all
- Spends additional hours organising the photos and assembling a condition report

The photos taken at each inspection are treated as disposable, so the work is redone from zero every single cycle.

## The Core Idea

EZproperty maintains a long-lived **Property Photo Library**. Photos belong to the property (via its rooms — a fixed room list per property, with custom rooms added when needed). Inspections are time-stamped slices that *reference* photos through a join table:

- Creating a new inspection can **inherit** the previous inspection's photos by copying join-table rows — not files, not records
- The manager then re-photographs only the rooms that changed
- Removing a photo from an inspection deletes only the reference; the file and its history remain intact for past inspections
- The same inheritance applies to report content: room conditions and tenancy details carry over, while per-visit findings (urgent actions, comments) start blank

## Current Status — v0.6

**Photo ingest pipeline**
- Every upload passes through a single normalisation pipeline: EXIF is read from the raw bytes first (`DateTimeOriginal` → stored as `taken_at`, `Orientation` → used to rotate/flip pixels, all 8 values implemented), then pixels are re-encoded — the stored files contain **no EXIF at all** (GPS and orientation tags never reach disk)
- Three tiers per photo, derived by naming convention from one storage key: full-resolution original (q0.85), 1600px medium (lightbox / PDF embedding), 200px thumbnail (grids). Raw upload bytes are not retained — the "original" is the normalised artifact
- PNG input is flattened onto white and converted to JPEG; CMYK and non-standard JPEGs decode via TwelveMonkeys
- Memory-bounded by design: orientation fix and RGB normalisation happen in a single draw (two full-size buffers max, 3 bytes/px), pixel work is serialised through a semaphore, and photos over 60 megapixels (panoramas, stitched images) are rejected before decoding with a clear message
- All three tiers must succeed or the upload rolls back — no orphan files, no orphan rows
- Honest date labelling everywhere (UI and PDF captions): "Taken {date}" only when EXIF says so, otherwise "Uploaded {date}"

**Cloud storage & signed URLs**
- `PhotoStorage` abstraction: local filesystem in dev, Cloudflare R2 (S3-compatible, AWS SDK v2) in prod — same storage keys on both
- The production bucket is private; all photo access goes through presigned GET URLs (24h TTL) generated server-side. Real authorisation happens at the API layer (`TenantGuard`); the URL is just a short-lived pass. The old public `/uploads/**` mapping exists in dev only
- PDF generation loads the 1600px tier through the storage abstraction with a fixed 8-thread pool — large reports no longer fetch photos serially

**Cloud deployment**
- Backend: multi-stage Dockerfile → Railway (Singapore) with Railway PostgreSQL; `DATABASE_URL` (postgres://) is mapped to Spring's JDBC form by an `EnvironmentPostProcessor`; Flyway migrates automatically on deploy
- Frontend: Vercel (`frontend/` root, `vercel.json` SPA rewrite); API base URL and Google client ID injected via `VITE_*` env vars
- Domains: `app.ez-property.net` → Vercel, `api.ez-property.net` → Railway; CORS origins are env-driven (`CORS_ALLOWED_ORIGINS`), so adding a domain is a config change, not a code change
- All secrets live in platform env vars; the prod profile has no default credentials and fails fast if `JWT_SECRET` is missing

**Production hardening**
- Backups: nightly `pg_dump` via GitHub Actions → private R2 backup bucket (`daily/` kept 30 days by lifecycle rule, `monthly/` kept forever); restore procedure documented and drilled (`docs/restore-drill.md`) — an unverified backup is treated as no backup
- Auth rate limiting (bucket4j, per client IP from `X-Forwarded-For`): login 10/min, register 5/hour → 429; login failures return a single uniform message either way (no email enumeration)
- Monitoring: Sentry on both backend (spring starter) and frontend (`@sentry/react`), DSNs env-driven and disabled when unset; UptimeRobot probes `/actuator/health` every 5 minutes (only actuator endpoint exposed)
- Unhandled 500s no longer masquerade as 401s (`/error` dispatch permitted) — errors surface honestly instead of logging users out
- Secret audit of full git history completed: no real credentials ever committed (dev-only defaults exempted by design)

## Tech Stack

| Layer      | Technology |
|------------|------------|
| Backend    | Java 17, Spring Boot 3.4, Spring MVC, JPA/Hibernate |
| Database   | PostgreSQL (Docker Compose dev / Railway prod), Flyway migrations |
| Storage    | Cloudflare R2 via AWS SDK v2 + presigned URLs (prod); local filesystem (dev) — one `PhotoStorage` abstraction |
| Imaging    | metadata-extractor (EXIF), TwelveMonkeys (decode), Thumbnailator (scaling) |
| PDF        | Thymeleaf, openhtmltopdf (io.github.openhtmltopdf fork) |
| Frontend   | React, Vite, React Router, Tailwind CSS v4 |
| Deployment | Railway (API, Singapore) + Vercel (frontend) + Cloudflare (DNS/R2) |
| Ops        | GitHub Actions backups, Sentry, UptimeRobot, bucket4j rate limiting |

## How to Run (dev)

Prerequisites: Java 17, Maven, Node.js 18+, Docker Desktop.

**1. Database** (PostgreSQL in Docker, data persists in a named volume):

```bash
docker compose up -d
```

**2. Backend** (port 8080; Flyway migrations run automatically on startup; photos go to the local `uploads/` folder — no cloud credentials needed):

```bash
mvn spring-boot:run
```

**3. Frontend** (Vite dev server, port 5173):

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 — you'll land on the sign-in page. Create an account (or use Google sign-in if configured, see below), then search or add your first property.

Optional configuration:
- **Google sign-in** — see [Google Sign-in Setup](#google-sign-in-setup); without it the button is hidden and password login works as normal
- **Production profile** — `SPRING_PROFILES_ACTIVE=prod` switches storage to R2 and requires env vars: `JWT_SECRET`, `DATABASE_URL`, `R2_ENDPOINT` / `R2_ACCESS_KEY` / `R2_SECRET_KEY` / `R2_BUCKET`, `CORS_ALLOWED_ORIGINS`, optionally `GOOGLE_CLIENT_ID` and `SENTRY_DSN`

## API Overview

```
Auth (v0.5)
  POST      /api/auth/register                              open registration, auto-creates agency (rate-limited)
  POST      /api/auth/login                                 returns access (30m) + refresh token pair (rate-limited)
  POST      /api/auth/google                                Google ID token -> app token pair (v0.5.1)
  POST      /api/auth/refresh                               rotates refresh token, returns new pair (v0.7, rate-limited)
  POST      /api/auth/logout                                revokes the refresh token family server-side (v0.7)
  GET       /api/auth/me                                    current user + agency

Properties
  GET/POST  /api/properties
  GET       /api/properties/{id}
  GET/POST  /api/properties/{propertyId}/rooms
  GET/POST  /api/properties/{propertyId}/inspections

Inspections
  GET       /api/inspections/{id}/rooms                     rooms + per-inspection photo counts
  GET/POST  /api/inspections/{id}/rooms/{roomId}/photos     photo DTOs carry thumbnail/medium/original URLs (signed in prod)
  DELETE    /api/inspections/{id}/photos                    removes references only

Reports (v0.4.1+)
  GET/PUT   /api/inspections/{id}/conditions                room conditions, batch upsert
  GET/PUT   /api/inspections/{id}/report-details
  GET       /api/inspections/{id}/report                    PDF download (v0.4.2; embeds 1600px tier, dated captions)
```

## Data Model (Flyway V1–V12)

```
agencies ─< users                                           (v0.5, tenant boundary)
agencies ─< properties
properties ─< rooms ─< photos                               (photos: storage_key + taken_at, v0.6)
properties ─< inspections ─< inspection_photos >─ photos   (reference join, RESTRICT on photo)
inspections ─< room_conditions                              (unique per inspection+room)
inspections ─1 report_details                               (PK = inspection id)
users ─< refresh_tokens                                     (v0.7, SHA-256 only, rotation family)
```

## Version History

- **v0.1** — Core flow: properties, rooms, photo upload/storage
- **v0.2** — Inspection layer: ENTRY/ROUTINE/EXIT, photo library with reference-based inheritance, new navigation flow
- **v0.3** — Response DTO layer across the API, per-room photo counts, inspection-scoped room endpoint
- **v0.4** — UI overhaul: Tailwind v4, Google-style home with autocomplete, room coverage grid, photo lightbox, custom rooms
- **v0.4.1** — NSW condition report: data model (`room_conditions`, `report_details`), report editor with tri-state conditions and All-satisfactory shortcut, inheritance extended to report content
- **v0.4.2** — PDF report generation (Thymeleaf + openhtmltopdf) with embedded photos and Download PDF button
- **v0.5** — Multi-user & authentication: agency tenant model (`agencies`, `users`, `properties.agency_id`), Spring Security + JWT (jjwt), open registration, tenant isolation via `TenantGuard`, login/register UI with protected routes
- **v0.5.1** — Google sign-in: GIS button on login/register, `/api/auth/google` verifies the ID token and auto-registers first-time users; `users.auth_provider`, `password_hash` nullable
- **v0.5.2** — Upload hardening: JPEG/PNG-only validation (frontend `accept` + backend magic bytes, HEIC rejected with a clear message), explicit size limits (15MB/photo, 100MB/request) with friendly errors, Download PDF auto-saves unsaved report changes first
- **v0.6** — Production launch: photo ingest normalisation pipeline (EXIF orientation fix + strip, three JPEG tiers, `taken_at`, 60MP cap, V11), `PhotoStorage` abstraction with Cloudflare R2 + 24h presigned URLs (private bucket, `/uploads` dev-only), cloud deployment (Railway Singapore + Vercel + `ez-property.net`), hardening (nightly pg_dump backups to R2 with restore drill, bucket4j auth rate limiting, Sentry + UptimeRobot, git-history secret audit)
- **v0.7 (Phase A)** — Refresh token system: 30-minute access tokens + DB-persisted refresh tokens (SHA-256 only, rotation with reuse detection, 30-day sliding expiry, `refresh_tokens` V12), `/api/auth/refresh` + `/api/auth/logout` (real server-side logout), web client silent refresh with single-flight retry on 401, daily purge of expired rows

## Google Sign-in Setup

1. [Google Cloud Console](https://console.cloud.google.com/) → create/select a project → **APIs & Services → Credentials → Create Credentials → OAuth client ID** (first time: configure the consent screen, External, only app name + email required)
2. Application type **Web application**; add **Authorized JavaScript origins**: `http://localhost:5173` (and your production domain). No redirect URI needed.
3. Copy the Client ID into:
   - `frontend/.env.local` → `VITE_GOOGLE_CLIENT_ID=<client-id>`
   - backend env → `GOOGLE_CLIENT_ID=<client-id>` (or edit `application.yml`)

## Roadmap

**Next (v0.7 direction)**
- ~~Refresh tokens (prerequisite for the iOS app)~~ — done in v0.7 Phase A
- iOS app v1: native camera + PHPicker batch import, camera pipeline fixed to JPEG (`AVCapturePhotoOutput`, quality prioritisation); server-side HEIC decoding deliberately not planned — HEIC uploads are rejected with a conversion hint
- Report finalize/lock mechanism (high priority, planned separately)

**Later**
- Team collaboration: invite colleagues into an agency, roles/permissions (schema already supports it)
- Email verification for password sign-ups (needs a mail provider — Resend / SES / SendGrid); Google accounts are already verified
- Photo library management: list & physically delete unreferenced photos (`PhotoStorage.delete()` and the RESTRICT constraint already make this safe); R2 object versioning once Cloudflare ships it
- Client-side pre-compression for web uploads; per-photo upload progress
- Cloudflare Turnstile on registration (trigger: spam sign-ups)
- Per-agency report template customisation; Victorian (CAV) item-level template
- Soft deletes; embedded PDF font for non-Latin comment text
- Privacy Policy / ToS (trigger: before first real-user data); revisit hosting region (AWS Sydney path kept open by the S3-compatible abstraction)
- AI-assisted features (change detection, report drafting) — further out
