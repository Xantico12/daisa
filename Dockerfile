# --- build stage ----------------------------------------------------
# Uses a full JDK to compile the sources with the project's own
# javac-driven build script. Keeps parity with how developers build
# locally (no Maven/Gradle is introduced just for the container).
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY src ./src
COPY scripts ./scripts
RUN mkdir -p build/classes \
    && find src/main/java -name "*.java" | sort > build/main-sources.txt \
    && javac -encoding UTF-8 -d build/classes @build/main-sources.txt

# --- runtime stage --------------------------------------------------
# JRE only — smaller image, no compiler. The vault is expected to be
# mounted at /vault by the operator; DAISA writes per-course artifacts
# back into that mounted directory.
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/build/classes ./classes
VOLUME ["/vault"]
ENV DAISA_OLLAMA_URL=http://host.docker.internal:11434
ENV DAISA_OLLAMA_MODEL=llama3.2
ENTRYPOINT ["java", "-cp", "classes", "daisa.App", "/vault"]
