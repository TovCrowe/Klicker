# Klicker - Spring Boot JWT Authentication

A Spring Boot REST API that handles player registration and login using JWT (JSON Web Tokens) for stateless authentication.

---

## What the app does

- Players can **register** with a username, email, and password
- Players can **login** and receive a JWT token
- Protected endpoints require the JWT token in the request header
- Player data (username, email, password, wins, losses) is stored in a database via JPA

---

## Project Structure

```
src/main/java/com/project/Klicker/
│
├── KlickerApplication.java          # Entry point
│
├── Entities/
│   └── Player.java                  # Database model
│
├── Repository/
│   └── PlayerRepository.java        # Database access
│
├── DTO/
│   ├── RegisterRequest.java         # Request body for /register
│   ├── LoginRequest.java            # Request body for /login
│   └── AuthResponse.java            # Response containing the JWT token
│
├── Controllers/
│   └── AuthController.java          # HTTP endpoints
│
├── Jwt/
│   ├── AuthService.java             # Register/login business logic
│   ├── JwtService.java              # JWT creation and validation
│   └── JwtFilter.java               # Intercepts requests to check JWT
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

### `@Repository` (via `JpaRepository`)
Extending `JpaRepository<Player, Long>` gives you free CRUD methods (`save`, `findById`, `findAll`, etc.) without writing SQL. You can also declare custom methods by name:

```java
Optional<Player> findPlayerByUsername(String username);
// Spring reads the method name and builds the query for you
```

### `@Service`
Marks a class as a business logic component. Spring manages it as a bean you can inject elsewhere.

### `@RestController` + `@RequestMapping`
Marks a class as a REST controller. Every method returns JSON by default (no need for `@ResponseBody`).

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController { ... }
```

### `@PostMapping` + `@RequestBody`
Maps a method to an HTTP POST request. `@RequestBody` deserializes the JSON body into a Java object.

```java
@PostMapping("/login")
public AuthResponse login(@RequestBody LoginRequest request) { ... }
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
public class AuthService {
    private final PlayerRepository playerRepository; // injected via constructor
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
}
```

### `@Value`
Injects values from `application.properties` into a field.

```java
@Value("${jwt.secret}")
private String secret;
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
```