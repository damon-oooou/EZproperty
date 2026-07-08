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

## Current Status — v0.4.2

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

## API Overview

```
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

## Data Model (Flyway V1–V7)

```
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

## Roadmap

**v0.5 — Multi-user & authentication**
- Tenant model decision (agency vs. individual PM ownership), data-ownership migration, Spring Security + JWT (shared by web and the future iOS app)

**Later**
- Photo ingest normalisation at upload time: EXIF orientation fix, unified JPEG re-encode, thumbnail generation (also the answer to egress costs — thumbnails for browsing, originals on demand, PDFs generated in-region)
- Upload format validation (frontend `accept` + backend content-type checks) — scheduled alongside the iOS app, whose camera/export pipeline is fixed to JPEG (`AVCapturePhotoOutput`, quality prioritisation, original resolution); server-side HEIC decoding deliberately not planned
- iOS app v1: native camera + PHPicker batch import; v2: in-app guided capture per room
- Per-agency report template customisation; Victorian (CAV) item-level template
- Soft deletes; embedded PDF font for non-Latin comment text
- Cloud storage selection notes: zero-egress providers (e.g. R2/B2) favoured; storage abstraction already in place
- AI-assisted features (change detection, report drafting) — further out