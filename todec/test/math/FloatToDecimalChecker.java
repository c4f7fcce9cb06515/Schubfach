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

import java.math.BigDecimal;
import java.util.Random;

import static java.lang.Float.*;
import static java.lang.Integer.numberOfTrailingZeros;
import static math.FloatToDecimal.*;
import static math.MathUtils.flog10pow2;

/*
 * @test
 * @author Raffaello Giulietti
 */
class FloatToDecimalChecker extends ToDecimalChecker {

    private static final int RANDOM_COUNT = 1_000_000;
    private static final boolean TEST_ALL = false;

    private float v;
    private final int originalBits;

    private FloatToDecimalChecker(float v, String s) {
        super(s);
        this.v = v;
        originalBits = floatToRawIntBits(v);
    }

    @Override
    BigDecimal toBigDecimal() {
        return new BigDecimal(v);
    }

    @Override
    boolean recovers(BigDecimal b) {
        return b.floatValue() == v;
    }

    @Override
    String hexBits() {
        return String.format("0x%01X__%02X__%02X_%04X",
                (originalBits >>> 31) & 0x1,
                (originalBits >>> 23) & 0xFF,
                (originalBits >>> 16) & 0x7F,
                originalBits & 0xFFFF);
    }

    @Override
    boolean recovers(String s) {
        return Float.parseFloat(s) == v;
    }

    @Override
    int minExp() {
        return FloatToDecimal.MIN_EXP;
    }

    @Override
    int maxExp() {
        return FloatToDecimal.MAX_EXP;
    }

    @Override
    int maxLen10() {
        return 9;
    }

    @Override
    boolean isZero() {
        return v == 0;
    }

    @Override
    boolean isInfinity() {
        return v == Float.POSITIVE_INFINITY;
    }

    @Override
    void negate() {
        v = -v;
    }

    @Override
    boolean isNegative() {
        return originalBits < 0;
    }

    @Override
    boolean isNaN() {
        return Float.isNaN(v);
    }

    private static void toDec(float v) {
//        String s = Float.toString(v);
        String s = FloatToDecimal.toString(v);
        new FloatToDecimalChecker(v, s).assertTrue();
    }

    /*
    MIN_NORMAL is incorrectly rendered by the JDK.
     */
    private static void testExtremeValues() {
        toDec(NEGATIVE_INFINITY);
        toDec(-MAX_VALUE);
        toDec(-MIN_NORMAL);
        toDec(-MIN_VALUE);
        toDec(-0.0f);
        toDec(0.0f);
        toDec(MIN_VALUE);
        toDec(MIN_NORMAL);
        toDec(MAX_VALUE);
        toDec(POSITIVE_INFINITY);
        toDec(NaN);

        /*
        Quiet NaNs have the most significant bit of the mantissa as 1,
        while signaling NaNs have it as 0.
        Exercise 4 combinations of quiet/signaling NaNs and
        "positive/negative" NaNs.
         */
        toDec(intBitsToFloat(0x7FC0_0001));
        toDec(intBitsToFloat(0x7F80_0001));
        toDec(intBitsToFloat(0xFFC0_0001));
        toDec(intBitsToFloat(0xFF80_0001));

        /*
        All values treated specially by Schubfach
         */
        toDec(1.4E-45F);
        toDec(2.8E-45F);
        toDec(4.2E-45F);
        toDec(5.6E-45F);
        toDec(7.0E-45F);
        toDec(8.4E-45F);
        toDec(9.8E-45F);
    }

    /*
    Many "powers of 10" are incorrectly rendered by the JDK.
    The rendering is either too long or it is not the closest decimal.
     */
    private static void testPowersOf10() {
        for (int e = MIN_EXP; e <= MAX_EXP; ++e) {
            toDec(parseFloat("1e" + e));
        }
    }

    /*
    Many powers of 2 are incorrectly rendered by the JDK.
    The rendering is either too long or it is not the closest decimal.
     */
    private static void testPowersOf2() {
        for (float v = MIN_VALUE; v <= MAX_VALUE; v *= 2) {
            toDec(v);
        }
    }

    /*
    Tests all positive integers below 2^23.
    These are all exact floats and exercise the fast path.
     */
    private static void testInts() {
        for (int i = 1; i < 1 << P - 1; ++i) {
            toDec(i);
        }
    }

    /*
    Random floats over the whole range.
     */
    private static void testRandom() {
        Random r = new Random();
        for (int i = 0; i < RANDOM_COUNT; ++i) {
            toDec(intBitsToFloat(r.nextInt()));
        }
    }

    /*
    All, really all, possible floats. Takes between 90 and 120 minutes.
     */
    private static void testAll() {
        // Avoid wrapping around Integer.MAX_VALUE
        int bits = Integer.MIN_VALUE;
        for (; bits < Integer.MAX_VALUE; ++bits) {
            toDec(Float.intBitsToFloat(bits));
        }
        toDec(Float.intBitsToFloat(bits));
    }

    private static void testConstants() {
        assertTrue(precision() == P, "P");
        assertTrue(flog10pow2(P) + 2 == H, "H");
        assertTrue(e(MIN_VALUE) == MIN_EXP, "MIN_EXP");
        assertTrue(e(MAX_VALUE) == MAX_EXP, "MAX_EXP");
    }

    private static int precision() {
        /*
        Given precision P, the floating point value 3 has the bits
        0e...e10...0
        where there are exactly P - 2 trailing zeroes.
        */
        return numberOfTrailingZeros(floatToRawIntBits(3)) + 2;
    }

    public static void main(String[] args) {
        testConstants();
        testExtremeValues();
        testPowersOf2();
        testPowersOf10();
        testInts();
        testRandom();
        if (TEST_ALL) {
            testAll();
        }
    }

}
