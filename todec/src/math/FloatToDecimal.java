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
import static java.lang.Integer.numberOfLeadingZeros;
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

    // used in rop()
    private static final long MASK_31 = (1L << 31) - 1;

    // used in the left-to-right extraction of the digits
    private static final int LTR = 28;
    private static final int MASK_LTR = (1 << LTR) - 1;

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
    private final byte[] buf = new byte[H + 6];

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
     *         decimal <i>d</i><sub><code>v</code></sub> is selected
     *         to represent {@code v}.
     *         <li> <em>Formatting as a string</em>: The decimal
     *         <i>d</i><sub><code>v</code></sub> is formatted as a string,
     *         either in plain or in computerized scientific notation,
     *         depending on its value.
     *     </ul>
     * </ul>
     *
     * <p>The selected decimal <i>d</i><sub><code>v</code></sub> has
     * a length <i>n</i> if it can be written as
     * <i>d</i><sub><code>v</code></sub> = <i>d</i>&middot;10<sup><i>i</i></sup>
     * for some integers <i>i</i> and <i>d</i> meeting
     * 10<sup><i>n</i>-1</sup> &le; |<i>d</i>| &lt; 10<sup><i>n</i></sup>.
     * It has all the following properties:
     * <ul>
     *     <li> It rounds to {@code v} according to the usual round-to-closest
     *     rule of IEEE 754 floating-point arithmetic.
     *     <li> Among the decimals above, it has a length of 2 or more.
     *     <li> Among all such decimals, it is one of those with the shortest
     *     length.
     *     <li> Among the latter ones, it is the one closest to {@code v}. Or
     *     if there are two that are equally close to {@code v}, it is the one
     *     whose least significant digit is even.
     * </ul>
     * More formally, let <i>x</i> = <i>d'</i>&middot;10<sup><i>i'</i></sup>
     * &ne; <i>d</i><sub><code>v</code></sub> be any other decimal that rounds
     * to {@code v} according to IEEE 754 and of a length <i>n'</i>. Then:
     * <ul>
     *     <li> either <i>n'</i> = 1, thus <i>x</i> is too short;
     *     <li> or <i>n'</i> &gt; <i>n</i>, thus <i>x</i> is too long;
     *     <li> or <i>n'</i> = <i>n</i> and
     *     <ul>
     *         <li> either |<i>d</i><sub><code>v</code></sub> - {@code v}| &lt;
     *         |<i>x</i> - {@code v}|: thus <i>x</i> is farther from {@code v};
     *         <li> or |<i>d</i><sub><code>v</code></sub> - {@code v}| =
     *         |<i>x</i> - {@code v}| and <i>d</i> is even while <i>d'</i> is
     *         odd
     *     </ul>
     * </ul>
     *
     * <p>The selected decimal <i>d</i><sub><code>v</code></sub> is then
     * formatted as a string. If <i>d</i><sub><code>v</code></sub> &lt; 0,
     * the first character of the string is the sign '{@code -}'.
     * Let |<i>d</i><sub><code>v</code></sub>| =
     * <i>f</i>&middot;10<sup><i>e</i></sup>, for the unique pair of
     * integer <i>e</i> and real <i>f</i> meeting 1 &le; <i>f</i> &lt; 10.
     * Also, let the decimal expansion of <i>f</i> be
     * <i>f</i><sub>1</sub>&thinsp;.&thinsp;<i>f</i><sub>2</sub>&thinsp;<!--
     * -->&hellip;&thinsp;<i>f</i><sub><i>m</i></sub>,
     * with <i>m</i> &ge; 1 and <i>f</i><sub><i>m</i></sub> &ne; 0.
     * <ul>
     *     <li>Case -3 &le; <i>e</i> &lt; 0:
     *     |<i>d</i><sub><code>v</code></sub>| is formatted as
     *     0&thinsp;.&thinsp;0&hellip;0<i>f</i><sub>1</sub>&hellip;<!--
     *     --><i>f</i><sub><i>m</i></sub>,
     *     where there are exactly -<i>e</i> leading zeroes before
     *     <i>f</i><sub>1</sub>, including the zero to the left of the
     *     decimal point; for example, {@code "0.01234"}.
     *     <li>Case 0 &le; <i>e</i> &lt; 7:
     *     <ul>
     *         <li>Subcase <i>i</i> &lt; <i>e</i> + 2:
     *         |<i>d</i><sub><code>v</code></sub>| is formatted as
     *         <i>f</i><sub>1</sub>&hellip;<!--
     *         --><i>f</i><sub><i>m</i></sub>0&hellip;0&thinsp;.&thinsp;0,
     *         where there are exactly <i>e</i> + 2 - <i>m</i> trailing zeroes
     *         after <i>f</i><sub><i>m</i></sub>, including the zero to the
     *         right of the decimal point; for example, {@code "1200.0"}.
     *         <li>Subcase <i>i</i> &ge; <i>e</i> + 2:
     *         |<i>d</i><sub><code>v</code></sub>| is formatted as
     *         <i>f</i><sub>1</sub>&hellip;<!--
     *         --><i>f</i><sub><i>e</i>+1</sub>&thinsp;.&thinsp;<!--
     *         --><i>f</i><sub><i>e</i>+2</sub>&hellip;<!--
     *         --><i>f</i><sub><i>m</i></sub>; for example, {@code "1234.32"}.
     *     </ul>
     *     <li>Case <i>e</i> &lt; -3 or <i>e</i> &ge; 7:
     *     computerized scientific notation is used to format
     *     |<i>d</i><sub><code>v</code></sub>|, by combining <i>f</i> and
     *     <i>e</i> separated by the exponent indicator '{@code E}'. The
     *     exponent <i>e</i> is formatted as in {@link Integer#toString(int)}.
     *     <ul>
     *         <li>Subcase <i>m</i> = 1:
     *         |<i>d</i><sub><code>v</code></sub>| is formatted as
     *         <i>f</i><sub>1</sub>&thinsp;.&thinsp;0E<i>e</i>;
     *         for example, {@code "2.0E23"}.
     *         <li>Subcase <i>m</i> &gt; 1:
     *         |<i>d</i><sub><code>v</code></sub>| is formatted as
     *         <i>f</i><sub>1</sub>&thinsp;.&thinsp;<i>f</i><sub>2</sub><!--
     *         -->&hellip;<i>f</i><sub><i>m</i></sub>E<i>e</i>;
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
        v = cb * 2^qb
         */
        int out = c & 0x1;

        long cb;
        long cbr;
        long cbl;
        int k;
        int shift;
        if (c != C_MIN | q == Q_MIN) {
            cb = c << 1;
            cbr = cb + 1;
            k = flog10pow2(q);
            shift = q + flog2pow10(-k) + 34;
        } else {
            cb = c << 2;
            cbr = cb + 2;
            k = flog10threeQuartersPow2(q);
            shift = q + flog2pow10(-k) + 33;
        }
        cbl = cb - 1;

        long g = floorPow10p1dHigh(-k) + 1;
        int vn = rop(g, cb << shift);
        int vnl = rop(g, cbl << shift);
        int vnr = rop(g, cbr << shift);

        int s = vn >> 2;
        if (s >= 100) {
            int s10 = s - s % 10;
            int t10 = s10 + 10;
            boolean uin10 = vnl + out <= s10 << 2;
            boolean win10 = (t10 << 2) + out <= vnr;
            if (uin10 != win10) {
                return toChars(uin10 ? s10 : t10, k);
            }
        } else if (s < 10) {
            /*
            Special cases that need to be made artificially longer to meet
            the specification
             */
            switch (s) {
                case 1: return toChars(14, -46); // 1.4 * 10^-45
                case 2: return toChars(28, -46); // 2.8 * 10^-45
                case 4: return toChars(42, -46); // 4.2 * 10^-45
                case 5: return toChars(56, -46); // 5.6 * 10^-45
                case 7: return toChars(70, -46); // 7.0 * 10^-45
                case 8: return toChars(84, -46); // 8.4 * 10^-45
                case 9: return toChars(98, -46); // 9.8 * 10^-45
            }
        }
        int t = s + 1;
        boolean uin = vnl + out <= s << 2;
        boolean win = (t << 2) + out <= vnr;
        if (uin != win) {
            /*
            Exactly one of s 10^k or t 10^k lies in Rv.
             */
            return toChars(uin ? s : t, k);
        }
        /*
        Both s 10^k and t 10^k lie in Rv: determine the one closest to v.
         */
        int cmp = vn - (s + t << 1);
        return toChars(cmp < 0 || cmp == 0 && (s & 0x1) == 0 ? s : t, k);
    }

    private static int rop(long g, long cp) {
        long x1 = multiplyHigh(g, cp);
        long vbp = x1 >> 31;
        return (int) (vbp | (x1 & MASK_31) + MASK_31 >>> 31);
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
    private String toChars(int f, int e) {
        // Normalize f to lie in the f-independent interval [10^(H-1), 10^H)
        int len10 = flog10pow2(Integer.SIZE - numberOfLeadingZeros(f));
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
        int l = f - 100_000_000 * h;

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
        buf[++index] = (byte) c;
    }

    private void appendDigit(int d) {
        buf[++index] = (byte) ('0' + d);
    }

    private String charsToString() {
        return new String(buf, 0, 0, index + 1);
    }

}
