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
    private static final int ARRAYS_PER_MAIN = 3;
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
