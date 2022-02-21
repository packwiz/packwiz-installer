package link.infra.packwiz.installer.task

import kotlin.reflect.KMutableProperty0

// TODO: task processing on 1 background thread; actual resolving of values calls out to a thread group
// TODO: progress bar is updated from each of these tasks
// TODO: have everything be lazy so there's no need to determine task ordering upfront? a bit like rust async - task results must be queried to occur

abstract class Task<T>(protected val ctx: TaskContext): TaskInput<T> {
	// TODO: lazy wrapper for fallible results
	// TODO: multithreaded fanout subclass/helper

	protected fun <T> wasUpdated(value: KMutableProperty0<T>, newValue: T): Boolean {
		if (value.get() == newValue) {
			return false
		}
		value.set(newValue)
		return true
	}
}