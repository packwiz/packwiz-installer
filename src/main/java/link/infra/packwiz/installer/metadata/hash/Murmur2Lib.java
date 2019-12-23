// Obtained from https://github.com/prasanthj/hasher/blob/master/src/main/java/hasher/Murmur2.java
/**
 *   Copyright 2014 Prasanth Jayachandran
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package link.infra.packwiz.installer.metadata.hash;

/**
 * Murmur2 32 and 64 bit variants.
 * 32-bit Java port of https://code.google.com/p/smhasher/source/browse/trunk/MurmurHash2.cpp#37
 * 64-bit Java port of https://code.google.com/p/smhasher/source/browse/trunk/MurmurHash2.cpp#96
 */
public class Murmur2Lib {
  // Constants for 32-bit variant
  private static final int M_32 = 0x5bd1e995;
  private static final int R_32 = 24;

  // Constants for 64-bit variant
  private static final long M_64 = 0xc6a4a7935bd1e995L;
  private static final int R_64 = 47;
  private static final int DEFAULT_SEED = 0;

  /**
   * Murmur2 32-bit variant.
   *
   * @param data - input byte array
   * @return - hashcode
   */
  public static int hash32(byte[] data) {
    return hash32(data, data.length, DEFAULT_SEED);
  }

  /**
   * Murmur2 32-bit variant.
   *
   * @param data   - input byte array
   * @param length - length of array
   * @param seed   - seed. (default 0)
   * @return - hashcode
   */
  public static int hash32(byte[] data, int length, int seed) {
    int h = seed ^ length;
    int len_4 = length >> 2;

    // body
    for (int i = 0; i < len_4; i++) {
      int i_4 = i << 2;
      int k = (data[i_4] & 0xff)
          | ((data[i_4 + 1] & 0xff) << 8)
          | ((data[i_4 + 2] & 0xff) << 16)
          | ((data[i_4 + 3] & 0xff) << 24);

      // mix functions
      k *= M_32;
      k ^= k >>> R_32;
      k *= M_32;
      h *= M_32;
      h ^= k;
    }

    // tail
    int len_m = len_4 << 2;
    int left = length - len_m;
    if (left != 0) {
      if (left >= 3) {
        h ^= (int) data[length - (left - 2)] << 16;
      }
      if (left >= 2) {
        h ^= (int) data[length - (left - 1)] << 8;
      }
      if (left >= 1) {
        h ^= data[length - left];
      }

      h *= M_32;
    }

    // finalization
    h ^= h >>> 13;
    h *= M_32;
    h ^= h >>> 15;

    return h;
  }

  /**
   * Murmur2 64-bit variant.
   *
   * @param data - input byte array
   * @return - hashcode
   */
  public static long hash64(final byte[] data) {
    return hash64(data, data.length, DEFAULT_SEED);
  }

  /**
   * Murmur2 64-bit variant.
   *
   * @param data   - input byte array
   * @param length - length of array
   * @param seed   - seed. (default 0)
   * @return - hashcode
   */
  public static long hash64(final byte[] data, int length, int seed) {
    long h = (seed & 0xffffffffl) ^ (length * M_64);
    int length8 = length >> 3;

    // body
    for (int i = 0; i < length8; i++) {
      final int i8 = i << 3;
      long k = ((long) data[i8] & 0xff)
          | (((long) data[i8 + 1] & 0xff) << 8)
          | (((long) data[i8 + 2] & 0xff) << 16)
          | (((long) data[i8 + 3] & 0xff) << 24)
          | (((long) data[i8 + 4] & 0xff) << 32)
          | (((long) data[i8 + 5] & 0xff) << 40)
          | (((long) data[i8 + 6] & 0xff) << 48)
          | (((long) data[i8 + 7] & 0xff) << 56);

      // mix functions
      k *= M_64;
      k ^= k >>> R_64;
      k *= M_64;
      h ^= k;
      h *= M_64;
    }

    // tail
    int tailStart = length8 << 3;
    switch (length - tailStart) {
      case 7:
        h ^= (long) (data[tailStart + 6] & 0xff) << 48;
      case 6:
        h ^= (long) (data[tailStart + 5] & 0xff) << 40;
      case 5:
        h ^= (long) (data[tailStart + 4] & 0xff) << 32;
      case 4:
        h ^= (long) (data[tailStart + 3] & 0xff) << 24;
      case 3:
        h ^= (long) (data[tailStart + 2] & 0xff) << 16;
      case 2:
        h ^= (long) (data[tailStart + 1] & 0xff) << 8;
      case 1:
        h ^= data[tailStart] & 0xff;
        h *= M_64;
    }

    // finalization
    h ^= h >>> R_64;
    h *= M_64;
    h ^= h >>> R_64;

    return h;
  }
}