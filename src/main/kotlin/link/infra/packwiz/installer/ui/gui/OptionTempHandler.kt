package link.infra.packwiz.installer.ui.gui

import link.infra.packwiz.installer.ui.data.IOptionDetails

// Serves as a proxy for IOptionDetails, so that setOptionValue isn't called until OK is clicked
internal class OptionTempHandler(private val opt: IOptionDetails) : IOptionDetails {
	override var optionValue = opt.optionValue

	override val name get() = opt.name
	override val optionDescription get() = opt.optionDescription

	fun finalise() {
		opt.optionValue = optionValue
	}
}