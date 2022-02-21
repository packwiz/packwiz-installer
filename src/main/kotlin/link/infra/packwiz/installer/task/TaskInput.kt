package link.infra.packwiz.installer.task

import kotlin.reflect.KProperty

interface TaskInput<T> {
	/**
	 * The value of this task input. May be lazily evaluated; must be threadsafe.
	 */
	val value: T

	/**
	 * True if the effective value of this input has changed since the task was last run.
	 * Doesn't require evaluation of the input value; should use cached data if possible.
	 * May be lazily evaluated; must be threadsafe.
	 */
	val upToDate: Boolean

	operator fun getValue(thisVal: Any?, property: KProperty<*>): T = value

	companion object {
		fun <T> raw(value: T): TaskInput<T> {
			return object: TaskInput<T> {
				override val value = value
				override val upToDate: Boolean
					get() = false
			}
		}
	}
}