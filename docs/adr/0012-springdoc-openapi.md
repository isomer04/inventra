+++
adr = "0012"

[[covers]]
id = "org.springdoc:springdoc-openapi-starter-webmvc-ui"
version = "${springdoc.version}"
manifest = "backend/pom.xml"
+++

# ADR-0012: API Documentation — SpringDoc OpenAPI 3

## Status
Accepted

## Context
Inventra's REST API requires machine-readable documentation for:
- **Frontend development** — Angular frontend needs accurate endpoint paths, request/response schemas, and authentication requirements
- **API testing** — QA and developers need an interactive UI to test endpoints without writing curl commands
- **Contract validation** — OpenAPI spec can be used in contract tests to verify API responses match documented schemas

SpringDoc OpenAPI automatically generates OpenAPI 3 specifications from Spring MVC controllers, with support for Spring Security authentication schemes and Jakarta Bean Validation constraints.

Source manifest: `backend/pom.xml`

## Decision
Use **springdoc-openapi-starter-webmvc-ui 3.0.3**.

This starter includes:
- OpenAPI 3 spec generation from `@RestController` classes
- Swagger UI at `/swagger-ui.html` for interactive API testing
- OpenAPI JSON spec at `/v3/api-docs`

## Consequences

### Advantages
- **Zero-configuration documentation** — SpringDoc scans `@RestController` classes and generates OpenAPI spec automatically; no manual YAML writing required
- **Swagger UI** — interactive web UI at `/swagger-ui.html` allows developers to test endpoints, view request/response examples, and authenticate with JWT tokens
- **Validation integration** — Jakarta Bean Validation constraints (`@NotNull`, `@Size`, `@Email`) are reflected in the OpenAPI spec's schema definitions
- **Security scheme support** — SpringDoc detects Spring Security's JWT authentication and adds `securitySchemes` to the OpenAPI spec

### Disadvantages
- **Spec accuracy** — auto-generated OpenAPI spec may not capture all edge cases (e.g., conditional response schemas, polymorphic types); manual `@Schema` annotations are needed for complex cases
- **Swagger UI security** — `/swagger-ui.html` exposes API structure and should be disabled or secured in production (mitigated by Spring Security configuration)

### Mitigations
- Use `@Operation`, `@ApiResponse`, and `@Schema` annotations to refine auto-generated documentation for complex endpoints
- Disable Swagger UI in production via `springdoc.swagger-ui.enabled=false` property, or restrict access to internal network

### Alternatives considered
- **Springfox** — Rejected. Springfox is no longer actively maintained (last release: 2020); SpringDoc is the recommended replacement for Spring Boot 3+.
- **Manual OpenAPI YAML** — Rejected. Writing OpenAPI specs by hand is error-prone and requires manual updates for every API change. SpringDoc's auto-generation keeps documentation in sync with code.
- **Redoc** — Rejected. Redoc provides a cleaner documentation UI than Swagger UI, but does not support interactive API testing. Swagger UI's "Try it out" feature is more useful for development.

### References
- https://springdoc.org/
- https://github.com/springdoc/springdoc-openapi
