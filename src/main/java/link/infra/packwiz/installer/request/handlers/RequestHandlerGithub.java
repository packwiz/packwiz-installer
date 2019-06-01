package link.infra.packwiz.installer.request.handlers;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RequestHandlerGithub extends RequestHandlerZip {
	
	public RequestHandlerGithub() {
		super(true);
	}

	@Override
	public URI getNewLoc(URI loc) {
		return loc;
	}
	
	// TODO: is caching really needed, if HTTPURLConnection follows redirects correctly?
	private Map<String, URI> zipUriMap = new HashMap<String, URI>();
	final ReentrantReadWriteLock zipUriLock = new ReentrantReadWriteLock();
	private static Pattern repoMatcherPattern = Pattern.compile("/([\\w.-]+/[\\w.-]+).*");
	
	private String getRepoName(URI loc) {
		Matcher matcher = repoMatcherPattern.matcher(loc.getPath());
		matcher.matches();
		return matcher.group(1);
	}

	@Override
	protected URI getZipUri(URI loc) throws Exception {
		String repoName = getRepoName(loc);
		String branchName = getBranch(loc);
		zipUriLock.readLock().lock();
		URI zipUri = zipUriMap.get(repoName + "/" + branchName);
		zipUriLock.readLock().unlock();
		if (zipUri != null) {
			return zipUri;
		}
		
		zipUri = new URI("https://api.github.com/repos/" + repoName + "/zipball/" + branchName);
		
		zipUriLock.writeLock().lock();
		// If another thread sets the value concurrently, use the value of the
		// thread that first acquired the lock.
		URI zipUriInserted = zipUriMap.putIfAbsent(repoName + "/" + branchName, zipUri);
		if (zipUriInserted != null) {
			zipUri = zipUriInserted;
		}
		zipUriLock.writeLock().unlock();
		return zipUri;
	}
	
	private static Pattern branchMatcherPattern = Pattern.compile("/[\\w.-]+/[\\w.-]+/blob/([\\w.-]+).*");
	
	private String getBranch(URI loc) {
		Matcher matcher = branchMatcherPattern.matcher(loc.getPath());
		matcher.matches();
		return matcher.group(1);
	}

	@Override
	protected URI getLocationInZip(URI loc) throws Exception {
		String path = "/" + getRepoName(loc) + "/blob/" + getBranch(loc);
		return new URI(loc.getScheme(), loc.getAuthority(), path, null, null).relativize(loc);
	}

	@Override
	public boolean matchesHandler(URI loc) {
		String scheme = loc.getScheme();
		if (!(scheme.equals("http") || scheme.equals("https"))) {
			return false;
		}
		if (!loc.getHost().equals("github.com")) {
			return false;
		}
		// TODO: sanity checks, support for more github urls
		return true;
	}

}
