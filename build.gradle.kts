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
	google()
}

val r8 by configurations.creating

dependencies {
	implementation("commons-cli:commons-cli:1.5.0")
	implementation("com.moandjiezana.toml:toml4j:0.7.2")
	implementation("com.google.code.gson:gson:2.9.0")
	implementation("com.squareup.okio:okio:3.0.0")
	implementation(kotlin("stdlib-jdk8"))
	implementation("com.squareup.okhttp3:okhttp:4.9.3")
	implementation("com.michael-bull.kotlin-result:kotlin-result:1.1.14")

	r8("com.android.tools:r8:3.3.28")
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

tasks.register<JavaExec>("shrinkJar") {
	val rules = file("src/main/proguard.txt")
	val r8File = tasks.shadowJar.get().archiveFile.get().asFile.run { resolveSibling(name.removeSuffix(".jar") + "-shrink.jar") }
	dependsOn(configurations.named("runtimeClasspath"))
	inputs.files(tasks.shadowJar, rules)
	outputs.file(r8File)

	classpath(r8)
	mainClass.set("com.android.tools.r8.R8")
	args = mutableListOf(
		"--release",
		"--classfile",
		"--output", r8File.toString(),
		"--pg-conf", rules.toString(),
		"--lib", System.getProperty("java.home"),
		*(if (System.getProperty("java.version").startsWith("1.")) {
			// javax.crypto, necessary on <1.9 for compiling Okio
			arrayOf("--lib", System.getProperty("java.home") + "/lib/jce.jar")
		} else { arrayOf() }),
		tasks.shadowJar.get().archiveFile.get().asFile.toString()
	)
}

// Used for vscode launch.json
tasks.register<Copy>("copyJar") {
	from(tasks.named("shrinkJar"))
	rename("packwiz-installer-(.*)\\.jar", "packwiz-installer.jar")
	into("build/libs/")
	outputs.file("build/libs/packwiz-installer.jar")
}

tasks.assemble {
	dependsOn("shrinkJar")
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
		freeCompilerArgs = listOf("-Xjvm-default=all", "-Xallow-result-return-type", "-Xopt-in=kotlin.io.path.ExperimentalPathApi", "-Xlambdas=indy")
	}
}
tasks.compileTestKotlin {
	kotlinOptions {
		jvmTarget = "1.8"
		freeCompilerArgs = listOf("-Xjvm-default=all", "-Xallow-result-return-type", "-Xopt-in=kotlin.io.path.ExperimentalPathApi", "-Xlambdas=indy")
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

