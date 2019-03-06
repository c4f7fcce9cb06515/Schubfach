/*
 * Copyright 2018-2019 Raffaello Giulietti
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package math;

import static java.lang.Double.*;
import static java.lang.Long.*;
import static java.lang.Math.multiplyHigh;
import static math.MathUtils.*;

/**
 * This class exposes a method to render a {@code double} as a string.
 *
 * @author Raffaello Giulietti
 */
final public class DoubleToDecimal {
    /*
    For full details about this code see the following references:

    [1] Giulietti, "The Schubfach way to render doubles",
        https://drive.google.com/open?id=1KLtG_LaIbK9ETXI290zqCxvBW94dj058

    [2] IEEE Computer Society, "IEEE Standard for Floating-Point Arithmetic"

    [3] Bouvier & Zimmermann, "Division-Free Binary-to-Decimal Conversion"

    Divisions are avoided for the benefit of those architectures that do not
    provide specific machine instructions or where they are slow.
    This is discussed in section 10 of [1].
     */

    // The precision in bits.
    static final int P = 53;

    // H is as in section 8 of [1].
    static final int H = 17;

    // 10^(MIN_EXP - 1) <= MIN_VALUE < 10^MIN_EXP
    static final int MIN_EXP = -323;

    // 10^(MAX_EXP - 1) <= MAX_VALUE < 10^MAX_EXP
    static final int MAX_EXP = 309;

    // Exponent width in bits.
    private static final int W = (Double.SIZE - 1) - (P - 1);

    // Minimum value of the exponent: -(2^(W-1)) - P + 3.
    private static final int Q_MIN = (-1 << W - 1) - P + 3;

    // Minimum value of the significand of a normal value: 2^(P-1).
    private static final long C_MIN = 1L << P - 1;

    // Mask to extract the biased exponent.
    private static final int BQ_MASK = (1 << W) - 1;

    // Mask to extract the fraction bits.
    private static final long T_MASK = (1L << P - 1) - 1;

    // Used in rop().
    private static final long MASK_63 = (1L << 63) - 1;

    // Used for left-to-tight digit extraction.
    private static final int MASK_28 = (1 << 28) - 1;

    // For thread-safety, each thread gets its own instance of this class.
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

    // Index into buf of rightmost valid character.
    private int index;

    private DoubleToDecimal() {
    }

