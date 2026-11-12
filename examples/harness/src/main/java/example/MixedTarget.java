// SPDX-License-Identifier: Apache-2.0
package example;

/** Small all-types integration fixture; arithmetic overflow is defined in Java. */
public final class MixedTarget {
    private MixedTarget() {}
    public static long fingerprint(byte[] bytes, String text, int salt, long seed, boolean enabled) {
        return enabled ? seed + salt + text.hashCode() + java.util.Arrays.hashCode(bytes) : seed;
    }
}
