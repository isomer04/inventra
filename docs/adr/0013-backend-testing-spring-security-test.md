+++
adr = "0013"

[[covers]]
id = "org.springframework.boot:spring-boot-starter-test"
version = "4.0.6"
manifest = "backend/pom.xml"

[[covers]]
id = "org.springframework.boot:spring-boot-starter-webmvc-test"
version = "4.0.6"
manifest = "backend/pom.xml"

[[covers]]
id = "org.springframework.security:spring-security-test"
version = "7.0.3"
manifest = "backend/pom.xml"
+++

# ADR-0013: Backend Testing — Spring Boot Test and Spring Security Test

## Status
Accepted

## Context
Inventra's backend requires comprehensive testing coverage:
- **Unit tests** — service layer logic (business rules, validation, error handling)
- **Integration tests** — controller layer (request routing, JSON serialization, HTTP status codes)
- **Security tests** — authentication and authorization rules (JWT validation, role-based access control)

Spring Boot Test provides test slices (`@WebMvcTest`, `@DataJpaTest`) that load only the necessary Spring context for specific layers, reducing test startup time. Spring Security Test provides utilities for mocking authenticated users and testing security rules.

Source manifest: `backend/pom.xml`

## Decision
Use **spring-boot-starter-test**, **spring-boot-starter-webmvc-test**, and **spring-security-test** (versions resolved via Spring Boot BOM 4.0.6).

These starters include:
- JUnit 5 (Jupiter) for test execution
- Mockito for mocking dependencies
- AssertJ for fluent assertions
- MockMvc for testing Spring MVC controllers without starting a full HTTP server
- `@WithMockUser` and `@WithUserDetails` for testing security rules

## Consequences

### Advantages
- **Test slices** — `@WebMvcTest` loads only the web layer (controllers, filters, exception handlers), making controller tests fast (< 1 second startup)
- **MockMvc** — tests HTTP requests and responses without starting an embedded Tomcat server, reducing test execution time
- **Security test utilities** — `@WithMockUser(roles = "ADMIN")` simulates an authenticated user with specific roles, allowing security rule testing without real JWT tokens
- **AssertJ fluent assertions** — `assertThat(response.getStatus()).isEqualTo(200)` provides more readable assertions than JUnit's `assertEquals`

### Disadvantages
- **Test slice limitations** — `@WebMvcTest` does not load the full Spring context, so service layer dependencies must be mocked; this can lead to brittle tests if service interfaces change frequently
- **MockMvc vs. real HTTP** — MockMvc tests do not exercise the full HTTP stack (no Tomcat, no network I/O), so some edge cases (e.g., request timeout handling) are not covered

### Mitigations
- Use `@SpringBootTest` with `@AutoConfigureMockMvc` for end-to-end integration tests that require the full Spring context
- Supplement MockMvc tests with Testcontainers-based integration tests (ADR-0014) that use real HTTP requests

### Alternatives considered
- **REST Assured** — Rejected. REST Assured provides a fluent API for testing REST APIs, but requires starting a full HTTP server (slower than MockMvc). MockMvc is sufficient for most controller tests.
- **JUnit 4** — Rejected. JUnit 5 (Jupiter) provides better extension model, parameterized tests, and nested test classes. Spring Boot 3+ defaults to JUnit 5.

### References
- https://docs.spring.io/spring-boot/reference/testing/
- https://docs.spring.io/spring-security/reference/servlet/test/
