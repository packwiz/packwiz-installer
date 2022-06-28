buildscript {
	repositories {
		mavenCentral()
	}
	dependencies {
		classpath("com.guardsquare:proguard-gradle:7.1.0") {
			exclude("com.android.tools.build")
		}
	}
}

plugins {
	java
	application
	id("com.github.johnrengelman.shadow") version "7.1.2"
	id("com.palantir.git-version") version "0.13.0"
	id("com.github.breadmoirai.github-release") version "2.2.12"
	kotlin("jvm") version "1.6.10"
	id("com.github.jk1.dependency-license-report") version "2.0"
	`maven-publish`
}

java {
	sourceCompatibility = JavaVersion.VERSION_1_8
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("commons-cli:commons-cli:1.5.0")
	implementation("com.moandjiezana.toml:toml4j:0.7.2")
	implementation("com.google.code.gson:gson:2.9.0")
	implementation("com.squareup.okio:okio:3.0.0")
	implementation(kotlin("stdlib-jdk8"))
	implementation("com.squareup.okhttp3:okhttp:4.9.3")
	implementation("com.michael-bull.kotlin-result:kotlin-result:1.1.14")
}

application {
	mainClassName = "link.infra.packwiz.installer.RequiresBootstrap"
}

val gitVersion: groovy.lang.Closure<*> by extra
version = gitVersion()

tasks.jar {
	manifest {
		attributes["Main-Class"] = "link.infra.packwiz.installer.RequiresBootstrap"
		attributes["Implementation-Version"] = project.version
	}
}

licenseReport {
	renderers = arrayOf<com.github.jk1.license.render.ReportRenderer>(
		com.github.jk1.license.render.InventoryMarkdownReportRenderer("licenses.md", "packwiz-installer")
	)
	filters = arrayOf<com.github.jk1.license.filter.DependencyFilter>(com.github.jk1.license.filter.LicenseBundleNormalizer())
}

tasks.shadowJar {
	exclude("**/*.kotlin_metadata")
	exclude("**/*.kotlin_builtins")
	exclude("META-INF/maven/**/*")
	exclude("META-INF/proguard/**/*")

	// Relocate Commons CLI, so that it doesn't clash with old packwiz-installer-bootstrap (that shades it)
	relocate("org.apache.commons.cli", "link.infra.packwiz.installer.deps.commons-cli")

	// from Commons CLI
	exclude("META-INF/LICENSE.txt")
	exclude("META-INF/NOTICE.txt")
}

tasks.register<proguard.gradle.ProGuardTask>("shrinkJar") {
	injars(tasks.shadowJar)
	outjars("build/libs/" + tasks.shadowJar.get().outputs.files.first().name.removeSuffix(".jar") + "-shrink.jar")
	if (System.getProperty("java.version").startsWith("1.")) {
		libraryjars("${System.getProperty("java.home")}/lib/rt.jar")
		libraryjars("${System.getProperty("java.home")}/lib/jce.jar")
	} else {
		// Use jmods for Java 9+
		val mods = listOf("java.base", "java.logging", "java.desktop", "java.sql")
		for (mod in mods) {
			libraryjars(mapOf(
				"jarfilter" to "!**.jar",
				"filter" to "!module-info.class"
			), "${System.getProperty("java.home")}/jmods/$mod.jmod")
		}
	}

	keep("class link.infra.packwiz.installer.** { *; }")
	dontoptimize()
	dontobfuscate()

	// Used by Okio and OkHttp
	dontwarn("org.codehaus.mojo.animal_sniffer.*")
	dontwarn("okhttp3.internal.platform.**")
	dontwarn("org.conscrypt.**")
	dontwarn("org.bouncycastle.**")
	dontwarn("org.openjsse.**")
}

// Used for vscode launch.json
tasks.register<Copy>("copyJar") {
	from(tasks.named("shrinkJar"))
	rename("packwiz-installer-(.*)\\.jar", "packwiz-installer.jar")
	into("build/libs/")
}

tasks.build {
	dependsOn("copyJar")
}

if (project.hasProperty("github.token")) {
	githubRelease {
		owner("comp500")
		repo("packwiz-installer")
		tagName("${project.version}")
		releaseName("Release ${project.version}")
		draft(true)
		token(findProperty("github.token") as String? ?: "")
		releaseAssets(tasks.jar.get().destinationDirectory.file("packwiz-installer.jar").get())
	}

	tasks.githubRelease {
		dependsOn(tasks.build)
	}
}

tasks.compileKotlin {
	kotlinOptions {
		jvmTarget = "1.8"
		freeCompilerArgs = listOf("-Xjvm-default=enable", "-Xallow-result-return-type", "-Xopt-in=kotlin.io.path.ExperimentalPathApi")
	}
}
tasks.compileTestKotlin {
	kotlinOptions {
		jvmTarget = "1.8"
		freeCompilerArgs = listOf("-Xjvm-default=enable", "-Xallow-result-return-type", "-Xopt-in=kotlin.io.path.ExperimentalPathApi")
	}
}

if (project.hasProperty("bunnycdn.token")) {
	publishing {
		publications {
			create<MavenPublication>("maven") {
				groupId = "link.infra.packwiz"
				artifactId = "packwiz-installer"

				from(components["java"])
			}
		}
		repositories {
			maven {
				url = uri("https://storage.bunnycdn.com/comp-maven")
				credentials(HttpHeaderCredentials::class) {
					name = "AccessKey"
					value = findProperty("bunnycdn.token") as String?
				}
				authentication {
					create<HttpHeaderAuthentication>("header")
				}
			}
		}
	}
}

