+++
adr = "0011"

[[covers]]
id = "lombok-mapstruct"
version = "1.6.3"
manifest = "backend/pom.xml"
+++

# ADR-0011: Developer Utilities — Lombok and MapStruct

## Status
Accepted

## Context
Inventra's backend requires:
- **Boilerplate reduction** — JPA entities, DTOs, and service classes need getters, setters, constructors, `equals()`, `hashCode()`, and `toString()` methods
- **DTO mapping** — converting between JPA entities and API DTOs (e.g., `Order` entity → `OrderResponse` DTO) requires repetitive field-by-field copying

Lombok generates boilerplate code at compile time via annotation processing (`@Data`, `@Builder`, `@NoArgsConstructor`). MapStruct generates type-safe DTO mappers at compile time from interface definitions.

Source manifest: `backend/pom.xml`

## Decision
Use **Lombok 1.18.38** and **MapStruct 1.6.3** with the **lombok-mapstruct-binding 0.2.0** compatibility layer.

The binding artifact ensures Lombok's annotation processor runs before MapStruct's, allowing MapStruct to see Lombok-generated getters/setters.

## Consequences

### Advantages
- **Reduced boilerplate** — `@Data` generates getters, setters, `equals()`, `hashCode()`, and `toString()` for JPA entities and DTOs
- **Immutable builders** — `@Builder` provides fluent builder pattern for complex object construction (e.g., `Order.builder().tenantId(1L).status(OrderStatus.PENDING).build()`)
- **Type-safe mapping** — MapStruct generates compile-time-checked mapper implementations from interface definitions; field name mismatches are caught at compile time, not runtime
- **Performance** — MapStruct-generated mappers use direct field access (no reflection), making them faster than runtime mapping libraries (ModelMapper, Dozer)

### Disadvantages
- **IDE integration** — Lombok requires IDE plugin installation (IntelliJ IDEA, Eclipse, VS Code) to recognize generated methods; without the plugin, IDEs show false errors
- **Debugging** — Lombok-generated code is not visible in source files, making step-through debugging harder (mitigated by delombok tool for generating source code)
- **Annotation processor ordering** — Lombok and MapStruct must run in the correct order (Lombok first, then MapStruct); the `lombok-mapstruct-binding` artifact enforces this, but adds a dependency

### Mitigations
- Document Lombok IDE plugin requirement in project README
- Use `@Builder` sparingly for complex objects only; prefer constructors for simple DTOs
- The `lombok-mapstruct-binding` artifact is maintained by the Lombok team and ensures correct annotation processor ordering

### Alternatives considered
- **Java records (JDK 16+)** — Rejected for entities. Records are immutable by design, but JPA entities require mutable fields for lazy loading and dirty checking. Records are acceptable for DTOs, but Lombok's `@Builder` provides more flexibility.
- **ModelMapper / Dozer** — Rejected. Runtime reflection-based mapping is slower than MapStruct's compile-time code generation and provides no compile-time safety for field name mismatches.
- **Manual DTO mapping** — Rejected. Writing field-by-field mapping code is error-prone and time-consuming; MapStruct's generated mappers are faster and safer.

### References
- https://projectlombok.org/
- https://mapstruct.org/
