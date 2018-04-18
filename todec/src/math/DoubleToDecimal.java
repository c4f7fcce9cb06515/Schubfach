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

import static java.lang.Double.*;
import static java.lang.Math.max;
import static java.lang.Long.numberOfLeadingZeros;
import static math.MathUtils.*;
import static math.DoubleToDecimal.Double.*;
import static math.Natural.valueOfShiftLeft;
import static math.Powers.*;

/**
 * This class exposes a method to render a {@code double} as a string.
 */
final public class DoubleToDecimal {
    /*
    For full details of the logic in this and the other supporting classes,
    search the web for
        d6b9e38fbe27f199d27e19f25acc26452e7e2ece
    and check that the title reads
        "Rendering doubles in Java"
     */

    /**
     * Exposes some constants related to the IEEE 754-2008 breakdown of
     * {@code double}s and some extractors suited for finite positive values.
     *
     * <p>A finite positive {@code double} <i>v</i> has the form
     * <i>v</i> = <i>c</i>&#xb7;2<sup><i>q</i></sup>,
     * where integers <i>c</i>, <i>q</i> meet
     * <ul>
     * <li> either 2<sup>{@link #P}-1</sup> &#x2264; <i>c</i> &#x3c;
     * 2<sup>{@link #P}</sup> and {@link #Q_MIN} &#x2264; <i>q</i> &#x2264;
     * {@link #Q_MAX} (normal <i>v</i>)
     * <li> or 0 &#x3c; <i>c</i> &#x3c; 2<sup>{@link #P}-1</sup> and
     * <i>c</i> = {@link #Q_MIN} (subnormal <i>v</i>)
     * </ul>
     */
    static final class Double {

        /**
         * Precision of normal values in bits.
         */
        static final int P = 53;

        /**
         * Length in bits of the exponent field.
         */
        static final int W = (java.lang.Double.SIZE - 1) - (P - 1);

        /**
         * Minimum value of the exponent.
         */
        static final int Q_MIN = (-1 << W - 1) - P + 3;

        /**
         * Maximum value of the exponent.
         */
        static final int Q_MAX = (1 << W - 1) - P;

        /**
         * Minimum value of the coefficient of a normal value.
         */
        static final long C_MIN = 1L << P - 1;

        /**
         * Maximum value of the coefficient of a normal value.
         */
        static final long C_MAX = (1L << P) - 1;

        /**
         * H = min {n integer | 10^(n-1) > 2^P}
         */
        static final int H = 17;

        /**
         * G = max {n integer | 2^(P-1) > 10^n}
         */
        static final int G = 15;

        /**
         * The integer <i>e</i> such that
         * 10<sup><i>e</i>-1</sup> &#x2264; {@link java.lang.Double#MIN_VALUE}
         * &#x3c; 10<sup><i>e</i></sup>.
         */
        static final int E_MIN_VALUE = -323;

        /**
         * The integer <i>e</i> such that
         * 10<sup><i>e</i>-1</sup> &#x2264; {@link java.lang.Double#MIN_NORMAL}
         * &#x3c; 10<sup><i>e</i></sup>.
         */
        static final int E_MIN_NORMAL = -307;

        /**
         * The integer <i>e</i> such that
         * 10<sup><i>e</i>-1</sup> &#x2264; {@link java.lang.Double#MAX_VALUE}
         * &#x3c; 10<sup><i>e</i></sup>.
         */
        static final int E_MAX_VALUE = 309;

        // Mask to extract the IEEE 754-2008 biased exponent.
        private static final int BQ_MASK = (1 << W) - 1;

        // Mask to extract the IEEE 754-2008 fraction bits.
        private static final long T_MASK = (1L << P - 1) - 1;

        // Constants for the computation of roundCeilPow10()
        private static final int D = Long.SIZE - P;
        private static final long CEIL_EPS = (1L << D) - 1;
        private static final int ORD_2_MIN_NORMAL = Q_MIN + P;

        private Double() {
        }

