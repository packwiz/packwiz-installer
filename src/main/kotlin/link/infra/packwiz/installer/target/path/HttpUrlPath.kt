package link.infra.packwiz.installer.target.path

import link.infra.packwiz.installer.request.RequestException
import link.infra.packwiz.installer.target.ClientHolder
import okhttp3.HttpUrl
import okhttp3.Request
import okio.BufferedSource
import okio.IOException

class HttpUrlPath(private val url: HttpUrl, path: String? = null): PackwizPath<HttpUrlPath>(path) {
	private fun build() = if (path == null) { url } else { url.newBuilder().addPathSegments(path).build() }

	@Throws(RequestException::class)
	override fun source(clientHolder: ClientHolder): BufferedSource {
		val req = Request.Builder()
			.url(build())
			.header("Accept", "application/octet-stream")
			.header("User-Agent", "packwiz-installer")
			.get()
			.build()
		try {
			val res = clientHolder.okHttpClient.newCall(req).execute()
			// Can't use .use since it would close the response body before returning it to the caller
			try {
				if (!res.isSuccessful) {
					throw RequestException.Response.HTTP.ErrorCode(req, res)
				}

				val body = res.body ?: throw RequestException.Internal.HTTP.NoResponseBody()
				return body.source()
			} catch (e: Exception) {
				// If an exception is thrown, close the response and rethrow
				res.close()
				throw e
			}
		} catch (e: IOException) {
			throw RequestException.Internal.HTTP.RequestFailed(e)
		} catch (e: IllegalStateException) {
			throw RequestException.Internal.HTTP.IllegalState(e)
		}
	}

	override fun construct(path: String): HttpUrlPath = HttpUrlPath(url, path)

	override val folder: Boolean
		get() = pathFolder ?: (url.pathSegments.last() == "")
	override val filename: String
		get() = pathFilename ?: url.pathSegments.last()

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false
		if (!super.equals(other)) return false

		other as HttpUrlPath

		if (url != other.url) return false

		return true
	}

	override fun hashCode(): Int {
		var result = super.hashCode()
		result = 31 * result + url.hashCode()
		return result
	}

	override fun toString() = build().toString()
}