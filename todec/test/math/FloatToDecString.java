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

import java.util.Random;

import static java.lang.Float.*;

/*
 * @test
 * @author Raffaello Giulietti
 */
public class FloatToDecString {

    private static final boolean FAILURE_THROWS_EXCEPTION = true;

    private static void assertTrue(boolean ok, float v, String s) {
        if (ok) {
            return;
        }
        String message = "Float::toString applied to " +
                "Float.intBitsToFloat(" +
                "0x" + Integer.toHexString(floatToRawIntBits(v)) +
                ")" +
                " returns " +
                "\"" + s + "\"" +
                ", which is not correct according to the specification.";
        if (FAILURE_THROWS_EXCEPTION) {
            throw new RuntimeException(message);
        }
        System.err.println(message);
    }

    private static void toDec(float v) {
//        String s = Float.toString(v);
        String s = FloatToDecimal.toString(v);
        assertTrue(new FloatToStringChecker(v, s).isOK(), v, s);
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
        for (int e = -44; e <= 39; ++e) {
            toDec(parseFloat("1e" + e));
        }
    }

    /*
    Many powers of 2 are incorrectly rendered by the JDK.
    The rendering is either too long or it is not the closest decimal.
     */
    private static void testPowersOf2() {
        for (float v = MIN_VALUE; v <= MAX_VALUE; v *= 2.0) {
            toDec(v);
        }
    }

    /*
    Tests all integers up to 1_000_000.
    These are all exact floats.
     */
    private static void testInts() {
        for (int i = 0; i <= 1_000_000; ++i) {
            toDec(i);
        }
    }

    /*
    Random floats over the whole range.
     */
    private static void testRandom() {
        Random r = new Random();
        for (int i = 0; i < 1_000_000; ++i) {
            toDec(intBitsToFloat(r.nextInt()));
        }
    }

    /*
    All, really all, possible floats. Takes between 90 and 120 minutes.
     */
    private static void testAll() {
        int bits = Integer.MIN_VALUE;
        for (; bits < Integer.MAX_VALUE; ++bits) {
            toDec(Float.intBitsToFloat(bits));
        }
        toDec(Float.intBitsToFloat(bits));
    }

    public static void main(String[] args) {
//        testAll();
        testExtremeValues();
        testPowersOf2();
        testPowersOf10();
        testInts();
        testRandom();
    }

}
