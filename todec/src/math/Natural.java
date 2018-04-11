/*
 * Copyright (c) 2018, Raffaello Giulietti. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 * This particular file is subject to the "Classpath" exception as provided
 * in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package math;

import java.math.BigInteger;

import static java.lang.Math.max;

/**
 * A minimal, limited implementation of non-negative large integers.
 *
 * <p>All operations implemented here are needed in other parts of the package,
 * while many are missing entirely because they are not needed.
 */
final class Natural {
    private static final long I_SZ = Integer.SIZE;
    private static final long I_MASK = (1L << I_SZ) - 1;
    private static final int I_MSB = -1 << I_SZ - 1;
    private static final long L_MSB = -1L << Long.SIZE - 1;

    /*
    A large natural is represented as a sequence of len B-ary digits,
    that is, in base
        B = 2^Integer.SIZE = 2^32
    Its value is
        d[0] + d[1]*B + d[2]*B^2 + ... + d[len-1]*B^(len-1)
    where each B-ary digit d[i] is interpreted as unsigned int.
    As usual, an empty sum has value 0, so if len = 0 then the value is 0.

    The following invariants hold:
        0 <= len <= d.length
        either len = 0 or d[len-1] != 0
     */
    private final int[] d;
    private final int len;

    private Natural(int[] d) {
        int i = d.length;
        while (--i >= 0 && d[i] == 0); // empty body intended
        this.len = i + 1;
        this.d = d;
    }

    /**
     * Returns a {@link Natural} with the same value as {@code v},
     * which is interpreted as an unsigned {@code long}.
     */
    static Natural valueOf(long v) {
        return new Natural(new int[]{(int) v, (int) (v >>> 32)});
    }

    /**
     * Returns a {@link Natural} with the value
     * {@code v} &#xb7; 2<sup>{@code n}</sup>.
     * The value {@code v} is interpreted as an unsigned {@code long} and
     * {@code n} must be non-negative.
     */
    static Natural valueOfShiftLeft(long v, int n) {
        // I_SZ = 2^5
        int q = n >> 5;
        int r = n & 0x1f;
        // length is q plus additional 2 for v and 1 for possible overlapping
        int[] rd = new int[q + 3];
        rd[q] = (int) v << r;
        rd[q + 1] = (int) (v >>> I_SZ - r);
        // A safe shift by 64 - r, even when r = 0
        rd[q + 2] = (int) (v >>> I_SZ >>> I_SZ - r);
        return new Natural(rd);
    }

    /**
     * Returns -1, 0 or 1, depending on whether {@code this} is
     * &#x3c;, = or &#x3e; {@code y}, respectively.
     */
    int compareTo(Natural y) {
        if (len < y.len) {
            return -1;
        }
        if (len > y.len) {
            return 1;
        }
        int i = len;
        while (--i >= 0 && d[i] == y.d[i]); // empty body intended
        if (i < 0) {
            return 0;
        }
        // Perform an unsigned int comparision
        if ((d[i] ^ I_MSB) < (y.d[i] ^ I_MSB)) {
            return -1;
        }
        return 1;
    }

    /**
     * Returns -1, 0 or 1, depending on whether {@code this} is closer to
     * {@code x}, equally close to both {@code x} and {@code y} or closer to
     * {@code y}, respectively.
     */
    int closerTo(Natural x, Natural y) {
        /*
        computes (2 * this - x - y).compareTo(0) without allocating objects
        for the intermediate results.
         */
        int cmp = 0;
        long c = 0;
        int maxLen = max(max(x.len, y.len), len);
        for (int i = 0; i < maxLen; ++i) {
            long td = i < len ? d[i] & I_MASK : 0;
            long xd = i < x.len ? x.d[i] & I_MASK : 0;
            long yd = i < y.len ? y.d[i] & I_MASK : 0;
            long s = (td << 1) - xd - yd + c;
            cmp |= (int) s;
            c = s >> I_SZ;
        }
        if (c < 0) {
            return -1;
        }
        if (cmp != 0) {
            return 1;
        }
        return 0;
    }

    /**
     * Returns {@code this} * {@code y}, where {@code y} is taken as an
     * unsigned {@code long}.
     */
    Natural multiply(long y) {
        // Straightforward paper-and-pencil method for multiplication.
        int[] rd = new int[len + 2];
        long y0 = y & I_MASK;
        long y1 = y >>> I_SZ;
        long c = 0;
        long r1 = 0;
        long q0 = 0;
        long q1 = 0;
        long s;
        int i = 0;
        for (; i < len; ++i) {
            long td = d[i] & I_MASK;
            long p0 = y0 * td;
            s = (r1 >>> I_SZ) + (q0 >>> I_SZ) +
                    (q1 & I_MASK) + (p0 & I_MASK) + c;
            rd[i] = (int) s;
            c = s >>> I_SZ;
            r1 = q1;
            q0 = p0;
            q1 = y1 * td;
        }
        s = (r1 >>> I_SZ) + (q0 >>> I_SZ) + (q1 & I_MASK) + c;
        rd[i] = (int) s;
        c = s >>> I_SZ;
        rd[i + 1] = (int) (c + (q1 >>> I_SZ));
        return new Natural(rd);
    }

    /**
     * Returns {@code this} - {@code y}, where it is assumed that
     * {@code this} &#x2265; {@code y}.
     */
    Natural subtract(Natural y) {
        int[] rd = new int[len];
        long c = 0;
        int i = 0;
        for (; i < y.len; ++i) {
            long s = (d[i] & I_MASK) - (y.d[i] & I_MASK) + c;
            rd[i] = (int) s;
            c = s >> I_SZ;
        }
        for (; i < len; ++i) {
            long s = (d[i] & I_MASK) + c;
            rd[i] = (int) s;
            c = s >> I_SZ;
        }
        return new Natural(rd);
    }

