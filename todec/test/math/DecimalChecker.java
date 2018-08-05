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

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;

class DecimalChecker {

    /*
    Returns whether s syntactically meets the expected output of
    Double.toString(double). It is restricted to finite positive outputs.
    It is an unusually long method but rather straightforward, too.
    Many conditionals could be merged, but KISS here.
     */
    private static boolean hasCorrectFormat(String s) {
        try {
            // first determine interesting boundaries in the string
            StringReader r = new StringReader(s);
            int c = r.read();

            int i = 0;
            while (c == '0') {
                ++i;
                c = r.read();
            }
            // i is just after zeroes starting the integer

            int d = i;
            while ('0' <= c && c <= '9') {
                ++d;
                c = r.read();
            }
            // d is just after digits ending the integer

            int fz = d;
            if (c == '.') {
                ++fz;
                c = r.read();
            }
            // fz is just after a decimal '.'

            int f = fz;
            while (c == '0') {
                ++f;
                c = r.read();
            }
            // f is just after zeroes starting the fraction

            int x = f;
            while ('0' <= c && c <= '9') {
                ++x;
                c = r.read();
            }
            // x is just after digits ending the fraction

            int g = x;
            if (c == 'E') {
                ++g;
                c = r.read();
            }
            // g is just after an exponent indicator 'E'

            int ez = g;
            if (c == '-') {
                ++ez;
                c = r.read();
            }
            // ez is just after a '-' sign in the exponent

            int e = ez;
            while (c == '0') {
                ++e;
                c = r.read();
            }
            // e is just after zeroes starting the exponent

            int z = e;
            while ('0' <= c && c <= '9') {
                ++z;
                c = r.read();
            }
            // z is just after digits ending the exponent

            // No other chars after the number
            if (z != s.length()) {
                return false;
            }

            // The integer must be present
            if (d == 0) {
                return false;
            }

            // The decimal '.' must be present
            if (fz == d) {
                return false;
            }

            // The fraction must be present
            if (x == fz) {
                return false;
            }

            // Plain notation, no exponent
            if (x == z) {
                // At most one 0 starting the integer
                if (i > 1) {
                    return false;
                }

                // The integer cannot have more than 7 digits
                if (d > 7) {
                    return false;
                }

                // If the integer is 0, at most 2 zeroes start the fraction
                if (i == 1 && f - fz > 2) {
                    return false;
                }

                // OK for plain notation
                return true;
            }

            // Computerized scientific notation

            // The integer has exactly one nonzero digit
            if (i != 0 || d != 1) {
                return false;
            }

            // There must be an exponent indicator
            if (x == g) {
                return false;
            }

            // There must be an exponent
            if (ez == z) {
                return false;
            }

            // The exponent must not start with zeroes
            if (ez != e) {
                return false;
            }

            int exp;
            // The exponent must parse as an int
            try {
                exp = Integer.parseInt(s, g, z, 10);
            } catch (NumberFormatException ex) {
                return false;
            }

            // The exponent must not lie in [-3, 7)
            if (-3 <= exp && exp < 7) {
                return false;
            }

            // OK for computerized scientific notation
            return true;
        } catch (IOException ex) {
            // An IOException on a StringReader??? Please...
            return false;
        }
    }

    /*
    And KISS even here.
     */
    static boolean isCorrect(double v, String s) {
        if (v != v) {
            return s.equals("NaN");
        }
        if (Double.doubleToRawLongBits(v) < 0) {
            if (s.isEmpty() || s.charAt(0) != '-') {
                return false;
            }
            return isCorrect(-v, s.substring(1));
        }
        if (v == Double.POSITIVE_INFINITY) {
            return s.equals("Infinity");
        }
        if (v == 0) {
            return s.equals("0.0");
        }
        if (!hasCorrectFormat(s)) {
            return false;
        }

        // s must of course recover v
        try {
            if (v != Double.parseDouble(s)) {
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }

        // b = d * 10^r for some integers d, r with d > 0
        BigDecimal b = new BigDecimal(s);

        // d > 0 has at most 17 digits, so must fit in a positive long
        if (b.unscaledValue().bitLength() >= Long.SIZE) {
            return false;
        }
        long d = b.unscaledValue().longValue();
        if (d >= 100_000_000_000_000_000L) {
            return false;
        }
        int r = -b.scale();

        // Determine the number of digits in d
        int len2 = Long.SIZE - Long.numberOfLeadingZeros(d);
        int len10 = MathUtils.ord10pow2(len2) - 1;
        if (d >= Powers.pow10[len10]) {
            len10 += 1;
        }

        // ord10 is such that 10^(ord10-1) <= v < 10^ord10
        int ord10 = r + len10;

        // Plain format iff -3 < ord10 <= 7
        boolean isPlain = -3 < ord10 && ord10 <= 7;

        // If plain then len10 > ord10, i.e., r < 0
        if (isPlain && r >= 0) {
            return false;
        }

        // If plain, trailing zero in fraction only if r = -1
        if (isPlain && d % 10 == 0 && r < -1) {
            return false;
        }

        // If not plain, trailing zero in fraction only if len10 = 2
        if (!isPlain && d % 10 == 0 && len10 > 2) {
            return false;
        }

        // Get rid of trailing zeroes
        while (d % 10 == 0) {
            d /= 10;
            r += 1;
            len10 -= 1;
        }

        if (len10 > 1) {
            // Try with a shorter number less than v
            long dsd = d / 10;
            int rsd = r + 1;
            BigDecimal bsd = BigDecimal.valueOf(dsd, -rsd);
            if (dsd >= 10 && bsd.doubleValue() == v) {
                return false;
            }

            // ... and with a shorter number greater than v
            long dsu = d / 10 + 1;
            int rsu = r + 1;
            BigDecimal bsu = BigDecimal.valueOf(dsu, -rsu);
            if (dsu > 10 && bsu.doubleValue() == v) {
                return false;
            }
        }

        BigDecimal bv = new BigDecimal(v);
        BigDecimal deltav = b.subtract(bv).abs();

        // Check if the decimal predecessor is closer
        long dsp = d - 1;
        BigDecimal bsp = BigDecimal.valueOf(dsp, -r);
        int cmpp = 1;
        if (bsp.doubleValue() == v) {
            BigDecimal deltap = bsp.subtract(bv).abs();
            cmpp = deltap.compareTo(deltav);
            if (cmpp < 0) {
                return false;
            }
        }

        // Check if the decimal successor is closer
        long dss = d + 1;
        BigDecimal bss = BigDecimal.valueOf(dss, -r);
        int cmps = 1;
        if (bss.doubleValue() == v) {
            BigDecimal deltas = bss.subtract(bv).abs();
            cmps = deltas.compareTo(deltav);
            if (cmps < 0) {
                return false;
            }
        }

        if (cmpp == 0 && (d & 0x1) != 0) {
            return false;
        }

        if (cmps == 0 && (d & 0x1) != 0) {
            return false;
        }

        return true;
    }

}
