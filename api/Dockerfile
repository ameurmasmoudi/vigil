FROM eclipse-temurin:25-jdk AS builder
WORKDIR /build

RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

COPY pom.xml .
COPY mvnw .
COPY .mvn/ .mvn/

RUN chmod +x mvnw

COPY src ./src


RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw package -DskipTests

RUN java -Djarmode=tools -jar target/vigil-api.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:25-jre
RUN rm -f /usr/bin/pebble

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

RUN groupadd --gid 1001 vigiluser \
    && useradd --uid 1001 --gid 1001 -m vigiluser

RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /vigil
RUN chown -R vigiluser:vigiluser /vigil
USER vigiluser

COPY --from=builder /build/extracted/dependencies/ ./
COPY --from=builder /build/extracted/spring-boot-loader/ ./
COPY --from=builder /build/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/extracted/application/ ./

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
