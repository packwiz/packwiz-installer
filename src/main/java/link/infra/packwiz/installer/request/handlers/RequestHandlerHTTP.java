package link.infra.packwiz.installer.request.handlers;

import java.io.InputStream;
import java.net.URI;

import link.infra.packwiz.installer.request.IRequestHandler;

public class RequestHandlerHTTP implements IRequestHandler {

	@Override
	public boolean matchesHandler(URI loc) {
		String scheme = loc.getScheme();
		return scheme.equals("http") || scheme.equals("https");
	}

	@Override
	public InputStream getFileInputStream(URI loc) {
		// TODO Auto-generated method stub
		return null;
	}

}
