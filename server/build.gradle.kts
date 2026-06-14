import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3

plugins {
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.spring") version "2.3.21"

	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
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

dependencies {
	// spring stuff
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-jooq")
	implementation("org.springframework.boot:spring-boot-starter-liquibase")
	implementation("org.springframework.boot:spring-boot-starter-mail")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")

	// kotlin thing
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")

	// dev utils
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

	// database
	runtimeOnly("org.postgresql:postgresql")

	// testing
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-jooq-test")
	testImplementation("org.springframework.boot:spring-boot-starter-liquibase-test")
	testImplementation("org.springframework.boot:spring-boot-starter-mail-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")

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
