package link.infra.packwiz.installer.ui

data class InstallProgress(
		val message: String,
		val hasProgress: Boolean = false,
		val progress: Int = 0,
		val progressTotal: Int = 0
) {
	constructor(message: String, progress: Int, progressTotal: Int) : this(message, true, progress, progressTotal)

	constructor(message: String) : this(message, false)
}