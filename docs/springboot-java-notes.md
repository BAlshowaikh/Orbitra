# Spring Boot & Java — Learning Notes

Concepts covered while building `auth-service`'s security layer (steps 8–13:
`SecurityConfig`, `JwtService`, `AuthService`, `JwtAuthFilter`, `AuthController`,
`GlobalExceptionHandler`). Each section explains the *general* concept first,
then points at where it showed up in our code as a working example.

---

## 1. Java Records

A `record` is a special kind of class, introduced in Java 16, for data that is
just a bundle of fields with no real behavior of its own.

```java
public record LoginRequest(
        @Email @NotBlank String email,
        @NotBlank String password
) {}
```

Writing this one line gives you, for free, everything you'd otherwise have to
hand-write in a normal class:

- A constructor taking `email` and `password`
- Accessor methods `email()` and `password()` (note: no `get` prefix, unlike a
  regular Java bean)
- `equals()`, `hashCode()`, and `toString()`

**Why it matters:** records are a great fit for DTOs (Data Transfer Objects —
the shapes that cross an HTTP boundary) precisely *because* they can't hold
extra behavior or mutable state. A DTO's whole job is to be data, nothing else
— `LoginRequest`, `RegisterRequest`, `AuthResponse`, `MeResponse`, and
`ErrorResponse` are all records in this project.

---

## 2. Bean Validation (Jakarta Validation)

Annotations like `@Email`, `@NotBlank`, `@Size` are **inert by themselves** —
they're just metadata sitting on a field. Nothing happens until something
reads them.

```java
public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password,
        @NotNull Role role,
        PartnerType partnerType
) {}
```

The thing that reads them is `@Valid`, placed on a controller parameter:

```java
public AuthResponse register(@Valid @RequestBody RegisterRequest request) { ... }
```

When a request comes in, Spring:
1. Uses `@RequestBody` to deserialize the JSON into a `RegisterRequest` object.
2. Because of `@Valid`, checks every validation annotation on that object.
3. If anything fails, throws `MethodArgumentNotValidException` — **before your
   method body ever runs.**

**Why it matters:** validation happens at the boundary, not buried inside
business logic. `AuthService` never has to defensively check "is this email
blank?" — by the time it receives a `RegisterRequest`, that's already
guaranteed.

Some rules can't be expressed as a single field annotation — e.g. "`partnerType`
is required only when `role == PARTNER`" is a *cross-field* rule. Those get
checked explicitly in business logic instead (see `AuthService.register()`).

---

## 3. Inversion of Control & Dependency Injection

Normally in Java, if class A needs class B, A calls `new B()` itself. Spring
flips this around: you *declare* what you need, and the framework builds it
and hands it to you.

```java
@Configuration
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

- **`@Configuration`** marks a class as a source of bean definitions — Spring
  scans it at startup and runs its `@Bean` methods.
- **`@Bean`** marks a method whose return value should be managed by Spring.
  The object is built once and reused everywhere it's needed.

Then, elsewhere, any class can just *ask* for that object via its constructor:

```java
public AuthService(AccountRepository accountRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
    this.accountRepository = accountRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
}
```

Spring sees that `AuthService` needs a `PasswordEncoder`, finds the bean
defined in `SecurityConfig`, and passes it in automatically. This is called
**constructor injection** — the preferred style, since it makes dependencies
explicit and the object impossible to construct in a half-initialized state.

**Why it matters:** nobody writes `new BCryptPasswordEncoder()` scattered
around the codebase. The recipe is defined once; the same instance is reused
everywhere; and swapping the implementation later means changing one method.

---

## 4. Spring Stereotypes

Spring needs to know which classes it should manage as beans. A handful of
annotations mark this:

| Annotation | Used for |
|---|---|
| `@Configuration` | A class whose `@Bean` methods produce infrastructure objects |
| `@Service` | A class holding business logic (`AuthService`) |
| `@RestController` | A class handling HTTP requests and returning JSON (`AuthController`) |
| `@Component` | The generic catch-all for anything else Spring should manage (`JwtService`, `JwtAuthFilter`) |

These are functionally similar (they all make Spring create and track an
instance of the class), but the specific name documents *intent* — a future
reader instantly knows `@Service` holds logic and `@RestController` handles
HTTP, without reading the class body.

---

## 5. Spring Security: the Filter Chain

Every HTTP request to a Spring Security–protected app passes through a chain
of **filters** before it ever reaches your controller. `SecurityConfig`
configures that chain:

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/register", "/auth/login").permitAll()
                .anyRequest().authenticated()
        )
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
}
```