        private static int bq(long bits) {
            return (int) (bits >>> P - 1) & BQ_MASK;
        }

        /**
         * Given the {@code bits} of a finite positive {@code double},
         * returns <i>q</i> described in {@link java.lang.Double}.
         */
        static int q(long bits) {
            int bq = bq(bits);
            if (bq > 0) {
                return Q_MIN - 1 + bq;
            }
            return Q_MIN;
        }

        /**
         * Given the {@code bits} of a finite positive {@code double},
         * returns <i>c</i> described in {@link java.lang.Double}.
         */
        static long c(long bits) {
            int bq = bq(bits);
            long t = bits & T_MASK;
            if (bq > 0) {
                return C_MIN | t;
            }
            return t;
        }

        private static int ord2(int q, long c) {
            // Fast path for the normal case.
            if (c >= C_MIN) {
                return P + q;
            }
            return Q_MIN + Long.SIZE - numberOfLeadingZeros(c);
        }

        /**
         * For finite positive {@code v}, returns the integer <i>e</i> such that
         * 2<sup><i>e</i>-1</sup> &#x2264; {@code v} &#x3c;
         * 2<sup><i>e</i></sup>.
         */
        private static int ord2(double v) {
            long bits = java.lang.Double.doubleToRawLongBits(v);
            return ord2(q(bits), c(bits));
        }

        /**
         * For finite positive {@code v}, returns the integer <i>e</i> such that
         * 10<sup><i>e</i>-1</sup> &#x2264; {@code v} &#x3c;
         * 10<sup><i>e</i></sup>.
         */
        static int ord10(double v) {
            int ep = ord10pow2(ord2(v)) - 1;
            if (v < roundCeilPow10(ep)) return ep;
            return ep + 1;
        }

        // Returns the smallest double v such that 10^e <= v.
        private static double roundCeilPow10(int e) {
            int e2 = ord2pow10(e);
            if (e2 >= ORD_2_MIN_NORMAL) {
                long c = (floorPow10d(e) + CEIL_EPS) >>> D;
                int q = e2 - P;
                long bits = (long) (q - (Q_MIN - 1)) << P - 1 | c & T_MASK;
                return java.lang.Double.longBitsToDouble(bits);
            }
            int d = ORD_2_MIN_NORMAL + D - e2;
            if (d < Long.SIZE) {
                long c = (floorPow10d(e) + (1L << d) - 1) >>> d;
                return java.lang.Double.longBitsToDouble(c);
            }
            return java.lang.Double.MIN_VALUE;
        }

    }

    // used in the left-to-right extraction of the digits
    private static final int LTR = 28;
    private static final int MASK_LTR = (1 << LTR) - 1;

    // MAX_SIGNIFICAND = 10^H
    private static final long MAX_SIGNIFICAND = 100_000_000_000_000_000L;

    // The additional precision, used in reduced()
    private static final int D = Long.SIZE - P;

    // for thread-safety, each thread gets its own instance of this class
    private static final ThreadLocal<DoubleToDecimal> threadLocal =
            ThreadLocal.withInitial(DoubleToDecimal::new);

    /*
    Given finite positive double v, there are two breakdowns:
        v = c * 2^q, as described in DoubleToDecimal.Double
        v = f * 10^e, with 0.1 <= f < 1
    e, q, and c are kept in the following fields.
     */
    private int e;
    private int q;
    private long c;

    /*
    For the default IEEE round-to-closest rounding, lout = rout always holds.
    However, two fields are kept for possible future extensions.
    Possible values are
        0, if the boundary of the rounding interval is included
        1, if the boundary of the rounding interval is excluded
     */
    private int lout; // left (closer to 0) boundary
    private int rout; // right (farther from 0) boundary

    /*
    Room for H digits, 3 exponent digits, 2 '-', 1 '.', 1 'E' = H + 7
    or for "-0.00" + H digits = H + 5
     */
    private final char[] buf = new char[H + 7];
    private int index; // index of rightmost valid character

    private DoubleToDecimal() {
    }

