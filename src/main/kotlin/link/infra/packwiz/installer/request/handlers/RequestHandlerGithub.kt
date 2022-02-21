package link.infra.packwiz.installer.request.handlers

import link.infra.packwiz.installer.metadata.SpaceSafeURI
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.regex.Pattern
import kotlin.concurrent.read
import kotlin.concurrent.write

class RequestHandlerGithub : RequestHandlerZip(true) {
	override fun getNewLoc(loc: SpaceSafeURI): SpaceSafeURI {
		return loc
	}

	companion object {
		private val repoMatcherPattern = Pattern.compile("/([\\w.-]+/[\\w.-]+).*")
		private val branchMatcherPattern = Pattern.compile("/[\\w.-]+/[\\w.-]+/blob/([\\w.-]+).*")
	}

	// TODO: is caching really needed, if HTTPURLConnection follows redirects correctly?
	private val zipUriMap: MutableMap<String, SpaceSafeURI> = HashMap()
	private val zipUriLock = ReentrantReadWriteLock()
	private fun getRepoName(loc: SpaceSafeURI): String? {
		val matcher = repoMatcherPattern.matcher(loc.path ?: return null)
		return if (matcher.matches()) {
			matcher.group(1)
		} else {
			null
		}
	}

	override fun getZipUri(loc: SpaceSafeURI): SpaceSafeURI {
		val repoName = getRepoName(loc)
		val branchName = getBranch(loc)

		zipUriLock.read {
			zipUriMap["$repoName/$branchName"]
		}?.let { return it }

		var zipUri = SpaceSafeURI("https://api.github.com/repos/$repoName/zipball/$branchName")
		zipUriLock.write {
			// If another thread sets the value concurrently, use the existing value from the
			// thread that first acquired the lock.
			zipUri = zipUriMap.putIfAbsent("$repoName/$branchName", zipUri) ?: zipUri
		}
		return zipUri
	}

	private fun getBranch(loc: SpaceSafeURI): String? {
		val matcher = branchMatcherPattern.matcher(loc.path ?: return null)
		return if (matcher.matches()) {
			matcher.group(1)
		} else {
			null
		}
	}

	override fun getLocationInZip(loc: SpaceSafeURI): SpaceSafeURI {
		val path = "/" + getRepoName(loc) + "/blob/" + getBranch(loc)
		return SpaceSafeURI(loc.scheme, loc.authority, path, null, null).relativize(loc)
	}

	override fun matchesHandler(loc: SpaceSafeURI): Boolean {
		val scheme = loc.scheme
		if (!("http" == scheme || "https" == scheme)) {
			return false
		}
		// TODO: more match testing?
		return "github.com" == loc.host && branchMatcherPattern.matcher(loc.path ?: return false).matches()
	}
}