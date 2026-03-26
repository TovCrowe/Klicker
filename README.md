# Klicker - Spring Boot JWT Authentication + Matchmaking

A Spring Boot REST API that handles player registration, login with JWT, and matchmaking between players.

---

## What the app does

- Players can **register** with a username, email, and password
- Players can **login** and receive a JWT token
- Protected endpoints require the JWT token in the request header
- Players can **join a matchmaking queue** — when two players are waiting, a match is created
- Player data and match data are stored in a database via JPA

---

## Project Structure

```
src/main/java/com/project/Klicker/
│
├── KlickerApplication.java          # Entry point
│
├── Entities/
│   ├── Player.java                  # Database model for players
│   └── Match.java                   # Database model for matches
│
├── Repository/
│   ├── PlayerRepository.java        # Database access for players
│   └── MatchRepository.java         # Database access for matches
│
├── DTO/
│   ├── RegisterRequest.java         # Request body for /register
│   ├── LoginRequest.java            # Request body for /login
│   └── AuthResponse.java            # Response containing the JWT token
│
├── enums/
│   └── MatchStatus.java             # Enum: WAITING, IN_PROGRESS, FINISHED
│
├── Controllers/
│   ├── AuthController.java          # HTTP endpoints for auth
│   └── MatchController.java         # HTTP endpoints for matchmaking
│
├── Jwt/
│   ├── AuthService.java             # Register/login business logic
│   ├── JwtService.java              # JWT creation and validation
│   └── JwtFilter.java               # Intercepts requests to check JWT
│
├── service/
│   └── MatchmakingService.java      # Matchmaking queue logic
│
└── Security/
    ├── PlayerUserDetailsService.java # Loads user from DB for Spring Security
    └── SecurityConfig.java          # Security rules and bean configuration
```

---

## Key Concepts & Annotations

### `@SpringBootApplication`
Marks the main class. Tells Spring Boot to auto-configure everything, scan for components, and start the app.

### `@Entity` + `@Table`
Marks a class as a JPA database entity. Spring will map it to a table automatically.

```java
@Entity
@Table(name = "players")
public class Player { ... }
```

### `@ManyToOne`
Defines a many-to-one relationship between two entities. In `Match.java`, both `player1` and `player2` are `Player` entities. Many matches can involve the same player.

```java
@ManyToOne
private Player player1;

@ManyToOne
private Player player2;
```
JPA uses this to generate a foreign key in the `matches` table pointing to the `players` table.

### `@Enumerated(EnumType.STRING)`
Tells JPA to store the enum as a string in the database (e.g. `"IN_PROGRESS"`) instead of an integer index. Always prefer `STRING` — if you add values to the enum, integer indexes shift and corrupt old data.

```java
@Enumerated(EnumType.STRING)
private MatchStatus status = MatchStatus.WAITING;
```

### `@Repository` (via `JpaRepository`)
Extending `JpaRepository<Entity, ID>` gives you free CRUD methods (`save`, `findById`, `findAll`, etc.) without writing SQL. You can also declare custom methods by name:

```java
Optional<Player> findPlayerByUsername(String username);
List<Match> findAllByStatus(MatchStatus status);
// Spring reads the method name and builds the query for you
```

### `@Service`
Marks a class as a business logic component. Spring manages it as a bean you can inject elsewhere.

### `@RestController` + `@RequestMapping`
Marks a class as a REST controller. Every method returns JSON by default (no need for `@ResponseBody`).

```java
@RestController
@RequestMapping("/api/match")
public class MatchController { ... }
```

### `@PostMapping` + `@GetMapping` + `@PathVariable`
Maps methods to HTTP verbs. `@PathVariable` extracts a value from the URL path.

```java
@GetMapping("/{matchId}")
public ResponseEntity<MatchStatus> getMatchStatus(@PathVariable Long matchId) { ... }
// GET /api/match/42  →  matchId = 42
```

### `@Configuration` + `@Bean`
`@Configuration` marks a class as a source of bean definitions. Methods annotated with `@Bean` register their return value as a Spring-managed bean.

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(); // Spring registers this and injects it anywhere PasswordEncoder is needed
}
```

### `@RequiredArgsConstructor` (Lombok)
Lombok generates a constructor for all `final` fields. Spring uses this constructor to inject dependencies — this is **constructor injection**, the recommended way to inject in Spring.

```java
@RequiredArgsConstructor
public class MatchmakingService {
    private final Queue<Long> waitingPlayers = new ConcurrentLinkedDeque<>();
    private final MatchRepository matchRepository;
    private final PlayerRepository playerRepository;
}
```

### `@Value`
Injects values from `application.properties` into a field.

```java
@Value("${jwt.secret}")
private String secret;
```

### `@Data` + `@NoArgsConstructor` (Lombok)
`@Data` generates getters, setters, `equals`, `hashCode`, and `toString`. `@NoArgsConstructor` generates a no-arg constructor — JPA requires this to instantiate entities when loading from the database.

```java
@Data
@NoArgsConstructor
public class Match { ... }
```

---

## How JWT Authentication Works (Flow)

### Registration (`POST /api/auth/register`)

```
Client → AuthController.register()
       → AuthService.register()
           → Check if username already exists
           → Create Player, hash password with BCrypt
           → Save to DB via PlayerRepository
           → JwtService.generateToken(username)
       → Return { "token": "eyJ..." }
```

### Login (`POST /api/auth/login`)

```
Client → AuthController.login()
       → AuthService.login()
           → Find player by username in DB
           → PasswordEncoder.matches(rawPassword, hashedPassword)
           → JwtService.generateToken(username)
       → Return { "token": "eyJ..." }
```

### Accessing a Protected Endpoint

```
Client sends: GET /api/whatever
              Authorization: Bearer eyJ...

