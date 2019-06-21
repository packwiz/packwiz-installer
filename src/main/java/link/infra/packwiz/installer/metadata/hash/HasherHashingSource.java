package link.infra.packwiz.installer.metadata.hash;

import okio.HashingSource;
import okio.Source;

public class HasherHashingSource implements IHasher {
	String type;

	public HasherHashingSource(String type) {
		this.type = type;
	}

	// i love naming things
	private class HashingSourceGeneralHashingSource extends GeneralHashingSource {
		HashingSource delegateHashing;
		HashingSourceHash value;

		public HashingSourceGeneralHashingSource(HashingSource delegate) {
			super(delegate);
			delegateHashing = delegate;
		}

		@Override
		public Object getHash() {
			if (value == null) {
				value = new HashingSourceHash(delegateHashing.hash().hex());
			}
			return value;
		}

	}

	// this some funky inner class stuff
	// each of these classes is specific to the instance of the HasherHashingSource
	// therefore HashingSourceHashes from different parent instances will be not instanceof each other
	private class HashingSourceHash {
		String value;
		private HashingSourceHash(String value) {
			this.value = value;
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof HashingSourceHash)) {
				return false;
			}
			HashingSourceHash objHash = (HashingSourceHash) obj;
			if (value != null) {
				return value.equals(objHash.value);
			} else {
				return objHash.value == null ? true : false;
			}
		}

		@Override
		public String toString() {
			return type + ": " + value;
		}
	}

	@Override
	public GeneralHashingSource getHashingSource(Source delegate) {
		switch (type) {
			case "sha256":
			return new HashingSourceGeneralHashingSource(HashingSource.sha256(delegate));
			// TODO: support other hash types
		}
		throw new RuntimeException("Invalid hash type provided");
	}

	@Override
	public Object getHash(String value) {
		return new HashingSourceHash(value);
	}
	
}