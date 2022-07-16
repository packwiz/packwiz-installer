plugins {
	java
	application
	id("com.github.johnrengelman.shadow") version "7.1.2"
	id("com.palantir.git-version") version "0.13.0"
	id("com.github.breadmoirai.github-release") version "2.4.1"
	kotlin("jvm") version "1.7.10"
	id("com.github.jk1.dependency-license-report") version "2.0"
	`maven-publish`
}

java {
	sourceCompatibility = JavaVersion.VERSION_1_8
}

repositories {
	mavenCentral()
	google()
	maven {
		url = uri("https://jitpack.io")
	}
}

val r8 by configurations.creating
val shrinkJarOutput by configurations.creating {
	isCanBeResolved = false
	isCanBeConsumed = true

	attributes {
		attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
		attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling::class.java, Bundling.EMBEDDED))
	}
}

dependencies {
	implementation("commons-cli:commons-cli:1.5.0")
	implementation("com.google.code.gson:gson:2.9.0")
	implementation("com.squareup.okio:okio:3.1.0")
	implementation(kotlin("stdlib-jdk8"))
	implementation("com.squareup.okhttp3:okhttp:4.10.0")
	implementation("cc.ekblad:4koma:1.1.0")

	r8("com.android.tools:r8:3.3.28")
}

application {
	mainClass.set("link.infra.packwiz.installer.RequiresBootstrap")
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
	// 4koma uses kotlin-reflect; requires Kotlin metadata
	//exclude("**/*.kotlin_metadata")
	//exclude("**/*.kotlin_builtins")
	exclude("META-INF/maven/**/*")
	exclude("META-INF/proguard/**/*")

	// Relocate Commons CLI, so that it doesn't clash with old packwiz-installer-bootstrap (that shades it)
	relocate("org.apache.commons.cli", "link.infra.packwiz.installer.deps.commons-cli")

	// from Commons CLI
	exclude("META-INF/LICENSE.txt")
	exclude("META-INF/NOTICE.txt")
}

val shrinkJar by tasks.registering(JavaExec::class) {
	val rules = file("src/main/proguard.txt")
	val r8File = base.libsDirectory.file(provider {
		base.archivesName.get() + "-" + project.version + "-all-shrink.jar"
	})
	dependsOn(configurations.named("runtimeClasspath"))
	inputs.files(tasks.shadowJar, rules)
	outputs.file(r8File)

	classpath(r8)
	mainClass.set("com.android.tools.r8.R8")
	args = mutableListOf(
		"--release",
		"--classfile",
		"--output", r8File.get().toString(),
		"--pg-conf", rules.toString(),
		"--lib", System.getProperty("java.home"),
		*(if (System.getProperty("java.version").startsWith("1.")) {
			// javax.crypto, necessary on <1.9 for compiling Okio
			arrayOf("--lib", System.getProperty("java.home") + "/lib/jce.jar")
		} else { arrayOf() }),
		tasks.shadowJar.get().archiveFile.get().asFile.toString()
	)
}

artifacts {
	add("shrinkJarOutput", shrinkJar) {
		classifier = "dist"
	}
}

// Used for vscode launch.json
val copyJar by tasks.registering(Copy::class) {
	from(shrinkJar)
	rename("packwiz-installer-(.*)\\.jar", "packwiz-installer.jar")
	into(layout.buildDirectory.dir("dist"))
	outputs.file(layout.buildDirectory.dir("dist").map { file("packwiz-installer.jar") })
}

tasks.build {
	dependsOn(copyJar)
}

githubRelease {
	owner("comp500")
	repo("packwiz-installer")
	tagName("${project.version}")
	releaseName("Release ${project.version}")
	draft(true)
	token(findProperty("github.token") as String?)
	releaseAssets(layout.buildDirectory.dir("dist").map { file("packwiz-installer.jar") })
}

tasks.githubRelease {
	dependsOn(copyJar)
	enabled = project.hasProperty("github.token") && project.findProperty("release") == "true"
}

tasks.publish {
	dependsOn(tasks.githubRelease)
}

tasks.compileKotlin {
	kotlinOptions {
		jvmTarget = "1.8"
		freeCompilerArgs = listOf("-Xjvm-default=all", "-Xallow-result-return-type", "-opt-in=kotlin.io.path.ExperimentalPathApi", "-Xlambdas=indy")
	}
}
tasks.compileTestKotlin {
	kotlinOptions {
		jvmTarget = "1.8"
		freeCompilerArgs = listOf("-Xjvm-default=all", "-Xallow-result-return-type", "-opt-in=kotlin.io.path.ExperimentalPathApi", "-Xlambdas=indy")
	}
}

val javaComponent = components["java"] as AdhocComponentWithVariants
javaComponent.addVariantsFromConfiguration(shrinkJarOutput) {
	mapToMavenScope("runtime")
	mapToOptional()
}
javaComponent.withVariantsFromConfiguration(configurations["shadowRuntimeElements"]) {
	skip()
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
				url = if (project.findProperty("release") == "true") {
					uri("https://storage.bunnycdn.com/comp-maven/repository/release")
				} else {
					uri("https://storage.bunnycdn.com/comp-maven/repository/snapshot")
				}
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