→ JwtFilter.doFilterInternal()
    → Extract token from "Bearer " header
    → JwtService.extractUsername(token)       → get username from token
    → PlayerUserDetailsService.loadUserByUsername()  → load from DB
    → JwtService.isTokenValid(token)          → verify signature & expiry
    → Set authentication in SecurityContextHolder
→ Request continues to the controller
```

If no token or invalid token → request is rejected with 403.

---

## How Matchmaking Works

### Joining the queue (`POST /api/match/join`)

This endpoint is **protected** — you must send a JWT token. The controller reads your identity from Spring Security instead of asking the client to send their own ID (which would be unsafe).

```
Client sends: POST /api/match/join
              Authorization: Bearer eyJ...

→ MatchController.joinMatch()
    → SecurityContextHolder.getContext().getAuthentication().getName()
        → extracts the username from the JWT (set by JwtFilter earlier)
    → PlayerRepository.findPlayerByUsername(username)
        → loads the full Player from DB
    → MatchmakingService.joinQueue(player.getId())
        → if player is already in queue → throw exception
        → add player to waitingPlayers queue
        → if queue has 2+ players:
            → poll player1 and player2 from queue
            → create Match with status IN_PROGRESS
            → save to DB
            → return the Match
        → if queue has < 2 players → return null

← 202 Accepted  (still waiting for an opponent)
   OR
← 200 OK + Match object (match was created)
```

### The Matchmaking Queue

`MatchmakingService` holds an in-memory queue using `ConcurrentLinkedDeque`. This is thread-safe, which matters because multiple HTTP requests can arrive at the same time.

```java
private final Queue<Long> waitingPlayers = new ConcurrentLinkedDeque<>();
```

**Important:** this queue lives in memory. If the server restarts, all waiting players are lost. A production app would use a database or Redis for the queue instead.

### Checking match status (`GET /api/match/{matchId}`)

```
Client sends: GET /api/match/42
              Authorization: Bearer eyJ...

→ MatchController.getMatchStatus(42)
→ MatchmakingService.getMatchStatus(42)
    → MatchRepository.findById(42)
    → return match.getStatus()
← MatchStatus enum value (e.g. "IN_PROGRESS")
```

---

## MatchStatus Enum

```java
public enum MatchStatus {
    WAITING,      // match created but not yet started (not currently used by the queue flow)
    IN_PROGRESS,  // both players matched, game is live
    FINISHED      // game over
}
```

Enums are a great way to represent a fixed set of states. Spring + JPA handle the conversion to/from the database string automatically via `@Enumerated(EnumType.STRING)`.

---

## SecurityContextHolder

`SecurityContextHolder` is where Spring Security stores the currently authenticated user for the duration of a request. After `JwtFilter` validates the token and sets the authentication, any code in that request can read it:

```java
String username = SecurityContextHolder.getContext()
        .getAuthentication()
        .getName(); // returns the username from the JWT
```

This is how `MatchController` knows who is making the request without the client sending their own ID.

---

## Security Configuration (`SecurityConfig.java`)

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/auth/**").permitAll()  // register & login are public
    .anyRequest().authenticated()                 // everything else needs a token
)
.sessionManagement(session -> session
    .sessionCreationPolicy(SessionCreationPolicy.STATELESS) // no HTTP sessions — JWT only
)
.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class) // run our filter first
```

**Why disable CSRF?**
CSRF protection is needed for session-based apps. Since this API is stateless (JWT, no cookies/sessions), CSRF is not a threat here, so it's disabled.

---

## DTOs (Data Transfer Objects)

DTOs are simple objects used to carry data between the client and the server. They are NOT entities — they don't map to the database.

| DTO | Direction | Fields |
|-----|-----------|--------|
| `RegisterRequest` | Client → Server | username, email, password |
| `LoginRequest` | Client → Server | username, password |
| `AuthResponse` | Server → Client | token |

This keeps your API contract separate from your database model (`Player`).

---

## Password Hashing

Passwords are **never stored in plain text**. `BCryptPasswordEncoder` hashes them before saving:

```java
// Saving (in AuthService)
newPlayer.setPassword(passwordEncoder.encode(registerRequest.getPassword()));

// Validating (in AuthService.login)
passwordEncoder.matches(rawPassword, storedHashedPassword) // returns true/false
```

BCrypt is a one-way hash — you can't reverse it. `matches()` re-hashes the raw input and compares.

---

## JWT Structure

A JWT looks like: `header.payload.signature`

- **Header**: algorithm used (HS256)
- **Payload**: data inside the token (here: username, issued-at, expiration)
- **Signature**: proves the token wasn't tampered with (signed with the secret key)

The app reads `jwt.secret` and `jwt.expiration` from `application.properties`.

---

## Known Bug

In `AuthService.java` line 27:
```java
newPlayer.setUsername(registerRequest.getPassword()); // BUG: should be getUsername()
```
This sets the username to the password value. The register flow will save the wrong username.

---

## Dependency Injection Summary

Spring creates and manages all beans. You never call `new SomeService()` manually. Instead:

1. Spring scans for `@Component`, `@Service`, `@Repository`, `@Controller`, `@Configuration`
2. It builds a dependency graph based on constructors / `@Autowired`
3. It creates them in the right order and injects them

Example chain:
```
AuthController
  └── AuthService
        ├── PlayerRepository  (managed by Spring Data JPA)
        ├── PasswordEncoder   (bean from SecurityConfig)
        └── JwtService        (managed by Spring)

MatchController
  ├── MatchmakingService
  │     ├── MatchRepository   (managed by Spring Data JPA)
  │     └── PlayerRepository  (managed by Spring Data JPA)
  └── PlayerRepository
```