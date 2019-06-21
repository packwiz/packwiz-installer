package link.infra.packwiz.installer.metadata.hash;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

public class Hash {
	public final String value;
	public final String type;

	public Hash(String value, String type) {
		this.value = value;
		this.type = type;
	}

	private static final Map<String, IHasher> hashTypeConversion = new HashMap<String, IHasher>();
	static {
		try {
			hashTypeConversion.put("sha256", new HasherMessageDigest("SHA-256"));
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
	}

	public IHasher getHasher() {
		return hashTypeConversion.get(type);
	}

	public static IHasher getHasher(String type) {
		return hashTypeConversion.get(type);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || !(obj instanceof Hash)) {
			return false;
		}
		Hash hash = (Hash)obj;
		return type.equals(hash.type) && getHasher().equalValues(value, hash.value);
	}

	public static class HashInputStream extends FilterInputStream {

		private IHasher md;
		private Hash output;
		private final String hashType;
		private Hash compare = null;

		public HashInputStream(InputStream in, String hashType) throws NoSuchAlgorithmException {
			super(in);
			this.hashType = hashType;
			md = hashTypeConversion.get(hashType);
		}

		public HashInputStream(InputStream in, Hash compare) throws NoSuchAlgorithmException {
			this(in, compare.type);
			this.compare = compare;
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

		public Hash get() {
			if (output == null) {
				String value = md.get();
				if (value != null) {
					output = new Hash(value, hashType);
				}
			}
			return output;
		}

		public boolean hashIsEqual() {
			if (output == null) {
				get();
			}
			return !output.equals(compare);
		}

	}
}