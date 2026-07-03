# EZproperty

A cloud-based property inspection platform that enables property managers to build and maintain a **persistent photo history** for every property. Instead of recreating condition reports from scratch for each tenancy, managers reuse existing photos and update only what has changed.

## The Problem

Every time a tenancy ends and a new one begins, a property manager typically:

- Re-photographs the entire property — **200 to 400 photos** per property, depending on whether it is an apartment or a house
- Repeats this even though most rooms haven't changed at all
- Spends additional hours organising the photos and assembling a condition report

The photos taken at each inspection are treated as disposable, so the work is redone from zero every single cycle.

## The Core Idea

EZproperty treats a property's photos as a **long-term Photo Library** — an asset that is built once and maintained over time, not recreated per tenancy.

- **Photos belong to the property**, organised by a fixed room list (Entrance, Lounge Room, Kitchen, Bedrooms 1–3, Bathroom, Gardens, Garage, and so on)
- **Inspections reference photos** rather than owning them. Each inspection (Entry / Routine / Exit, following Australian condition report conventions) holds a set of references into the library
- **Creating a new inspection can inherit** all photos from the previous inspection — as an explicit, user-initiated choice, never automatically. The manager then re-photographs only the rooms that changed
- **Removing a photo from an inspection removes only the reference.** The photo itself, and every historical inspection that used it, remains intact

This reference-based model means a photo taken once can serve many inspections, and the history of every past inspection is immutable.

## Current Status — v0.2

Full-stack web MVP, working end to end:

- Property and room management with the fixed room list
- Inspection lifecycle: create Entry / Routine / Exit inspections with a date
- Photo inheritance from the previous inspection via a single checkbox
- Per-room photo upload and viewing within an inspection
- Reference-only removal of photos from an inspection, preserving history

## Architecture

### Stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.4, Maven |
| Database | PostgreSQL (Docker), Flyway migrations |
| Frontend | React + Vite, react-router-dom |
| Storage | Local filesystem (abstracted behind a storage layer for a future S3 migration) |

### Data Model

```
Property ──< Room ──< Photo            (the Photo Library)
Property ──< Inspection ──< InspectionPhoto >── Photo   (references)
```

- `inspection_photos` is a join table: an inspection "contains" a photo by holding a reference row, not a copy of the file
- Inheritance is implemented as a batch copy of reference rows from the most recent prior inspection
- Photo files are stored per room on disk, independent of any inspection — which is exactly what makes cross-inspection reuse free

### API Overview

```
GET    /api/properties
POST   /api/properties
GET    /api/properties/{id}
GET    /api/properties/{propertyId}/rooms
GET    /api/properties/{propertyId}/inspections
POST   /api/properties/{propertyId}/inspections        (type, date, inheritFromPrevious)
GET    /api/inspections/{inspectionId}/rooms/{roomId}/photos
POST   /api/inspections/{inspectionId}/rooms/{roomId}/photos   (multipart upload)
DELETE /api/inspections/{inspectionId}/photos          (removes references only)
```

The API is designed to also serve a future iOS app for on-site photo capture.

## Getting Started

### Prerequisites

- Java 17
- Node.js
- Docker (for PostgreSQL)
- Maven

### Run the backend

```bash
# Start PostgreSQL
docker compose up -d

# Start Spring Boot (Flyway runs all migrations automatically on first start)
mvn spring-boot:run
```

Backend runs at `http://localhost:8080`.

### Run the frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend runs at `http://localhost:5173`.

## Roadmap

- **v0.3 candidates**
  - Per-room photo counts in the inspection view (e.g. "Kitchen (12)") to show at a glance which rooms still need photos
  - Response DTOs to decouple the API contract from JPA entities (prerequisite for the iOS app)
  - Soft deletes and richer photo metadata, so the Photo Library is preserved as a true long-term asset
- **Further out**
  - iOS app for on-site photo capture, feeding the same backend API
  - Condition report generation from an inspection's photo set
  - Migration of photo storage from local filesystem to S3 (storage layer already abstracted for this)
