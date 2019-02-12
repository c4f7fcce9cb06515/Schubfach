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

class FloatToStringChecker extends StringChecker {

    private float v;

    FloatToStringChecker(float v, String s) {
        super(s);
        this.v = v;
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
    boolean recovers(String s) {
        return Float.parseFloat(s) == v;
    }

    @Override
    int maxExp() {
        return 39;
    }

    @Override
    int minExp() {
        return -44;
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
    void invert() {
        v = -v;
    }

    @Override
    boolean isNegative() {
        return Float.floatToRawIntBits(v) < 0;
    }

    @Override
    boolean isNaN() {
        return Float.isNaN(v);
    }

}
