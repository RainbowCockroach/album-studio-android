# CLAUDE.md — To Print (Android)

This file guides Claude Code agents working in this repository.

## What This Is

A minimal Android app with one purpose: **get photos from the phone's gallery onto the owner's home server with as little friction as possible.** The owner's flow:

> Open gallery → select photo(s) → Share → "To Print" → photos upload instantly, owner is back in the gallery within a second.

System context (sibling repos, do not modify them from here):
- `../album-studio-server/` — Node/Fastify transfer hub this app uploads to (its CLAUDE.md holds the canonical API contract)
- `../album-studio/` — desktop app that later pulls the photos for sorting/printing

**Current state: fresh Compose template only** (`MainActivity` + theme files, package `com.rainbowcockroach.albumstudio.toprint`, minSdk 26, targetSdk 36, Kotlin + Compose Material3, version catalog in `gradle/libs.versions.toml`). Build the real app per this spec.

## Architecture — REQUIRED

Three pieces, in priority order:

### 1. Share Target (the main entry point)

- An activity (e.g. `ShareReceiverActivity`) with intent filters for `ACTION_SEND` and `ACTION_SEND_MULTIPLE`, mimeType `image/*`, exported, labeled **"To Print"**.
- On receipt: take **persistable/read permission** on each content URI, **copy each shared image into app-private storage immediately** (`filesDir/pending/`) — content URI grants are NOT reliable after the sharing activity finishes, so never hand raw content URIs to WorkManager.
- **Determine `capturedAt` at this point too** (see "Capture Time Determination" below) — the MediaStore fallback needs the original content URI, which is gone once this activity finishes. Persist it alongside the pending file (Room record / work input).
- Enqueue one upload Work item per photo, show a brief confirmation (toast or tiny translucent activity: "3 photos queued"), then `finish()`. Total foreground time must stay well under a second; do the file copies in the activity only if instant (small count), otherwise in an expedited Worker.
- If server URL/token are not configured yet, open the settings screen instead with an explanatory message.

### 2. Upload Engine — WorkManager

