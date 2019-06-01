package link.infra.packwiz.installer.request.handlers;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public abstract class RequestHandlerZip extends RequestHandlerHTTP {
	
	private class ZipReader {
		
		private final ZipInputStream zis;
		private final Map<URI, byte[]> readFiles = new HashMap<URI, byte[]>();
		// Write lock implies access to ZipInputStream - only 1 thread must read at a time!
		final ReentrantReadWriteLock filesLock = new ReentrantReadWriteLock();
		private ZipEntry entry;

		public ZipReader(InputStream zip) {
			zis = new ZipInputStream(zip);
		}
		
		// File write lock must be obtained before calling this function
		private byte[] readCurrFile() throws IOException {
			byte[] bytes = new byte[(int) entry.getSize()];
			DataInputStream dis = new DataInputStream(zis);
			dis.readFully(bytes);
			return bytes;
		}
		
		// File write lock must be obtained before calling this function
		private byte[] findFile(URI loc) throws IOException, URISyntaxException {
			while (true) {
				entry = zis.getNextEntry();
				if (entry == null) {
					return null;
				}
				byte[] data = readCurrFile();
				if (loc.equals(new URI(entry.getName()))) {
					return data;
				} else {
					readFiles.put(loc, data);
				}
			}
		}
		
		public InputStream getFileInputStream(URI loc) throws Exception {
			filesLock.readLock().lock();
			byte[] file = readFiles.get(loc);
			filesLock.readLock().unlock();
			if (file != null) {
				// Assume files are only read once, allow GC
				filesLock.writeLock().lock();
				readFiles.remove(loc);
				filesLock.writeLock().unlock();
				return new ByteArrayInputStream(file);
			}
			filesLock.writeLock().lock();
			// Test again after receiving write lock
			file = readFiles.get(loc);
			if (file != null) {
				// Assume files are only read once, allow GC
				readFiles.remove(loc);
				filesLock.writeLock().unlock();
				return new ByteArrayInputStream(file);
			}
			
			file = findFile(loc);
			filesLock.writeLock().unlock();
			if (file != null) {
				return new ByteArrayInputStream(file);
			}
			return null;
		}
		
	}
	
	private final Map<URI, ZipReader> cache = new HashMap<URI, ZipReader>();
	final ReentrantReadWriteLock cacheLock = new ReentrantReadWriteLock();
	
	protected abstract URI getZipUri(URI loc) throws Exception;
	
	protected abstract URI getLocationInZip(URI loc) throws Exception;
	
	@Override
	public abstract boolean matchesHandler(URI loc);

	@Override
	public InputStream getFileInputStream(URI loc) throws Exception {
		URI zipUri = getZipUri(loc);
		cacheLock.readLock().lock();
		ZipReader zr = cache.get(zipUri);
		cacheLock.readLock().unlock();
		if (zr == null) {
			cacheLock.writeLock().lock();
			// Recheck, because unlocking read lock allows another thread to modify it
			zr = cache.get(zipUri);
			if (zr == null) {
				zr = new ZipReader(super.getFileInputStream(zipUri));
				cache.put(zipUri, zr);
			}
			cacheLock.writeLock().unlock();
		}
		
		return zr.getFileInputStream(getLocationInZip(loc));
	}

}
