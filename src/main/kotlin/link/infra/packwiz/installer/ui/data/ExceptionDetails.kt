package link.infra.packwiz.installer.ui.data

data class ExceptionDetails(
		val name: String,
		val exception: Exception,
		val modUrl: String? = null
)