    /**
     * Returns a string rendering of the {@code double} argument.
     *
     * <p>The characters of the result are all drawn from the ASCII set.
     * <ul>
     *     <li> Any NaN, whether quiet or signaling, is rendered symbolically
     *     as "NaN", regardless of the sign bit.
     *     <li> The infinities +&#x221e; and -&#x221e; are rendered as
     *     "Infinity" and "-Infinity", respectively.
     *     <li> The zeroes +0.0 and -0.0 are rendered as "0.0" and "-0.0",
     *     respectively.
     *     <li> Otherwise {@code v} is finite and non-zero.
     *     It is rendered in two stages:
     *     <ul>
     *         <li> Selection of a decimal: A well-specified non-zero decimal
     *         <i>d</i> is selected to represent {@code v}.
     *         <li> Formatting as a string: The decimal <i>d</i> is formatted
     *         as a string, either in plain or in computerized scientific
     *         notation, depending on its value.
     *     </ul>
     * </ul>
     *
     * <p>A decimal <i>d</i> is said to have length <i>i</i> if it has
     * the form <i>d</i> = <i>c</i> &#xb7; 10<sup><i>q</i></sup>
     * for some integers <i>c</i> and <i>q</i> and if the decimal expansion of
     * <i>c</i> consists of <i>i</i> digits. Note that if <i>d</i> has some
     * length, then it has any other greater length as well: grow <i>c</i> by
     * appending trailing zeroes and decrease <i>q</i> accordingly.
     *
     * <p>Abstractly, the unique decimal <i>d</i> to represent {@code v}
     * is selected as follows:
     * <ul>
     *     <li>First, all decimals that round to {@code v} according to the
     *     usual round-to-closest rule of IEEE 754 floating-point arithmetic
     *     are tentatively selected, while the other are discarded.
     *     There is never the need to go beyond a length of 17.
     *     <li>Among these, only the ones that have the shortest possible
     *     length  not less than 2 are selected and the other are discarded.
     *     <li>Finally, among these, only the one closest to {@code v} is
     *     definitely selected: or if two are equally close to {@code v}, the
     *     one whose least significant digit is even is definitely selected.
     * </ul>
     *
     * <p>The selected decimal <i>d</i> is then formatted as a string.
     * If <i>d</i> &#x3c; 0, the first character of the string is the sign
     * '{@code -}'. Then consider the absolute value and let
     * |<i>d</i>| = <i>m</i> &#xb7; 10<sup><i>k</i></sup>, for some unique
     * real <i>m</i> meeting 1 &#x2264; <i>m</i> &lt; 10 and integer <i>k</i>.
     * Further, let the decimal expansion of <i>m</i> be
     * <i>m</i><sub>1</sub>.<i>m</i><sub>2</sub>&#x2026;<!--
     * --><i>m</i><sub><i>i</i></sub>,
     * with <i>i</i> &#x2265; 1 and <i>m</i><sub><i>i</i></sub> &#x2260; 0.
     * <ul>
     *     <li>Case -3 &#x2264; k &#x3c; 0: |<i>d</i>| is formatted as
     *     0.0&#x2026;0<i>m</i><sub>1</sub>&#x2026;<!--
     *     --><i>m</i><sub><i>i</i></sub>,
     *     where there are exactly -<i>k</i> leading zeroes before
     *     <i>m</i><sub>1</sub>, including the zero before the decimal point.
     *     For example, {@code "0.01234"}.
     *     <li>Case 0 &#x2264; <i>k</i> &#x3c; 7:
     *     <ul>
     *         <li>Subcase <i>i</i> &#x3c; <i>k</i> + 2:
     *         |<i>d</i>| is formatted as
     *         <i>m</i><sub>1</sub>&#x2026;<!--
     *         --><i>m</i><sub><i>i</i></sub>0&#x2026;0.0,
     *         where there are exactly <i>k</i> + 2 - <i>i</i> trailing zeroes
     *         after <i>m</i><sub><i>i</i></sub>, including the zero after
     *         the decimal point.
     *         For example, {@code "1200.0"}.
     *         <li>Subcase <i>i</i> &#x2265; <i>k</i> + 2:
     *         |<i>d</i>| is formatted as
     *         <i>m</i><sub>1</sub>&#x2026;<i>m</i><sub><i>k</i>+1</sub>.<!--
     *         --><i>m</i><sub><i>k</i>+2</sub>&#x2026;<!--
     *         --><i>m</i><sub><i>i</i></sub>.
     *         For example, {@code "1234.567"}.
     *     </ul>
     *     <li>Case <i>k</i> &#x3c; -3 or <i>k</i> &#x2265; 7:
     *     computerized scientific notation is used to format |<i>d</i>|,
     *     by combining <i>m</i> and <i>k</i> separated by the exponent
     *     indicator '{@code E}'.
     *     <ul>
     *         <li>Subcase <i>i</i> = 1:
     *         |<i>d</i>| is formatted as
     *         <i>m</i><sub>1</sub>.0E<i>k</i>.
     *         For example, {@code "2.0E23"}.
     *         <li>Subcase <i>i</i> > 1:
     *         |<i>d</i>| is formatted as
     *         <i>m</i><sub>1</sub>.<i>m</i><sub>2</sub>&#x2026;<!--
     *         --><i>m</i><sub><i>i</i></sub>E<i>k</i>.
     *         For example, {@code "1.2345E-67"}.
     *     </ul>
     *     The exponent <i>k</i> is formatted as in
     *     {@link Integer#toString(int)}.
     *  </ul>
     *
     * @param  v the {@code double} to be rendered.
     * @return a string rendering of the argument.
     */
    public static String toString(double v) {
        return threadLocalInstance().toDecimal(v);
    }

