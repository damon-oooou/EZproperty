# EZproperty

A cloud-based property inspection platform that enables property managers to build and maintain a persistent photo history for every property. Instead of recreating condition reports from scratch for each tenancy, managers reuse existing photos and update only what has changed — on the web today, with mobile on-site capture and AI-assisted reporting on the roadmap.

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

## Current Status — v0.5.2

**Upload hardening (v0.5.2)**
- Format validation: only JPEG and PNG accepted — frontend `accept="image/jpeg,image/png"` plus client-side type check, backend verification by magic bytes (declared content-type not trusted); HEIC rejected with a clear conversion hint
- Size limits set explicitly: 15MB per photo / 100MB per request (`max-file-size` / `max-request-size`) — bill protection once storage moves to the cloud; over-limit uploads get a clear message on both ends instead of a blank 500
- Download PDF auto-saves: unsaved report changes are saved automatically before the PDF is generated, so the download always reflects the latest edits (if the save fails, no PDF is produced)

**Google sign-in (v0.5.1)**
- "Continue with Google" on the login and register pages (Google Identity Services); backend verifies the ID token signature/audience and issues the same app JWT as password login
- First Google sign-in auto-registers (creates a single-member agency); an existing password account with the same email simply signs in — the email is already verified by Google
- Configured via one OAuth Client ID used by both sides: `VITE_GOOGLE_CLIENT_ID` (frontend, `.env.local`) and `GOOGLE_CLIENT_ID` (backend env). Unset = the button is hidden and password login works as normal


**Multi-user & authentication (v0.5)**
- Agency tenant model: users belong to an agency, properties belong to the agency; registration auto-creates a single-member agency, so solo use is unchanged and team sharing needs no future schema change
- Open registration + JWT login (`/api/auth/register`, `/api/auth/login`, `/api/auth/me`); BCrypt password hashing; stateless Spring Security — the same token API will serve the future iOS app
- Tenant isolation via a single `TenantGuard` entry point: every property/inspection load verifies agency ownership; foreign resources return 404 (existence not leaked)
- Frontend: sign-in / create-account pages, protected routes with post-login redirect, automatic `Authorization` header, 401 → session cleared and redirected to login; PDF download switched to authenticated fetch + blob
- Known limitation: `/uploads/**` photo files stay public (UUID filenames, not enumerable) — signed URLs planned with the S3 migration


**Property & inspection management**
- Google-style home page with instant autocomplete search and create-property flow
- Property page with inspection list (ENTRY / ROUTINE / EXIT), inherit-from-previous option
- Inspection page with Photos / Report tabs (tab state persisted in the URL)

**Photo library**
- Room coverage grid per inspection (photo counts, amber flags for unphotographed rooms)
- Per-room photo upload, lightbox viewing, reference-based removal
- Custom rooms added inline (property-level assets, visible across all inspections)

**Condition report (NSW routine format)**
- Room-level conditions: Satisfactory / Not satisfactory / Not inspected + comments, with a one-click "All satisfactory" shortcut
- Report details: landlord, tenant, lease expiry, smoke alarms, tenant repairs, urgent action / general comments / tenant action boxes, agency info, editable disclaimer
- Full inheritance: conditions and identity fields copy to the next inspection; per-visit action boxes intentionally start blank

**PDF generation**
- `GET /api/inspections/{id}/report` renders a downloadable A4 PDF (Thymeleaf template → openhtmltopdf)
- Three sections: report header, room condition table, photos grouped by room (two-column grid, auto-captioned)
- Photos downscaled to 1200px and embedded; page numbering in the footer
- Own template and wording throughout — no reproduction of copyrighted industry forms; the agent disclaimer is a user-editable field

## Tech Stack

| Layer      | Technology |
|------------|------------|
| Backend    | Java 17, Spring Boot 3.4, Spring MVC, JPA/Hibernate |
| Database   | PostgreSQL (Docker Compose), Flyway migrations |
| PDF        | Thymeleaf, openhtmltopdf (io.github.openhtmltopdf fork) |
| Frontend   | React, Vite, React Router, Tailwind CSS v4 |
| Storage    | Local filesystem behind a service layer (S3-compatible migration path preserved) |

## How to Run

Prerequisites: Java 17, Maven, Node.js 18+, Docker Desktop.

**1. Database** (PostgreSQL 15 in Docker, data persists in a named volume):

```bash
docker compose up -d
```

**2. Backend** (port 8080; Flyway migrations run automatically on startup):

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
- **Production** — set a random `JWT_SECRET` env var (the yml default is dev-only) and add your domain to the CORS origins in `CorsConfig`

## API Overview

```
Auth (v0.5)
  POST      /api/auth/register                              open registration, auto-creates agency
  POST      /api/auth/login                                 returns JWT (72h)
  POST      /api/auth/google                                Google ID token -> app JWT (v0.5.1)
  GET       /api/auth/me                                    current user + agency

Properties
  GET/POST  /api/properties
  GET       /api/properties/{id}
  GET/POST  /api/properties/{propertyId}/rooms
  GET/POST  /api/properties/{propertyId}/inspections

Inspections
  GET       /api/inspections/{id}/rooms                     rooms + per-inspection photo counts
  GET/POST  /api/inspections/{id}/rooms/{roomId}/photos
  DELETE    /api/inspections/{id}/photos                    removes references only

Reports (v0.4.1+)
  GET/PUT   /api/inspections/{id}/conditions                room conditions, batch upsert
  GET/PUT   /api/inspections/{id}/report-details
  GET       /api/inspections/{id}/report                    PDF download (v0.4.2)
```

## Data Model (Flyway V1–V10)

```
agencies ─< users                                           (v0.5, tenant boundary)
agencies ─< properties
properties ─< rooms ─< photos
properties ─< inspections ─< inspection_photos >─ photos   (reference join, RESTRICT on photo)
inspections ─< room_conditions                              (unique per inspection+room)
inspections ─1 report_details                               (PK = inspection id)
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

## Google Sign-in Setup

1. [Google Cloud Console](https://console.cloud.google.com/) → create/select a project → **APIs & Services → Credentials → Create Credentials → OAuth client ID** (first time: configure the consent screen, External, only app name + email required)
2. Application type **Web application**; add **Authorized JavaScript origins**: `http://localhost:5173` (and later your production domain). No redirect URI needed.
3. Copy the Client ID into:
   - `frontend/.env.local` → `VITE_GOOGLE_CLIENT_ID=<client-id>`
   - backend env → `GOOGLE_CLIENT_ID=<client-id>` (or edit `application.yml`)

## Roadmap

**Later**
- Team collaboration: invite colleagues into an agency, roles/permissions (schema already supports it)
- Email verification for password sign-ups (needs a mail provider — Resend / SES / SendGrid); Google accounts are already verified
- Photo ingest normalisation at upload time: EXIF orientation fix, unified JPEG re-encode, thumbnail generation (also the answer to egress costs — thumbnails for browsing, originals on demand, PDFs generated in-region)
- iOS camera/export pipeline fixed to JPEG (`AVCapturePhotoOutput`, quality prioritisation, original resolution); server-side HEIC decoding deliberately not planned — HEIC uploads are rejected with a conversion hint (v0.5.2)
- iOS app v1: native camera + PHPicker batch import; v2: in-app guided capture per room
- Per-agency report template customisation; Victorian (CAV) item-level template
- Soft deletes; embedded PDF font for non-Latin comment text
- Cloud storage selection notes: zero-egress providers (e.g. R2/B2) favoured; storage abstraction already in place
- AI-assisted features (change detection, report drafting) — further out