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
import static java.lang.Long.numberOfLeadingZeros;
import static math.MathUtils.*;
import static java.lang.Math.multiplyHigh;

/**
 * This class exposes a method to render a {@code double} as a string.

 * @author Raffaello Giulietti
 */
final public class DoubleToDecimal {
    /*
    According to IEEE 754-2008, define
        P: the precision in bits
        SIZE: the total size in bits
    The quantities here and the following Java constants are then derived.
        Q_MAX = 2^(W-1) - P,    maximum value of the exponent
        C_MAX = 2^P - 1,    maximum value of the significand
     */

    // Precision in bits.
    private static final int P = 53;

    // Width in bits of the biased exponent.
    private static final int W = (Double.SIZE - 1) - (P - 1);

    // Minimum value of the exponent: -(2^(W-1)) - P + 3.
    private static final int Q_MIN = (-1 << W - 1) - P + 3;

    // Minimum value of the significand of a normal value: 2^(P-1).
    private static final long C_MIN = 1L << P - 1;

    // Mask to extract the biased exponent.
    private static final int BQ_MASK = (1 << W) - 1;

    // Mask to extract the fraction bits.
    private static final long T_MASK = (1L << P - 1) - 1;

    /*
    The quantity
        H = min {n integer | 10^(n-1) > 2^P}
    is the minimal number of decimal digits needed to ensure that
        round-to-half-even(toString(v)) = v
    holds for any finite v.
     */
    private static final int H = 17;

    // used in rop()
    private static final long MASK_63 = (1L << 63) - 1;

    // used in the left-to-right extraction of the digits
    private static final int LTR = 28;
    private static final int MASK_LTR = (1 << LTR) - 1;

    // for thread-safety, each thread gets its own instance of this class
    private static final ThreadLocal<DoubleToDecimal> threadLocal =
            ThreadLocal.withInitial(DoubleToDecimal::new);

    /*
    Room for the longer of the forms
        -ddddd.dddddddddddd         H + 2 characters
        -0.00ddddddddddddddddd      H + 5 characters
        -d.ddddddddddddddddE-eee    H + 7 characters
    where there are H digits d
     */
    private final byte[] buf = new byte[H + 7];

    // index into buf of rightmost valid character
    private int index;

    private DoubleToDecimal() {
    }

