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

import java.text.DecimalFormat;
import java.util.Random;

/*
Some simple benchmarks to evaluate speeds.
 */
public class D2DBenchmark {

    private static final int N = 100_000_000;
    private static final double[] x = new double[N];
    private static final DecimalFormat intFormat =
            new DecimalFormat("#,##0");
    private static final DecimalFormat doubleFormat =
            new DecimalFormat("#,##0.000");
    private static final int RUNS_PER_LIB = 3;
    private static Random r;
    private static long d2dNs;
    private static long jdkNs;

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("arguments");
            System.out.println("  [ <seed> ]");
            System.out.println();
        }
        Long seed = args.length > 0 ? Long.parseLong(args[0]) : null;
        r = seed != null ? new Random(seed) : new Random();
        micro();
        milli();
        integers();
        nonNaNRange();
    }

    private static void benchmark() {
        d2dNs = jdkNs = 0;
        for (int i = 1; i <= RUNS_PER_LIB; ++i) {
            benchmarkD2d(i);
        }
        for (int i = 1; i <= RUNS_PER_LIB; ++i) {
            benchmarkJdk(i);
        }
        printSpeedup();
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
                + " integer random doubles... ");
        System.out.flush();
        for (int i = 0; i < x.length; ++i) {
            x[i] = r.nextInt();
        }
        System.out.println("finished");
    }

    private static void prepareMilli() {
        System.out.print("generating " + intFormat.format(x.length)
                + " \"milli\" random doubles... ");
        System.out.flush();
        for (int i = 0; i < x.length; ++i) {
            x[i] = r.nextInt() / 1e3;
        }
        System.out.println("finished");
    }

    private static void prepareMicro() {
        System.out.print("generating " + intFormat.format(x.length)
                + " \"micro\" random doubles... ");
        System.out.flush();
        for (int i = 0; i < x.length; ++i) {
            x[i] = r.nextInt() / 1e6;
        }
        System.out.println("finished");
    }

    private static void prepareNonNaNDoubles() {
        System.out.print("generating " + intFormat.format(x.length)
                + " non NaN random doubles... ");
        System.out.flush();
        int i = 0;
        while (i < x.length) {
            double v = Double.longBitsToDouble(r.nextLong());
            if (v == v) {
                x[i++] = v;
            }
        }
        System.out.println("finished");
    }

    private static void benchmarkJdk(int take) {
        long tot = 0;
        long begin = System.nanoTime();
        for (double v : x) {
            tot += Double.toString(v).length();
        }
        long ns = System.nanoTime() - begin;
        jdkNs += ns;
        print("java.lang.Double",take, ns, tot);
    }

    private static void benchmarkD2d(int take) {
        long tot = 0;
        long begin = System.nanoTime();
        for (double v : x) {
            tot += DoubleToDecimal.toString(v).length();
        }
        long ns = System.nanoTime() - begin;
        d2dNs += ns;
        print("math.DoubleToDecimal", take, ns, tot);
    }

    private static void print(String lib, int take, long ns, long tot) {
        System.out.println(lib + "[" + take + "/" + RUNS_PER_LIB + "]");
        System.out.println("--------");
        System.out.println("n=" + intFormat.format(x.length));
        System.out.println("elapsed=" + intFormat.format(ns) + " ns");
        System.out.println(intFormat.format(ns / x.length) + " ns/rendering");
        System.out.println("total length of output=" + intFormat.format(tot));
        System.out.println();
    }

    private static void printSpeedup() {
        System.out.println("speedup factor=" +
                doubleFormat.format((double) jdkNs / (double) d2dNs));
        System.out.println();
    }

}
