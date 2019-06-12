package link.infra.packwiz.installer.metadata.hash;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HasherMessageDigest implements IHasher {
	MessageDigest md;

	public HasherMessageDigest(String hashType) throws NoSuchAlgorithmException {
		md = MessageDigest.getInstance(hashType);
	}

	@Override
	public void update(byte[] data) {
		md.update(data);
	}

	@Override
	public void update(byte[] data, int offset, int length) {
		md.update(data, offset, length);
	}

	@Override
	public void update(byte data) {
		md.update(data);
	}

	@Override
	public String get() {
		return HashUtils.printHexBinary(md.digest());
	}

	// Enforce case insensitivity
	@Override
	public boolean equalValues(String a, String b) {
		if (a == null) {
			if (b == null) {
				return true;
			}
			return false;
		}
		return a.equalsIgnoreCase(b);
	}
	
}