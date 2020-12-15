package link.infra.packwiz.installer.util

object Log {
	fun info(message: String) = println(message)

	fun warn(message: String) = println("[Warning] $message")
	fun warn(message: String, exception: Exception) = println("[Warning] $message: $exception")

	fun fatal(message: String) {
		println("[FATAL] $message")
	}
	fun fatal(message: String, exception: Exception) {
		println("[FATAL] $message: ")
		exception.printStackTrace()
	}
}