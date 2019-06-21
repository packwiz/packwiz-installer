package link.infra.packwiz.installer.metadata.hash;

import okio.Source;

public interface IHasher {
	public GeneralHashingSource getHashingSource(Source delegate);
	public Object getHash(String value);
}