    /**
     * Returns a string rendering of the {@code double} argument.
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
        /*
        For finite v != 0, determine integers c and q such that
            |v| = c 2^q    and
            Q_MIN <= q <= Q_MAX    and
                either    C_MIN <= c <= C_MAX              (normal value)
                or        0 < c < C_MIN  and  q = Q_MIN    (subnormal value)

        bits are the raw bits of v, bq is its biased exponent.
        See IEEE 754 for the details related to the different cases below.
         */
        long bits = doubleToRawLongBits(v);
        int bq = (int) (bits >>> P - 1) & BQ_MASK;
        if (bq < BQ_MASK) {
            index = -1;
            if (bits < 0) {
                append('-');
            }
            if (bq > 0) {
                return toDecimal(Q_MIN - 1 + bq, C_MIN | bits & T_MASK);
            }
            if (bits == 0x0000_0000_0000_0000L) {
                return "0.0";
            }
            if (bits == 0x8000_0000_0000_0000L) {
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

    private String toDecimal(int q, long c) {
        /*
        Here, let
            v = c 2^q
        denote the absolute value of the original double, where c and q are
        as in toDecimal(double)
        Define
            v has regular spacing      if    c != C_MIN  or  q = Q_MIN
            v has irregular spacing    otherwise
        Let
            vl = (c - 1/2) 2^q    if    v has regular spacing
            vl = (c - 1/4) 2^q    otherwise
        and
            vr = (c + 1/2) 2^q
        These numbers are the lower and upper boundaries of the rounding
        interval Rv of v. They are included in Rv iff c is even. That is
            Rv = [vl, vr]    if    c is even
            Rv = (vl, vr)    otherwise

        out numerically indicates whether the boundaries of Rv are excluded:
            out = 0,    if the boundaries of Rv are included, i.e., c is even
            out = 1,    if they are excluded, i.e., c is odd

        It would be straightforward to treat the lower and upper boundaries
        separately to accommodate other input roundings.
        However, for toString(double) there's no need to do so,
        as round-half-to-even is implied by the specification.
        For the sake of completeness, though, this is discussed further below,
        on usages of out.

        With
            qb = q - 1    if    v has regular spacing
            qb = q - 2    otherwise
        and cb, cbr and cbl defined as in the code below, it follows that
            v = cb 2^qb
            vl = cbl 2^qb
            vr = cbr 2^qb
         */
        int out = (int) c & 0x1;
        long cb;
        long cbr;
        long cbl;
        int k;
        int shift;
        if (c != C_MIN | q == Q_MIN) {
            // regular spacing
            cb = c << 1;
            cbr = cb + 1;
            k = flog10pow2(q);
            shift = q + flog2pow10(-k) + 3;
        } else {
            // irregular spacing
            cb = c << 2;
            cbr = cb + 2;
            k = flog10threeQuartersPow2(q);
            shift = q + flog2pow10(-k) + 2;
        }
        cbl = cb - 1;

        long g1 = floorPow10p1dHigh(-k);
        long g0 = floorPow10p1dLow(-k);
        long vb  = rop(g1, g0, cb << shift);
        long vbl = rop(g1, g0, cbl << shift);
        long vbr = rop(g1, g0, cbr << shift);

        /*
        To include/exclude the left and right boundaries of the
        rounding interval Rv independently, assume two int lout, rout
        replacing the single out. Both would have similar semantics:
            1 if the boundary is excluded from Rv
            0 otherwise.
        Then the initializers for the booleans uin* and win* would have
        lout and rout, resp., in place of out alone.

        Back to the decimal selection, with s and t as below, at least one of
            s 10^k    and    t 10^k
        lies in Rv.
         */
        long s = vb >> 2;
        if (s >= 100) {
            /*
            When s has 3 or more digits, first consider the shorter variants
            with one digit less:
                s' = floor(s / 10)    and     t' = s' + 1
            which are used to define
                u' = s' 10^(k+1)    and    w' = t' 10^(k+1)
            At most one of them lies in Rv.
            Fall out of this branch if none lies in Rv.

            In the code below, rather than s' and t' it is more convenient
            to consider s10 = 10 s' and t10 = 10 t' = s10 + 10.
            This is the only place where a division is carried out.
             */
            long s10 = s - s % 10;
            long t10 = s10 + 10;
            boolean uin10 = vbl + out <= s10 << 2;
            boolean win10 = (t10 << 2) + out <= vbr;
            if (uin10 != win10) {
                return toChars(uin10 ? s10 : t10, k);
            }
        } else if (s < 10) {
            /*
            When s has only 1 digit, it needs to be made artificially longer
            to meet the specification.
            The only cases are well-known, so can be coded specially:
            not elegant, but does the job.
             */
            switch ((int) s) {
                case 4: return toChars(49, -325); // 4.9 10^(-324)
                case 9: return toChars(99, -325); // 9.9 10^(-324)
            }
        }
        /*
        Otherwise s has 2 digits, and so no shorter variants need to be checked,
        or control reaches here because shorter variants are outside Rv.
         */
        long t = s + 1;
        boolean uin = vbl + out <= s << 2;
        boolean win = (t << 2) + out <= vbr;
        if (uin != win) {
            /*
            Exactly one of s 10^k or t 10^k lies in Rv.
             */
            return toChars(uin ? s : t, k);
        }
        /*
        Both s 10^k and t 10^k lie in Rv: determine the one closest to v.
         */
        long cmp = vb - (s + t << 1);
        return toChars(cmp < 0 || cmp == 0 && (s & 0x1) == 0 ? s : t, k);
    }

    private static long rop(long g1, long g0, long cp) {
        long x1 = multiplyHigh(g0, cp);
        long y0 = g1 * cp;
        long y1 = multiplyHigh(g1, cp);
        long z = (y0 >>> 1) + x1;
        long vbp = y1 + (z >>> 63);
        return vbp | (z & MASK_63) + MASK_63 >>> 63;
    }

    /*
    The method formats the number f 10^e

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
        /*
        Here
            10^(len10-1) <= f < 10^len10
        Now transform it to ensure
            10^(H-1) <= f < 10^H
        and adjust e such that the original number f 10^e is the same as
            f 10^(-H) 10^e = 0.f 10^e
         */
        f *= pow10[H - len10];
        e += len10;

        /*
        The toChars_*() methods support left-to-right digits extraction
        using longs provided that the arguments are limited to 8 digits.
        Therefore, split the H = 17 digits of f into:
            h = the most significant digit of f
            m = the next 8 most significant digits of f
            l = the last 8, least significant digits of f
        that is
            f = 10^8 (10^8 h + m) + l = 10^8 hm + l,    hm = 10^8 h + m

        Do it as
            hm = floor(f / 10^8)
            l = f % 10^8 = f - 10^8 hm
            h = floor(hm / 10^8)
            m = hm % 10^8 = hm - 10^8 h

        It can be shown that
            floor(f / 10^8) = floor(193_428_131_138_340_668 f / 2^84)
        and from there
            floor(f / 10^8) = floor(48_357_032_784_585_167 f / 2^82) =
                floor(floor(48_357_032_784_585_167 f / 2^64) / 2^18) =
        Similarly
            floor(hm / 10^8) = floor(1_441_151_881 hm / 2^57)
        This way, divisions are avoided.
         */
        long hm = multiplyHigh(f, 48_357_032_784_585_167L) >>> 18;
        int l = (int) (f - 100_000_000L * hm);
        int h = (int) (hm * 1_441_151_881L >>> 57);
        int m = (int) (hm - 100_000_000 * h);

        if (0 < e && e <= 7) {
            return toChars_1(h, m, l, e);
        }
        if (-3 < e && e <= 0) {
            return toChars_2(h, m, l, e);
        }
        return toChars_3(h, m, l, e);
    }

    // 0 < e <= 7: plain format without leading zeroes.
    private String toChars_1(int h, int m, int l, int e) {
        appendDigit(h);
        /*
        See the discussion in toChar() for the replacement of the division
            floor(2^LTR (m + 1) / 10^8)

        The left-to-right digits generation is inspired by
        * Bouvier & Zimmermann, "Division-Free Binary-to-Decimal Conversion"
         */
        int y = (int) (multiplyHigh(
                (long) (m + 1) << LTR,
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
        lowDigits(l);
        return charsToString();
    }

    // -3 < e <= 0: plain format with leading zeroes.
    private String toChars_2(int h, int m, int l, int e) {
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

    // -3 >= e | e > 7: computerized scientific notation
    private String toChars_3(int h, int m, int l, int e) {
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
        /*
        See the discussion in toChar() for the replacement of the division
            floor(2^LTR (v + 1) / 10^8)

        The left-to-right digits generation is inspired by
        * Bouvier & Zimmermann, "Division-Free Binary-to-Decimal Conversion"
         */
        int y = (int) (multiplyHigh(
                (long) (v + 1) << LTR,
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
        /*
        It can be shown that
            floor(e / 10) = floor(205 e / 2^11)
        and that
            floor(e / 100) = floor(1_311 e / 2^17)
        Divisions are avoided.
         */
        if (e < 100) {
            // d = floor(e / 10), e % 10 = e - 10 d
            int d = e * 205 >>> 11;
            appendDigit(d);
            appendDigit(e - 10 * d);
            return;
        }
        // d = floor(e / 100), e % 100 = e - 100 d
        int d = e * 1_311 >>> 17;
        appendDigit(d);
        e -= 100 * d;
        // d = floor(e / 10), e % 10 = e - 10 d
        d = e * 205 >>> 11;
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