    /**
     * Returns &#x23a3;{@code this} &#xb7; 2<sup>-{@code n}</sup>&#x23a6;,
     * where it is assumed that {@code n} &#x2265; 0 and that the result
     * is an unsigned {@code long}.
     */
    long shiftRight(int n) {
        int q = n >> 5;
        int r = n & 0x1f;
        long d0 = d[q] & I_MASK;
        long d1 = d[q + 1] & I_MASK;
        long d2 = q + 2 < len ? d[q + 2] & I_MASK : 0;
        // The double shift is safe even when r = 0
        return d0 >>> r | d1 << I_SZ - r | d2 << I_SZ << I_SZ - r;
    }

    /**
     * Returns {@code this} + {@code y} &#xb7; 2<sup>{@code n}</sup>,
     * where it is assumed that {@code n} &#x2265; 0.
     */
    Natural addShiftLeft(Natural y, int n) {
        int maxLen = max(len, y.len);
        int[] rd = new int[maxLen + 1];

        long c = 0;
        long yd = 0;
        int i = 0;
        for (; i < maxLen; ++i) {
            long t0 = i < len ? d[i] & I_MASK : 0;
            long y0 = i < y.len ? y.d[i] & I_MASK : 0;
            yd = yd >>> I_SZ | y0 << n;
            long s = t0 + (yd & I_MASK) + c;
            rd[i] = (int) s;
            c = s >>> I_SZ;
        }
        rd[i] = (int) ((yd >>> I_SZ) + c);
        return new Natural(rd);
    }

    private BigInteger toBigInteger() {
        // additional 1 for the "sign" most significant byte at index 0
        byte[] b = new byte[1 + (len << 2)];
        for (int i = 1; i <= len; ++i) {
            int d0 = d[len - i];
            b[(i << 2) - 3] = (byte) (d0 >>> 24);
            b[(i << 2) - 2] = (byte) (d0 >>> 16);
            b[(i << 2) - 1] = (byte) (d0 >>> 8);
            b[i << 2] = (byte) d0;
        }
        return new BigInteger(b);
    }

    /*
    Here for debugging purposes only. There's otherwise no need for it.
     */
    @Override
    public String toString() {
        // Quick-and-dirty solution to avoid implementing a special division.
        return toBigInteger().toString();
    }

    /*
    Assumes 0 <= n
     */
    private Natural shiftLeft(int n) {
        int q = n >> 5;
        int r = n & 0x1f;
        // Allocates one int more than necessary to simplify the division
        if (r == 0) {
            int[] rd = new int[len + q + 1];
            for (int i = 0; i < len; ++i) {
                rd[q + i] = d[i];
            }
            return new Natural(rd);
        }
        int[] rd = new int[len + q + 2];
        rd[q] = d[0] << r;
        int i = 1;
        for (; i < len; ++i) {
            // safe shift, as 0 < r < I_SZ
            rd[q + i] = d[i] << r | d[i - 1] >>> I_SZ - r;
        }
        rd[q + i] = d[i - 1] >>> I_SZ - r;
        return new Natural(rd);
    }

    /**
     * Returns &#x23a3;{@code this} / {@code y}&#x23a6;.
     * <p>Assumes that:
     * <ul>
     * <li> {@code this} &#x2265; 2<sup>32</sup>.
     * <li> {@code y} &#x3e; 0.
     * <li> The result is an unsigned {@code long} &#x2265; 2<sup>32</sup>.
     * </ul>
     */
    long divide(Natural y) {
        int r = Integer.numberOfLeadingZeros(y.d[y.len - 1]) - 1 & 0x1f;
        // Ensure that v.len >= 2 and that vp meets the inequalities below
        if (y.len == 1) {
            r += I_SZ;
        }
        Natural u = shiftLeft(r);
        Natural v = y.shiftLeft(r);
        // by construction, 2^30 <= vp < 2^31: no need for masking
        long vp = v.d[v.len - 1];
        long v_n2 = v.d[v.len - 2] & I_MASK;
        long q = 0;
        for (int k = 1; k >= 0; --k) {
            int n = v.len + k;
            // this assumes that n <= u.d.length
            long up = (long) u.d[n] << I_SZ | u.d[n - 1] & I_MASK;
            long qb = up / vp;
            if (qb > I_MASK) qb = I_MASK;
            long rb = up - qb * vp;
            while (rb <= I_MASK &&
                    (qb * v_n2 ^ L_MSB) >
                            ((rb << I_SZ | u.d[n - 2] & I_MASK) ^ L_MSB)) {
                qb -= 1;
                rb += vp;
            }
            long s = 0;
            int i = 0;
            for (; i < v.len; ++i) {
                long p = qb * (v.d[i] & I_MASK) + s;
                long t = (u.d[i + k] & I_MASK) - (p & I_MASK);
                u.d[i + k] = (int) t;
                s = (p >>> I_SZ) - (t >> I_SZ);
            }
            long t = (u.d[i + k] & I_MASK) - (s & I_MASK);
            u.d[i + k] = (int) t;
            s = -(t >> I_SZ);
            if (s > 0) {
                qb -= 1;
                s = 0;
                for (i = 0; i < v.len; ++i) {
                    t = (u.d[i + k] & I_MASK) + (v.d[i] & I_MASK) + s;
                    u.d[i + k] = (int) t;
                    s = t >>> I_SZ;
                }
            }
            q = q << I_SZ | qb;
        }
        return q;
    }

}