Three separate decisions bundled into one chain:

- **CSRF (Cross-Site Request Forgery) protection** guards against a browser
  tricking a logged-in user's *session cookie* into firing an unwanted
  request. It's meaningless for a stateless, cookie-free JWT API — there's no
  session cookie to forge — so it's disabled here. (This would matter again if
  the app ever used cookie-based sessions.)
- **Session policy** — `STATELESS` tells Spring Security "never create or read
  an `HttpSession`." Every request must prove its own identity from scratch.
  This is what makes the service horizontally scalable (any server instance
  can handle any request — nothing sticky about a particular server holding a
  particular user's session).
- **Authorization rules** — a simple allow-list: certain paths are public
  (`permitAll`), everything else requires an authenticated request.

**Why it matters:** security concerns are centralized in one configuration
class, rather than each controller manually checking "is this user logged
in?"

---

## 6. Password Hashing

Passwords are never stored or compared in plain text.

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

BCrypt is a one-way hash function with a built-in random salt:

- **On register**: `passwordEncoder.encode(rawPassword)` produces a hash;
  only the hash is saved to the database.
- **On login**: `passwordEncoder.matches(rawPassword, storedHash)` re-hashes
  the input the same way and compares — there's no way to reverse a hash back
  into the original password, so this comparison method is the only way to
  check a match.

**Why it matters:** if the database is ever leaked, raw passwords are never
exposed — only hashes, which are computationally expensive to reverse.

---

## 7. JWT (JSON Web Tokens)

A JWT is a compact, **signed** (not encrypted) token used to prove identity
without a server-side session. It has three parts, separated by dots:
`header.payload.signature`.

```java
public String generateToken(Account account) {
    return Jwts.builder()
            .subject(account.getId().toString())
            .claim("role", account.getRole().name())
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact();
}
```

- **`subject` (the `sub` claim)** — the primary identifier the token is
  about. Here it's the account's id, not the email — a stable, storage-safe
  identifier that other services can use to look up "whose data is this?"
- **Custom claims** (like `role`) — extra facts embedded in the token so
  downstream code doesn't need a database round-trip to know them.
- **Signature** — computed from the header + payload using a secret key only
  the server has (`Keys.hmacShaKeyFor(secret)`). This is the mechanism that
  makes the token tamper-evident.

**Signed, not encrypted, means:** anyone can decode and *read* a JWT's payload
(it's just base64) — never put actual secrets in there. But nobody can
*modify* it and have it still verify, because changing even one character of
the payload invalidates the signature:

```java
private Claims parseClaims(String token) {
    return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)   // throws if signature doesn't match
            .getPayload();
}
```

**Why it matters:** this is what enables *stateless* authentication — the
server doesn't need to remember anything about a login. Each request carries
its own proof of identity, verifiable with just the secret key, and that's
also why JWTs pair naturally with `SessionCreationPolicy.STATELESS` above.

---

## 8. Custom Servlet Filters (`OncePerRequestFilter`)

A filter is code that runs on *every* request, before it reaches a
controller. Spring Security's default authentication mechanisms (form login,
HTTP Basic) are themselves implemented as filters — ours is a custom one for
JWTs:

```java
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);   // let it through, unauthenticated
            return;
        }

        String token = header.substring("Bearer ".length());

        if (jwtService.isTokenValid(token)) {
            // ... populate SecurityContext (see next section) ...
        }

        filterChain.doFilter(request, response);   // always continue the chain
    }
}
```

- **`OncePerRequestFilter`** guarantees this logic runs exactly once per
  request, even in edge cases (like internal servlet forwards) that might
  otherwise trigger a filter twice.
- **`filterChain.doFilter(request, response)`** is the hand-off to the next
  filter/servlet in line. It's called unconditionally — this filter's job is
  only to *optionally attach identity*, never to itself block a request. The
  actual allow/deny decision belongs to `authorizeHttpRequests` back in
  `SecurityConfig`.
- **Filter ordering matters.** `addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)`
  tells Spring Security to run our filter *before* its own built-in
  authentication filter, so identity is already resolved by the time later
  checks run.

---

## 9. `SecurityContextHolder` and `Authentication`

Think of `SecurityContextHolder` as a sticky note, valid only for the current
request, that says "here's who's making this request."

```java
List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
Authentication authentication = new UsernamePasswordAuthenticationToken(accountId, null, authorities);
SecurityContextHolder.getContext().setAuthentication(authentication);
```

- **`GrantedAuthority`** — Spring Security's representation of "what this
  user is allowed to do." The `"ROLE_"` prefix is a convention required for
  role-based checks (like `.hasRole("TRAVELER")`) to recognize it.
- **`UsernamePasswordAuthenticationToken`** — despite the name, this is just a
  generic container for "who is this" (principal), "what proves it" (
  credentials — `null` here, since the JWT itself already proved identity),
  and "what can they do" (authorities).
- **`SecurityContextHolder`** — by default backed by a `ThreadLocal`, so this
  is genuinely per-request. Combined with `STATELESS` sessions, nothing here
  is remembered between requests — every single request rebuilds this from
  scratch by re-validating its JWT.

**Why it matters:** anything downstream — `authorizeHttpRequests`'s
`anyRequest().authenticated()` check, or a controller reading "who called
this?" — reads from this one shared, per-request place, rather than each
piece of code re-parsing the token itself.

---

## 10. Reading `Authentication` in a Controller

Since `Authentication` implements `java.security.Principal`, Spring MVC can
inject it directly as a controller method parameter — no special annotation
needed:

```java
@GetMapping("/me")
public MeResponse me(Authentication authentication) {
    Long accountId = (Long) authentication.getPrincipal();
    Role role = Role.valueOf(extractRoleName(authentication));
    return new MeResponse(accountId, role);
}
```

Reaching this method at all already implies the JWT filter validated the
token — so this reads identity straight from what's already in
`SecurityContextHolder`, with **no database lookup required**.

---

## 11. REST Controllers & HTTP Mapping

```java
@RestController
@RequestMapping("/auth")
public class AuthController {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }
}
```

- **`@RestController`** = `@Controller` + `@ResponseBody`. Whatever a method
  returns gets serialized straight to JSON (via Jackson) and becomes the
  response body — you never manually build the JSON string.
- **`@RequestMapping("/auth")`** on the class sets a shared path prefix;
  `@PostMapping("/register")` / `@GetMapping("/me")` add the method and
  sub-path for each handler.
- **`@RequestBody`** deserializes the incoming JSON into the parameter type.
- **`@ResponseStatus(HttpStatus.CREATED)`** overrides Spring's default
  success status (`200`) — used here because registering *creates* a new
  resource (an `Account` row), which is what `201 Created` conventionally
  signals. Endpoints that don't create anything (like `login`) keep the
  default `200`.

**Controllers stay thin.** Notice `register()` and `login()` do nothing but
delegate to `AuthService` and return its result — no business logic lives in
the controller layer.

---

## 12. `ResponseEntity<T>` vs `@ResponseStatus`

Two different tools for controlling a response's status code:

- **`@ResponseStatus`** — fixed at compile time, good when a method (or an
  exception class) always maps to the same status.
- **`ResponseEntity<T>`** — built programmatically, good when the status
  depends on runtime logic:

```java
private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message) {
    ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), message);
    return ResponseEntity.status(status).body(body);
}
```

Our exception handlers use `ResponseEntity` because *which* status to return
(`400`, `409`, `500`) depends on which exception was caught.

---

## 13. Centralized Exception Handling

Two Java rules combine here: an exception, once thrown and not caught by a
`try/catch`, keeps propagating up the call stack until *something* catches
it. `@RestControllerAdvice` is Spring's mechanism for being that "something,"
globally, for every controller in the app:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmail(DuplicateEmailException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong");
    }
}
```

Key ideas:

- **A custom exception class defines *what* went wrong; an `@ExceptionHandler`
  defines *what to do about it*.** Throwing `DuplicateEmailException` on its
  own does nothing but unwind the stack — nothing tells Spring "turn this into
  a 409" until a handler exists for it. Without one, it would fall through to
  whatever *is* handled — in our case, the generic `Exception` handler,
  producing a misleading `500` for something that isn't actually a bug.
- **Spring picks the most specific matching handler.** A `DuplicateEmailException`
  matches both `handleDuplicateEmail` (exact type) and `handleUnexpected`
  (its ancestor, `Exception`) — Spring always prefers the more specific match.
  This is why a broad `catch(Exception)`-style fallback handler is safe to
  have: it only ever catches what nothing more specific already claimed.
- **Never leak internals to the client.** The fallback logs the *full*
  exception (with stack trace) server-side via `log.error`, but returns only
  a generic message to the caller. Exposing a raw exception message (e.g. a
  SQL error naming a table/column) is a real information-disclosure risk.
- **One consistent response shape.** Every failure — validation, business
  rule, or genuine bug — returns the same `ErrorResponse(timestamp, status,
  message)` shape, so client code has one format to parse regardless of
  cause.

---

## 14. Custom Exceptions for Precise HTTP Semantics

Not every failure deserves the same status code, even if they're all "the
request didn't succeed."

```java
public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException(String message) {
        super(message);
    }
}
```

The distinction that matters:
- **`400 Bad Request`** — the request itself is malformed (e.g. an
  invalid email format). The problem is with the request's content, on its
  own terms.
- **`409 Conflict`** — the request is perfectly well-formed, but conflicts
  with existing server state (e.g. that email is valid, it's just already
  taken by someone else).

Introducing a dedicated exception type — rather than reusing a generic
`IllegalArgumentException` for every failure — lets the exception handler
give each *kind* of failure its semantically correct status code.

(Note: `@ResponseStatus(HttpStatus.CONFLICT)` could be placed directly on the
exception class instead, letting Spring auto-map the status with no handler
method at all. We didn't use that here specifically because it would fall
back to Spring Boot's *default* error body shape instead of our own
`ErrorResponse` — once you want a consistent custom body across your whole
API, an explicit `@ExceptionHandler` is what keeps that consistent.)

---

## 15. HTTP Status Code Semantics (cheat sheet)

| Code | Meaning | Example in this project |
|---|---|---|
| `200 OK` | General success | Successful login |
| `201 Created` | A new resource was created | Successful registration |
| `400 Bad Request` | Malformed/invalid input | Bad login credentials, disabled account |
| `401 Unauthorized` | Not authenticated at all | Missing/invalid JWT (handled by Spring Security itself, before reaching our controllers) |
| `403 Forbidden` | Authenticated, but not allowed | Would apply to a role-restricted endpoint |
| `409 Conflict` | Valid request, conflicts with existing state | Duplicate email at registration |
| `500 Internal Server Error` | Unexpected server-side failure | Our catch-all fallback handler |

Worth remembering: **`401`/`403` from Spring Security happen in the filter
chain**, before a request ever reaches `DispatcherServlet`/your controllers —
so `@RestControllerAdvice` never sees them at all. Getting those into the
same response shape as everything else would require configuring a custom
`AuthenticationEntryPoint`/`AccessDeniedHandler` separately.

---

## 16. Logging with SLF4J

Java's equivalent of `console.log`/`console.error` is a `Logger`:

```java
private static final Logger log = LoggerFactory.getLogger(AuthService.class);

