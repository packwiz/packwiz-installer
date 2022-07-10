package link.infra.packwiz.installer.target.path

import cc.ekblad.toml.model.TomlValue
import cc.ekblad.toml.tomlMapper
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import link.infra.packwiz.installer.request.RequestException
import link.infra.packwiz.installer.target.ClientHolder
import okio.BufferedSource

abstract class PackwizPath<T: PackwizPath<T>>(path: String? = null) {
	protected val path: String?

	init {
		if (path != null) {
			// Check for NUL bytes
			if (path.contains('\u0000')) { throw RequestException.Validation.PathContainsNUL(path) }
			// Normalise separator, to prevent differences between Unix/Windows
			val pathNorm = path.replace('\\', '/')
			// Split, create new lists for output
			val split = pathNorm.split('/')
			val canonicalised = mutableListOf<String>()

			// Backward pass: collapse ".." components, remove "." components and empty components (except an empty component at the end; indicating a folder)
			var parentComponentCount = 0
			var first = true
			for (component in split.asReversed()) {
				if (first) {
					first = false
					if (component == "") {
						canonicalised += component
					}
				}
				// URL-encoded . is normalised
				val componentNorm = component.replace("%2e", ".")
				if (componentNorm == "." || componentNorm == "") {
					// Do nothing
				} else if (componentNorm == "..") {
					parentComponentCount++
				} else if (parentComponentCount > 0) {
					parentComponentCount--
				} else {
					canonicalised += componentNorm
					// Don't allow volume letters (allows traversal to the root on Windows)
					if (componentNorm[0] in 'a'..'z' || componentNorm[0] in 'A'..'Z') {
						if (componentNorm[1] == ':') {
							throw RequestException.Validation.PathContainsVolumeLetter(path)
						}
					}
				}
			}

			if (canonicalised.isEmpty()) {
				this.path = null
			} else {
				// Join path
				this.path = canonicalised.asReversed().joinToString("/")
			}
		} else {
			this.path = null
		}
	}

	protected abstract fun construct(path: String): T

	protected val pathFolder: Boolean? get() = path?.endsWith("/")
	abstract val folder: Boolean
	protected val pathFilename: String? get() = path?.split("/")?.last()
	abstract val filename: String

	fun resolve(path: String): T {
		return if (path.startsWith('/') || path.startsWith('\\')) {
			// Absolute (but still relative to base of pack)
			construct(path)
		} else if (folder) {
			// File in folder; append
			construct((this.path ?: "") + path)
		} else {
			// File in parent folder; append with parent component
			construct((this.path ?: "") + "/../" + path)
		}
	}

	operator fun div(path: String) = resolve(path)

	fun <U: PackwizPath<U>> rebase(path: U) = path.resolve(this.path ?: "")

	val parent: T get() = resolve(if (folder) { ".." } else { "." })

	/**
	 * Obtain a BufferedSource for this path
	 * @throws RequestException When resolving the file failed
	 */
	@Throws(RequestException::class)
	abstract fun source(clientHolder: ClientHolder): BufferedSource

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as PackwizPath<*>

		if (path != other.path) return false

		return true
	}

	override fun hashCode() = path.hashCode()

	companion object {
		fun mapperRelativeTo(base: PackwizPath<*>) = tomlMapper {
			encoder { it: PackwizPath<*> -> TomlValue.String(it.path ?: "") }
			decoder { it: TomlValue.String -> base.resolve(it.value) }
		}

		fun <T: PackwizPath<T>> adapterRelativeTo(base: T) = object : TypeAdapter<T>() {
			override fun write(writer: JsonWriter, value: T?) {
				writer.value(value?.path)
			}
			override fun read(reader: JsonReader) = base.resolve(reader.nextString())
		}
	}

	override fun toString() = "(Unknown base) $path"
}