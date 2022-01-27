package link.infra.packwiz.installer.request

import okhttp3.Response
import okio.IOException

sealed class RequestException: Exception {
	constructor(message: String, cause: Throwable) : super(message, cause)
	constructor(message: String) : super(message)

	/**
	 * Internal errors that should not be shown to the user when the code is correct
	 */
	sealed class Internal: RequestException {
		constructor(message: String, cause: Throwable) : super(message, cause)
		constructor(message: String) : super(message)

		class UnsinkableBase: Internal("Base associated with this path is not a SinkableBase")

		sealed class HTTP: Internal {
			constructor(message: String, cause: Throwable) : super(message, cause)
			constructor(message: String) : super(message)

			class NoResponseBody: HTTP("HTTP response in onResponse must have a response body")
			class RequestFailed(cause: IOException): HTTP("HTTP request failed; may have been cancelled", cause)
			class IllegalState(cause: IllegalStateException): HTTP("Internal fatal HTTP request error", cause)
		}
	}

	/**
	 * Errors indicating that the request is malformed
	 */
	sealed class Validation: RequestException {
		constructor(message: String, cause: Throwable) : super(message, cause)
		constructor(message: String) : super(message)

		// TODO: move out of RequestException?
		class PathContainsNUL(path: String): Validation("Invalid path; contains NUL bytes: ${path.replace("\u0000", "")}")
		class PathContainsVolumeLetter(path: String): Validation("Invalid path; contains volume letter: $path")
	}

	/**
	 * Errors relating to the response from the server
	 */
	sealed class Response: RequestException {
		constructor(message: String, cause: Throwable) : super(message, cause)
		constructor(message: String) : super(message)

		// TODO: fancier way of displaying this?
		sealed class HTTP: Response {
			val response: okhttp3.Response

			constructor(response: okhttp3.Response, message: String, cause: Throwable) : super(message, cause) {
				this.response = response
			}
			constructor(response: okhttp3.Response, message: String) : super(message) {
				this.response = response
			}

			class ErrorCode(res: okhttp3.Response): HTTP(res, "Non-successful error code from HTTP request: ${res.code}")
		}

		sealed class File: RequestException {
			constructor(message: String, cause: Throwable) : super(message, cause)
			constructor(message: String) : super(message)

			class FileNotFound(file: String): File("File path not found: $file")
			class Other(cause: Throwable): File("Failed to read file", cause)
		}
	}
}
