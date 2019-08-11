package link.infra.packwiz.installer.ui;

public class InputStateHandler {
	private boolean optionsButtonPressed = false;
	private boolean cancelButtonPressed = false;

	synchronized void pressCancelButton() {
		this.cancelButtonPressed = true;
	}

	synchronized void pressOptionsButton() {
		this.optionsButtonPressed = true;
	}

	public synchronized boolean getCancelButton() {
		return cancelButtonPressed;
	}

	public synchronized boolean getOptionsButton() {
		return optionsButtonPressed;
	}
}
