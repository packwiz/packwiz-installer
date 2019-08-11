package link.infra.packwiz.installer.request.handlers;

import link.infra.packwiz.installer.metadata.SpaceSafeURI;
import link.infra.packwiz.installer.request.IRequestHandler;
import okio.Okio;
import okio.Source;

import java.net.URLConnection;

public class RequestHandlerHTTP implements IRequestHandler {

	@Override
	public boolean matchesHandler(SpaceSafeURI loc) {
		String scheme = loc.getScheme();
		return "http".equals(scheme) || "https".equals(scheme);
	}

	@Override
	public Source getFileSource(SpaceSafeURI loc) throws Exception {
		URLConnection conn = loc.toURL().openConnection();
		// TODO: when do we send specific headers??? should there be a way to signal this?
		// github *sometimes* requires it, sometimes not!
		//conn.addRequestProperty("Accept", "application/octet-stream");
		// 30 second read timeout
		conn.setReadTimeout(30 * 1000);
		return Okio.source(conn.getInputStream());
	}

}
