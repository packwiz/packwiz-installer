plugins {
	java
	application
	id("com.github.johnrengelman.shadow") version "5.0.0"
	id("com.palantir.git-version") version "0.11.0"
	id("com.github.breadmoirai.github-release") version "2.2.9"
	kotlin("jvm") version "1.3.61"
}

java {
	sourceCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
	implementation("commons-cli:commons-cli:1.4")
	implementation("com.moandjiezana.toml:toml4j:0.7.2")
	implementation("com.google.code.gson:gson:2.8.1")
	implementation("com.squareup.okio:okio:2.2.2")
	implementation(kotlin("stdlib-jdk8"))
}

repositories {
	jcenter()
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

// Commons CLI and Minimal JSON are already included in packwiz-installer-bootstrap
tasks.shadowJar {
	dependencies {
		exclude(dependency("commons-cli:commons-cli:1.4"))
		exclude(dependency("com.eclipsesource.minimal-json:minimal-json:0.9.5"))
	}
}

// Used for vscode launch.json
tasks.register<Copy>("copyJar") {
	from(tasks.shadowJar)
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
		freeCompilerArgs = listOf("-Xjvm-default=enable")
	}
}
tasks.compileTestKotlin {
	kotlinOptions {
		jvmTarget = "1.8"
		freeCompilerArgs = listOf("-Xjvm-default=enable")
	}
}