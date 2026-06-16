import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.DirectoryResourceAccessor
import nu.studer.gradle.jooq.JooqEdition
import org.jooq.meta.kotlin.*
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.io.File
import java.sql.DriverManager

buildscript {
	repositories {
		mavenCentral()
	}
	dependencies {
		classpath("org.testcontainers:testcontainers-postgresql:2.0.5")
		classpath("org.liquibase:liquibase-core:5.0.3")
		classpath("org.postgresql:postgresql:42.7.11")
	}
}

plugins {
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.spring") version "2.3.21"

	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
	id("nu.studer.jooq") version "10.2.1"
}

group = "br.com.investlog"
version = "0.1.0"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)

		sourceCompatibility = JavaVersion.VERSION_25
		targetCompatibility = JavaVersion.VERSION_25
	}
}

repositories {
	mavenCentral()
}

val jooqVersion = "3.21.5"
val postgresDriverVersion = "42.7.11"
val kotlinLoggingJvmVersion = "7.0.14"

dependencies {
	// spring stuff
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-jooq")
	implementation("org.springframework.boot:spring-boot-starter-liquibase")
	implementation("org.springframework.boot:spring-boot-starter-mail")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.data:spring-data-commons")

	// logging
	implementation("io.github.oshai:kotlin-logging-jvm:$kotlinLoggingJvmVersion")

	// kotlin thing
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")

	// dev utils
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

	// database
	runtimeOnly("org.postgresql:postgresql")
	jooqGenerator("org.postgresql:postgresql:$postgresDriverVersion")

	// testing
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-jooq-test")
	testImplementation("org.springframework.boot:spring-boot-starter-liquibase-test")
	testImplementation("org.springframework.boot:spring-boot-starter-mail-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("tools.jackson.core:jackson-databind")

	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")

	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")

	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		jvmTarget.set(JVM_25)
		languageVersion.set(KOTLIN_2_3)
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

jooq {
	version.set(jooqVersion)
	edition.set(JooqEdition.OSS)

	configurations {
		create("main") {
			generateSchemaSourceOnCompilation.set(true)

			jooqConfiguration.apply {
				generator {
					name = "org.jooq.codegen.KotlinGenerator"

					database {
						name = "org.jooq.meta.postgres.PostgresDatabase"

						schemata {
							schema {
								inputSchema = "system"
							}
							schema {
								inputSchema = "finances"
							}
						}
					}

					target {
						packageName = "br.com.investlog.server.jooq"
						directory = "build/generated-sources/jooq/main"
					}
				}
			}
		}
	}
}

val jooqDb = PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))

val startJooqDb by tasks.registering {
	description = "start jooq to generate database metadata from schema"
	doLast {
		jooqDb.start()

		val connection = DriverManager.getConnection(jooqDb.jdbcUrl, jooqDb.username, jooqDb.password)

		connection.use { connection ->
            val database = DatabaseFactory
				.getInstance()
				.findCorrectDatabaseImplementation(JdbcConnection(connection))

			val resourceAccessor = DirectoryResourceAccessor(File(projectDir, "src/main/resources"))

            Liquibase("db/changelog/db.changelog-master.xml", resourceAccessor, database)
				.update()
        }

		jooq.configurations.getByName("main").jooqConfiguration.jdbc {
			driver = "org.postgresql.Driver"
			url = jooqDb.jdbcUrl
			user = jooqDb.username
			password = jooqDb.password
		}
	}
}

val stopJooqDb by tasks.registering {
	description = "stop jooq to generate database metadata from schema"
	doLast {
		jooqDb.stop()
	}
}

startJooqDb {
	finalizedBy(stopJooqDb)
}

tasks.named("generateJooq") {
	dependsOn(startJooqDb)
	finalizedBy(stopJooqDb)
}

springBoot {
	buildInfo {
		properties {
			group.set(project.group as String)
			version.set(project.version as String)
			artifact.set(project.name)

			description = "Investlog server service"

			name.set("investlog-server")
		}
	}
}

tasks {
	test {
		useJUnitPlatform()
		maxParallelForks = Runtime.getRuntime().availableProcessors()
	}

	bootJar {
		layered {
			enabled.set(true)
			application {
				intoLayer("spring-boot-loader") {
					include("org/springframework/boot/loader/**")
				}
				intoLayer("application")
			}
			dependencies {
				intoLayer("application") {
					includeProjectDependencies()
				}
				intoLayer("snapshot-dependencies") {
					include("*:*:*SNAPSHOT")
				}
				intoLayer("dependencies")
			}
			layerOrder.set(listOf("dependencies", "spring-boot-loader", "snapshot-dependencies", "application"))
		}
		archiveFileName.set("${project.name}.${archiveExtension.get()}")
	}

	bootBuildImage {
		environment.put("BP_JVM_VERSION", JVM_25.target)
		environment.put("BPE_DELIM_JAVA_TOOL_OPTIONS", " ")
		environment.put(
			"BPE_APPEND_JAVA_TOOL_OPTIONS",
			"-XX:MetaspaceSize=128M -XX:MaxMetaspaceSize=256M -XX:+UseG1GC -XX:+UseStringDeduplication -Dfile.encoding=UTF-8 -Duser.timezone=UTC"
		)
		imageName.set("investlog/${project.name}:v${project.version}")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
