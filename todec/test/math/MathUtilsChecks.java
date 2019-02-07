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

import java.math.BigInteger;

import static java.math.BigInteger.*;

import static math.MathUtils.*;

/*
 * @test
 * @author Raffaello Giulietti
 */
public class MathUtilsChecks {

    private static final BigInteger THREE = BigInteger.valueOf(3);

    private static void check(boolean claim) {
        if (!claim) {
            throw new RuntimeException();
        }
    }

    /*
    Let
        10^e = beta 2^r
    for the unique integer r and real beta meeting
        2^125 <= beta < 2^126
    Further, let g = g1 2^63 + g0.
    Checks that:
        2^62 <= g1 < 2^63,
        0 <= g0 < 2^63,
        g - 1 <= beta < g,    (that is, g = floor(beta) + 1)
    The last predicate, after multiplying by 2^r, is equivalent to
        (g - 1) 2^r < 10^e <= g 2^r
    This is the predicate that will be checked in various forms.

    Throws an exception iff the check fails.
     */
    private static void checkPow10(int e, long g1, long g0) {
        // 2^62 <= g1 < 2^63, 0 <= g0 < 2^63
        check(g1 << 1 < 0 && g1 >= 0 && g0 >= 0);

        BigInteger g = valueOf(g1).shiftLeft(63).or(valueOf(g0));
        // double check that 2^125 <= g < 2^126
        check(g.signum() > 0 && g.bitLength() == 126);

        // see javadoc of MathUtils.floorPow10p1dHigh(int)
        int r = flog2pow10(e) - 125;

        /*
        The predicate
            (g - 1) 2^r <= 10^e < g 2^r
        is equivalent to
            g - 1 <= 10^e 2^(-r) < g
        When
            e >= 0 & r < 0
        all numerical subexpressions are integer-valued. This is the same as
            g - 1 = 10^e 2^(-r)
         */
        if (e >= 0 && r < 0) {
            check(g.subtract(ONE).compareTo(TEN.pow(e).shiftLeft(-r)) == 0);
            return;
        }

        /*
        The predicate
            (g - 1) 2^r <= 10^e < g 2^r
        is equivalent to
            g 10^(-e) - 10^(-e) <= 2^(-r) < g 10^(-e)
        When
            e < 0 & r < 0
        all numerical subexpressions are integer-valued.
         */
        if (e < 0 && r < 0) {
            BigInteger pow5 = TEN.pow(-e);
            BigInteger mhs = ONE.shiftLeft(-r);
            BigInteger rhs = g.multiply(pow5);
            check(rhs.subtract(pow5).compareTo(mhs) <= 0 &&
                    mhs.compareTo(rhs) < 0);
            return;
        }

        /*
        Finally, when
            e >= 0 & r >= 0
        the predicate
            (g - 1) 2^r < 10^e <= g 2^r
        can be used straightforwardly as all numerical subexpressions are
        already integer-valued.
         */
        if (e >= 0) {
            BigInteger mhs = TEN.pow(e);
            check(g.subtract(ONE).shiftLeft(r).compareTo(mhs) <= 0 &&
                    mhs.compareTo(g.shiftLeft(r)) < 0);
            return;
        }

        /*
        For combinatorial reasons, the only remaining case is
            e < 0 & r >= 0
        which, however, cannot arise. Indeed, the predicate
            (g - 1) 2^r <= 10^e < g 2^r
        implies
            (g - 1) 2^r 10^(-e) <= 1
        which cannot hold, as the left-hand side is greater than 1.
         */
        check(false);
    }

