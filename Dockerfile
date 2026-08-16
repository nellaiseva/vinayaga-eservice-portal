FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY . .

RUN chmod +x mvnw

RUN ./mvnw clean package -DskipTests

EXPOSE 8080

CMD ["java", "-Xms128m", "-Xmx384m", "-jar", "target/eservice1-0.0.1-SNAPSHOT.jar"]