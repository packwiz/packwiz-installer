package link.infra.packwiz.installer.metadata.hash;

import okio.ForwardingSource;
import okio.Source;

public abstract class GeneralHashingSource extends ForwardingSource {

	public GeneralHashingSource(Source delegate) {
		super(delegate);
	}

	public abstract Hash getHash();

	public boolean hashIsEqual(Object compareTo) {
		return compareTo.equals(getHash());
	}

}