    /*
    Verifies the soundness of the values returned by
    floorPow10p1dHigh() and floorPow10p1dLow().
     */
    private static void testPow10Table() {
        for (int e = MIN_EXP; e <= MAX_EXP; ++e) {
            checkPow10(e, floorPow10p1dHigh(e), floorPow10p1dLow(e));
        }
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
        b = len2(n)
    denote its length in bits. This means exactly the same as
        2^(b-1) <= n < 2^b
     */
    private static void testFlog10threeQuartersPow2() {
        /*
        First check the case e = 1
         */
        check(flog10threeQuartersPow2(1) == 0);

        /*
        Now check the range -300_000 <= e <= 0.
        By rewriting, the predicate to check is equivalent to
            3 10^(-k-1) < 2^(2-e) < 3 10^(-k)
        As e <= 0, it follows that 2^(2-e) >= 4 and the right inequality
        implies k < 0, so the powers of 10 are integers.

        The left inequality is equivalent to
            len2(3 10^(-k-1)) <= 2 - e
        and the right inequality to
            2 - e < len2(3 10^(-k))
        The original predicate is therefore equivalent to
            len2(3 10^(-k-1)) <= 2 - e < len2(3 10^(-k))

        Starting with e = 0 and decrementing until the lower bound, the code
        keeps track of the two powers of 10 to avoid recomputing them.
        This is easy because at each iteration k changes at most by 1. A simple
        multiplication by 10 computes the next power of 10 when needed.
         */
        int e = 0;
        int k0 = flog10threeQuartersPow2(e);
        check(k0 < 0);
        BigInteger l = THREE.multiply(TEN.pow(-k0 - 1));
        BigInteger u = l.multiply(TEN);
        for (;;) {
            check(l.bitLength() <= 2 - e && 2 - e < u.bitLength());
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
                u = u.multiply(TEN);
            }
        }

        /*
        Finally, check the range 2 <= e <= 300_000.
        In predicate
            10^k < 3 2^(e-2) < 10^(k+1)
        the right inequality shows that k >= 0 as soon as e >= 2.
        It is equivalent to
            10^k / 3 < 2^(e-2) < 10^(k+1) / 3
        Both the powers of 10 and the powers of 2 are integer-valued.
        The left inequality is therefore equivalent to
            floor(10^k / 3) < 2^(e-2)
        and thus to
            len2(floor(10^k / 3)) <= e - 2
        while the right inequality is equivalent to
            2^(e-2) <= floor(10^(k+1) / 3)
        and hence to
            e - 2 < len2(floor(10^(k+1) / 3))
        These are summarized as
            len2(floor(10^k / 3)) <= e - 2 < len2(floor(10^(k+1) / 3))
         */
        e = 2;
        k0 = flog10threeQuartersPow2(e);
        check(k0 >= 0);
        BigInteger l10 = TEN.pow(k0);
        BigInteger u10 = l10.multiply(TEN);
        l = l10.divide(THREE);
        u = u10.divide(THREE);
        for (;;) {
            check(l.bitLength() <= e - 2 && e - 2 < u.bitLength());
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
                u10 = u10.multiply(TEN);
                l = u;
                u = u10.divide(THREE);
            }
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
        k = 0,    if e = 0
        10^k < 2^e < 10^(k+1),    otherwise
    The latter will be transformed in various ways for checking purposes.

    For integer n > 0, let further
        b = len2(n)
    denote its length in bits. This means exactly the same as
        2^(b-1) <= n < 2^b
     */
    private static void testFlog10pow2() {
        /*
        First check the case e = 0
         */
        check(flog10pow2(0) == 0);

        /*
        Now check the range -300_000 <= e < 0.
        By inverting all quantities, the predicate to check is equivalent to
            10^(-k-1) < 2^(-e) < 10^(-k)
        As e < 0, it follows that 2^(-e) >= 2 and the right inequality
        implies k < 0.
        The left inequality means exactly the same as
            len2(10^(-k-1)) <= -e
        Similarly, the right inequality is equivalent to
            -e < len2(10^(-k))
        The original predicate is therefore equivalent to
            len2(10^(-k-1)) <= -e < len2(10^(-k))
        The powers of 10 are integer-valued because k < 0.

        Starting with e = -1 and decrementing towards the lower bound, the code
        keeps track of the two powers of 10 so as to avoid recomputing them.
        This is easy because at each iteration k changes at most by 1. A simple
        multiplication by 10 computes the next power of 10 when needed.
         */
        int e = -1;
        int k = flog10pow2(e);
        check(k < 0);
        BigInteger l = TEN.pow(-k - 1);
        BigInteger u = l.multiply(TEN);
        for (;;) {
            check(l.bitLength() <= -e && -e < u.bitLength());
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
                u = u.multiply(TEN);
            }
        }

        /*
        Finally, in a similar vein, check the range 0 <= e <= 300_000.
        In predicate
            10^k < 2^e < 10^(k+1)
        the right inequality shows that k >= 0.
        The left inequality means the same as
            len2(10^k) <= e
        and the right inequality holds iff
            e < len2(10^(k+1))
        The original predicate is thus equivalent to
            len2(10^k) <= e < len2(10^(k+1))
        As k >= 0, the powers of 10 are integer-valued.
         */
        e = 1;
        k = flog10pow2(e);
        check(k >= 0);
        l = TEN.pow(k);
        u = l.multiply(TEN);
        for (;;) {
            check(l.bitLength() <= e && e < u.bitLength());
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
                u = u.multiply(TEN);
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
        k = 0,    if e = 0
        2^k < 10^e < 2^(k+1),    otherwise
    The latter will be transformed in various ways for checking purposes.

    For integer n > 0, let further
        b = len2(n)
    denote its length in bits. This means exactly the same as
        2^(b-1) <= n < 2^b
    */
    private static void testFlog2pow10() {
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
            len2(10^(-e)) = -k
        The powers of 10 are integer values since e < 0.
         */
        int e = -1;
        int k0 = flog2pow10(e);
        check(k0 <= -4);
        BigInteger l = TEN;
        for (;;) {
            check(l.bitLength() == -k0);
            if (e == -100_000) {
                break;
            }
            --e;
            k0 = flog2pow10(e);
            l = l.multiply(TEN);
        }

        /*
        Finally check the range 0 < e <= 100_000.
        From the predicate
            2^k < 10^e < 2^(k+1)
        as e > 0, it follows that 10^e >= 10 and the right inequality implies
        k >= 3.
        The above means the same as
            len2(10^e) = k + 1
        The powers of 10 are all integer valued, as e > 0.
         */
        e = 1;
        k0 = flog2pow10(e);
        check(k0 >= 3);
        l = TEN;
        for (;;) {
            check(l.bitLength() == k0 + 1);
            if (e == 100_000) {
                break;
            }
            ++e;
            k0 = flog2pow10(e);
            l = l.multiply(TEN);
        }
    }

    public static void main(String[] args) {
        testPow10Table();
        testFlog10pow2();
        testFlog10threeQuartersPow2();
        testFlog2pow10();
    }

}
