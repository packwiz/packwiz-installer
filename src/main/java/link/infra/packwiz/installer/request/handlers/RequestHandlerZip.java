package link.infra.packwiz.installer.request.handlers;

import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;
import okio.Source;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public abstract class RequestHandlerZip extends RequestHandlerHTTP {
	
	private final boolean modeHasFolder;
	
	public RequestHandlerZip(boolean modeHasFolder) {
		this.modeHasFolder = modeHasFolder;
	}
	
	private String removeFolder(String name) {
		if (modeHasFolder) {
			return name.substring(name.indexOf("/")+1);
		} else {
			return name;
		}
	}
	
	private class ZipReader {
		
		private final ZipInputStream zis;
		private final Map<URI, Buffer> readFiles = new HashMap<>();
		// Write lock implies access to ZipInputStream - only 1 thread must read at a time!
		final ReentrantLock filesLock = new ReentrantLock();
		private ZipEntry entry;

		private final BufferedSource zipSource;

		ZipReader(Source zip) {
			zis = new ZipInputStream(Okio.buffer(zip).inputStream());
			zipSource = Okio.buffer(Okio.source(zis));
		}
		
		// File lock must be obtained before calling this function
		private Buffer readCurrFile() throws IOException {
			Buffer fileBuffer = new Buffer();
			zipSource.readFully(fileBuffer, entry.getSize());
			return fileBuffer;
		}
		
		// File lock must be obtained before calling this function
		private Buffer findFile(URI loc) throws IOException, URISyntaxException {
			while (true) {
				entry = zis.getNextEntry();
				if (entry == null) {
					return null;
				}
				Buffer data = readCurrFile();
				URI fileLoc = new URI(removeFolder(entry.getName()));
				if (loc.equals(fileLoc)) {
					return data;
				} else {
					readFiles.put(fileLoc, data);
				}
			}
		}
		
		Source getFileSource(URI loc) throws Exception {
			filesLock.lock();
			// Assume files are only read once, allow GC by removing
			Buffer file = readFiles.remove(loc);
			if (file != null) {
				filesLock.unlock();
				return file;
			}
			
			file = findFile(loc);
			filesLock.unlock();
			return file;
		}
		
		URI findInZip(Predicate<URI> matches) throws Exception {
			filesLock.lock();
			for (URI file : readFiles.keySet()) {
				if (matches.test(file)) {
					filesLock.unlock();
					return file;
				}
			}
			
			while (true) {
				entry = zis.getNextEntry();
				if (entry == null) {
					filesLock.unlock();
					return null;
				}
				Buffer data = readCurrFile();
				URI fileLoc = new URI(removeFolder(entry.getName()));
				readFiles.put(fileLoc, data);
				if (matches.test(fileLoc)) {
					filesLock.unlock();
					return fileLoc;
				}
			}
		}
		
	}
	
	private final Map<URI, ZipReader> cache = new HashMap<>();
	private final ReentrantReadWriteLock cacheLock = new ReentrantReadWriteLock();
	
	protected abstract URI getZipUri(URI loc) throws Exception;
	
	protected abstract URI getLocationInZip(URI loc) throws Exception;
	
	@Override
	public abstract boolean matchesHandler(URI loc);

	@Override
	public Source getFileSource(URI loc) throws Exception {
		URI zipUri = getZipUri(loc);
		cacheLock.readLock().lock();
		ZipReader zr = cache.get(zipUri);
		cacheLock.readLock().unlock();
		if (zr == null) {
			cacheLock.writeLock().lock();
			// Recheck, because unlocking read lock allows another thread to modify it
			zr = cache.get(zipUri);
			if (zr == null) {
				Source src = super.getFileSource(zipUri);
				if (src == null) {
					cacheLock.writeLock().unlock();
					return null;
				}
				zr = new ZipReader(src);
				cache.put(zipUri, zr);
			}
			cacheLock.writeLock().unlock();
		}
		
		return zr.getFileSource(getLocationInZip(loc));
	}
	
	protected URI findInZip(URI loc, Predicate<URI> matches) throws Exception {
		URI zipUri = getZipUri(loc);
		cacheLock.readLock().lock();
		ZipReader zr = cache.get(zipUri);
		cacheLock.readLock().unlock();
		if (zr == null) {
			cacheLock.writeLock().lock();
			// Recheck, because unlocking read lock allows another thread to modify it
			zr = cache.get(zipUri);
			if (zr == null) {
				zr = new ZipReader(super.getFileSource(zipUri));
				cache.put(zipUri, zr);
			}
			cacheLock.writeLock().unlock();
		}
		
		return zr.findInZip(matches);
	}

}
