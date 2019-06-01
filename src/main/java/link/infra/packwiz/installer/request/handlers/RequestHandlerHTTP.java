package link.infra.packwiz.installer.request.handlers;

import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;

import link.infra.packwiz.installer.request.IRequestHandler;

public class RequestHandlerHTTP implements IRequestHandler {

	@Override
	public boolean matchesHandler(URI loc) {
		String scheme = loc.getScheme();
		return scheme.equals("http") || scheme.equals("https");
	}

	@Override
	public InputStream getFileInputStream(URI loc) throws Exception {
		URLConnection conn = loc.toURL().openConnection();
		conn.addRequestProperty("Accept", "application/octet-stream");
		// 30 second read timeout
		conn.setReadTimeout(30 * 1000);
		return conn.getInputStream();
	}

}