- One `OneTimeWorkRequest` per photo. Constraints: `NetworkType.CONNECTED`. Backoff: exponential. This is the heart of the app: uploads must survive process death, airplane mode, and bad signal, completing whenever connectivity returns.
- Worker: OkHttp multipart `POST {serverUrl}/photos`, file field `file` (original filename preserved), plus text field `capturedAt` (ISO 8601) when known, header `Authorization: Bearer <token>`.
- Parse response `{"hash", "existed", ...}`. Both `existed` true and false are success. Delete the local pending copy on success.
- Optional optimization (do last): compute SHA-256 locally and `HEAD /photos/{hash}` first; skip the upload body if 200.
- HTTP 401 → fail permanently (don't retry; surface "check token" in status). 5xx/network errors → `Result.retry()`.
- Use `setForegroundAsync`/expedited work with a notification ("Uploading 2 of 5") — required on modern Android for reliable execution, and gives the owner visibility.

### Capture Time Determination — IMPORTANT

This app is the **single decision point** for when a photo was taken (`capturedAt`). The server stores the value verbatim; the desktop app uses it to group photos into month-based projects (`2026-06` etc.). The chain mirrors the desktop app's existing `get_display_date()` logic, in priority order:

1. **EXIF `DateTimeOriginal`** via `androidx.exifinterface.ExifInterface` (open an InputStream from the content URI; handles JPEG/HEIC/WebP/DNG). Fall back to EXIF `DateTime` if `DateTimeOriginal` is absent.
2. **Filename pattern** `YYYYMMDD_HHMMSS` at the start of the display name (regex `^(\d{4})(\d{2})(\d{2})_(\d{2})(\d{2})(\d{2})`), validated as a real date.
3. **MediaStore `DATE_TAKEN`** queried with the original content URI.
4. All failed → `null` (the field is simply omitted from the upload; the desktop falls back to upload month).

Serialize as ISO 8601 (e.g. `2026-05-30T18:21:09`). EXIF datetimes have no timezone — treat them as local time, do not invent a zone offset. Run the chain in `ShareReceiverActivity` while the URI grant is still alive (see above).

### 3. Main UI (minimal, Compose)

Two screens, Material3, no over-engineering:
- **Upload list (home):** recent uploads with per-item status — Queued / Uploading / Done / Failed (tap to retry). Back this with a small **Room** table (`uploads`: id, fileName, localPath, status, hash?, errorMsg?, createdAt) that Workers update; the UI observes it as a Flow. Don't try to derive UI state from WorkManager queries alone — WorkManager prunes finished work.
- **Settings:** server URL + token, stored in **Jetpack DataStore (Preferences)**. Validate with a "Test connection" button that calls `GET /health` and a `HEAD`-with-auth check (e.g. `GET /photos?since=<current-month>` → expect 200) to verify the token.

## API Contract (duplicate — canonical copy lives in `../album-studio-server/CLAUDE.md`)

All routes except `/health` require `Authorization: Bearer <token>`.

| Method & Path | Request | Response |
|---|---|---|
| `GET /health` | — (no auth) | `200 {"status": "ok"}` |
| `POST /photos` | multipart/form-data: file field `file` (filename = original name), optional text field `capturedAt` (ISO 8601) | `200 {"hash": "<sha256>", "existed": bool, "size": int, "uploadedAt": "<ISO8601>", "capturedAt": "<ISO8601>\|null"}` |
| `HEAD /photos/:hash` | — | `200` exists / `404` not |
| `GET /photos?since=YYYY-MM` | — | `200 {"photos": [...]}` (this app doesn't need it except for token validation) |

Errors: `{"error": "<message>"}`; `401` on bad token. Server accepts `jpg jpeg png heic heif webp dng gif`; others get `415`.

## Dependencies to Add

Via the existing version catalog (`gradle/libs.versions.toml`): `androidx.work:work-runtime-ktx`, `com.squareup.okhttp3:okhttp`, `androidx.exifinterface:exifinterface`, `androidx.room:*` (+ ksp), `androidx.datastore:datastore-preferences`, `androidx.navigation:navigation-compose` (or just two composables and a boolean — navigation lib optional at this scale). No Retrofit needed — two endpoints, plain OkHttp is fine. No DI framework — manual construction is fine at this size.

## Commands

```bash
./gradlew assembleDebug        # build
./gradlew installDebug         # install on connected device
./gradlew test                 # unit tests
./gradlew lint
```

## Conventions

- Package root: `com.rainbowcockroach.albumstudio.toprint`. Suggested subpackages: `ui/`, `upload/` (worker + queue), `data/` (Room, DataStore, OkHttp client).
- Keep the existing Compose theme files; build screens with Material3 defaults. Function over beauty — this app is used for ~10 seconds at a time.
- Manifest needs `INTERNET`, `POST_NOTIFICATIONS` (runtime-request on 33+), and a notification channel for upload progress.
- Server URL is HTTPS via Cloudflare Tunnel — no cleartext traffic config needed for production, but allow `http://` URLs too (LAN testing). Add `android:usesCleartextTraffic="true"` only if needed for debug builds, prefer a network security config scoped to debug.

## Testing Expectations

- Unit-test the upload worker's response handling (success / existed / 401-no-retry / 5xx-retry) with OkHttp `MockWebServer`.
- Unit-test the capture-time chain: EXIF present, EXIF absent but filename matches `YYYYMMDD_HHMMSS`, invalid filename date (e.g. month 13 → fall through), nothing available → null.
- Manual end-to-end: share 3 photos from Google Photos → verify they land in the server's `data/photos/` (server repo has `scripts/smoke.sh` and can run locally via `npm run dev`).

## Non-Goals — Do NOT Build

- No browsing/viewing photos already on the server, no download direction, no auto-backup of albums, no editing. Upload queue only.
