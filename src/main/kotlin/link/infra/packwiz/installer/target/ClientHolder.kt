package link.infra.packwiz.installer.target

import okhttp3.OkHttpClient
import okio.FileSystem

class ClientHolder {
	// TODO: timeouts?
	// TODO: a button to increase timeouts temporarily when retrying?
	val okHttpClient by lazy { OkHttpClient.Builder().build() }

	val fileSystem = FileSystem.SYSTEM
}