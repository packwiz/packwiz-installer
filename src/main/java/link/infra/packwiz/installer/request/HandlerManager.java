package link.infra.packwiz.installer.request;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import link.infra.packwiz.installer.request.handlers.RequestHandlerGithub;
import link.infra.packwiz.installer.request.handlers.RequestHandlerHTTP;
import okio.Source;

public abstract class HandlerManager {
	
	public static List<IRequestHandler> handlers = new ArrayList<IRequestHandler>();
	
	static {
		handlers.add(new RequestHandlerGithub());
		handlers.add(new RequestHandlerHTTP());
	}
	
	public static URI getNewLoc(URI base, URI loc) {
		if (loc == null) return null;
		if (base != null) {
			loc = base.resolve(loc);
		}
		
		for (IRequestHandler handler : handlers) {
			if (handler.matchesHandler(loc)) {
				return handler.getNewLoc(loc);
			}
		}
		return loc;
	}
	
	// TODO: What if files are read multiple times??
	// Zip handler discards once read, requesting multiple times on other handlers would cause multiple downloads
	// Caching system? Copy from already downloaded files?

	public static Source getFileSource(URI loc) throws Exception {
		for (IRequestHandler handler : handlers) {
			if (handler.matchesHandler(loc)) {
				Source src = handler.getFileSource(loc);
				if (src == null) {
					throw new Exception("Couldn't find URI: " + loc.toString());
				} else {
					return src;
				}
			}
		}
		// TODO: specialised exception classes??
		throw new Exception("No handler available for URI: " + loc.toString());
	}
	
	// github toml resolution
	// e.g. https://github.com/comp500/Demagnetize -> demagnetize.toml
	// https://github.com/comp500/Demagnetize/blob/master/demagnetize.toml
	
	// To handle "progress", just count tasks, rather than individual progress
	// It'll look bad, especially for zip-based things, but it should work fine
}
