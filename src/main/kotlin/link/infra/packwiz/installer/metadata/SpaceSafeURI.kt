package link.infra.packwiz.installer.metadata

import com.google.gson.annotations.JsonAdapter
import link.infra.packwiz.installer.util.URIUtils
import java.io.Serializable
import java.net.*

// The world's worst URI wrapper
@JsonAdapter(SpaceSafeURIParser::class)
class SpaceSafeURI : Comparable<SpaceSafeURI>, Serializable {
	private val u: URI

	@Throws(URISyntaxException::class)
	constructor(str: String) {
		u = URI(URIUtils.encPath(str, Charsets.UTF_8))
	}

	constructor(uri: URI) {
		u = uri
	}

	@Throws(URISyntaxException::class)
	constructor(scheme: String?, authority: String?, path: String?, query: String?, fragment: String?) { // TODO: do all components need to be replaced?
		u = URI(
				scheme,
				authority,
				URIUtils.encPath(path, Charsets.UTF_8),
				URIUtils.encPath(query, Charsets.UTF_8),
				fragment
		)
	}

	val path: String get() = URIUtils.urlDecode(u.path, Charsets.UTF_8, true)

	override fun toString(): String =  URIUtils.urlDecode(u.toString(), Charsets.UTF_8, true)

	fun resolve(path: String): SpaceSafeURI = SpaceSafeURI(u.resolve(path))

	fun resolve(loc: SpaceSafeURI): SpaceSafeURI = SpaceSafeURI(u.resolve(loc.u))

	fun relativize(loc: SpaceSafeURI): SpaceSafeURI = SpaceSafeURI(u.relativize(loc.u))

	override fun equals(other: Any?): Boolean {
		return if (other is SpaceSafeURI) {
			u == other.u
		} else false
	}

	override fun hashCode() = u.hashCode()

	override fun compareTo(other: SpaceSafeURI): Int = u.compareTo(other.u)

	val scheme: String get() = u.scheme
	val authority: String get() = u.authority
	val host: String get() = u.host

	@Throws(MalformedURLException::class)
	fun toURL(): URL = u.toURL()
}