+++
adr = "0006"

[[covers]]
id = "org.springframework.boot:spring-boot-starter-web"
version = "4.0.6"
manifest = "backend/pom.xml"

[[covers]]
id = "org.springframework.boot:spring-boot-starter-validation"
version = "4.0.6"
manifest = "backend/pom.xml"
+++

# ADR-0006: Spring Boot Web and Validation Starters

## Status
Accepted

## Context
Inventra's backend exposes a REST API for inventory, order, and tenant management operations. The API must handle HTTP requests, serialize/deserialize JSON payloads, validate input data, and return appropriate HTTP status codes and error responses.

Spring Boot provides two starters that bundle the necessary dependencies and auto-configuration:
- `spring-boot-starter-web` — includes Spring MVC, embedded Tomcat, and Jackson for JSON processing
- `spring-boot-starter-validation` — includes Hibernate Validator (Jakarta Bean Validation reference implementation) for declarative input validation

Both starters are versioned via the Spring Boot BOM (4.0.6), ensuring compatible transitive dependencies.

Source manifest: `backend/pom.xml`

## Decision
Use **spring-boot-starter-web** and **spring-boot-starter-validation** (versions resolved via Spring Boot BOM 4.0.6).

## Consequences

### Advantages
- **Zero-configuration REST controllers** — `@RestController` and `@RequestMapping` annotations automatically handle request routing, JSON serialization, and exception translation
- **Declarative validation** — `@Valid` on controller method parameters triggers Jakarta Bean Validation constraints (`@NotNull`, `@Size`, `@Email`, etc.) with automatic 400 Bad Request responses for invalid input
- **Embedded servlet container** — Tomcat is bundled and auto-configured, eliminating the need for external application server deployment
- **Production-ready defaults** — Jackson is pre-configured with sensible settings (ISO-8601 date formatting, null-safe serialization)

### Disadvantages
- **Tomcat overhead** — embedded Tomcat adds ~30 MB to the application JAR and ~50 MB to runtime memory usage (acceptable for server-side deployment)
- **Validation error messages** — default error responses expose internal field names and constraint details, which may leak implementation details to API consumers (mitigated by custom `@ControllerAdvice` exception handler)

### Mitigations
- Custom `@ControllerAdvice` class maps validation exceptions to sanitized error responses with user-friendly messages
- Tomcat overhead is acceptable for cloud deployment (2 GB+ RAM instances)

### Alternatives considered
- **Spring WebFlux** — Rejected. Reactive programming model (Mono/Flux) adds complexity without clear benefit for Inventra's use case (database-bound operations, not high-concurrency streaming)
- **JAX-RS (Jersey / RESTEasy)** — Rejected. Spring MVC provides better integration with Spring Security and Spring Data JPA, and the team has existing Spring MVC experience

### References
- https://docs.spring.io/spring-boot/reference/web/servlet.html
- https://docs.spring.io/spring-boot/reference/io/validation.html
