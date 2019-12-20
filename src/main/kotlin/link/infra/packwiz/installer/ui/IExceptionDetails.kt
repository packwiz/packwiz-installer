package link.infra.packwiz.installer.ui

interface IExceptionDetails {
	val exception: Exception
	val name: String

	enum class ExceptionListResult {
		CONTINUE, CANCEL, IGNORE
	}
}