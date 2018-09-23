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

import static java.lang.Float.POSITIVE_INFINITY;
import static java.lang.Float.floatToRawIntBits;
import static java.lang.Long.numberOfLeadingZeros;
import static java.lang.Math.multiplyHigh;
import static math.MathUtils.*;

/**
 * This class exposes a method to render a {@code float} as a string.

 * @author Raffaello Giulietti
 */
final public class FloatToDecimal {

    // Precision of normal values in bits.
    private static final int P = 24;

    // Length in bits of the exponent field.
    private static final int W = (Float.SIZE - 1) - (P - 1);

    // Minimum value of the exponent.
    private static final int Q_MIN = (-1 << W - 1) - P + 3;

    // Minimum value of the coefficient of a normal value.
    private static final int C_MIN = 1 << P - 1;

    // Mask to extract the IEEE 754-2008 biased exponent.
    private static final int BQ_MASK = (1 << W) - 1;

    // Mask to extract the IEEE 754-2008 fraction bits.
    private static final int T_MASK = (1 << P - 1) - 1;

    // H = min {n integer | 10^(n-1) > 2^P}
    private static final int H = 9;

    // used in the left-to-right extraction of the digits
    private static final int LTR = 28;
    private static final int MASK_LTR = (1 << LTR) - 1;

    private static final long MASK_63 = (1L << Long.SIZE - 1) - 1;

    // for thread-safety, each thread gets its own instance of this class
    private static final ThreadLocal<FloatToDecimal> threadLocal =
            ThreadLocal.withInitial(FloatToDecimal::new);

    /*
    Room for the longer of the forms
        -ddddd.dddd         H + 2 characters
        -0.00ddddddddd      H + 5 characters
        -d.ddddddddE-ee     H + 6 characters
    where there are H digits d
     */
    private final char[] buf = new char[H + 6];

    // index of rightmost valid character
    private int index;

    private FloatToDecimal() {
    }