    private static DoubleToDecimal threadLocalInstance() {
        return threadLocal.get();
    }

    private String toDecimal(double v) {
        // Get rid of NaNs, infinities and zeroes right at the beginning
        if (v != v) {
            return "NaN";
        }
        if (v == POSITIVE_INFINITY) {
            return "Infinity";
        }
        if (v == NEGATIVE_INFINITY) {
            return "-Infinity";
        }
        long bits = doubleToRawLongBits(v);
        if (bits == 0x0000_0000_0000_0000L) {
            return "0.0";
        }
        if (bits == 0x8000_0000_0000_0000L) {
            return "-0.0";
        }
        index = -1;
        if (bits < 0) {
            append('-');
            v = -v;
        }
        e = ord10(v);
        /*
        When v is an integer less than 10^9, a common case in practice,
        use a customized faster method.
         */
        long l = (long) v;
        if (l == v & l < 1_000_000_000L) {
            return integer(l);
        }
        q = q(bits);
        c = c(bits);
        lout = rout = (int) (c) & 0x1;
        /*
        The reduced() method assumes v is normal, i.e., has full precision P,
        and that powers of 2 have unequally distant predecessor and successor.
        MIN_NORMAL is normal and a power of 2 but its predecessor and
        its successor are equally close to it, so is excluded from reduced().
        Note that reduced() might failover to full().
         */
        if (v > MIN_NORMAL) {
            return reduced();
        }
        return full();
    }

    private String integer(long l) {
        return toChars(l * pow10[H - 8 - e], e);
    }

    private String full() {
        long cb;
        int qb;
        long cbr;
        if (c != C_MIN | q == Q_MIN) {
            cb = c << 1;
            qb = q - 1;
            cbr = cb + 1;
        } else {
            cb = c << 2;
            qb = q - 2;
            cbr = cb + 2;
        }
        if (e <= H) {
            if (e - qb <= H) {
                return fullCaseM(qb, cb, cbr);
            }
            if (H - 3 <= e) {
                return fullSubcaseS(qb, cb, cbr);
            }
            int p = q > Q_MIN || c > C_MIN ?
                    P :
                    Long.SIZE - numberOfLeadingZeros(c - 1);
            int i = max(ord10pow2(p - 1) - 1, 2);
            return fullCaseXS(qb, cb, cbr, i);
        }
        if (qb - e <= 8 - H) {
            return fullSubcaseL(qb, cb, cbr);
        }
        return fullCaseXL(qb, cb, cbr);
    }

