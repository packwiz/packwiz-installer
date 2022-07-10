package link.infra.packwiz.installer.target

import link.infra.packwiz.installer.util.Log
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.FileSystem
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class ClientHolder {
	// TODO: a button to increase timeouts temporarily when retrying? manual retry button?
	val okHttpClient by lazy { OkHttpClient.Builder()
		// Retry requests up to 3 times, increasing the timeouts slightly if it failed
		.addInterceptor {
			val req = it.request()

			var lastException: SocketTimeoutException? = null
			var res: Response? = null

			try {
				res = it.proceed(req)
			} catch (e: SocketTimeoutException) {
				lastException = e
			}

			var tryCount = 0
			while (res == null && tryCount < 3) {
				tryCount++

				Log.info("OkHttp connection to ${req.url} timed out; retrying... ($tryCount/3)")

				val longerTimeoutChain = it
					.withConnectTimeout(10 * tryCount, TimeUnit.SECONDS)
					.withReadTimeout(10 * tryCount, TimeUnit.SECONDS)
					.withWriteTimeout(10 * tryCount, TimeUnit.SECONDS)
				try {
					res = longerTimeoutChain.proceed(req)
				} catch (e: SocketTimeoutException) {
					lastException = e
				}
			}

			res ?: throw lastException!!
		}
		.build() }

	val fileSystem = FileSystem.SYSTEM

	fun close() {
		okHttpClient.dispatcher.cancelAll()
		okHttpClient.dispatcher.executorService.shutdown()
		okHttpClient.connectionPool.evictAll()
	}
}