    /**
     * Returns a string rendering of the {@code float} argument.
     *
     * <p>The characters of the result are all drawn from the ASCII set.
     * <ul>
     *     <li> Any NaN, whether quiet or signaling, is rendered symbolically
     *     as {@code "NaN"}, regardless of the sign bit.
     *     <li> The infinities +&infin; and -&infin; are rendered as
     *     {@code "Infinity"} and {@code "-Infinity"}, respectively.
     *     <li> The positive and negative zeroes are rendered as
     *     {@code "0.0"} and {@code "-0.0"}, respectively.
     *     <li> Otherwise {@code v} is finite and non-zero.
     *     It is rendered in two stages:
     *     <ul>
     *         <li> <em>Selection of a decimal</em>: A well-specified non-zero
     *         decimal <i>d</i> of finite length is selected to represent
     *         {@code v}.
     *         <li> <em>Formatting as a string</em>: The decimal <i>d</i> is
     *         formatted as a string, either in plain or in computerized
     *         scientific notation, depending on its value.
     *     </ul>
     * </ul>
     *
     * <p>A number <i>d</i> is a decimal of finite length if and only if it has
     * the form <i>d</i> = <i>c</i>&middot;10<sup><i>q</i></sup> for some
     * integers <i>c</i> and <i>q</i>. It has a length <i>n</i> if
     * 10<sup><i>n</i>-1</sup> &le; |<i>c</i>| &lt; 10<sup><i>n</i></sup>.
     * <p>The selected decimal <i>d</i> has all the following properties:
     * <ul>
     *     <li> It rounds to {@code v} according to the usual round-to-closest
     *     rule of IEEE 754 floating-point arithmetic.
     *     <li> It has a shortest length <i>n</i> &ge; 2.
     *     <li> It is the decimal closest to {@code v} among those meeting the
     *     previous properties.
     * </ul>
     * More formally, let <i>d'</i> = <i>c'</i>&middot;10<sup><i>q'</i></sup>
     * &ne; <i>d</i> be another decimal that also rounds to {@code v} according
     * to IEEE 754 and with a length <i>n'</i>. Then:
     * <ul>
     *     <li> <i>n'</i> = 1 (<i>d'</i> is too short) or
     *     <li> <i>n'</i> &gt; <i>n</i> (<i>d'</i> is too long) or
     *     <li> <i>n'</i> = <i>n</i> and
     *     <ul>
     *         <li> |<i>d</i> - {@code v}| &lt; |<i>d'</i> - {@code v}|
     *         (<i>d'</i> is farther)
     *         <li> |<i>d</i> - {@code v}| = |<i>d'</i> - {@code v}| and
     *         <i>c</i> is even while <i>c'</i> is odd (tie-breaking rule when
     *         <i>d</i> and <i>d'</i> are equally close to {@code v})
     *     </ul>
     * </ul>
     *
     * <p>The selected decimal <i>d</i> is then formatted as a string.
     * If <i>d</i> &lt; 0, the first character of the string is the sign
     * '{@code -}'. Let |<i>d</i>| = <i>m</i>&middot;10<sup><i>k</i></sup>,
     * for the unique pair of integer <i>k</i> and real <i>m</i> meeting
     * 1 &le; <i>m</i> &lt; 10. Also, let the decimal expansion of <i>m</i> be
     * <i>m</i><sub>1</sub>&thinsp;.&thinsp;<i>m</i><sub>2</sub>&thinsp;<!--
     * -->&hellip;&thinsp;<i>m</i><sub><i>i</i></sub>,
     * with <i>i</i> &ge; 1 and <i>m</i><sub><i>i</i></sub> &ne; 0.
     * <ul>
     *     <li>Case -3 &le; k &lt; 0: |<i>d</i>| is formatted as
     *     0&thinsp;.&thinsp;0&hellip;0<i>m</i><sub>1</sub>&hellip;<!--
     *     --><i>m</i><sub><i>i</i></sub>,
     *     where there are exactly -<i>k</i> leading zeroes before
     *     <i>m</i><sub>1</sub>, including the zero to the left of the
     *     decimal point; for example, {@code "0.01234"}.
     *     <li>Case 0 &le; <i>k</i> &lt; 7:
     *     <ul>
     *         <li>Subcase <i>i</i> &lt; <i>k</i> + 2:
     *         |<i>d</i>| is formatted as
     *         <i>m</i><sub>1</sub>&hellip;<!--
     *         --><i>m</i><sub><i>i</i></sub>0&hellip;0&thinsp;.&thinsp;0,
     *         where there are exactly <i>k</i> + 2 - <i>i</i> trailing zeroes
     *         after <i>m</i><sub><i>i</i></sub>, including the zero to the
     *         right of the decimal point; for example, {@code "1200.0"}.
     *         <li>Subcase <i>i</i> &ge; <i>k</i> + 2:
     *         |<i>d</i>| is formatted as <i>m</i><sub>1</sub>&hellip;<!--
     *         --><i>m</i><sub><i>k</i>+1</sub>&thinsp;.&thinsp;<!--
     *         --><i>m</i><sub><i>k</i>+2</sub>&hellip;<!--
     *         --><i>m</i><sub><i>i</i></sub>; for example, {@code "1234.32"}.
     *     </ul>
     *     <li>Case <i>k</i> &lt; -3 or <i>k</i> &ge; 7:
     *     computerized scientific notation is used to format |<i>d</i>|,
     *     by combining <i>m</i> and <i>k</i> separated by the exponent
     *     indicator '{@code E}'. The exponent <i>k</i> is formatted as in
     *     {@link Integer#toString(int)}.
     *     <ul>
     *         <li>Subcase <i>i</i> = 1: |<i>d</i>| is formatted as
     *         <i>m</i><sub>1</sub>&thinsp;.&thinsp;0E<i>k</i>;
     *         for example, {@code "2.0E23"}.
     *         <li>Subcase <i>i</i> &gt; 1: |<i>d</i>| is formatted as
     *         <i>m</i><sub>1</sub>&thinsp;.&thinsp;<i>m</i><sub>2</sub><!--
     *         -->&hellip;<i>m</i><sub><i>i</i></sub>E<i>k</i>;
     *         for example, {@code "1.234E-32"}.
     *     </ul>
     *  </ul>
     *
     * @param  v the {@code float} to be rendered.
     * @return a string rendering of the argument.
     */
    public static String toString(float v) {
        return threadLocalInstance().toDecimal(v);
    }

    private static FloatToDecimal threadLocalInstance() {
        return threadLocal.get();
    }

