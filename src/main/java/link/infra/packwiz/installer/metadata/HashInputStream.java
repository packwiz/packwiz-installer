package link.infra.packwiz.installer.metadata;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashInputStream extends FilterInputStream {

	private MessageDigest md;
	private byte[] output;

	public HashInputStream(InputStream in) throws NoSuchAlgorithmException {
		super(in);
		md = MessageDigest.getInstance("SHA-256");
	}

	@Override
	public int read() throws IOException {
		int value = super.read();
		if (value == -1) {
			return value;
		}
		md.update((byte) value);
		return value;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int bytesRead = super.read(b, off, len);
		if (bytesRead > 0) {
			md.update(b, off, len);
		}
		return bytesRead;
	}

	@Override
	public void reset() throws IOException {
		throw new IOException("HashInputStream doesn't support reset()");
	}

	@Override
	public boolean markSupported() {
		return false;
	}

	@Override
	public void mark(int readlimit) {
		// Do nothing
	}

	public byte[] getHashValue() {
		if (output == null) {
			output = md.digest();
		}
		return output;
	}

}