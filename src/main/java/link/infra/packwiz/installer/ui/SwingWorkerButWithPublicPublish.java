package link.infra.packwiz.installer.ui;

import javax.swing.SwingWorker;

// Q: AAA WHAT HAVE YOU DONE THIS IS DISGUSTING
// A: it just makes things easier, so i can easily have one interface for CLI/GUI
//    if someone has a better way to do this please PR it
public abstract class SwingWorkerButWithPublicPublish<T,V> extends SwingWorker<T,V> {
	@SafeVarargs
	public final void publishPublic(V... chunks) {
		publish(chunks);
	}
}