    private String toDecimal(float v) {
        int bits = floatToRawIntBits(v);
        int bq = (bits >>> P - 1) & BQ_MASK;
        if (bq < BQ_MASK) {
            index = -1;
            if (bits < 0) {
                append('-');
            }
            if (bq > 0) {
                return toDecimal(Q_MIN - 1 + bq, C_MIN | bits & T_MASK);
            }
            if (bits == 0x0000_0000) {
                return "0.0";
            }
            if (bits == 0x8000_0000) {
                return "-0.0";
            }
            return toDecimal(Q_MIN, bits & T_MASK);
        }
        if (v != v) {
            return "NaN";
        }
        if (v == POSITIVE_INFINITY) {
            return "Infinity";
        }
        return "-Infinity";
    }

    // Let v = c * 2^q be the absolute value of the original float. Renders v.
    private String toDecimal(int q, int c) {
        /*
        out = 0, if the boundaries of the rounding interval are included
        out = 1, if they are excluded
        d = 1 for even, d = 2 for uneven spacing around v.
        v = cb * 2^qb
        predecessor(v) = cbl * 2^qb
        successor(v) = cbr * 2^qb
         */
        int out = c & 0x1;

        long cb;
        long cbr;
        long cbl;
        int k;
        int ord2alpha;
        if (c != C_MIN | q == Q_MIN) {
            cb = c << 1;
            cbr = cb + 1;
            k = flog10pow2(q);
            ord2alpha = q + flog2pow10(-k) + 1;
        } else {
            cb = c << 2;
            cbr = cb + 2;
            k = flog10threeQuartersPow2(q);
            ord2alpha = q + flog2pow10(-k);
        }
        cbl = cb - 1;
        long mask = (1L << 63 - ord2alpha) - 1;
        long threshold = 1L << 62 - ord2alpha;

        // pow5 = pow51*2^63 + pow50
        long pow51 = ceilPow5dHigh(-k);
        long pow50 = ceilPow5dLow(-k);

        // p = p2*2^126 + p1*2^63 + p0 and p = pow5 * cb
        long x0 = pow50 * cb;
        long x1 = multiplyHigh(pow50, cb);
        long y0 = pow51 * cb;
        long y1 = multiplyHigh(pow51, cb);
        long z = (x1 << 1 | x0 >>> 63) + (y0 & MASK_63);
        long p0 = x0 & MASK_63;
        long p1 = z & MASK_63;
        long p2 = (y1 << 1 | y0 >>> 63) + (z >>> 63);
        long vn = p2 << 1 + ord2alpha | p1 >>> 62 - ord2alpha;
        if ((p1 & mask) != 0 || p0 >= threshold) {
            vn |= 1;
        }

        // Similarly as above, with p = pow5 * cbl
        x0 = pow50 * cbl;
        x1 = multiplyHigh(pow50, cbl);
        y0 = pow51 * cbl;
        y1 = multiplyHigh(pow51, cbl);
        z = (x1 << 1 | x0 >>> 63) + (y0 & MASK_63);
        p0 = x0 & MASK_63;
        p1 = z & MASK_63;
        p2 = (y1 << 1 | y0 >>> 63) + (z >>> 63);
        long vnl = p2 << ord2alpha | p1 >>> 63 - ord2alpha;
        if ((p1 & mask) != 0 || p0 >= threshold) {
            vnl |= 1;
        }

        // Similarly as above, with p = pow5 * cbr
        x0 = pow50 * cbr;
        x1 = multiplyHigh(pow50, cbr);
        y0 = pow51 * cbr;
        y1 = multiplyHigh(pow51, cbr);
        z = (x1 << 1 | x0 >>> 63) + (y0 & MASK_63);
        p0 = x0 & MASK_63;
        p1 = z & MASK_63;
        p2 = (y1 << 1 | y0 >>> 63) + (z >>> 63);
        long vnr = p2 << ord2alpha | p1 >>> 63 - ord2alpha;
        if ((p1 & mask) != 0 || p0 >= threshold) {
            vnr |= 1;
        }

        long s = vn >> 2;
        if (s >= 100) {
            long s10 = s - s % 10;
            long t10 = s10 + 10;
            boolean uin10 = vnl + out <= s10 << 1;
            boolean win10 = (t10 << 1) + out <= vnr;
            if (uin10 | win10) {
                if (!win10) {
                    return toChars(s10, k);
                }
                if (!uin10) {
                    return toChars(t10, k);
                }
            }
        } else if (s < 10) {
            /*
            Special cases that need to be made artificially longer to meet
            the specification
             */
            switch ((int) s) {
                case 1: return toChars(14, -46); // 1.4 * 10^-45
                case 2: return toChars(28, -46); // 2.8 * 10^-45
                case 4: return toChars(42, -46); // 4.2 * 10^-45
                case 5: return toChars(56, -46); // 5.6 * 10^-45
                case 7: return toChars(70, -46); // 7.0 * 10^-45
                case 8: return toChars(84, -46); // 8.4 * 10^-45
                case 9: return toChars(98, -46); // 9.8 * 10^-45
            }
        }
        long t = s + 1;
        boolean uin = vnl + out <= s << 1;
        boolean win = (t << 1) + out <= vnr;
//        assert uin || win; // because 10^r <= 2^q
        if (!win) {
            return toChars(s, k);
        }
        if (!uin) {
            return toChars(t, k);
        }
        long cmp = vn - (s + t << 1);
        if (cmp < 0) {
            return toChars(s, k);
        }
        if (cmp > 0) {
            return toChars(t, k);
        }
        if ((s & 1) == 0) {
            return toChars(s, k);
        }
        return toChars(t, k);
    }

