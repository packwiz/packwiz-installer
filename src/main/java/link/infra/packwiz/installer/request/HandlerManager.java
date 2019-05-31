package link.infra.packwiz.installer.request;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import link.infra.packwiz.installer.request.handlers.RequestHandlerHTTP;

public abstract class HandlerManager {
	
	public static List<IRequestHandler> handlers = new ArrayList<IRequestHandler>();
	
	static {
		handlers.add(new RequestHandlerHTTP());
	}
	
	public static URI getNewLoc(URI base, URI loc) {
		if (base != null) {
			loc = base.resolve(loc);
		}
		if (loc == null) return null;
		
		for (IRequestHandler handler : handlers) {
			if (handler.matchesHandler(loc)) {
				return handler.getNewLoc(loc);
			}
		}
		return loc;
	}

	public static InputStream getFileInputStream(URI loc) {
		for (IRequestHandler handler : handlers) {
			if (handler.matchesHandler(loc)) {
				return handler.getFileInputStream(loc);
			}
		}
		return null;
	}
	
	public class RequestTaskManager {
		private Map<URI, RequestTask> tasks = new HashMap<URI, RequestTask>();
		private int numTasks = 0;
		private int numRemainingTasks = 0;
		private ExecutorService threadPool = Executors.newFixedThreadPool(10);
		
		public int getNumTasks() {
			synchronized (tasks) {
				return numTasks;
			}
		}
		
		public int getNumTasksRemaining() {
			synchronized (tasks) {
				return numRemainingTasks;
			}
		}
		
		// UHHHHHHHH fix this maybe? it's O(n^2) and kinda bad
		// because it has to be done every time you download a thing, to check if you can download more
		// maybe:
		// store list of dependents and dependencies for all tasks, update when task completes??????
		// this is hard!!!
		// still have the issue of how to read while also allowing it to be re-read (cachedinputstream)
		// or go to a dependency free system in some way, but just have 2 types of request: progress only, and actual download???????
		// and how to multithread it???
		private boolean hasRemainingDependencies(RequestTask task) {
			synchronized (tasks) {
				return Arrays.stream(task.getDependencies()).filter(depUri -> {
					RequestTask depTask = tasks.get(depUri);
					if (depTask == null) {
						return true;
					} else {
						return !depTask.isDone();
					}
				}).count() > 0;
			}
		}
		
		public void enqueue(URI loc) {
			// get a requesttask somehow
			RequestTask task = null;
			
			Stack<URI> toEnqueue = new Stack<URI>();
			
			URI[] remainingDeps;
			synchronized (tasks) {
				remainingDeps = Arrays.stream(task.getDependencies()).filter(depUri -> {
					RequestTask depTask = tasks.get(depUri);
					if (depTask == null) {
						toEnqueue.add(depUri);
						return true;
					} else {
						return !depTask.isDone();
					}
				}).toArray(URI[]::new);
			}
			
			synchronized (tasks) {
				tasks.put(loc, null);
			}
			
			while (toEnqueue.size() > 0) {
				enqueue(toEnqueue.pop());
			}
			
			if (remainingDeps.length == 0) {
				// execute it
				// after executing, check deps of other tasks
			}
		}
	}
	
	public abstract class RequestTask implements Future<byte[]> {
		
		protected final Consumer<Integer> setProgress;
		public final URI requestLocation;
		
		public abstract URI[] getDependencies();
		
		public RequestTask(Consumer<Integer> setProgress, URI requestLocation) {
			this.setProgress = setProgress;
			this.requestLocation = requestLocation;
		}
	}
	
	// how to handle progress of zip download, for zip/github downloads?
	
	// github toml resolution
	// e.g. https://github.com/comp500/Demagnetize -> demagnetize.toml
	// https://github.com/comp500/Demagnetize/blob/master/demagnetize.toml
	
	// Use a Request class?
	// sub requests, can get progress (but not data) of sub things
	// function to get length -> -1 means indeterminate
	// function / callback to get progress
	// input stream progress tracker, like the bootstrapper?
	// https://docs.oracle.com/javase/tutorial/uiswing/components/progress.html -> swingworker, other magic to get progress
	
	// stack of request tasks
	// deduplicated
	
	
	// UHHHHH I THINK I HAVE THE HALTING PROBLEM AND ITS NOT NICE
}
