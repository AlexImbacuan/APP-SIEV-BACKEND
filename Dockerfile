# ============================================================
# Etapa 1: compilar el proyecto con Maven
# ============================================================
FROM maven:3.9-amazoncorretto-21 AS build

WORKDIR /app

# Copiar primero solo el pom.xml y descargar las dependencias.
# Docker guarda este paso en caché: mientras el pom.xml no cambie,
# no vuelve a descargar las dependencias en cada build.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copiar el código fuente y generar el JAR (sin tests, porque
# necesitan una base de datos que no existe durante el build)
COPY src ./src
RUN mvn package -DskipTests -B

# ============================================================
# Etapa 2: imagen final, solo con Java y el JAR
# ============================================================
FROM amazoncorretto:21-alpine-jdk

WORKDIR /app

# Copiar el JAR generado en la etapa anterior
COPY --from=build /app/target/demo-0.0.1-SNAPSHOT.jar app.jar

# Exponer el puerto en el que se ejecutará la aplicación
EXPOSE 8080

# Comando para ejecutar la aplicación
ENTRYPOINT ["java", "-jar", "app.jar"]