    /*
    The method formats the number f * 10^e

    Division is avoided altogether by replacing it with multiplications
    and shifts. This has a noticeable impact on performance.
    For more in-depth readings, see for example
    * Moeller & Granlund, "Improved division by invariant integers"
    * ridiculous_fish, "Labor of Division (Episode III): Faster Unsigned
        Division by Constants"

    Also, once the quotient is known, the remainder is computed indirectly.
     */
    private String toChars(long f, int e) {
        // Normalize f to lie in the f-independent interval [10^(H-1), 10^H)
        int len10 = flog10pow2(Long.SIZE - numberOfLeadingZeros(f));
        if (f >= pow10[len10]) {
            len10 += 1;
        }
        // 10^(len10-1) <= f < 10^len10
        f *= pow10[H - len10];
        e += len10;

        /*
        Split the H = 9 digits of f into:
            h = the most significant digit of f
            l = the last 8, least significant digits of f

        Pictorially, the selected decimal to format as String is
            0.hllllllll * 10^e
        Depending on the value of e, plain or computerized scientific notation
        is used.
         */
        int h = (int) (f * 1_441_151_881L >>> 57);
        int l = (int) (f - 100_000_000 * h);

        /*
        The left-to-right digits generation in toChars_* is inspired by
        * Bouvier & Zimmermann, "Division-Free Binary-to-Decimal Conversion"
         */
        if (0 < e && e <= 7) {
            return toChars_1(h, l, e);
        }
        if (-3 < e && e <= 0) {
            return toChars_2(h, l, e);
        }
        return toChars_3(h, l, e);
    }

    // 0 < e <= 7: plain format without leading zeroes.
    private String toChars_1(int h, int l, int e) {
        appendDigit(h);
        // y = (l + 1) * 2^LTR / 100_000_000 - 1;
        int y = (int) (multiplyHigh(
                (long) (l + 1) << LTR,
                48_357_032_784_585_167L) >>> 18) - 1;
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
        removeTrailingZeroes();
        return charsToString();
    }

    // -3 < e <= 0: plain format with leading zeroes.
    private String toChars_2(int h, int l, int e) {
        appendDigit(0);
        append('.');
        for (; e < 0; ++e) {
          appendDigit(0);
        }
        appendDigit(h);
        append8Digits(l);
        removeTrailingZeroes();
        return charsToString();
    }

    // -3 >= e | e > 7: computerized scientific notation
    private String toChars_3(int h, int l, int e) {
        appendDigit(h);
        append('.');
        append8Digits(l);
        removeTrailingZeroes();
        exponent(e - 1);
        return charsToString();
    }

    private void append8Digits(int v) {
        // y = (v + 1) * 2^LTR / 100_000_000 - 1;
        int y = (int) (multiplyHigh((long) (v + 1) << LTR,
                48_357_032_784_585_167L) >>> 18) - 1;
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
            return;
        }
        // d = e / 10
        int d = e * 205 >>> 11;
        appendDigit(d);
        appendDigit(e - 10 * d);
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
