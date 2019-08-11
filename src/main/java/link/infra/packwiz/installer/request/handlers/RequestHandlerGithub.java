package link.infra.packwiz.installer.request.handlers;

import link.infra.packwiz.installer.metadata.SpaceSafeURI;

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
	public SpaceSafeURI getNewLoc(SpaceSafeURI loc) {
		return loc;
	}
	
	// TODO: is caching really needed, if HTTPURLConnection follows redirects correctly?
	private Map<String, SpaceSafeURI> zipUriMap = new HashMap<>();
	private final ReentrantReadWriteLock zipUriLock = new ReentrantReadWriteLock();
	private static Pattern repoMatcherPattern = Pattern.compile("/([\\w.-]+/[\\w.-]+).*");
	
	private String getRepoName(SpaceSafeURI loc) {
		Matcher matcher = repoMatcherPattern.matcher(loc.getPath());
		if (matcher.matches()) {
			return matcher.group(1);
		} else {
			return null;
		}
	}

	@Override
	protected SpaceSafeURI getZipUri(SpaceSafeURI loc) throws Exception {
		String repoName = getRepoName(loc);
		String branchName = getBranch(loc);
		zipUriLock.readLock().lock();
		SpaceSafeURI zipUri = zipUriMap.get(repoName + "/" + branchName);
		zipUriLock.readLock().unlock();
		if (zipUri != null) {
			return zipUri;
		}
		
		zipUri = new SpaceSafeURI("https://api.github.com/repos/" + repoName + "/zipball/" + branchName);
		
		zipUriLock.writeLock().lock();
		// If another thread sets the value concurrently, use the value of the
		// thread that first acquired the lock.
		SpaceSafeURI zipUriInserted = zipUriMap.putIfAbsent(repoName + "/" + branchName, zipUri);
		if (zipUriInserted != null) {
			zipUri = zipUriInserted;
		}
		zipUriLock.writeLock().unlock();
		return zipUri;
	}
	
	private static Pattern branchMatcherPattern = Pattern.compile("/[\\w.-]+/[\\w.-]+/blob/([\\w.-]+).*");
	
	private String getBranch(SpaceSafeURI loc) {
		Matcher matcher = branchMatcherPattern.matcher(loc.getPath());
		if (matcher.matches()) {
			return matcher.group(1);
		} else {
			return null;
		}
	}

	@Override
	protected SpaceSafeURI getLocationInZip(SpaceSafeURI loc) throws Exception {
		String path = "/" + getRepoName(loc) + "/blob/" + getBranch(loc);
		return new SpaceSafeURI(loc.getScheme(), loc.getAuthority(), path, null, null).relativize(loc);
	}

	@Override
	public boolean matchesHandler(SpaceSafeURI loc) {
		String scheme = loc.getScheme();
		if (!("http".equals(scheme) || "https".equals(scheme))) {
			return false;
		}
		if (!"github.com".equals(loc.getHost())) {
			return false;
		}
		// TODO: sanity checks, support for more github urls
		return true;
	}

}
