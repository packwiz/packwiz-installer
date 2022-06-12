package link.infra.packwiz.installer.request.handlers

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.regex.Pattern
import kotlin.concurrent.read
import kotlin.concurrent.write

class RequestHandlerGithub : RequestHandlerZip(true) {
	override fun getNewLoc(loc: HttpUrl): HttpUrl {
		return loc
	}

	companion object {
		private val repoMatcherPattern = Pattern.compile("/([\\w.-]+/[\\w.-]+).*")
		private val branchMatcherPattern = Pattern.compile("/[\\w.-]+/[\\w.-]+/blob/([\\w.-]+).*")
	}

	// TODO: is caching really needed, if HTTPURLConnection follows redirects correctly?
	private val zipUriMap: MutableMap<String, HttpUrl> = HashMap()
	private val zipUriLock = ReentrantReadWriteLock()
	private fun getRepoName(loc: HttpUrl): String? {
		val matcher = repoMatcherPattern.matcher(loc.encodedPath ?: return null)
		return if (matcher.matches()) {
			matcher.group(1)
		} else {
			null
		}
	}

	override fun getZipUri(loc: HttpUrl): HttpUrl {
		val repoName = getRepoName(loc)
		val branchName = getBranch(loc)

		zipUriLock.read {
			zipUriMap["$repoName/$branchName"]
		}?.let { return it }

		var zipUri = "https://api.github.com/repos/$repoName/zipball/$branchName".toHttpUrl()
		zipUriLock.write {
			// If another thread sets the value concurrently, use the existing value from the
			// thread that first acquired the lock.
			zipUri = zipUriMap.putIfAbsent("$repoName/$branchName", zipUri) ?: zipUri
		}
		return zipUri
	}

	private fun getBranch(loc: HttpUrl): String? {
		val matcher = branchMatcherPattern.matcher(loc.encodedPath ?: return null)
		return if (matcher.matches()) {
			matcher.group(1)
		} else {
			null
		}
	}

	override fun getLocationInZip(loc: HttpUrl): HttpUrl {
		val path = "./" + getRepoName(loc) + "/blob/" + getBranch(loc)
		return loc.resolve(path) ?: loc
	}

	override fun matchesHandler(loc: HttpUrl): Boolean {
		val scheme = loc.scheme
		if (!("http" == scheme || "https" == scheme)) {
			return false
		}
		// TODO: more match testing?
		return "github.com" == loc.host && branchMatcherPattern.matcher(loc.encodedPath ?: return false).matches()
	}
}