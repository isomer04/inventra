# Build stage — uses official Maven image for reproducible builds
# Pinned to SHA-256 digest for supply chain integrity
# To update: docker pull maven:3-eclipse-temurin-26 && docker pull eclipse-temurin:25-jre
#            then update the digests below (last verified: 2026-05-23)
# The build image is JDK 26 while the runtime is JRE 25; this is safe because the Maven
# build targets release=25. Keep the two in step when bumping either.
FROM maven:3-eclipse-temurin-26@sha256:6ce830fe4c216ab9baaf63d3899d8d178432658bdeb9092cca2bf573475b6b6e AS build
WORKDIR /build

# Cache dependencies as a separate layer (only re-downloads when pom.xml changes)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Build the application. Tests are deliberately skipped here — they run in CI, which
# gates the image build; running them again inside Docker would only slow the build.
COPY src ./src
RUN mvn package -DskipTests -q

# Runtime stage
FROM eclipse-temurin:25-jre@sha256:5cf92df78f6dba978777d5cffa3c856e583f86814fde82a6c3534ccdfd794f2f
# Install curl for the Docker healthcheck
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN addgroup --system inventra && adduser --system --ingroup inventra inventra
WORKDIR /app
COPY --from=build /build/target/inventra-api-1.0.0.jar app.jar
USER inventra
ENTRYPOINT ["java", "-jar", "app.jar"]
