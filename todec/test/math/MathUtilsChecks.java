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


    private static void check(boolean claim) {
        if (!claim) {
            throw new RuntimeException();
        }
    }

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
        BigInteger FIVE = BigInteger.valueOf(5);
        // 2^62 <= c1 < 2^63, 0 <= c0 < 2^63
        check(c1 << 1 < 0 && c1 >= 0 && c0 >= 0);

        BigInteger c = valueOf(c1).shiftLeft(63).or(valueOf(c0));
        // double check that 2^125 <= c < 2^126
        check(c.signum() > 0 && c.bitLength() == 126);

        // see javadoc of MathUtils.ceilPow5dHigh(int)
        int r = flog2pow10(e) - e - 125;

        /*
        The predicate
            (c - 1) 2^r < 5^e <= c 2^r
        is equivalent to
            c - 1 < 5^e 2^(-r) <= c
        When
            e >= 0 & r < 0
        all numerical subexpressions are integer-valued. This is the same as
            c = 5^e 2^(-r)
         */
        if (e >= 0 && r < 0) {
            check(c.compareTo(FIVE.pow(e).shiftLeft(-r)) == 0);
            return;
        }

        /*
        The predicate
            (c - 1) 2^r < 5^e <= c 2^r
        is equivalent to
            c 5^(-e) - 5^(-e) < 2^(-r) <= c 5^(-e)
        When
            e < 0 & r < 0
        all numerical subexpressions are integer-valued.
         */
        if (e < 0 && r < 0) {
            BigInteger pow5 = FIVE.pow(-e);
            BigInteger mhs = ONE.shiftLeft(-r);
            BigInteger rhs = c.multiply(pow5);
            check(rhs.subtract(pow5).compareTo(mhs) < 0 &&
                    mhs.compareTo(rhs) <= 0);
            return;
        }

        /*
        Finally, when
            e >= 0 & r >= 0
        the predicate
            (c - 1) 2^r < 5^e <= c 2^r
        can be used straightforwardly as all numerical subexpressions are
        already integer-valued.
         */
        if (e >= 0) {
            BigInteger mhs = FIVE.pow(e);
            check(c.subtract(ONE).shiftLeft(r).compareTo(mhs) < 0 &&
                    mhs.compareTo(c.shiftLeft(r)) <= 0);
            return;
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
        check(false);
    }

    /*
    Verifies the soundness of the value returned by
    ceilPow5dHigh() and ceilPow5dLow().
     */
    private static void testPow5Table() {
        for (int e = MIN_EXP; e <= MAX_EXP; ++e) {
            checkPow5(e, ceilPow5dHigh(e), ceilPow5dLow(e));
        }
    }

    /*
    Verifies the soundness of the pow10 array.
     */
    private static void testPow10Table() {
        int e = 0;
        long p10 = 1;
        for (; e < pow10.length; e += 1, p10 *= 10) {
            check(pow10[e] == p10);
        }
        check(e > 17);
    }

    /*
    Let
        k = floor(log10(3/4 2^e))
    The method verifies that
        k = flog10threeQuartersPow2(e),    |e| <= 300_000
    This range amply covers all binary exponents of IEEE 754 binary formats up
    to binary256 (octuple precision), so also suffices for doubles and floats.

    The first equation above is equivalent to
        10^k <= 3 2^(e-2) < 10^(k+1)
    Equality never holds. Henceforth, the predicate to check is
        10^k < 3 2^(e-2) < 10^(k+1)
    This will be transformed in various ways for checking purposes.

    For integer n > 0, let further
        b = bitlength(n)
    denote its length in bits. This means exactly the same as
        2^(b-1) <= n < 2^b
     */
    private static void testFlog10threeQuartersPow2() {
        BigInteger THREE = BigInteger.valueOf(3);
        BigInteger FIVE = BigInteger.valueOf(5);
        /*
        First check the case e = 0
         */
        check(flog10threeQuartersPow2(0) == -1);

        /*
        Now check the range -300_000 <= e < 0.
        By inverting all quantities, the predicate to check is equivalent to
            3 10^(-k-1) < 2^(2-e) < 3 10^(-k)
        As e < 0, it follows that 2^(2-e) >= 8 and the right inequality implies
        k < 0.
        The left inequality holds iff
            3 5^(-k-1) < 2^(k-e+3)
        also holds and this means exactly the same as
            bitlength(3 5^(-k-1)) <= k - e + 3
        Similarly, the right inequality is equivalent to
            2^(k-e+2) < 3 5^(-k)
        and hence to
            k - e + 3 <= bitlength(3 5^(-k))
        The original predicate is therefore equivalent to
            bitlength(3 5^(-k-1)) <= k - e + 3 <= bitlength(3 5^(-k))
        Since k < 0, the powers of 5 are integer-valued.

        Starting with e = -1 and decrementing until the lower bound, the code
        keeps track of the two powers of 5 so as to avoid recomputing them.
        This is easy because at each iteration k changes at most by 1. A simple
        multiplication by 5 computes the next power of 5 when needed.
         */
        int e = -1;
        int k0 = flog10threeQuartersPow2(e);
        check(k0 < 0);
        BigInteger l = THREE.multiply(FIVE.pow(-k0 - 1));
        BigInteger u = l.multiply(FIVE);
        for (;;) {
            check(l.bitLength() <= k0 - e + 3 && k0 - e + 3 <= u.bitLength());
            if (e == -300_000) {
                break;
            }
            --e;
            int kp = flog10threeQuartersPow2(e);
            check(kp <= k0);
            if (kp < k0) {
                // k changes at most by 1 at each iteration, hence:
                check(k0 - kp == 1);
                k0 = kp;
                l = u;
                u = u.multiply(FIVE);
            }
        }

        /*
        By definition
            k = floor(log10(3/4 2^e))
        so when e > 0
            k <= log10(3/4 2^e) = log10(3/4) + e log10(2) < e log10(2) < e/3
        Hence, as soon as e >= 5
            e - k - 3 > e - e/3 - 3 = 2e/3 - 3 > 0
        Thus, check the range 5 <= e <= 300_000 here.
        The exponents 1 <= e < 5 are checked separately below.

        Now, in predicate
            10^k < 3 2^(e-2) < 10^(k+1)
        the right inequality shows that k >= 1 because e >= 5.
        It is equivalent to
            5^k/3 < 2^(e-k-2) & 2^(e-k-3) < 5^(k+1)/3
        The powers of 5 are integer-valued.
        The powers of 2 are integer-values as well.
        The left inequality is therefore equivalent to
            floor(5^k/3) < 2^(e-k-2)
        and thus to
            bitlength(floor(5^k/3)) <= e - k - 2
        while the right inequality is equivalent to
            2^(e-k-3) < floor(5^(k+1)/3)
        and hence to
            e - k - 2 <= bitlength(floor(5^(k+1)/3))
        These are summarized in
            bitlength(floor(5^k/3)) <= e - k - 2 <= bitlength(floor(5^(k+1)/3))
         */
        e = 5;
        k0 = flog10threeQuartersPow2(e);
        check(k0 >= 1);
        BigInteger l5 = FIVE.pow(k0);
        BigInteger u5 = l5.multiply(FIVE);
        l = l5.divide(THREE);
        u = u5.divide(THREE);
        for (;;) {
            check(l.bitLength() <= e - k0 - 2 && e - k0 - 2 <= u.bitLength());
            if (e == 300_000) {
                break;
            }
            ++e;
            int kp = flog10threeQuartersPow2(e);
            check(kp >= k0);
            if (kp > k0) {
                // k changes at most by 1 at each iteration, hence:
                check(kp - k0 == 1);
                k0 = kp;
                u5 = u5.multiply(FIVE);
                l = u;
                u = u5.divide(THREE);
            }
        }

        /*
        Finally, check the exponents 1 <= e < 5.
        The predicate to check means the same as
            4 10^k < 3 2^e < 4 10^(k+1)
        Both the powers of 10 and the powers of 2 are integer-valued.
         */
        e = 1;
        while (e < 5) {
            k0 = flog10threeQuartersPow2(e);
            check(k0 >= 0);
            l = TEN.pow(k0).shiftLeft(2);
            u = l.multiply(TEN);
            BigInteger m = THREE.shiftLeft(e);
            check(l.compareTo(m) < 0 && m.compareTo(u) < 0);
            ++e;
        }
    }

    /*
    Let
        k = floor(log10(2^e))
    The method verifies that
        k = flog10pow2(e),    |e| <= 300_000
    This range amply covers all binary exponents of IEEE 754 binary formats up
    to binary256 (octuple precision), so also suffices for doubles and floats.

    The first equation above is equivalent to
        10^k <= 2^e < 10^(k+1)
    Equality holds iff e = 0, implying k = 0.
    Henceforth, the predicates to check are equivalent to
        k = 0,    e = 0
        10^k < 2^e < 10^(k+1),    e != 0
    The latter will be transformed in various ways for checking purposes.

    For integer n > 0, let further
        b = bitlength(n)
    denote its length in bits. This means exactly the same as
        2^(b-1) <= n < 2^b
     */
    private static void testFlog10pow2() {
        BigInteger FIVE = BigInteger.valueOf(5);
        /*
        First check the case e = 0
         */
        check(flog10pow2(0) == 0);

        /*
        Now check the range -300_000 <= e < 0.
        By inverting all quantities, the predicate to check is equivalent to
            10^(-k-1) < 2^(-e) < 10^(-k)
        As e < 0, it follows that 2^(-e) >= 2 and the right inequality implies
        k < 0.
        The left inequality holds iff
            5^(-k-1) < 2^(k-e+1)
        also holds and this means exactly the same as
            bitlength(5^(-k-1)) <= k - e + 1
        Similarly, the right inequality is equivalent to
            2^(k-e) < 5^(-k)
        As k != 0, this is the same as
            k - e + 1 <= bitlength(5^(-k))
        The original predicate is therefore equivalent to
            bitlength(5^(-k-1)) <= k - e + 1 <= bitlength(5^(-k))
        The powers of 5 are integer-valued because k < 0.

        Starting with e = -1 and decrementing towards the lower bound, the code
        keeps track of the two powers of 5 so as to avoid recomputing them.
        This is easy because at each iteration k changes at most by 1. A simple
        multiplication by 5 computes the next power of 5 when needed.
         */
        int e = -1;
        int k = flog10pow2(e);
        check(k < 0);
        BigInteger l = FIVE.pow(-k - 1);
        BigInteger u = l.multiply(FIVE);
        for (;;) {
            check(l.bitLength() <= k - e + 1 && k - e + 1 <= u.bitLength());
            if (e == -300_000) {
                break;
            }
            --e;
            int kp = flog10pow2(e);
            check(kp <= k);
            if (kp < k) {
                // k changes at most by 1 at each iteration, hence:
                check(k - kp == 1);
                k = kp;
                l = u;
                u = u.multiply(FIVE);
            }
        }

        /*
        Finally, in a similar vein, check the range 0 < e <= 300_000.
        In predicate
            10^k < 2^e < 10^(k+1)
        the right inequality shows that k >= 0. It is equivalent to
            5^k < 2^(e-k) & 2^(e-k-1) < 5^(k+1)
        Similarly as above, the left inequality means the same as
            bitlength(5^k) <= e - k
        and the right inequality holds iff
            e - k <= bitlength(5^(k+1))
        The original predicate is thus equivalent to
            bitlength(5^k) <= e - k <= bitlength(5^(k+1))
        As k >= 0, the powers of 5 are integer-valued.
         */
        e = 1;
        k = flog10pow2(e);
        check(k >= 0);
        l = FIVE.pow(k);
        u = l.multiply(FIVE);
        for (;;) {
            check(l.bitLength() <= e - k && e - k <= u.bitLength());
            if (e == 300_000) {
                break;
            }
            ++e;
            int kp = flog10pow2(e);
            check(kp >= k);
            if (kp > k) {
                // k changes at most by 1 at each iteration, hence:
                check(kp - k == 1);
                k = kp;
                l = u;
                u = u.multiply(FIVE);
            }
        }
    }

    /*
    Let
        k = floor(log2(10^e))
    The method verifies that
        k = flog2pow10(e),    |e| <= 100_000
    This range amply covers all decimal exponents of IEEE 754 binary formats up
    to binary256 (octuple precision), so also suffices for doubles and floats.

    The first equation above is equivalent to
        2^k <= 10^e < 2^(k+1)
    Equality holds iff e = 0, implying k = 0.
    Henceforth, the equivalent predicates to check are
        k = 0,    e = 0
        2^k < 10^e < 2^(k+1),    e != 0
    The latter will be transformed in various ways for checking purposes.

    For integer n > 0, let further
        b = bitlength(n)
    denote its length in bits. This means exactly the same as
        2^(b-1) <= n < 2^b
    */
    private static void testFlog2pow10() {
        BigInteger FIVE = BigInteger.valueOf(5);
        /*
        First check the case e = 0
         */
        check(flog2pow10(0) == 0);

        /*
        Now check the range -100_000 <= e < 0.
        By inverting all quantities, the predicate to check is equivalent to
            2^(-k-1) < 10^(-e) < 2^(-k)
        As e < 0, this leads to 10^(-e) >= 10 and the right inequality implies
        k <= -4.
        The above means the same as
            2^(e-k-1) < 5^(-e) < 2^(e-k)
        and thus the same as
            bitlength(5^(-e)) = e - k
        The powers of 5 are integer values since e < 0.
         */
        int e = -1;
        int k0 = flog2pow10(e);
        check(k0 <= -4);
        BigInteger l = FIVE;
        for (;;) {
            check(l.bitLength() == e - k0);
            if (e == -100_000) {
                break;
            }
            --e;
            k0 = flog2pow10(e);
            l = l.multiply(FIVE);
        }

        /*
        Finally check the range 0 < e <= 100_000.
        From the predicate
            2^k < 10^e < 2^(k+1)
        as e > 0, it follows that 10^e >= 10 and the right inequality implies
        k >= 3.
        The above means the same as
            2^(k-e) < 5^e < 2^(k-e+1)
        and thus the same as
            bitlength(5^e) = k - e + 1
        The powers of 5 are all integer valued, as e > 0.
         */
        e = 1;
        k0 = flog2pow10(e);
        check(k0 >= 3);
        l = FIVE;
        for (;;) {
            check(l.bitLength() == k0 - e + 1);
            if (e == 100_000) {
                break;
            }
            ++e;
            k0 = flog2pow10(e);
            l = l.multiply(FIVE);
        }
    }

    public static void main(String[] args) {
        testFlog10pow2();
        testFlog2pow10();
        testFlog10threeQuartersPow2();
        testPow10Table();
        testPow5Table();
    }

}
