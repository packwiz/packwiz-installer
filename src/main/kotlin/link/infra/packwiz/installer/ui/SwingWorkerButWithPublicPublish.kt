package link.infra.packwiz.installer.ui

import javax.swing.SwingWorker

// Q: AAA WHAT HAVE YOU DONE THIS IS DISGUSTING
// A: it just makes things easier, so i can easily have one interface for CLI/GUI
//    if someone has a better way to do this please PR it
abstract class SwingWorkerButWithPublicPublish<T, V> : SwingWorker<T, V>() {
	@SafeVarargs
	fun publishPublic(vararg chunks: V) {
		publish(*chunks)
	}
}