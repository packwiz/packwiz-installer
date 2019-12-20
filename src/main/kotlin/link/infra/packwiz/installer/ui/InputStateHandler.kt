package link.infra.packwiz.installer.ui

class InputStateHandler {
	// TODO: convert to coroutines/locks?
	@get:Synchronized
	var optionsButton = false
		private set
	@get:Synchronized
	var cancelButton = false
		private set

	@Synchronized
	fun pressCancelButton() {
		cancelButton = true
	}

	@Synchronized
	fun pressOptionsButton() {
		optionsButton = true
	}
}