    private String fullCaseXS(int qb, long cb, long cb_r, int i) {
        Natural m = pow5(H - e);
        Natural vb = m.multiply(cb);
        Natural vbl = vb.subtract(m);
        Natural vbr = m.multiply(cb_r);
        int p = e - H - qb;
        long sbH = vb.shiftRight(p);
        for (int g = H - i; g >= 0; --g) {
            long di = pow10[g];
            long sbi = sbH - sbH % di;
            Natural ubi = valueOfShiftLeft(sbi, p);
            Natural wbi = valueOfShiftLeft(sbi + di, p);
            boolean uin = vbl.compareTo(ubi) + lout <= 0;
            boolean win = wbi.compareTo(vbr) + rout <= 0;
            if (uin & !win) {
                return toChars(sbi,  e);
            }
            if (!uin & win) {
                return toChars(sbi + di, e);
            }
            if (uin) {
                int cmp = vb.closerTo(ubi, wbi);
                if (cmp < 0 || cmp == 0 && (sbi / di & 0x1) == 0) {
                    return toChars(sbi, e);
                }
                return toChars(sbi + di,  e);
            }
        }
        throw new AssertionError("unreachable code");
    }

    private String fullSubcaseS(int qb, long cb, long cb_r) {
        long m = pow5[H - e];
        long vb = cb * m;
        long vbl = vb - m;
        long vbr = cb_r * m;
        int p = e - H - qb;
        long sbH = vb >> p;
        for (int g = H - G; g >= 0; --g) {
            long di = pow10(g);
            long sbi = sbH - sbH % di;
            long ubi = sbi << p;
            long wbi = sbi + di << p;
            boolean uin = vbl + lout <= ubi;
            boolean win = wbi + rout <= vbr;
            if (uin & !win) {
                return toChars(sbi,  e);
            }
            if (!uin & win) {
                return toChars(sbi + di, e);
            }
            if (uin) {
                int cmp = (int) (2 * vb - ubi - wbi);
                if (cmp < 0 || cmp == 0 && (sbi / di & 0x1) == 0) {
                    return toChars(sbi, e);
                }
                return toChars(sbi + di,  e);
            }
        }
        throw new AssertionError("unreachable code");
    }

    private String fullCaseM(int qb, long cb, long cb_r) {
        long m = pow5[H - e] << H - e + qb;
        long vb = cb * m;
        long vbl = vb - m;
        long vbr = cb_r * m;
        for (int g = H - G; g > 0; --g) {
            long di = pow10(g);
            long sbi = vb - vb % di;
            long tbi = sbi + di;
            boolean uin = vbl + lout <= sbi;
            boolean win = tbi + rout <= vbr;
            if (uin & !win) {
                return toChars(sbi,  e);
            }
            if (!uin & win) {
                return toChars(tbi, e);
            }
            if (uin) {
                int cmp = (int) (2 * vb - sbi - tbi);
                if (cmp < 0 || cmp == 0 && (vb / di & 0x1) == 0) {
                    return toChars(sbi, e);
                }
                return toChars(tbi,  e);
            }
        }
        /*
        The loop didn't produce a shorter result.
        The full sb_H = s_H = vb is needed. This is done outside the loop,
        as there's no need to check tb_H = t_H as well.
         */
        return toChars(vb, e);
    }

    private String fullSubcaseL(int qb, long cb, long cb_r) {
        int p = H - e + qb;
        long vb = cb << p;
        long vbl = cb - 1 << p;
        long vbr = cb_r << p;
        long m = pow5[e - H];
        long sbH = vb / m;
        for (int g = H - G; g >= 0; --g) {
            long di = pow10(g);
            long sbi = sbH - sbH % di;
            long ubi = sbi * m;
            long wbi = ubi + (pow5[e - H + g] << g);
            boolean uin = vbl + lout <= ubi;
            boolean win = wbi + rout <= vbr;
            if (uin & !win) {
                return toChars(sbi,  e);
            }
            if (!uin & win) {
                return toChars(sbi + di, e);
            }
            if (uin) {
                int cmp = (int) (2 * vb - ubi - wbi);
                if (cmp < 0 || cmp == 0 && (sbi / di & 0x1) == 0) {
                    return toChars(sbi, e);
                }
                return toChars(sbi + di,  e);
            }
        }
        throw new AssertionError("unreachable code");
    }

