# Design note: Nordia meeting rooms (Java)

**1. Need and assumptions.** A small API replacing an Excel sheet: list rooms, create/cancel bookings, list a room's day, with five business rules. I assumed one local time zone, same-day bookings only, back-to-back allowed, and that "active" means not cancelled, counted on the booking's start date. I could not confirm these with the client, so they are listed in the README.

**2. Architecture and two hesitations.** Spring Boot, three layers: `Rules` (pure functions, no Spring), `BookingService` + repositories (JdbcTemplate over SQLite), controller + one error handler. The clock is a bean so tests can move time. *Hesitation A: JdbcTemplate vs JPA/Hibernate.* I chose plain SQL: two tables, no mapping layer, and the overlap and daily-count queries are easier to read and explain; JPA's SQLite dialect support is weaker. *Hesitation B: double-booking protection.* A `UNIQUE` constraint cannot express "ranges overlap", so I check then insert inside one transaction, protected by an in-process lock taken outside the transaction. This is correct for one application instance; with several instances I would need a database-level guarantee (for example PostgreSQL exclusion constraints). I also use 422 for problems in the request itself (weekend, hours, duration) and 409 for conflicts with existing data (overlap, limit, already started).

**3. Tested / not tested.** Unit tests for each rule and its boundaries (08:00, 19:00, 15 min, 4 h), and HTTP tests through MockMvc with a real SQLite file: filter, success, unknown room, overlap, back-to-back, daily limit and next day, cancel frees quota, cancel after start, double cancel, daily list, invalid input and time zone offsets. Not tested: real concurrent requests, daylight-saving changes, large data volumes.

**4. What I cut.** Pagination (6 rooms, short daily lists), authentication (so anyone can cancel any booking: a real security gap), Dockerfile. They were optional and I preferred fewer rules done well.

**5. AI use.** I used Claude (Anthropic) to draft the structure, the rules and the tests from the brief. [Edit this: say what you changed, what you read line by line, and what you ran yourself.] I checked the output by running `mvn test`, reading every rule against the brief, and trying boundary cases by hand.

**6. With one more day.** Simple authentication so only the organiser can cancel; pagination; a Dockerfile; a concurrency test with parallel requests; explicit time zone support; Flyway migrations instead of `CREATE TABLE IF NOT EXISTS`.
