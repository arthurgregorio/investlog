import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.DirectoryResourceAccessor
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3
import org.jooq.codegen.gradle.CodegenPluginExtension
import org.jooq.meta.jaxb.Jdbc
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.security.MessageDigest
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
    id("org.jooq.jooq-codegen-gradle") version "3.21.5"
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
    jooqCodegen("org.postgresql:postgresql:$postgresDriverVersion")

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
    configuration {
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

tasks.named("compileKotlin") {
    dependsOn("jooqCodegen")
}

val jooqDb = PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))

val changelogDir = file("src/main/resources/db/changelog")
val jooqCodegenMarker = layout.buildDirectory.file("jooq-codegen-changelog.sha256")

fun changelogDigest(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    changelogDir.walkTopDown().filter { it.isFile }.sortedBy { it.path }.forEach { changelogFile ->
        digest.update(changelogFile.readBytes())
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

fun changelogUnchangedSinceLastCodegen(): Boolean {
    val markerFile = jooqCodegenMarker.get().asFile
    return markerFile.exists() && markerFile.readText() == changelogDigest()
}

val startJooqDb by tasks.registering {
    description = "start jooq to generate database metadata from schema"
    onlyIf { !changelogUnchangedSinceLastCodegen() }
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

        project.extensions.getByType(CodegenPluginExtension::class.java)
            .executions.getByName("")
            .configuration
            .withJdbc(
                Jdbc()
                    .withDriver("org.postgresql.Driver")
                    .withUrl(jooqDb.jdbcUrl)
                    .withUser(jooqDb.username)
                    .withPassword(jooqDb.password)
            )
    }
}

val stopJooqDb by tasks.registering {
    description = "stop jooq to generate database metadata from schema"
    onlyIf { startJooqDb.get().didWork }
    doLast {
        jooqDb.stop()
    }
}

startJooqDb {
    finalizedBy(stopJooqDb)
}

tasks.named("jooqCodegen") {
    dependsOn(startJooqDb)
    finalizedBy(stopJooqDb)
    onlyIf { startJooqDb.get().didWork }
    doLast {
        jooqCodegenMarker.get().asFile.apply {
            parentFile.mkdirs()
            writeText(changelogDigest())
        }
    }
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
        maxParallelForks = 4
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