    /**
     * Returns a string rendering of the {@code double} argument.
     *
     * <p>The characters of the result are all drawn from the ASCII set.
     * <ul>
     * <li> Any NaN, whether quiet or signaling, is rendered as
     * {@code "NaN"}, regardless of the sign bit.
     * <li> The infinities +&infin; and -&infin; are rendered as
     * {@code "Infinity"} and {@code "-Infinity"}, respectively.
     * <li> The positive and negative zeroes are rendered as
     * {@code "0.0"} and {@code "-0.0"}, respectively.
     * <li> A finite negative {@code v} is rendered as the sign
     * '{@code -}' followed by the rendering of the magnitude -{@code v}.
     * <li> A finite positive {@code v} is rendered in two stages:
     * <ul>
     * <li> <em>Selection of a decimal</em>: A well-defined
     * decimal <i>d</i><sub><code>v</code></sub> is selected
     * to represent {@code v}.
     * <li> <em>Formatting as a string</em>: The decimal
     * <i>d</i><sub><code>v</code></sub> is formatted as a string,
     * either in plain or in computerized scientific notation,
     * depending on its value.
     * </ul>
     * </ul>
     *
     * <p>A <em>decimal</em> is a number of the form
     * <i>d</i>&times;10<sup><i>i</i></sup>
     * for some (unique) integers <i>d</i> &gt; 0 and <i>i</i> such that
     * <i>d</i> is not a multiple of 10.
     * These integers are the <em>significand</em> and
     * the <em>exponent</em>, respectively, of the decimal.
     * The <em>length</em> of the decimal is the (unique)
     * integer <i>n</i> meeting
     * 10<sup><i>n</i>-1</sup> &le; <i>d</i> &lt; 10<sup><i>n</i></sup>.
     *
     * <p>The decimal <i>d</i><sub><code>v</code></sub>
     * for a finite positive {@code v} is defined as follows:
     * <ul>
     * <li>Let <i>R</i> be the set of all decimals that round to {@code v}
     * according to the usual round-to-closest rule of
     * IEEE 754 floating-point arithmetic.
     * <li>Let <i>m</i> be the minimal length over all decimals in <i>R</i>.
     * <li>When <i>m</i> &ge; 2, let <i>T</i> be the set of all decimals
     * in <i>R</i> with length <i>m</i>.
     * Otherwise, let <i>T</i> be the set of all decimals
     * in <i>R</i> with length 1 or 2.
     * <li>Define <i>d</i><sub><code>v</code></sub> as
     * the decimal in <i>T</i> that is closest to {@code v}.
     * Or if there are two such decimals in <i>T</i>,
     * select the one with the even significand (there is exactly one).
     * </ul>
     *
     * <p>The (uniquely) selected decimal <i>d</i><sub><code>v</code></sub>
     * is then formatted.
     *
     * <p>Let <i>d</i>, <i>i</i> and <i>n</i> be the significand, exponent and
     * length of <i>d</i><sub><code>v</code></sub>, respectively.
     * Further, let <i>e</i> = <i>n</i> + <i>i</i> - 1 and let
     * <i>d</i><sub>1</sub>&hellip;<i>d</i><sub><i>n</i></sub>
     * be the usual decimal expansion of the significand.
     * Note that <i>d</i><sub>1</sub> &ne; 0 &ne; <i>d</i><sub><i>n</i></sub>.
     * <ul>
     * <li>Case -3 &le; <i>e</i> &lt; 0:
     * <i>d</i><sub><code>v</code></sub> is formatted as
     * <code>0.0</code>&hellip;<code>0</code><!--
     * --><i>d</i><sub>1</sub>&hellip;<i>d</i><sub><i>n</i></sub>,
     * where there are exactly -(<i>n</i> + <i>i</i>) zeroes between
     * the decimal point and <i>d</i><sub>1</sub>.
     * For example, 123 &times; 10<sup>-4</sup> is formatted as
     * {@code 0.0123}.
     * <li>Case 0 &le; <i>e</i> &lt; 7:
     * <ul>
     * <li>Subcase <i>i</i> &ge; 0:
     * <i>d</i><sub><code>v</code></sub> is formatted as
     * <i>d</i><sub>1</sub>&hellip;<i>d</i><sub><i>n</i></sub><!--
     * --><code>0</code>&hellip;<code>0.0</code>,
     * where there are exactly <i>i</i> zeroes
     * between <i>d</i><sub><i>n</i></sub> and the decimal point.
     * For example, 123 &times; 10<sup>2</sup> is formatted as
     * {@code 12300.0}.
     * <li>Subcase <i>i</i> &lt; 0:
     * <i>d</i><sub><code>v</code></sub> is formatted as
     * <i>d</i><sub>1</sub>&hellip;<!--
     * --><i>d</i><sub><i>n</i>+<i>i</i></sub>.<!--
     * --><i>d</i><sub><i>n</i>+<i>i</i>+1</sub>&hellip;<!--
     * --><i>d</i><sub><i>n</i></sub>.
     * There are exactly -<i>i</i> digits to the right of
     * the decimal point.
     * For example, 123 &times; 10<sup>-1</sup> is formatted as
     * {@code 12.3}.
     * </ul>
     * <li>Case <i>e</i> &lt; -3 or <i>e</i> &ge; 7:
     * computerized scientific notation is used to format
     * <i>d</i><sub><code>v</code></sub>.
     * Here <i>e</i> is formatted as by {@link Integer#toString(int)}.
     * <ul>
     * <li>Subcase <i>n</i> = 1:
     * <i>d</i><sub><code>v</code></sub> is formatted as
     * <i>d</i><sub>1</sub><code>.0E</code><i>e</i>.
     * For example, 1 &times; 10<sup>23</sup> is formatted as
     * {@code 1.0E23}.
     * <li>Subcase <i>n</i> &gt; 1:
     * <i>d</i><sub><code>v</code></sub> is formatted as
     * <i>d</i><sub>1</sub><code>.</code><i>d</i><sub>2</sub><!--
     * -->&hellip;<i>d</i><sub><i>n</i></sub><code>E</code><i>e</i>.
     * For example, 123 &times; 10<sup>-21</sup> is formatted as
     * {@code 1.23E-19}.
     * </ul>
     * </ul>
     *
     * @param v the {@code double} to be rendered.
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
        For full details see references [2] and [1].

        Let
            Q_MAX = 2^(W-1) - P
        For finite v != 0, determine integers c and q such that
            |v| = c 2^q    and
            Q_MIN <= q <= Q_MAX    and
                either    2^(P-1) <= c < 2^P                 (normal)
                or        0 < c < 2^(P-1)  and  q = Q_MIN    (subnormal)
         */
        long bits = doubleToRawLongBits(v);
        long t = bits & T_MASK;
        int bq = (int) (bits >>> P - 1) & BQ_MASK;
        if (bq < BQ_MASK) {
            index = -1;
            if (bits < 0) {
                append('-');
            }
            if (bq != 0) {
                // normal value. Here mq = -q
                int mq = -Q_MIN + 1 - bq;
                long c = C_MIN | t;
                // The fast path discussed in section 8.3 of [1].
                if (0 < mq & mq < P) {
                    long f = c >> mq;
                    if (f << mq == c) {
                        return toChars(f, 0);
                    }
                }
                return toDecimal(-mq, c);
            }
            if (t != 0) {
                // subnormal value
                return toDecimal(Q_MIN, t);
            }
            return bits == 0 ? "0.0" : "-0.0";
        }
        if (t != 0) {
            return "NaN";
        }
        return bits > 0 ? "Infinity" : "-Infinity";
    }

    private String toDecimal(int q, long c) {
        /*
        The skeleton corresponds to figure 4 of [1].
        The efficient computations are those summarized in figure 7.

        Here's a correspondence between Java names and names in [1],
        expressed as approximate LaTeX source code and informally.
        Other names are identical.
        cb:     \bar{c}     "c-bar"
        cbr:    \bar{c}_r   "c-bar-r"
        cbl:    \bar{c}_l   "c-bar-l"

        vb:     \bar{v}     "v-bar"
        vbr:    \bar{v}_r   "v-bar-r"
        vbl:    \bar{v}_l   "v-bar-l"

        rop:    r_o'        "r-o-prime"
         */
        int out = (int) c & 0x1;
        long cb;
        long cbr;
        long cbl;
        int k;
        int h;
        /*
        flog10pow2(e) = floor(log_10(2^e))
        flog10threeQuartersPow2(e) = floor(log_10(3/4 2^e))
        flog2pow10(e) = floor(log_2(10^e))
         */
        if (c != C_MIN | q == Q_MIN) {
            // regular spacing
            cb = c << 1;
            cbr = cb + 1;
            k = flog10pow2(q);
            h = q + flog2pow10(-k) + 3;
        } else {
            // irregular spacing
            cb = c << 2;
            cbr = cb + 2;
            k = flog10threeQuartersPow2(q);
            h = q + flog2pow10(-k) + 2;
        }
        cbl = cb - 1;

        // g1 and g0 are as in section 9.8.3, so g = g1 2^63 + g0
        long g1 = g1(-k);
        long g0 = g0(-k);

        long vb = rop(g1, g0, cb << h);
        long vbl = rop(g1, g0, cbl << h);
        long vbr = rop(g1, g0, cbr << h);

        long s = vb >> 2;
        if (s >= 100) {
            /*
            For n = 17, m = 1 the table in section 10 of [1] shows
                s' =
                floor(s / 10) = floor(s 115'292'150'460'684'698 / 2^60) =
                floor(s 115'292'150'460'684'698 2^4 / 2^64)

            sp10 = 10 s',    tp10 = 10 t' = sp10 + 10
            upin    iff    u' = sp10 10^k in Rv
            wpin    iff    w' = tp10 10^k in Rv
            See section 9.3.
             */
            long sp10 = 10 * multiplyHigh(s, 115_292_150_460_684_698L << 4);
            long tp10 = sp10 + 10;
            boolean upin = vbl + out <= sp10 << 2;
            boolean wpin = (tp10 << 2) + out <= vbr;
            if (upin != wpin) {
                return toChars(upin ? sp10 : tp10, k);
            }
        } else if (s < 10) {
            switch ((int) s) {
                case 4:
                    return toChars(49, -325); // 4.9 10^(-324)
                case 9:
                    return toChars(99, -325); // 9.9 10^(-324)
            }
        }

        /*
        10 <= s < 100    or    s >= 100  and  u', w' not in Rv
        uin    iff    u = s 10^k in Rv
        win    iff    w = t 10^k in Rv
        See section 9.3.
         */
        long t = s + 1;
        boolean uin = vbl + out <= s << 2;
        boolean win = (t << 2) + out <= vbr;
        if (uin != win) {
            // Exactly one of u or w lies in Rv.
            return toChars(uin ? s : t, k);
        }
        /*
        Both u and w lie in Rv: determine the one closest to v.
        See section 9.3.
         */
        long cmp = vb - (s + t << 1);
        return toChars(cmp < 0 || cmp == 0 && (s & 0x1) == 0 ? s : t, k);
    }

    /*
    Computes rop(cp g 2^(-127)), where g = g1 2^63 + g0
    See section 9.9 and figure 6 of [1].
     */
    private static long rop(long g1, long g0, long cp) {
        long x1 = multiplyHigh(g0, cp);
        long y0 = g1 * cp;
        long y1 = multiplyHigh(g1, cp);
        long z = (y0 >>> 1) + x1;
        long vbp = y1 + (z >>> 63);
        return vbp | (z & MASK_63) + MASK_63 >>> 63;
    }

    /*
    Formats the decimal f 10^e.
     */
    private String toChars(long f, int e) {
        /*
        For details not discussed here see section 10 of [1].

        Determine len such that
            10^(len-1) <= f < 10^len
         */
        int len = flog10pow2(Long.SIZE - numberOfLeadingZeros(f));
        if (f >= pow10(len)) {
            len += 1;
        }

        /*
        Let fp and ep be the original f and e, respectively.
        Transform f and e to ensure
            10^(H-1) <= f < 10^H
            fp 10^ep = f 10^(e-H) = 0.f 10^e
         */
        f *= pow10(H - len);
        e += len;

        /*
        The toChars?() methods perform left-to-right digits extraction
        using ints, provided that the arguments are limited to 8 digits.
        Therefore, split the H = 17 digits of f into:
            h = the most significant digit of f
            m = the next 8 most significant digits of f
            l = the last 8, least significant digits of f

        For n = 17, m = 8 the table in section 10 of [1] shows
            floor(f / 10^8) = floor(193'428'131'138'340'668 f / 2^84) =
            floor(floor(193'428'131'138'340'668 f / 2^64) / 2^20)
        and for n = 9, m = 8
            floor(hm / 10^8) = floor(1'441'151'881 hm / 2^57)
         */
        long hm = multiplyHigh(f, 193_428_131_138_340_668L) >>> 20;
        int l = (int) (f - 100_000_000L * hm);
        int h = (int) (hm * 1_441_151_881L >>> 57);
        int m = (int) (hm - 100_000_000 * h);

        if (0 < e && e <= 7) {
            return toChars1(h, m, l, e);
        }
        if (-3 < e && e <= 0) {
            return toChars2(h, m, l, e);
        }
        return toChars3(h, m, l, e);
    }

    private String toChars1(int h, int m, int l, int e) {
        /*
        0 < e <= 7: plain format without leading zeroes.
        Left-to-right digits extraction:
        algorithm 1 in [3], with b = 10, k = 8, n = 28.
         */
        appendDigit(h);
        int y = y(m);
        int t;
        int i = 1;
        for (; i < e; ++i) {
            t = 10 * y;
            appendDigit(t >>> 28);
            y = t & MASK_28;
        }
        append('.');
        for (; i <= 8; ++i) {
            t = 10 * y;
            appendDigit(t >>> 28);
            y = t & MASK_28;
        }
        lowDigits(l);
        return charsToString();
    }

    private String toChars2(int h, int m, int l, int e) {
        // -3 < e <= 0: plain format with leading zeroes.
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

    private String toChars3(int h, int m, int l, int e) {
        // -3 >= e | e > 7: computerized scientific notation
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

    private void append8Digits(int m) {
        /*
        Left-to-right digits extraction:
        algorithm 1 in [3], with b = 10, k = 8, n = 28.
         */
        int y = y(m);
        for (int i = 0; i < 8; ++i) {
            int t = 10 * y;
            appendDigit(t >>> 28);
            y = t & MASK_28;
        }
    }

    private void removeTrailingZeroes() {
        while (buf[index] == '0') {
            --index;
        }
        // ... but do not remove the one directly to the right of '.'
        if (buf[index] == '.') {
            ++index;
        }
    }

    private int y(int a) {
        /*
        Algorithm 1 in [3] needs computation of
            floor((a + 1) 2^n / b^k) - 1
        with a < 10^8, b = 10, k = 8, n = 28.
        Noting that
            (a + 1) 2^n <= 10^8 2^28 < 10^17
        For n = 17, m = 8 the table in section 10 of [1] leads to:
         */
        return (int) (multiplyHigh(
                (long) (a + 1) << 28,
                193_428_131_138_340_668L) >>> 20) - 1;
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
        int d;
        if (e >= 100) {
            /*
            For n = 3, m = 2 the table in section 10 of [1] shows
                floor(e / 100) = floor(1'311 e / 2^17)
             */
            d = e * 1_311 >>> 17;
            appendDigit(d);
            e -= 100 * d;
        }
        /*
        For n = 2, m = 1 the table in section 10 of [1] shows
            floor(e / 10) = floor(103 e / 2^10)
         */
        d = e * 103 >>> 10;
        appendDigit(d);
        appendDigit(e - 10 * d);
    }

    private void append(int c) {
        buf[++index] = (byte) c;
    }

    private void appendDigit(int d) {
        buf[++index] = (byte) ('0' + d);
    }

    /*
    Using the deprecated constructor enhances performance.
     */
    @SuppressWarnings("deprecation")
    private String charsToString() {
        return new String(buf, 0, 0, index + 1);
    }

}
