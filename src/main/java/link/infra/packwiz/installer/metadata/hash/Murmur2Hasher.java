package link.infra.packwiz.installer.metadata.hash;

import java.io.IOException;

import okio.Buffer;
import okio.Source;

public class Murmur2Hasher implements IHasher {
	private class Murmur2GeneralHashingSource extends GeneralHashingSource {
		Murmur2Hash value;
		Buffer internalBuffer = new Buffer();
		Buffer tempBuffer = new Buffer();
		Source delegate;

		public Murmur2GeneralHashingSource(Source delegate) {
			super(delegate);
			this.delegate = delegate;
		}

		@Override
		public long read(Buffer sink, long byteCount) throws IOException {
			long out = delegate.read(tempBuffer, byteCount);
			if (out > -1) {
				sink.write(tempBuffer.clone(), out);
				internalBuffer.write(tempBuffer, out);
			}
			return out;
		}

		@Override
		public Object getHash() {
			if (value == null) {
				byte[] data = computeNormalizedArray(internalBuffer.readByteArray());
				value = new Murmur2Hash(Murmur2Lib.hash32(data, data.length, 1));
			}
			return value;
		}

		// Credit to https://github.com/modmuss50/CAV2/blob/master/murmur.go
		private byte[] computeNormalizedArray(byte[] input) {
			byte[] output = new byte[input.length];
			int num = 0;
			for (int i = 0; i < input.length; i++) {
				byte b = input[i];
				if (!(b == 9 || b == 10 || b == 13 || b == 32)) {
					output[num] = b;
					num++;
				}
			}
			byte[] outputTrimmed = new byte[num];
			System.arraycopy(output, 0, outputTrimmed, 0, num);
			return outputTrimmed;
		}

	}

	private class Murmur2Hash {
		int value;
		private Murmur2Hash(String value) {
			// Parsing as long then casting to int converts values gt int max value but lt uint max value
			// into negatives. I presume this is how the murmur2 code handles this.
			this.value = (int)Long.parseLong(value);
		}

		private Murmur2Hash(int value) {
			this.value = value;
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof Murmur2Hash)) {
				return false;
			}
			Murmur2Hash objHash = (Murmur2Hash) obj;
			return value == objHash.value;
		}

		@Override
		public String toString() {
			return "murmur2: " + value;
		}
	}

	@Override
	public GeneralHashingSource getHashingSource(Source delegate) {
		return new Murmur2GeneralHashingSource(delegate);
	}

	@Override
	public Object getHash(String value) {
		return new Murmur2Hash(value);
	}
	
}