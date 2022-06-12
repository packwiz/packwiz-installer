package link.infra.packwiz.installer.request.handlers

import link.infra.packwiz.installer.request.IRequestHandler
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import okio.Source
import okio.source
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit

open class RequestHandlerHTTP : IRequestHandler {
	private val client: OkHttpClient

	init {
		client = OkHttpClient.Builder()
				// 30 second read timeout
				.readTimeout(30 * 1000, TimeUnit.MILLISECONDS)
				.build()
	}

	override fun matchesHandler(loc: HttpUrl): Boolean {
		val scheme = loc.scheme
		return "http" == scheme || "https" == scheme
	}

	override fun getFileSource(loc: HttpUrl): Source? {
		val request = Request.Builder()
				.url(loc)
				// TODO: when do we send specific headers??? should there be a way to signal this?
				.addHeader("Accept", "application/octet-stream")
				// TODO: include version?
				.addHeader("User-Agent", "packwiz-installer")

				.method("GET", null)
				.build()

		val response = client.newCall(request).execute()

		if (!response.isSuccessful) throw IOException("Unexpected code $response")

		return response.body!!.source()
	}
}