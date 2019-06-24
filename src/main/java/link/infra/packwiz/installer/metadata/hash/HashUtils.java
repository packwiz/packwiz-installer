package link.infra.packwiz.installer.metadata.hash;

import java.util.HashMap;
import java.util.Map;

public class HashUtils {
	private static final Map<String, IHasher> hashTypeConversion = new HashMap<String, IHasher>();
	static {
		hashTypeConversion.put("sha256", new HashingSourceHasher("sha256"));
		hashTypeConversion.put("murmur2", new Murmur2Hasher());
	}

	public static IHasher getHasher(String type) throws Exception {
		IHasher hasher = hashTypeConversion.get(type);
		if (hasher == null) {
			throw new Exception("Hash type not supported: " + type);
		}
		return hasher;
	}

	public static Object getHash(String type, String value) throws Exception {
		if (hashTypeConversion.containsKey(type)) {
			return hashTypeConversion.get(type).getHash(value);
		}

		throw new Exception("Hash type not supported: " + type);
	}

}