log.warn("Failed login attempt for email={} - password mismatch", request.email());
log.info("Successful login for account id={} email={}", account.getId(), account.getEmail());
```

- **`LoggerFactory.getLogger(SomeClass.class)`** — conventionally one static
  logger per class, tagged with that class's name so log lines show their
  origin.
- **Log levels** (`info`, `warn`, `error`, ...) — let you filter verbosity
  without changing code; production environments often show only `warn`+.
- **Placeholders (`{}`)** — avoids string concatenation; the logging
  framework only builds the final string if that level is actually enabled.
- **Never log secrets.** Passwords and JWTs never appear in a log line —
  only safe identifiers like email or account id.
- **Where logs go**: your terminal (wherever `spring-boot:run` is executing),
  not the browser's DevTools — that's a separate, client-side JavaScript
  console with zero visibility into the server process.

---

## 17. `@Transactional`

```java
@Transactional
public AuthResponse register(RegisterRequest request) {
    // ... checks ...
    accountRepository.save(account);
    // ...
}
```

Marks a method's database operations as one atomic unit — if anything after
a write fails, the whole thing rolls back rather than leaving a half-completed
change. Only needed on methods that *write*; `login()` has no
`@Transactional` since it only reads.

---

## Recap: how it all fits together for one request

For `POST /auth/register` with an email that's already taken:

1. Request hits the **filter chain** — `JwtAuthFilter` runs but the path is
   `permitAll`, so it doesn't matter whether a token is present.
2. `DispatcherServlet` routes to `AuthController.register()`, binding JSON
   into a `RegisterRequest` via `@RequestBody`.
3. `@Valid` runs Bean Validation — passes, since the email is validly
   *formatted* (uniqueness isn't a field-level rule).
4. `AuthController` delegates to `AuthService.register()`.
5. `AuthService` finds the email already exists, logs a `warn`, and throws
   `DuplicateEmailException`.
6. The exception propagates, uncaught, out of both methods.
7. `GlobalExceptionHandler`'s `@ExceptionHandler(DuplicateEmailException.class)`
   catches it — the most specific match Spring can find.
8. It builds a `ResponseEntity<ErrorResponse>` with status `409`.
9. Spring serializes that to JSON and sends it back — the client gets a
   clean, predictable error body, never a raw stack trace.
