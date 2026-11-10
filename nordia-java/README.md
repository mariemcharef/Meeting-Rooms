# Nordia meeting rooms API (Java)

REST API replacing the Excel sheet for booking meeting rooms.
Java 17+, Spring Boot 3.3, SQLite (via JDBC), Maven.

## Run
```bash
mvn spring-boot:run                 # http://localhost:8080
```
- OpenAPI / Swagger UI: http://localhost:8080/swagger-ui.html
- Data is stored in `bookings.db` in the working directory (change with `DB_PATH=/path/file.db`).
- The 6 rooms of `src/main/resources/rooms.json` are loaded on first start.

## Test
```bash
mvn test        # unit tests of the rules + HTTP tests (MockMvc, temporary DB file, fake clock)
```

## Endpoints
| Method | Path | Notes |
|---|---|---|
| GET | `/rooms?min_capacity=8` | rooms with capacity >= value |
| POST | `/bookings` | body: `room_id, start, end, organiser, subject` -> 201 |
| DELETE | `/bookings/{id}` | cancels (soft): status becomes `cancelled` |
| GET | `/rooms/{id}/bookings?date=2026-11-10` | active bookings of that day, sorted |

```bash
curl -X POST localhost:8080/bookings -H 'content-type: application/json' \
  -d '{"room_id":3,"start":"2026-11-10T09:00:00","end":"2026-11-10T10:00:00","organiser":"alice","subject":"Sprint planning"}'
```

## Errors
Always `{"error": {"code": "...", "message": "..."}}`.
- **422**: invalid input or a slot rule broken (`weekend`, `outside_hours`, `too_short`, `too_long`, `multi_day`, `invalid_range`, `invalid_input`, `timezone_not_supported`)
- **404**: unknown room or booking
- **409**: conflict with current state (`overlap`, `daily_limit`, `already_started`, `already_cancelled`)

## Assumptions and choices
- One local time zone; times are sent without a UTC offset (an offset or `Z` is rejected with 422).
- A booking must start and end on the same day.
- Back-to-back bookings (10:00-11:00 then 11:00-12:00) are allowed.
- The 3-per-day limit counts non-cancelled bookings of the organiser (case-insensitive) whose **start date** is that day.
- Cancelled bookings free their slot and quota and are hidden from the daily list.
- "Not started yet" means `start > now`. Creating a booking in the past is not forbidden (not in the brief).

## Code map (`src/main/java/com/nordia/rooms`)
- `Rules` : pure business rules. `Times` : date-time parsing/formatting.
- `BookingService` : use cases; check + insert run under a lock and one transaction.
- `BookingRepository`, `RoomRepository`, `DatabaseInitializer` : SQL with `JdbcTemplate`.
- `MeetingRoomController`, `ApiExceptionHandler` : HTTP layer and the error format.

## Not done
Pagination, authentication, Dockerfile (see DESIGN_NOTE.md).
