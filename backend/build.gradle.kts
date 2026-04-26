plugins {
    java
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.ibizdrive"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Spring Session (JDBC, ADR #12 + 사용자 명시)
    implementation("org.springframework.session:spring-session-jdbc")

    // DB
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // tus + AWS SDK v2 (ADR #13) — A4에서 활용, scaffold 단계 의존성만
    implementation("software.amazon.awssdk:s3:2.28.16")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // fixtures는 repo root의 docs/normalize-fixtures.json을 직접 로드
    // (단일 진실 출처, ADR #16). 작업 디렉토리는 backend/ 가정.
    workingDir = projectDir
}
