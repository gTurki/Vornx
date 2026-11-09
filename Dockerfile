# How Railway builds and runs VORNX. Two stages:
#   1. "build": a full JDK + Maven compiles the code into one .jar
#   2. "run":   a small Java runtime only runs that .jar (no source code, no Maven -> smaller and safer)

# ---------- 1. Build ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy the Maven files first and download dependencies: Docker caches this layer,
# so later builds only re-download when pom.xml changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

COPY src/ src/
# Tests that need a database are run on your PC; the image build only packages.
RUN ./mvnw -q -B package -DskipTests

# ---------- 2. Run ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Never run as root inside the container.
RUN useradd --system --create-home vornx
USER vornx

COPY --from=build /app/target/*.jar app.jar

# Use up to 75% of the container's memory; dates default to UTC.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
