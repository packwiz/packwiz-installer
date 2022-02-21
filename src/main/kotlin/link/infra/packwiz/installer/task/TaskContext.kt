package link.infra.packwiz.installer.task

import link.infra.packwiz.installer.target.ClientHolder

class TaskContext {
	// TODO: thread pools, protocol roots
	// TODO: cache management

	val cache = CacheManager()

	val clients = ClientHolder()
}