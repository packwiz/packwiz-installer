package link.infra.packwiz.installer.target.path

import link.infra.packwiz.installer.request.RequestException
import link.infra.packwiz.installer.target.ClientHolder
import okhttp3.HttpUrl
import okhttp3.Request
import okio.BufferedSource
import okio.IOException

data class HttpUrlBase(private val url: HttpUrl): PackwizPath.Base {
	private fun resolve(path: String) = url.newBuilder().addPathSegments(path).build()

	override fun source(path: String, clientHolder: ClientHolder): BufferedSource {
		val req = Request.Builder()
			.url(resolve(path))
			.header("Accept", "application/octet-stream")
			.header("User-Agent", "packwiz-installer")
			.get()
			.build()
		try {
			val res = clientHolder.okHttpClient.newCall(req).execute()
			// Can't use .use since it would close the response body before returning it to the caller
			try {
				if (!res.isSuccessful) {
					throw RequestException.Response.HTTP.ErrorCode(res)
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
}