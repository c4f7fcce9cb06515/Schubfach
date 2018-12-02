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

package bench;

import math.FloatToDecimal;

import java.text.DecimalFormat;
import java.util.Random;

import static java.lang.Math.rint;

/*
Some simple benchmarks to evaluate speeds.
 */
public class F2DBenchmark {

    private static final int N = 100_000_000;
    private static final float[] x = new float[N];
    private static final DecimalFormat intFormat =
            new DecimalFormat("#,##0");
    private static final int RUNS_PER_ARRAY = 5;
    private static final int ARRAYS_PER_MAIN = 5;
    private static Random r;

    private static final boolean CHECK_BACK_CONVERSION = false;

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("arguments");
            System.out.println("  [ <seed> ]");
            System.out.println();
        }
        Long seed = args.length > 0 ? Long.parseLong(args[0]) : null;
        r = seed != null ? new Random(seed) : new Random();
//        micro();
//        milli();
//        integers();
        for (int i = 0; i < ARRAYS_PER_MAIN; ++i)
            nonNaNRange();
    }

    private static void benchmark() {
        for (int i = 1; i <= RUNS_PER_ARRAY; ++i) {
            benchmarkD2d(i);
        }
    }

    private static void micro() {
        prepareMicro();
        benchmark();
    }

    private static void milli() {
        prepareMilli();
        benchmark();
    }

    private static void integers() {
        prepareIntegers();
        benchmark();
    }

    private static void nonNaNRange() {
        prepareNonNaNDoubles();
        benchmark();
    }

    private static void prepareIntegers() {
        System.out.print("generating " + intFormat.format(x.length)
                + " integer random floats... ");
        System.out.flush();
        for (int i = 0; i < x.length; ++i) {
            x[i] = r.nextInt();
        }
        System.out.println("finished");
    }

    private static void prepareMilli() {
        System.out.print("generating " + intFormat.format(x.length)
                + " \"milli\" random floats... ");
        System.out.flush();
        for (int i = 0; i < x.length; ++i) {
            x[i] = r.nextInt() / 1e3F;
        }
        System.out.println("finished");
    }

    private static void prepareMicro() {
        System.out.print("generating " + intFormat.format(x.length)
                + " \"micro\" random floats... ");
        System.out.flush();
        for (int i = 0; i < x.length; ++i) {
            x[i] = r.nextInt() / 1e6F;
        }
        System.out.println("finished");
    }

    private static void prepareNonNaNDoubles() {
        System.out.print("generating " + intFormat.format(x.length)
                + " non NaN random floats... ");
        System.out.flush();
        int i = 0;
        while (i < x.length) {
            float v = Float.intBitsToFloat(r.nextInt());
            if (v == v) {
                x[i++] = v;
            }
        }
        System.out.println("finished");
    }

    private static void benchmarkD2d(int take) {
        long tot = 0;
        long begin = System.nanoTime();
        for (float v : x) {
            String s = FloatToDecimal.toString(v);
            if (CHECK_BACK_CONVERSION) {
                if (v == v && Float.parseFloat(s) != v) {
                    throw new AssertionError(v + " " + s);
                }
            }
            tot += s.length();
        }
        long ns = System.nanoTime() - begin;
        print(take, ns, tot);
    }

    private static void print(int take, long ns, long tot) {
        System.out.println("[" + take + "/" + RUNS_PER_ARRAY + "]");
        System.out.println("--------");
        System.out.println("n=" + intFormat.format(x.length));
        System.out.println("elapsed=" + intFormat.format(ns) + " ns");
        System.out.println(intFormat.format((int) rint((double) ns / x.length)) + " ns/rendering");
        System.out.println("total length of output=" + intFormat.format(tot));
        System.out.println();
    }

}
