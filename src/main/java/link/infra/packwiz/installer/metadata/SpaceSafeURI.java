package link.infra.packwiz.installer.metadata;

import com.google.gson.annotations.JsonAdapter;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

// The world's worst URI wrapper
@JsonAdapter(SpaceSafeURIParser.class)
public class SpaceSafeURI implements Comparable<SpaceSafeURI>, Serializable {
	private final URI u;

	public SpaceSafeURI(String str) throws URISyntaxException {
		u = new URI(str.replace(" ", "%20"));
	}

	public SpaceSafeURI(URI uri) {
		this.u = uri;
	}

	public SpaceSafeURI(String scheme, String authority, String path, String query, String fragment) throws URISyntaxException {
		// TODO: do all components need to be replaced?
		scheme = scheme.replace(" ", "%20");
		authority = authority.replace(" ", "%20");
		path = path.replace(" ", "%20");
		query = query.replace(" ", "%20");
		fragment = fragment.replace(" ", "%20");
		u = new URI(scheme, authority, path, query, fragment);
	}

	public String getPath() {
		return u.getPath().replace("%20", " ");
	}

	public String toString() {
		return u.toString().replace("%20", " ");
	}

	@SuppressWarnings("WeakerAccess")
	public SpaceSafeURI resolve(String path) {
		return new SpaceSafeURI(u.resolve(path.replace(" ", "%20")));
	}

	public SpaceSafeURI resolve(SpaceSafeURI loc) {
		return new SpaceSafeURI(u.resolve(loc.u));
	}

	public SpaceSafeURI relativize(SpaceSafeURI loc) {
		return new SpaceSafeURI(u.relativize(loc.u));
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof SpaceSafeURI) {
			return u.equals(((SpaceSafeURI) obj).u);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return u.hashCode();
	}

	@Override
	public int compareTo(SpaceSafeURI uri) {
		return u.compareTo(uri.u);
	}

	public String getScheme() {
		return u.getScheme();
	}

	public String getAuthority() {
		return u.getAuthority();
	}

	public String getHost() {
		return u.getHost();
	}

	public URL toURL() throws MalformedURLException {
		return u.toURL();
	}
}