    private String fullCaseXL(int qb, long cb, long cb_r) {
        int p = H - e + qb;
        Natural vb = valueOfShiftLeft(cb, p);
        Natural vbl = valueOfShiftLeft(cb - 1, p);
        Natural vbr = valueOfShiftLeft(cb_r, p);
        Natural m = pow5(e - H);
        long sbH = vb.divide(m);
        for (int g = H - G; g >= 0; --g) {
            long di = pow10(g);
            long sbi = sbH - sbH % di;
            Natural ubi = m.multiply(sbi);
            Natural wbi = ubi.addShiftLeft(pow5(e - H + g), g);
            boolean uin = vbl.compareTo(ubi) + lout <= 0;
            boolean win = wbi.compareTo(vbr) + rout <= 0;
            if (uin & !win) {
                return toChars(sbi,  e);
            }
            if (!uin & win) {
                return toChars(sbi + di, e);
            }
            if (uin) {
                int cmp = vb.closerTo(ubi, wbi);
                if (cmp < 0 || cmp == 0 && (sbi / di & 0x1) == 0) {
                    return toChars(sbi, e);
                }
                return toChars(sbi + di,  e);
            }
        }
        throw new AssertionError("unreachable code");
    }

    /*
    A faster version that succeeds in about 99.5% of the cases.
    It must be invoked only on values greater than MIN_NORMAL.
    When it fails, it resorts to the full() version.
     */
    private String reduced() {
        int p = -P - q - pow10r(H - e);
        long t = floorPow10d(H - e);
        long cb = c << D;
        long vh = multiplyHighUnsigned(cb, t);
        long cbl = cb - (1L << D - (c != Double.C_MIN ? 1 : 2));
        long vhl = multiplyHighUnsigned(cbl, t);
        long cbr = cb + (1L << D - 1);
        long vhr = multiplyHighUnsigned(cbr, t);
        long shH = vh >>> p;
        long vhu = vh + 2;
        for (int g = H - G; g >= 0; --g) {
            long di = pow10(g);
            long uhi = shH - shH % di << p;
            long whi = uhi + (di << p);
            boolean uin = uhi - vhl >= 2;
            boolean wout = whi - vhr >= 2;
            if (uin & wout) {
                return toChars(uhi >>> p, e);
            }
            boolean uout = uhi - vhl - lout < 0;
            boolean win = whi - vhr + rout <= 0;
            if (uout & win) {
                return toChars(whi >>> p, e);
            }
            if (uin & win) {
                if (vhu - uhi <= whi - vhu) {
                    return toChars(uhi >>> p, e);
                }
                if (whi - vh < vh - uhi) {
                    return toChars(whi >>> p, e);
                }
                return full();
            }
            if (!uout & !uin | !wout) {
                return full();
            }
        }
        throw new AssertionError("unreachable code");
    }

    /*
    Limited usage, but does magic during JIT compilation. Note that
    0 <= g <= 2 = H - G when invoked, so the default branch is never taken.
     */
    private long pow10(int g) {
        switch (g) {
            case 0:
                return 1;
            case 1:
                return 10;
            case 2:
                return 100;
            default:
                return 0;
        }
    }

