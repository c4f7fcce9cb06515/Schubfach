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

import static java.math.BigInteger.*;

import static math.MathUtils.*;

/*
 * @test
 * @author Raffaello Giulietti
 */
public class MathUtilsChecks {

    /*
    Let
        5^e = d 2^r
    for the unique integer r and real d meeting
        2^125 <= d < 2^126
    Further, let c = c1 2^63 + c0.
    Checks that:
        2^62 <= c1 < 2^63,
        0 <= c0 < 2^63,
        c - 1 < d <= c,    (that is, c = ceil(d))
    The last predicate, after multiplying by 2^r, is equivalent to
        (c - 1) 2^r < 5^e <= c 2^r
    This is the predicate that will be checked in various forms.

    Throws an exception iff the check fails.
     */
    private static void checkPow5(int e, long c1, long c0) {
        // 2^62 <= c1 < 2^63, 0 <= c0 < 2^63
        if (c1 << 1 >= 0 || c1 < 0 || c0 < 0) {
            throw new RuntimeException();
        }

        BigInteger c = valueOf(c1).shiftLeft(63).or(valueOf(c0));
        // double check that 2^125 <= c < 2^126
        if (c.signum() <= 0 || c.bitLength() != 126) {
            throw new RuntimeException();
        }

        // see javadoc of MathUtils.ceilPow5dHigh(int)
        int r = flog2pow10(e) - e - 125;
        BigInteger _5 = BigInteger.valueOf(5);

        /*
        When
            e >= 0 & r < 0
        the predicate
            (c - 1) 2^r < 5^e <= c 2^r
        is equivalent to
            c - 1 < 5^e 2^(-r) <= c
        where numerical subexpressions are integer-valued.
         */
        if (e >= 0 && r < 0) {
            BigInteger mhs = _5.pow(e).shiftLeft(-r);
            if (c.subtract(ONE).compareTo(mhs) < 0 &&
                    mhs.compareTo(c) <= 0) {
                return;
            }
            throw new RuntimeException();
        }

        /*
        When
            e < 0 & r < 0
        the predicate
            (c - 1) 2^r < 5^e <= c 2^r
        is equivalent to
            c 5^(-e) - 5^(-e) < 2^(-r) <= c 5^(-e)
        where numerical subexpressions are integer-valued.
         */
        if (e < 0 && r < 0) {
            BigInteger pow5 = _5.pow(-e);
            BigInteger mhs = ONE.shiftLeft(-r);
            BigInteger rhs = c.multiply(pow5);
            if (rhs.subtract(pow5).compareTo(mhs) < 0 &&
                    mhs.compareTo(rhs) <= 0) {
                return;
            }
            throw new RuntimeException();
        }

        /*
        Finally, when
            e >= 0 & r >= 0
        the predicate
            (c - 1) 2^r < 5^e <= c 2^r
        can be used straightforwardly: all numerical subexpressions are
        already integer-valued.
         */
        if (e >= 0) {
            BigInteger mhs = _5.pow(e);
            if (c.subtract(ONE).shiftLeft(r).compareTo(mhs) < 0 &&
                    mhs.compareTo(c.shiftLeft(r)) <= 0) {
                return;
            }
            throw new RuntimeException();
        }

        /*
        For combinatorial reasons, the only remaining case is
            e < 0 & r >= 0
        which, however, cannot arise. Indeed, the predicate
            (c - 1) 2^r < 5^e <= c 2^r
        implies
            (c - 1) 2^r 5^(-e) < 1
        which cannot hold, since the left-hand side is a positive integer.
         */
        throw new RuntimeException();
    }

    /*
    Verifies the soundness of the value returned by
    ceilPow5dHigh() and ceilPow5dLow().
     */
    private static void testTable() {
        for (int e = MIN_EXP; e <= MAX_EXP; ++e) {
            checkPow5(e, ceilPow5dHigh(e), ceilPow5dLow(e));
        }
    }

    public static void main(String[] args) {
        testTable();
    }

}
