plugins {
	java
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "dev.lavra"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	// One BOM aligns every Azure client (storage, messaging and their shared
	// azure-core) on versions that were tested together.
	implementation(platform("com.azure:azure-sdk-bom:1.3.8"))
	implementation("com.azure:azure-storage-blob")
	implementation("com.azure:azure-messaging-servicebus")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testImplementation("org.testcontainers:testcontainers-azure")
	testImplementation("org.testcontainers:testcontainers-mssqlserver")
	// The Service Bus emulator needs a SQL Server behind it, and Testcontainers
	// waits for that one over JDBC — hence a driver we otherwise never use.
	testRuntimeOnly("com.microsoft.sqlserver:mssql-jdbc")
	// Validates every published event against the JSON Schema in contracts/
	// before it can reach a broker — contracts-first, enforced by the build.
	testImplementation("com.networknt:json-schema-validator:3.0.6")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// The event schemas are the contract, not a copy of it: the tests read them
// straight from contracts/events/ so a schema edit cannot leave the assertions
// behind.
tasks.named<ProcessResources>("processTestResources") {
	from(file("../contracts/events")) {
		into("contracts/events")
	}
}

tasks.named<Test>("test") {
	useJUnitPlatform {
		// The Service Bus emulator drags a SQL Server container along (~1.5 GB)
		// and minutes of startup. It is a real test and it is meant to be run —
		// just not on every `gradle test`. See `emulatorTest`.
		excludeTags("emulator")
	}
}

tasks.register<Test>("emulatorTest") {
	group = "verification"
	description = "Runs the tests that need the Azure Service Bus emulator (Docker, heavy)."
	testClassesDirs = sourceSets["test"].output.classesDirs
	classpath = sourceSets["test"].runtimeClasspath
	useJUnitPlatform {
		includeTags("emulator")
	}
	// Nothing to cache: the point is to exercise a live broker.
	outputs.upToDateWhen { false }
}