    /*
    f comes from integer(), from full() or from reduced().
    In the former case
        10^8 <= f < 10^9
    and the method formats the number (f * 10^8) * 10^(e-H).
    Otherwise
        10^(H-1) <= f <= 10^H
    and the method formats the number f * 10^(e-H)

    Division is avoided, where possible, by replacing it with multiplications
    and shifts. This makes a noticeable difference in performance, in
    particular when generating the digits of the exponent.
    For more in-depth readings, see for example
    Moeller N, Granlund T, "Improved division by invariant integers"
    ridiculous_fish, "Labor of Division (Episode III): Faster Unsigned
        Division by Constants"

    Also, once the quotient is known, the remainder is computed indirectly.
     */
    private String toChars(long f, int e) {
        int h; // the 1 most significant
        int m; // the next 8 most significant digits
        int l; // the 8 least significant digits
        if (f != MAX_SIGNIFICAND) {
            long hm;
            if (f < 1_000_000_000L) {
                hm = f;
                l = 0;
            } else {
                hm = f / 100_000_000L;
                l = (int) (f - 100_000_000L * hm);
            }
            h = (int) (hm * 1_441_151_881L >>> 57); // h = hm / 100_000_000
            m = (int) (hm - 100_000_000 * h);
        } else {
            // This might happen for doubles close or equal to powers of 10
            h = 1;
            m = l = 0;
            e += 1;
        }
        /*
        The left-to-right digits generation in toChars_* is inspired by
        Bouvier C, Zimmermann P, "Division-Free Binary-to-Decimal Conversion"
         */
        if (0 < e && e <= 7) {
            return toChars_1(h, m, l, e);
        }
        if (-3 < e && e <= 0) {
            return toChars_2(h, m, l, e);
        }
        return toChars_3(h, m, l, e);
    }

    private String toChars_1(int h, int m, int l, int e) {
        // 0 < e <= 7
        appendDigit(h);
        int y = (int) (((long) (m + 1) << LTR) / 100_000_000L) - 1;
        int t;
        int i = 1;
        for (; i < e; ++i) {
            t = 10 * y;
            appendDigit(t >>> LTR);
            y = t & MASK_LTR;
        }
        append('.');
        for (; i <= 8; ++i) {
            t = 10 * y;
            appendDigit(t >>> LTR);
            y = t & MASK_LTR;
        }
        lowDigits(l);
        return charsToString();
    }

    private String toChars_2(int h, int m, int l, int e) {
      // -3 < e <= 0
      appendDigit(0);
      append('.');
      for (; e < 0; ++e) {
          appendDigit(0);
      }
      appendDigit(h);
      append8Digits(m);
      lowDigits(l);
      return charsToString();
    }

    private String toChars_3(int h, int m, int l, int e) {
        // computerized scientific notation
        appendDigit(h);
        append('.');
        append8Digits(m);
        lowDigits(l);
        exponent(e - 1);
        return charsToString();
    }

    private void lowDigits(int l) {
        if (l != 0) {
            append8Digits(l);
        }
        removeTrailingZeroes();
    }

    private void append8Digits(int v) {
        int y = (int) (((long) (v + 1) << LTR) / 100_000_000L) - 1;
        for (int i = 0; i < 8; ++i) {
            int t = 10 * y;
            appendDigit(t >>> LTR);
            y = t & MASK_LTR;
        }
    }

    private void removeTrailingZeroes() {
        while (buf[index] == '0') {
            --index;
        }
        if (buf[index] == '.') {
            ++index;
        }
    }

    private void exponent(int e) {
        append('E');
        if (e < 0) {
            append('-');
            e = -e;
        }
        if (e < 10) {
            appendDigit(e);
        } else if (e < 100) {
            int d = e * 205 >>> 11; // d = e / 10
            appendDigit(d);
            appendDigit(e - 10 * d);
        } else {
            int d = e * 1_311 >>> 17; // d = e / 100
            appendDigit(d);
            e -= 100 * d;
            d = e * 205 >>> 11; // d = e / 10
            appendDigit(d);
            appendDigit(e - 10 * d);
        }
    }

    private void append(int c) {
        buf[++index] = (char) c;
    }

    private void appendDigit(int d) {
        buf[++index] = (char) ('0' + d);
    }

    private String charsToString() {
        return new String(buf, 0, index + 1);
    }

}
