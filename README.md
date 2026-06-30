# Property Photos

A property management photo documentation system that solves a real pain point in real estate: **reusing condition-report photos across tenancies instead of re-photographing entire properties every time.**

## The Problem

Property managers re-shoot a full condition report (200–400 photos per property) before every new tenancy, even though most rooms haven't changed since the last shoot. This wastes hours per property, every single time.

## The Solution

Photos are stored per-room and persist across tenancies. Before the next tenant moves in, the property manager opens each room, deletes only the photos that are outdated, and imports new ones for what's changed. Everything else carries over automatically — no full re-shoot required.

## Tech Stack

**Backend**
- Java 17, Spring Boot 3.4, Spring MVC
- PostgreSQL + Spring Data JPA / Hibernate
- Flyway (database migrations)
- Local file storage for MVP (abstracted behind a service layer for a future S3 migration)
- Docker + Docker Compose

**Frontend**
- React (Vite)
- React Router

## How It Works

1. **Create a property** — automatically generates a fixed set of 18 rooms (Entrance, Lounge Room, Kitchen, Bedroom 1–3, Bathroom, Garage, etc.)
2. **Upload photos** to any room
3. **Before the next tenancy**, open a room, select outdated photos via checkbox, delete them, and import new ones — unaffected photos are left untouched
4. Photo history is preserved per property across multiple tenancies, eliminating redundant reshoots

## Project Structure

```
property-photos/
├── src/main/java/com/propertymap/
│   ├── controller/      # REST endpoints (Property, Room, Photo)
│   ├── service/         # Business logic
│   ├── repository/      # Spring Data JPA repositories
│   ├── model/            # JPA entities
│   └── config/           # CORS + static resource (uploads) config
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/    # Flyway migrations (V1–V3)
├── docker-compose.yml    # PostgreSQL container
└── frontend/
    ├── src/
    │   ├── pages/         # PropertiesPage, PropertyDetailPage, RoomPage
    │   └── api/client.js  # API layer
    └── package.json
```

## Database Schema

```
properties (1) ──< rooms (N) ──< photos (N)
```

- **properties**: address, type (HOUSE / APARTMENT)
- **rooms**: 18 default rooms auto-created per property, ordered by position
- **photos**: file metadata + local storage path, linked to a room

## API Overview

| Method | Endpoint | Description |
|--------|----------|--------------|
| GET | `/api/properties` | List all properties |
| POST | `/api/properties` | Create a property (auto-creates 18 rooms) |
| GET | `/api/properties/{id}/rooms` | List rooms for a property |
| GET | `/api/rooms/{roomId}/photos` | List photos in a room |
| POST | `/api/rooms/{roomId}/photos` | Upload photos to a room |
| DELETE | `/api/rooms/{roomId}/photos` | Delete selected photos |

## Running Locally

**Backend**
```bash
docker-compose up -d        # start PostgreSQL
mvn spring-boot:run         # runs on :8080, Flyway migrates automatically
```

**Frontend**
```bash
cd frontend
npm install
npm run dev                 # runs on :5173
```

## Status

MVP core flow is complete: create property → auto-generate rooms → upload/view/delete photos per room. Report generation and authentication are planned for a later phase.

## Roadmap

- [ ] React page polish (upload progress, delete confirmation)
- [ ] Editable room list (add/remove rooms per property)
- [ ] Condition report generation (PDF export)
- [ ] User authentication for multi-manager support
- [ ] S3 migration for production storage
- [ ] iOS app reusing the same Spring Boot API
