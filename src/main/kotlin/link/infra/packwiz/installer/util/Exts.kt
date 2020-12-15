package link.infra.packwiz.installer.util

import link.infra.packwiz.installer.ui.IUserInterface

inline fun <T> iflet(value: T?, whenNotNull: (T) -> Unit) {
	if (value != null) {
		whenNotNull(value)
	}
}

inline fun <T, U> IUserInterface.ifletOrErr(value: T?, message: String, whenNotNull: (T) -> U): U =
	if (value != null) {
		whenNotNull(value)
	} else {
		this.showErrorAndExit(message)
	}

inline fun <T, U, V> IUserInterface.ifletOrErr(value: T?, value2: U?, message: String, whenNotNull: (T, U) -> V): V =
	if (value != null && value2 != null) {
		whenNotNull(value, value2)
	} else {
		this.showErrorAndExit(message)
	}

inline fun <T> ifletOrWarn(value: T?, message: String, whenNotNull: (T) -> Unit) {
	if (value != null) {
		whenNotNull(value)
	} else {
		Log.warn(message)
	}
}

inline fun <T, U> iflet(value: T?, whenNotNull: (T) -> U, whenNull: () -> U): U =
	if (value != null) {
		whenNotNull(value)
	} else {
		whenNull()
	}
