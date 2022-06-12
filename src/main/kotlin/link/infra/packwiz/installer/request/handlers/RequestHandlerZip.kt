package link.infra.packwiz.installer.request.handlers

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Buffer
import okio.Source
import okio.buffer
import okio.source
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Predicate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.concurrent.read
import kotlin.concurrent.withLock
import kotlin.concurrent.write

abstract class RequestHandlerZip(private val modeHasFolder: Boolean) : RequestHandlerHTTP() {
	private fun removeFolder(name: String): String {
		return if (modeHasFolder) {
			// TODO: replace with proper path checks once switched to Path??
			name.substring(name.indexOf("/") + 1)
		} else {
			name
		}
	}
	private fun removeFolder(path: HttpUrl): HttpUrl {
		return if (modeHasFolder) {
			path.newBuilder().removePathSegment(path.pathSegments.size - 1).build()
		} else {
			path
		}
	}

	private inner class ZipReader(zip: Source) {
		private val zis = ZipInputStream(zip.buffer().inputStream())
		private val readFiles: MutableMap<HttpUrl, Buffer> = HashMap()
		// Write lock implies access to ZipInputStream - only 1 thread must read at a time!
		val filesLock = ReentrantLock()
		private var entry: ZipEntry? = null

		private val zipSource = zis.source().buffer()

		// File lock must be obtained before calling this function
		private fun readCurrFile(): Buffer {
			val fileBuffer = Buffer()
			zipSource.readFully(fileBuffer, entry!!.size)
			return fileBuffer
		}

		// File lock must be obtained before calling this function
		private fun findFile(loc: HttpUrl): Buffer? {
			while (true) {
				entry = zis.nextEntry
				entry?.also {
					val data = readCurrFile()
					val fileLoc = removeFolder(it.name).toHttpUrl()
					if (loc == fileLoc) {
						return data
					} else {
						readFiles[fileLoc] = data
					}
				} ?: return null
			}
		}

		fun getFileSource(loc: HttpUrl): Source? {
			filesLock.withLock {
				// Assume files are only read once, allow GC by removing
				readFiles.remove(loc)?.also { return it }
				return findFile(loc)
			}
		}

		fun findInZip(matches: Predicate<HttpUrl>): HttpUrl? {
			filesLock.withLock {
				readFiles.keys.find { matches.test(it) }?.let { return it }

				do {
					val entry = zis.nextEntry?.also {
						val data = readCurrFile()
						val fileLoc = removeFolder(it.name).toHttpUrl()
						readFiles[fileLoc] = data
						if (matches.test(fileLoc)) {
							return fileLoc
						}
					}
				} while (entry != null)
				return null
			}
		}
	}

	private val cache: MutableMap<HttpUrl, ZipReader> = HashMap()
	private val cacheLock = ReentrantReadWriteLock()

	protected abstract fun getZipUri(loc: HttpUrl): HttpUrl
	protected abstract fun getLocationInZip(loc: HttpUrl): HttpUrl
	abstract override fun matchesHandler(loc: HttpUrl): Boolean

	override fun getFileSource(loc: HttpUrl): Source? {
		val zipUri = getZipUri(loc)
		var zr = cacheLock.read { cache[zipUri] }
		if (zr == null) {
			cacheLock.write {
				// Recheck, because unlocking read lock allows another thread to modify it
				zr = cache[zipUri]

				if (zr == null) {
					val src = super.getFileSource(zipUri) ?: return null
					zr = ZipReader(src).also { cache[zipUri] = it }
				}
			}
		}
		return zr?.getFileSource(getLocationInZip(loc))
	}

	protected fun findInZip(loc: HttpUrl, matches: Predicate<HttpUrl>): HttpUrl? {
		val zipUri = getZipUri(loc)
		return (cacheLock.read { cache[zipUri] } ?: cacheLock.write {
			// Recheck, because unlocking read lock allows another thread to modify it
			cache[zipUri] ?: run {
				// Create the ZipReader if it doesn't exist, return null if getFileSource returns null
				super.getFileSource(zipUri)?.let { ZipReader(it) }
						?.also { cache[zipUri] = it }
			}
		})?.findInZip(matches)
	}

}