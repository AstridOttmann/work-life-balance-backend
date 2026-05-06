# Work-Life Balance — Backend

Spring Boot 3.3.0 / Java 21 / PostgreSQL 16. JWT auth via Spring Security 6 + JJWT 0.12.3.

## Run

```bash
# Start DB
docker compose up -d

# Start app
./mvnw spring-boot:run

# Run tests (uses H2 in-memory)
./mvnw test
```

API runs on `http://localhost:8080`.

## Architecture

```
controller/   REST endpoints — inject @AuthenticationPrincipal User, delegate to service
service/      Business logic — all methods accept User as first param for data scoping
model/        JPA entities: User, DailyEntry, Appointment, TimeBlock
repository/   Spring Data JPA — all queries scoped to user (findByIdAndUser etc.)
security/     JwtUtil (generate/validate tokens), JwtAuthFilter (reads Bearer header)
config/       SecurityConfig (filter chain), CorsConfig
dto/          DailyEntryDto, AppointmentDto, TimeBlockDto, SummaryDto, AuthResponse, etc.
```

## Auth

- `POST /api/auth/register` and `POST /api/auth/login` are public; everything else requires a Bearer token.
- `SecurityConfig` has an `AuthenticationEntryPoint` that returns 401 (not 403) for expired/missing tokens.
- Token expiry: `jwt.expiration-ms` in `application.properties`. Currently 24 h — **pending change to 30 days**.
- JWT secret: env var `JWT_SECRET` with a dev fallback in `application.properties`.

## Key patterns

- `DailyEntry` has a composite unique constraint on `(user_id, date)` — same date allowed for different users.
- `workHours` / `freeTimeHours` on `DailyEntry` are legacy stored values; if `TimeBlock` records exist they are computed instead.
- `server.error.include-message=always` — backend error messages surface to clients as `error.response.data.message`.

## Current branch state (feature/auth)

All JWT auth tasks complete. One **uncommitted change**: `SecurityConfig.java` — `AuthenticationEntryPoint` added to return proper 401. Commit this before adding new features.

## Properties

| Property | Default | Notes |
|---|---|---|
| `jwt.secret` | base64 dev key | Override with `JWT_SECRET` env var in prod |
| `jwt.expiration-ms` | 86400000 (24 h) | Change to 2592000000 for 30 days |
| `spring.datasource.url` | Railway-compatible with local fallback | |
