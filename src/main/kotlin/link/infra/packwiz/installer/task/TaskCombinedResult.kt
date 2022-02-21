package link.infra.packwiz.installer.task

/**
 * An object for storing results where result and upToDate are calculated simultaneously
 */
data class TaskCombinedResult<T>(val result: T, val upToDate: Boolean)