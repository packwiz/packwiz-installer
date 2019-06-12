package link.infra.packwiz.installer.metadata.hash;

public interface IHasher {
	public void update(byte[] data);
	public void update(byte[] data, int offset, int length);
	public void update(byte data);
	public String get();
	public default boolean equalValues(String a, String b) {
		if (a == null) {
			if (b == null) {
				return true;
			}
			return false;
		}
		return a.equals(b);
	}
}