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

import static math.Natural.valueOf;

/**
 * Package-privately exposes
 * <ul>
 * <li> integer powers of 5 as unsigned {@code long}s, up to the exponent
 * {@link #MAX_POW_5_EXP}
 * <li> integer powers of 10 as unsigned {@code long}s, up to the exponent
 * {@link #MAX_POW_10_EXP}
 * <li> integer powers of 5 as {@link Natural}s, up to the exponent
 * {@link #MAX_POW_5_N_EXP}
 * </ul>
 *
 * <p>
 * Since this is a package-private class, no checks are made to ensure
 * that usages are correct.
 */
final class Powers {

    /**
     * The integer <i>e</i> such that
     * 5<sup><i>e</i></sup> &le; <i>M</i> &lt; 5<sup><i>e</i>+1</sup>,
     * where <i>M</i> is the largest unsigned {@code long}, namely
     * <i>M</i> = 2<sup>{@link Long#SIZE}</sup> - 1.
     */
    static final int MAX_POW_5_EXP = 27;

    /*
    The greatest power of 5 fitting in an unsigned {@code long},
    namely 5^MAX_POW_5_EXP
     */
    private static final long MAX_POW_5 = 7_450_580_596_923_828_125L;

    /**
     * The integer <i>e</i> such that
     * 10<sup><i>e</i></sup> &le; <i>M</i> &lt; 10<sup><i>e</i>+1</sup>,
     * where <i>M</i> is the largest unsigned {@code long}, namely
     * <i>M</i> = 2<sup>{@link Long#SIZE}</sup> - 1.
     */
    static final int MAX_POW_10_EXP = 19;

    /**
     * The greatest exponent for {@link #pow5(int)}.
     */
    /*
    MAX_POW_5_N_EXP = Double.H - Double.E_MIN_VALUE
     */
    static final int MAX_POW_5_N_EXP = 340;

    /**
     * Powers of 5, as unsigned {@code long}s, for exponents between
     * 0 and {@link #MAX_POW_5_EXP}.
     */
    static final long[] pow5;

    /**
     * Powers of 10, as unsigned {@code long}s, for exponents between
     * 0 and {@link #MAX_POW_10_EXP}.
     */
    static final long[] pow10;

    /*
    pow5n is populated lazily. More precisely, values for the exponents between
    0 and MAX_POW_5_EXP are initialized upon class loading.
    Other values are computed upon request (see pow5()).

    Invariant:
        e0max is a multiple of MAX_POW_5_EXP and all values for exponents
        that are multiples of MAX_POW_5_EXP, up to e0max, are already present
        in the array.
     */
    private static final Natural[] pow5n;
    private static int e0max = MAX_POW_5_EXP;

    static {
        /*
        Fully initializes the pow5 and pow10 array and partial initializes
        pow5n, which will be populated lazily, as need arises.
         */
        pow5n = new Natural[MAX_POW_5_N_EXP + 1];
        pow5 = new long[MAX_POW_5_EXP + 1];
        pow5[0] = 1;
        for (int k = 1; k < pow5.length; ++k) {
            pow5[k] = 5 * pow5[k - 1];
            pow5n[k] = valueOf(pow5[k]);
        }

        pow10 = new long[MAX_POW_10_EXP + 1];
        pow10[0] = 1;
        for (int k = 1; k < pow10.length; ++k) {
            pow10[k] = 10 * pow10[k - 1];
        }
    }

    private Powers() {
    }

    /**
     * Powers of 5, for exponents between 0 and {@link #MAX_POW_5_N_EXP}.
     */
    static synchronized Natural pow5(int e) {
        if (pow5n[e] != null) {
            return pow5n[e];
        }
        int e0 = e / MAX_POW_5_EXP * MAX_POW_5_EXP;
        /*
        Guard for the loop: mathematically not necessary but measurably
        enhances performance when there's no need to enter the loop.
         */
        if (e0max < e0) {
            for (; e0max < e0; e0max += MAX_POW_5_EXP) {
                pow5n[e0max + MAX_POW_5_EXP] = pow5n[e0max].multiply(MAX_POW_5);
            }
        }
        if (e0 < e) {
            pow5n[e] = pow5n[e0].multiply(pow5[e - e0]);
        }
        return pow5n[e];
    }

}
