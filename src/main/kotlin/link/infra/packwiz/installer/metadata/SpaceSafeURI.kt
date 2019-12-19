package link.infra.packwiz.installer.metadata

import com.google.gson.annotations.JsonAdapter
import java.io.Serializable
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL

// The world's worst URI wrapper
@JsonAdapter(SpaceSafeURIParser::class)
class SpaceSafeURI : Comparable<SpaceSafeURI>, Serializable {
	private val u: URI

	@Throws(URISyntaxException::class)
	constructor(str: String) {
		u = URI(str.replace(" ", "%20"))
	}

	constructor(uri: URI) {
		u = uri
	}

	@Throws(URISyntaxException::class)
	constructor(scheme: String?, authority: String?, path: String?, query: String?, fragment: String?) { // TODO: do all components need to be replaced?
		u = URI(
				scheme?.replace(" ", "%20"),
				authority?.replace(" ", "%20"),
				path?.replace(" ", "%20"),
				query?.replace(" ", "%20"),
				fragment?.replace(" ", "%20")
		)
	}

	val path: String get() = u.path.replace("%20", " ")

	override fun toString(): String = u.toString().replace("%20", " ")

	fun resolve(path: String): SpaceSafeURI = SpaceSafeURI(u.resolve(path.replace(" ", "%20")))

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