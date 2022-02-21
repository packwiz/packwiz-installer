package link.infra.packwiz.installer.target.path

import link.infra.packwiz.installer.request.RequestException
import link.infra.packwiz.installer.target.ClientHolder
import okio.BufferedSink
import okio.BufferedSource

class PackwizPath(path: String, base: Base) {
	val path: String
	val base: Base

	init {
		this.base = base

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

		// Join path
		this.path = canonicalised.asReversed().joinToString("/")
	}

	val folder: Boolean get() = path.endsWith("/")

	fun resolve(path: String): PackwizPath {
		return if (path.startsWith('/') || path.startsWith('\\')) {
			// Absolute (but still relative to base of pack)
			PackwizPath(path, base)
		} else if (folder) {
			// File in folder; append
			PackwizPath(this.path + path, base)
		} else {
			// File in parent folder; append with parent component
			PackwizPath(this.path + "/../" + path, base)
		}
	}

	/**
	 * Obtain a BufferedSource for this path
	 * @throws RequestException When resolving the file failed
	 */
	fun source(clientHolder: ClientHolder): BufferedSource = base.source(path, clientHolder)

	/**
	 * Obtain a BufferedSink for this path
	 * @throws RequestException.Internal.UnsinkableBase When the base of this path does not have a sink
	 * @throws RequestException When resolving the file failed
	 */
	fun sink(clientHolder: ClientHolder): BufferedSink =
		if (base is SinkableBase) { base.sink(path, clientHolder) } else { throw RequestException.Internal.UnsinkableBase() }

	interface Base {
		/**
		 * Resolve the given (canonical) path against the base, and get a BufferedSource for this file.
		 * @throws RequestException
		 */
		fun source(path: String, clientHolder: ClientHolder): BufferedSource

		operator fun div(path: String) = PackwizPath(path, this)
	}

	interface SinkableBase: Base {
		/**
		 * Resolve the given (canonical) path against the base, and get a BufferedSink for this file.
		 * @throws RequestException
		 */
		fun sink(path: String, clientHolder: ClientHolder): BufferedSink
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as PackwizPath

		if (path != other.path) return false
		if (base != other.base) return false

		return true
	}

	override fun hashCode(): Int {
		var result = path.hashCode()
		result = 31 * result + base.hashCode()
		return result
	}

	override fun toString(): String {
		return "base=$base; $path"
	}
}