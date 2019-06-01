package link.infra.packwiz.installer.request;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import link.infra.packwiz.installer.request.handlers.RequestHandlerGithub;
import link.infra.packwiz.installer.request.handlers.RequestHandlerHTTP;

public abstract class HandlerManager {
	
	public static List<IRequestHandler> handlers = new ArrayList<IRequestHandler>();
	
	static {
		handlers.add(new RequestHandlerGithub());
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

	public static InputStream getFileInputStream(URI loc) throws Exception {
		for (IRequestHandler handler : handlers) {
			if (handler.matchesHandler(loc)) {
				return handler.getFileInputStream(loc);
			}
		}
		return null;
	}
	
	// To enqueue stuff:
//	private ExecutorService threadPool = Executors.newFixedThreadPool(10);
//	CompletionService<InputStream> completionService = new ExecutorCompletionService<InputStream>(threadPool);
//
//	public Future<InputStream> enqueue(URI loc) {
//		for (IRequestHandler handler : handlers) {
//			if (handler.matchesHandler(loc)) {
//				return completionService.submit(new Callable<InputStream>() {
//					public InputStream call() {
//						return handler.getFileInputStream(loc);
//					}
//				});
//			}
//		}
//		// TODO: throw error??
//		return null;
//	}
	// Use completionService.take() to get (waits until available) a Future<InputStream>, where you can call .get() and handle exceptions etc
	
	// github toml resolution
	// e.g. https://github.com/comp500/Demagnetize -> demagnetize.toml
	// https://github.com/comp500/Demagnetize/blob/master/demagnetize.toml
	
	// To handle "progress", just count tasks, rather than individual progress
	// It'll look bad, especially for zip-based things, but it should work fine
}
