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
        return v != v;
    }

}
