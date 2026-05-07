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
    // AOP — A2.1b @Audited annotation processing (ADR #24).
    // SpringBoot autoconfig가 @EnableAspectJAutoProxy를 활성화 → @Aspect 빈이 자동 등록.
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // Mail — a1.5 password reset/forgot 흐름의 이메일 전송 (EmailService 추상화).
    // dev/default 프로파일은 ConsoleEmailService(stdout dump)만 사용하므로 SMTP 서버 없이도 부팅.
    // prod 프로파일에서만 JavaMailSender 빈 + SmtpEmailService 활성. spring.mail.* 환경변수 주입 필요.
    implementation("org.springframework.boot:spring-boot-starter-mail")

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

    // Repository 통합 테스트 — Postgres-specific schema (partial unique index, TIMESTAMPTZ)
    // 검증 위해 Testcontainers 사용. CI(ubuntu-latest)는 Docker 가용. 로컬 Windows는
    // Docker Desktop 필요 — 부재 시 본 테스트만 실패하고 NormalizeUtilTest 등은 무관.
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")

    // TestRestTemplate underlying client — 기본 JDK HttpURLConnection은 401 응답 시
    // streaming POST body를 재전송할 수 없어 HttpRetryException을 던진다 (JDK 알려진 함정).
    // Apache HttpClient 5는 401을 일반 응답으로 처리하여 해당 함정을 회피.
    // AuthScenarioIntegrationTest의 wrong-pw 401 루프 등 인증 실패 응답 검증에 필수.
    testImplementation("org.apache.httpcomponents.client5:httpclient5")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // fixtures는 repo root의 docs/normalize-fixtures.json을 직접 로드
    // (단일 진실 출처, ADR #16). 작업 디렉토리는 backend/ 가정.
    workingDir = projectDir
    // 다수의 @SpringBootTest 컨텍스트(현재 30+개)가 ContextCache에 누적 — 기본 heap(512m)으로
    // GitHub Actions ubuntu-latest에서 OOM 시 Hikari pool이 강제 shutdown되며 후속 테스트가
    // "Connection refused"/"Interrupted during connection acquisition"으로 일제 실패. 2g로 상향.
    maxHeapSize = "2g"
    // CI에서 실패 시 expected/actual + stacktrace 전체를 콘솔에 출력 — 로컬-CI 환차 디버깅용
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}
