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

import static math.DoubleToDecimal.Double.c;
import static math.DoubleToDecimal.Double.q;

final class MathUtils {

    private static final int I = Integer.SIZE;
    private static final long MASK_I = (1L << I) - 1;

    /*
    The doubles below are expressed in hex notation to avoid possible anomalies
    during decimal tokenization. Hex tokenization is assumed to be completely
    reliable, as it is simpler from a mathematical perspective.
     */

    // The double closest to log10(2), 0.3010299956639812 in decimal
    private static final double LOG_10_2 = 0x1.34413509F79FFp-2;

    // The double closest to log2(10), 3.321928094887362 in decimal
    private static final double LOG_2_10 = 0x1.A934F0979A371p1;

    /**
     * The minimum exponent for {@link #floorPow10d(int)}
     * and {@link #pow10r(int)}
     */
    static final int MIN_EXP = -324;

    /**
     * The maximum exponent for {@link #floorPow10d(int)}
     * and {@link #pow10r(int)}
     */
    static final int MAX_EXP = 324;

    private MathUtils() {
    }

    /*
    This implementation is simple but is restricted to its usage here, when
    the assumptions below hold.

    It assumes
        v = 0 (thus c = 0) or 0 <= -q < Long.SIZE

    Also note that for v < 0
        floor(v) = -ceil(-v)
     */
    private static int floor(double v) {
        if (v >= 0) {
            long bits = java.lang.Double.doubleToRawLongBits(v);
            return (int) (c(bits) >>> -q(bits));
        }
        long bits = java.lang.Double.doubleToRawLongBits(-v);
        int q = q(bits);
        return -(int) (c(bits) + (1L << -q) - 1 >>> -q);
    }

    /**
     * Returns the integer <i>k</i> such that 10<sup><i>k</i>-1</sup> &#x2264;
     * 2<sup>{@code e}</sup> &#x3c; 10<sup><i>k</i></sup>.
     * <p>
     * The result is correct when -198'096'464 &#x2264; {@code e} &#x2264;
     * 146'964'307.
     * Otherwise the result may or may not be correct.
     */
    static int ord10pow2(int e) {
        return floor(e * LOG_10_2) + 1;
    }

    /**
     * Returns the integer <i>k</i> such that 2<sup><i>k</i>-1</sup> &#x2264;
     * 10<sup>{@code e}</sup> &#x3c; 2<sup><i>k</i></sup>.
     * <p>
     * The result is correct when -44'240'664 &#x2264; {@code e} &#x2264;
     * 59'632'977.
     * Otherwise the result may or may not be correct.
     */
    static int ord2pow10(int e) {
        return floor(e * LOG_2_10) + 1;
    }

    /**
     * Returns the high {@link Long#SIZE} bits of the full product
     * {@code x}{@code y}, namely
     * &#x23a3;{@code x}{@code y} &#xb7; 2<sup>-{@link Long#SIZE}</sup>&#x23a6;.
     * <p>
     * Both {@code x} and {@code y} as well as the result are interpreted as
     * unsigned {@code long}s.
     */
    static long multiplyHighUnsigned(long x, long y) {
        /*
        Unfortunately, the plain version of Karatsuba cannot be applied here:
        the mixed product would overflow with unrecoverable loss of bits.
        Thus, the plain paper-and-pencil scheme requiring 4 long
        multiplications is used.

        This could be a good candidate a for JIT compiler intrinsic.
         */
        final long x1 = x >>> I;
        final long x0 = x & MASK_I;
        final long y1 = y >>> I;
        final long y0 = y & MASK_I;
        final long x1y0 = x1 * y0;
        final long x0y1 = x0 * y1;
        return x1 * y1 +
                (x0y1 >>> I) +
                (x1y0 >>> I) +
                ((x0y1 & MASK_I) + (x1y0 & MASK_I) + (x0 * y0 >>> I) >>> I);
    }

    /**
     * Returns one of two components of an approximation of a power of 10.
     *
     * <p>More precisely, let
     * 10<sup>{@code e}</sup> = <i>d</i> &#xb7; 2<sup><i>r</i></sup>
     * for some integer <i>r</i> and real <i>d</i> with
     * 2<sup>{@link Long#SIZE}-1</sup> &#x2264; <i>d</i> &#x3c;
     * 2<sup>{@link Long#SIZE}</sup>.
     *
     * <p>This method returns &#x23a3;<i>d</i>&#x23a6; as an
     * unsigned {@code long}, while {@link #pow10r(int)} returns <i>r</i>.
     *
     * @param e  The exponent of the power of 10, bounded by
     *           {@link #MIN_EXP} &#x2264; {@code e} &#x2264; {@link #MAX_EXP}
     * @see #pow10r(int)
     */
    static long floorPow10d(int e) {
        return floorPow10d[e - MIN_EXP];
    }

    /**
     * Returns one of two components of an approximation of a power of 10.
     *
     * <p>This method returns <i>r</i> from the representation described in
     * {@link #floorPow10d(int)}.
     *
     * @param e  The exponent of the power of 10, bounded by
     *           {@link #MIN_EXP} &#x2264; {@code e} &#x2264; {@link #MAX_EXP}
     * @see #floorPow10d(int)
     */
    static int pow10r(int e) {
        return ord2pow10(e) - Long.SIZE;
    }

    /*
    The array has been computed and checked using full precision.
    The values are prefixed with a comment indicating the exponent.

    Contrary to common coding conventions, its definition is located here,
    at the end of the file, because the length of its source would be
    distracting for reading the rest.
     */
    private static final long[] floorPow10d = {
        /* -324 */ 0xCF42_894A_5DCE_35EAL,
        /* -323 */ 0x8189_95CE_7AA0_E1B2L,
        /* -322 */ 0xA1EB_FB42_1949_1A1FL,
        /* -321 */ 0xCA66_FA12_9F9B_60A6L,
        /* -320 */ 0xFD00_B897_4782_38D0L,
        /* -319 */ 0x9E20_735E_8CB1_6382L,
        /* -318 */ 0xC5A8_9036_2FDD_BC62L,
        /* -317 */ 0xF712_B443_BBD5_2B7BL,
        /* -316 */ 0x9A6B_B0AA_5565_3B2DL,
        /* -315 */ 0xC106_9CD4_EABE_89F8L,
        /* -314 */ 0xF148_440A_256E_2C76L,
        /* -313 */ 0x96CD_2A86_5764_DBCAL,
        /* -312 */ 0xBC80_7527_ED3E_12BCL,
        /* -311 */ 0xEBA0_9271_E88D_976BL,
        /* -310 */ 0x9344_5B87_3158_7EA3L,
        /* -309 */ 0xB815_7268_FDAE_9E4CL,
        /* -308 */ 0xE61A_CF03_3D1A_45DFL,
        /* -307 */ 0x8FD0_C162_0630_6BABL,
        /* -306 */ 0xB3C4_F1BA_87BC_8696L,
        /* -305 */ 0xE0B6_2E29_29AB_A83CL,
        /* -304 */ 0x8C71_DCD9_BA0B_4925L,
        /* -303 */ 0xAF8E_5410_288E_1B6FL,
        /* -302 */ 0xDB71_E914_32B1_A24AL,
        /* -301 */ 0x8927_31AC_9FAF_056EL,
        /* -300 */ 0xAB70_FE17_C79A_C6CAL,
        /* -299 */ 0xD64D_3D9D_B981_787DL,
        /* -298 */ 0x85F0_4682_93F0_EB4EL,
        /* -297 */ 0xA76C_5823_38ED_2621L,
        /* -296 */ 0xD147_6E2C_0728_6FAAL,
        /* -295 */ 0x82CC_A4DB_8479_45CAL,
        /* -294 */ 0xA37F_CE12_6597_973CL,
        /* -293 */ 0xCC5F_C196_FEFD_7D0CL,
        /* -292 */ 0xFF77_B1FC_BEBC_DC4FL,
        /* -291 */ 0x9FAA_CF3D_F736_09B1L,
        /* -290 */ 0xC795_830D_7503_8C1DL,
        /* -289 */ 0xF97A_E3D0_D244_6F25L,
        /* -288 */ 0x9BEC_CE62_836A_C577L,
        /* -287 */ 0xC2E8_01FB_2445_76D5L,
        /* -286 */ 0xF3A2_0279_ED56_D48AL,
        /* -285 */ 0x9845_418C_3456_44D6L,
        /* -284 */ 0xBE56_91EF_416B_D60CL,
        /* -283 */ 0xEDEC_366B_11C6_CB8FL,
        /* -282 */ 0x94B3_A202_EB1C_3F39L,
        /* -281 */ 0xB9E0_8A83_A5E3_4F07L,
        /* -280 */ 0xE858_AD24_8F5C_22C9L,
        /* -279 */ 0x9137_6C36_D999_95BEL,
        /* -278 */ 0xB585_4744_8FFF_FB2DL,
        /* -277 */ 0xE2E6_9915_B3FF_F9F9L,
        /* -276 */ 0x8DD0_1FAD_907F_FC3BL,
        /* -275 */ 0xB144_2798_F49F_FB4AL,
        /* -274 */ 0xDD95_317F_31C7_FA1DL,
        /* -273 */ 0x8A7D_3EEF_7F1C_FC52L,
        /* -272 */ 0xAD1C_8EAB_5EE4_3B66L,
        /* -271 */ 0xD863_B256_369D_4A40L,
        /* -270 */ 0x873E_4F75_E222_4E68L,
        /* -269 */ 0xA90D_E353_5AAA_E202L,
        /* -268 */ 0xD351_5C28_3155_9A83L,
        /* -267 */ 0x8412_D999_1ED5_8091L,
        /* -266 */ 0xA517_8FFF_668A_E0B6L,
        /* -265 */ 0xCE5D_73FF_402D_98E3L,
        /* -264 */ 0x80FA_687F_881C_7F8EL,
        /* -263 */ 0xA139_029F_6A23_9F72L,
        /* -262 */ 0xC987_4347_44AC_874EL,
        /* -261 */ 0xFBE9_1419_15D7_A922L,
        /* -260 */ 0x9D71_AC8F_ADA6_C9B5L,
        /* -259 */ 0xC4CE_17B3_9910_7C22L,
        /* -258 */ 0xF601_9DA0_7F54_9B2BL,
        /* -257 */ 0x99C1_0284_4F94_E0FBL,
        /* -256 */ 0xC031_4325_637A_1939L,
        /* -255 */ 0xF03D_93EE_BC58_9F88L,
        /* -254 */ 0x9626_7C75_35B7_63B5L,
        /* -253 */ 0xBBB0_1B92_8325_3CA2L,
        /* -252 */ 0xEA9C_2277_23EE_8BCBL,
        /* -251 */ 0x92A1_958A_7675_175FL,
        /* -250 */ 0xB749_FAED_1412_5D36L,
        /* -249 */ 0xE51C_79A8_5916_F484L,
        /* -248 */ 0x8F31_CC09_37AE_58D2L,
        /* -247 */ 0xB2FE_3F0B_8599_EF07L,
        /* -246 */ 0xDFBD_CECE_6700_6AC9L,
        /* -245 */ 0x8BD6_A141_0060_42BDL,
        /* -244 */ 0xAECC_4991_4078_536DL,
        /* -243 */ 0xDA7F_5BF5_9096_6848L,
        /* -242 */ 0x888F_9979_7A5E_012DL,
        /* -241 */ 0xAAB3_7FD7_D8F5_8178L,
        /* -240 */ 0xD560_5FCD_CF32_E1D6L,
        /* -239 */ 0x855C_3BE0_A17F_CD26L,
        /* -238 */ 0xA6B3_4AD8_C9DF_C06FL,
        /* -237 */ 0xD060_1D8E_FC57_B08BL,
        /* -236 */ 0x823C_1279_5DB6_CE57L,
        /* -235 */ 0xA2CB_1717_B524_81EDL,
        /* -234 */ 0xCB7D_DCDD_A26D_A268L,
        /* -233 */ 0xFE5D_5415_0B09_0B02L,
        /* -232 */ 0x9EFA_548D_26E5_A6E1L,
        /* -231 */ 0xC6B8_E9B0_709F_109AL,
        /* -230 */ 0xF867_241C_8CC6_D4C0L,
        /* -229 */ 0x9B40_7691_D7FC_44F8L,
        /* -228 */ 0xC210_9436_4DFB_5636L,
        /* -227 */ 0xF294_B943_E17A_2BC4L,
        /* -226 */ 0x979C_F3CA_6CEC_5B5AL,
        /* -225 */ 0xBD84_30BD_0827_7231L,
        /* -224 */ 0xECE5_3CEC_4A31_4EBDL,
        /* -223 */ 0x940F_4613_AE5E_D136L,
        /* -222 */ 0xB913_1798_99F6_8584L,
        /* -221 */ 0xE757_DD7E_C074_26E5L,
        /* -220 */ 0x9096_EA6F_3848_984FL,
        /* -219 */ 0xB4BC_A50B_065A_BE63L,
        /* -218 */ 0xE1EB_CE4D_C7F1_6DFBL,
        /* -217 */ 0x8D33_60F0_9CF6_E4BDL,
        /* -216 */ 0xB080_392C_C434_9DECL,
        /* -215 */ 0xDCA0_4777_F541_C567L,
        /* -214 */ 0x89E4_2CAA_F949_1B60L,
        /* -213 */ 0xAC5D_37D5_B79B_6239L,
        /* -212 */ 0xD774_85CB_2582_3AC7L,
        /* -211 */ 0x86A8_D39E_F771_64BCL,
        /* -210 */ 0xA853_0886_B54D_BDEBL,
        /* -209 */ 0xD267_CAA8_62A1_2D66L,
        /* -208 */ 0x8380_DEA9_3DA4_BC60L,
        /* -207 */ 0xA461_1653_8D0D_EB78L,
        /* -206 */ 0xCD79_5BE8_7051_6656L,
        /* -205 */ 0x806B_D971_4632_DFF6L,
        /* -204 */ 0xA086_CFCD_97BF_97F3L,
        /* -203 */ 0xC8A8_83C0_FDAF_7DF0L,
        /* -202 */ 0xFAD2_A4B1_3D1B_5D6CL,
        /* -201 */ 0x9CC3_A6EE_C631_1A63L,
        /* -200 */ 0xC3F4_90AA_77BD_60FCL,
        /* -199 */ 0xF4F1_B4D5_15AC_B93BL,
        /* -198 */ 0x9917_1105_2D8B_F3C5L,
        /* -197 */ 0xBF5C_D546_78EE_F0B6L,
        /* -196 */ 0xEF34_0A98_172A_ACE4L,
        /* -195 */ 0x9580_869F_0E7A_AC0EL,
        /* -194 */ 0xBAE0_A846_D219_5712L,
        /* -193 */ 0xE998_D258_869F_ACD7L,
        /* -192 */ 0x91FF_8377_5423_CC06L,
        /* -191 */ 0xB67F_6455_292C_BF08L,
        /* -190 */ 0xE41F_3D6A_7377_EECAL,
        /* -189 */ 0x8E93_8662_882A_F53EL,
        /* -188 */ 0xB238_67FB_2A35_B28DL,
        /* -187 */ 0xDEC6_81F9_F4C3_1F31L,
        /* -186 */ 0x8B3C_113C_38F9_F37EL,
        /* -185 */ 0xAE0B_158B_4738_705EL,
        /* -184 */ 0xD98D_DAEE_1906_8C76L,
        /* -183 */ 0x87F8_A8D4_CFA4_17C9L,
        /* -182 */ 0xA9F6_D30A_038D_1DBCL,
        /* -181 */ 0xD474_87CC_8470_652BL,
        /* -180 */ 0x84C8_D4DF_D2C6_3F3BL,
        /* -179 */ 0xA5FB_0A17_C777_CF09L,
        /* -178 */ 0xCF79_CC9D_B955_C2CCL,
        /* -177 */ 0x81AC_1FE2_93D5_99BFL,
        /* -176 */ 0xA217_27DB_38CB_002FL,
        /* -175 */ 0xCA9C_F1D2_06FD_C03BL,
        /* -174 */ 0xFD44_2E46_88BD_304AL,
        /* -173 */ 0x9E4A_9CEC_1576_3E2EL,
        /* -172 */ 0xC5DD_4427_1AD3_CDBAL,
        /* -171 */ 0xF754_9530_E188_C128L,
        /* -170 */ 0x9A94_DD3E_8CF5_78B9L,
        /* -169 */ 0xC13A_148E_3032_D6E7L,
        /* -168 */ 0xF188_99B1_BC3F_8CA1L,
        /* -167 */ 0x96F5_600F_15A7_B7E5L,
        /* -166 */ 0xBCB2_B812_DB11_A5DEL,
        /* -165 */ 0xEBDF_6617_91D6_0F56L,
        /* -164 */ 0x936B_9FCE_BB25_C995L,
        /* -163 */ 0xB846_87C2_69EF_3BFBL,
        /* -162 */ 0xE658_29B3_046B_0AFAL,
        /* -161 */ 0x8FF7_1A0F_E2C2_E6DCL,
        /* -160 */ 0xB3F4_E093_DB73_A093L,
        /* -159 */ 0xE0F2_18B8_D250_88B8L,
        /* -158 */ 0x8C97_4F73_8372_5573L,
        /* -157 */ 0xAFBD_2350_644E_EACFL,
        /* -156 */ 0xDBAC_6C24_7D62_A583L,
        /* -155 */ 0x894B_C396_CE5D_A772L,
        /* -154 */ 0xAB9E_B47C_81F5_114FL,
        /* -153 */ 0xD686_619B_A272_55A2L,
        /* -152 */ 0x8613_FD01_4587_7585L,
        /* -151 */ 0xA798_FC41_96E9_52E7L,
        /* -150 */ 0xD17F_3B51_FCA3_A7A0L,
        /* -149 */ 0x82EF_8513_3DE6_48C4L,
        /* -148 */ 0xA3AB_6658_0D5F_DAF5L,
        /* -147 */ 0xCC96_3FEE_10B7_D1B3L,
        /* -146 */ 0xFFBB_CFE9_94E5_C61FL,
        /* -145 */ 0x9FD5_61F1_FD0F_9BD3L,
        /* -144 */ 0xC7CA_BA6E_7C53_82C8L,
        /* -143 */ 0xF9BD_690A_1B68_637BL,
        /* -142 */ 0x9C16_61A6_5121_3E2DL,
        /* -141 */ 0xC31B_FA0F_E569_8DB8L,
        /* -140 */ 0xF3E2_F893_DEC3_F126L,
        /* -139 */ 0x986D_DB5C_6B3A_76B7L,
        /* -138 */ 0xBE89_5233_8609_1465L,
        /* -137 */ 0xEE2B_A6C0_678B_597FL,
        /* -136 */ 0x94DB_4838_40B7_17EFL,
        /* -135 */ 0xBA12_1A46_50E4_DDEBL,
        /* -134 */ 0xE896_A0D7_E51E_1566L,
        /* -133 */ 0x915E_2486_EF32_CD60L,
        /* -132 */ 0xB5B5_ADA8_AAFF_80B8L,
        /* -131 */ 0xE323_1912_D5BF_60E6L,
        /* -130 */ 0x8DF5_EFAB_C597_9C8FL,
        /* -129 */ 0xB173_6B96_B6FD_83B3L,
        /* -128 */ 0xDDD0_467C_64BC_E4A0L,
        /* -127 */ 0x8AA2_2C0D_BEF6_0EE4L,
        /* -126 */ 0xAD4A_B711_2EB3_929DL,
        /* -125 */ 0xD89D_64D5_7A60_7744L,
        /* -124 */ 0x8762_5F05_6C7C_4A8BL,
        /* -123 */ 0xA93A_F6C6_C79B_5D2DL,
        /* -122 */ 0xD389_B478_7982_3479L,
        /* -121 */ 0x8436_10CB_4BF1_60CBL,
        /* -120 */ 0xA543_94FE_1EED_B8FEL,
        /* -119 */ 0xCE94_7A3D_A6A9_273EL,
        /* -118 */ 0x811C_CC66_8829_B887L,
        /* -117 */ 0xA163_FF80_2A34_26A8L,
        /* -116 */ 0xC9BC_FF60_34C1_3052L,
        /* -115 */ 0xFC2C_3F38_41F1_7C67L,
        /* -114 */ 0x9D9B_A783_2936_EDC0L,
        /* -113 */ 0xC502_9163_F384_A931L,
        /* -112 */ 0xF643_35BC_F065_D37DL,
        /* -111 */ 0x99EA_0196_163F_A42EL,
        /* -110 */ 0xC064_81FB_9BCF_8D39L,
        /* -109 */ 0xF07D_A27A_82C3_7088L,
        /* -108 */ 0x964E_858C_91BA_2655L,
        /* -107 */ 0xBBE2_26EF_B628_AFEAL,
        /* -106 */ 0xEADA_B0AB_A3B2_DBE5L,
        /* -105 */ 0x92C8_AE6B_464F_C96FL,
        /* -104 */ 0xB77A_DA06_17E3_BBCBL,
        /* -103 */ 0xE559_9087_9DDC_AABDL,
        /* -102 */ 0x8F57_FA54_C2A9_EAB6L,
        /* -101 */ 0xB32D_F8E9_F354_6564L,
        /* -100 */ 0xDFF9_7724_7029_7EBDL,
        /*  -99 */ 0x8BFB_EA76_C619_EF36L,
        /*  -98 */ 0xAEFA_E514_77A0_6B03L,
        /*  -97 */ 0xDAB9_9E59_9588_85C4L,
        /*  -96 */ 0x88B4_02F7_FD75_539BL,
        /*  -95 */ 0xAAE1_03B5_FCD2_A881L,
        /*  -94 */ 0xD599_44A3_7C07_52A2L,
        /*  -93 */ 0x857F_CAE6_2D84_93A5L,
        /*  -92 */ 0xA6DF_BD9F_B8E5_B88EL,
        /*  -91 */ 0xD097_AD07_A71F_26B2L,
        /*  -90 */ 0x825E_CC24_C873_782FL,
        /*  -89 */ 0xA2F6_7F2D_FA90_563BL,
        /*  -88 */ 0xCBB4_1EF9_7934_6BCAL,
        /*  -87 */ 0xFEA1_26B7_D781_86BCL,
        /*  -86 */ 0x9F24_B832_E6B0_F436L,
        /*  -85 */ 0xC6ED_E63F_A05D_3143L,
        /*  -84 */ 0xF8A9_5FCF_8874_7D94L,
        /*  -83 */ 0x9B69_DBE1_B548_CE7CL,
        /*  -82 */ 0xC244_52DA_229B_021BL,
        /*  -81 */ 0xF2D5_6790_AB41_C2A2L,
        /*  -80 */ 0x97C5_60BA_6B09_19A5L,
        /*  -79 */ 0xBDB6_B8E9_05CB_600FL,
        /*  -78 */ 0xED24_6723_473E_3813L,
        /*  -77 */ 0x9436_C076_0C86_E30BL,
        /*  -76 */ 0xB944_7093_8FA8_9BCEL,
        /*  -75 */ 0xE795_8CB8_7392_C2C2L,
        /*  -74 */ 0x90BD_77F3_483B_B9B9L,
        /*  -73 */ 0xB4EC_D5F0_1A4A_A828L,
        /*  -72 */ 0xE228_0B6C_20DD_5232L,
        /*  -71 */ 0x8D59_0723_948A_535FL,
        /*  -70 */ 0xB0AF_48EC_79AC_E837L,
        /*  -69 */ 0xDCDB_1B27_9818_2244L,
        /*  -68 */ 0x8A08_F0F8_BF0F_156BL,
        /*  -67 */ 0xAC8B_2D36_EED2_DAC5L,
        /*  -66 */ 0xD7AD_F884_AA87_9177L,
        /*  -65 */ 0x86CC_BB52_EA94_BAEAL,
        /*  -64 */ 0xA87F_EA27_A539_E9A5L,
        /*  -63 */ 0xD29F_E4B1_8E88_640EL,
        /*  -62 */ 0x83A3_EEEE_F915_3E89L,
        /*  -61 */ 0xA48C_EAAA_B75A_8E2BL,
        /*  -60 */ 0xCDB0_2555_6531_31B6L,
        /*  -59 */ 0x808E_1755_5F3E_BF11L,
        /*  -58 */ 0xA0B1_9D2A_B70E_6ED6L,
        /*  -57 */ 0xC8DE_0475_64D2_0A8BL,
        /*  -56 */ 0xFB15_8592_BE06_8D2EL,
        /*  -55 */ 0x9CED_737B_B6C4_183DL,
        /*  -54 */ 0xC428_D05A_A475_1E4CL,
        /*  -53 */ 0xF533_0471_4D92_65DFL,
        /*  -52 */ 0x993F_E2C6_D07B_7FABL,
        /*  -51 */ 0xBF8F_DB78_849A_5F96L,
        /*  -50 */ 0xEF73_D256_A5C0_F77CL,
        /*  -49 */ 0x95A8_6376_2798_9AADL,
        /*  -48 */ 0xBB12_7C53_B17E_C159L,
        /*  -47 */ 0xE9D7_1B68_9DDE_71AFL,
        /*  -46 */ 0x9226_7121_62AB_070DL,
        /*  -45 */ 0xB6B0_0D69_BB55_C8D1L,
        /*  -44 */ 0xE45C_10C4_2A2B_3B05L,
        /*  -43 */ 0x8EB9_8A7A_9A5B_04E3L,
        /*  -42 */ 0xB267_ED19_40F1_C61CL,
        /*  -41 */ 0xDF01_E85F_912E_37A3L,
        /*  -40 */ 0x8B61_313B_BABC_E2C6L,
        /*  -39 */ 0xAE39_7D8A_A96C_1B77L,
        /*  -38 */ 0xD9C7_DCED_53C7_2255L,
        /*  -37 */ 0x881C_EA14_545C_7575L,
        /*  -36 */ 0xAA24_2499_6973_92D2L,
        /*  -35 */ 0xD4AD_2DBF_C3D0_7787L,
        /*  -34 */ 0x84EC_3C97_DA62_4AB4L,
        /*  -33 */ 0xA627_4BBD_D0FA_DD61L,
        /*  -32 */ 0xCFB1_1EAD_4539_94BAL,
        /*  -31 */ 0x81CE_B32C_4B43_FCF4L,
        /*  -30 */ 0xA242_5FF7_5E14_FC31L,
        /*  -29 */ 0xCAD2_F7F5_359A_3B3EL,
        /*  -28 */ 0xFD87_B5F2_8300_CA0DL,
        /*  -27 */ 0x9E74_D1B7_91E0_7E48L,
        /*  -26 */ 0xC612_0625_7658_9DDAL,
        /*  -25 */ 0xF796_87AE_D3EE_C551L,
        /*  -24 */ 0x9ABE_14CD_4475_3B52L,
        /*  -23 */ 0xC16D_9A00_9592_8A27L,
        /*  -22 */ 0xF1C9_0080_BAF7_2CB1L,
        /*  -21 */ 0x971D_A050_74DA_7BEEL,
        /*  -20 */ 0xBCE5_0864_9211_1AEAL,
        /*  -19 */ 0xEC1E_4A7D_B695_61A5L,
        /*  -18 */ 0x9392_EE8E_921D_5D07L,
        /*  -17 */ 0xB877_AA32_36A4_B449L,
        /*  -16 */ 0xE695_94BE_C44D_E15BL,
        /*  -15 */ 0x901D_7CF7_3AB0_ACD9L,
        /*  -14 */ 0xB424_DC35_095C_D80FL,
        /*  -13 */ 0xE12E_1342_4BB4_0E13L,
        /*  -12 */ 0x8CBC_CC09_6F50_88CBL,
        /*  -11 */ 0xAFEB_FF0B_CB24_AAFEL,
        /*  -10 */ 0xDBE6_FECE_BDED_D5BEL,
        /*   -9 */ 0x8970_5F41_36B4_A597L,
        /*   -8 */ 0xABCC_7711_8461_CEFCL,
        /*   -7 */ 0xD6BF_94D5_E57A_42BCL,
        /*   -6 */ 0x8637_BD05_AF6C_69B5L,
        /*   -5 */ 0xA7C5_AC47_1B47_8423L,
        /*   -4 */ 0xD1B7_1758_E219_652BL,
        /*   -3 */ 0x8312_6E97_8D4F_DF3BL,
        /*   -2 */ 0xA3D7_0A3D_70A3_D70AL,
        /*   -1 */ 0xCCCC_CCCC_CCCC_CCCCL,
        /*    0 */ 0x8000_0000_0000_0000L,
        /*    1 */ 0xA000_0000_0000_0000L,
        /*    2 */ 0xC800_0000_0000_0000L,
        /*    3 */ 0xFA00_0000_0000_0000L,
        /*    4 */ 0x9C40_0000_0000_0000L,
        /*    5 */ 0xC350_0000_0000_0000L,
        /*    6 */ 0xF424_0000_0000_0000L,
        /*    7 */ 0x9896_8000_0000_0000L,
        /*    8 */ 0xBEBC_2000_0000_0000L,
        /*    9 */ 0xEE6B_2800_0000_0000L,
        /*   10 */ 0x9502_F900_0000_0000L,
        /*   11 */ 0xBA43_B740_0000_0000L,
        /*   12 */ 0xE8D4_A510_0000_0000L,
        /*   13 */ 0x9184_E72A_0000_0000L,
        /*   14 */ 0xB5E6_20F4_8000_0000L,
        /*   15 */ 0xE35F_A931_A000_0000L,
        /*   16 */ 0x8E1B_C9BF_0400_0000L,
        /*   17 */ 0xB1A2_BC2E_C500_0000L,
        /*   18 */ 0xDE0B_6B3A_7640_0000L,
        /*   19 */ 0x8AC7_2304_89E8_0000L,
        /*   20 */ 0xAD78_EBC5_AC62_0000L,
        /*   21 */ 0xD8D7_26B7_177A_8000L,
        /*   22 */ 0x8786_7832_6EAC_9000L,
        /*   23 */ 0xA968_163F_0A57_B400L,
        /*   24 */ 0xD3C2_1BCE_CCED_A100L,
        /*   25 */ 0x8459_5161_4014_84A0L,
        /*   26 */ 0xA56F_A5B9_9019_A5C8L,
        /*   27 */ 0xCECB_8F27_F420_0F3AL,
        /*   28 */ 0x813F_3978_F894_0984L,
        /*   29 */ 0xA18F_07D7_36B9_0BE5L,
        /*   30 */ 0xC9F2_C9CD_0467_4EDEL,
        /*   31 */ 0xFC6F_7C40_4581_2296L,
        /*   32 */ 0x9DC5_ADA8_2B70_B59DL,
        /*   33 */ 0xC537_1912_364C_E305L,
        /*   34 */ 0xF684_DF56_C3E0_1BC6L,
        /*   35 */ 0x9A13_0B96_3A6C_115CL,
        /*   36 */ 0xC097_CE7B_C907_15B3L,
        /*   37 */ 0xF0BD_C21A_BB48_DB20L,
        /*   38 */ 0x9676_9950_B50D_88F4L,
        /*   39 */ 0xBC14_3FA4_E250_EB31L,
        /*   40 */ 0xEB19_4F8E_1AE5_25FDL,
        /*   41 */ 0x92EF_D1B8_D0CF_37BEL,
        /*   42 */ 0xB7AB_C627_0503_05ADL,
        /*   43 */ 0xE596_B7B0_C643_C719L,
        /*   44 */ 0x8F7E_32CE_7BEA_5C6FL,
        /*   45 */ 0xB35D_BF82_1AE4_F38BL,
        /*   46 */ 0xE035_2F62_A19E_306EL,
        /*   47 */ 0x8C21_3D9D_A502_DE45L,
        /*   48 */ 0xAF29_8D05_0E43_95D6L,
        /*   49 */ 0xDAF3_F046_51D4_7B4CL,
        /*   50 */ 0x88D8_762B_F324_CD0FL,
        /*   51 */ 0xAB0E_93B6_EFEE_0053L,
        /*   52 */ 0xD5D2_38A4_ABE9_8068L,
        /*   53 */ 0x85A3_6366_EB71_F041L,
        /*   54 */ 0xA70C_3C40_A64E_6C51L,
        /*   55 */ 0xD0CF_4B50_CFE2_0765L,
        /*   56 */ 0x8281_8F12_81ED_449FL,
        /*   57 */ 0xA321_F2D7_2268_95C7L,
        /*   58 */ 0xCBEA_6F8C_EB02_BB39L,
        /*   59 */ 0xFEE5_0B70_25C3_6A08L,
        /*   60 */ 0x9F4F_2726_179A_2245L,
        /*   61 */ 0xC722_F0EF_9D80_AAD6L,
        /*   62 */ 0xF8EB_AD2B_84E0_D58BL,
        /*   63 */ 0x9B93_4C3B_330C_8577L,
        /*   64 */ 0xC278_1F49_FFCF_A6D5L,
        /*   65 */ 0xF316_271C_7FC3_908AL,
        /*   66 */ 0x97ED_D871_CFDA_3A56L,
        /*   67 */ 0xBDE9_4E8E_43D0_C8ECL,
        /*   68 */ 0xED63_A231_D4C4_FB27L,
        /*   69 */ 0x945E_455F_24FB_1CF8L,
        /*   70 */ 0xB975_D6B6_EE39_E436L,
        /*   71 */ 0xE7D3_4C64_A9C8_5D44L,
        /*   72 */ 0x90E4_0FBE_EA1D_3A4AL,
        /*   73 */ 0xB51D_13AE_A4A4_88DDL,
        /*   74 */ 0xE264_589A_4DCD_AB14L,
        /*   75 */ 0x8D7E_B760_70A0_8AECL,
        /*   76 */ 0xB0DE_6538_8CC8_ADA8L,
        /*   77 */ 0xDD15_FE86_AFFA_D912L,
        /*   78 */ 0x8A2D_BF14_2DFC_C7ABL,
        /*   79 */ 0xACB9_2ED9_397B_F996L,
        /*   80 */ 0xD7E7_7A8F_87DA_F7FBL,
        /*   81 */ 0x86F0_AC99_B4E8_DAFDL,
        /*   82 */ 0xA8AC_D7C0_2223_11BCL,
        /*   83 */ 0xD2D8_0DB0_2AAB_D62BL,
        /*   84 */ 0x83C7_088E_1AAB_65DBL,
        /*   85 */ 0xA4B8_CAB1_A156_3F52L,
        /*   86 */ 0xCDE6_FD5E_09AB_CF26L,
        /*   87 */ 0x80B0_5E5A_C60B_6178L,
        /*   88 */ 0xA0DC_75F1_778E_39D6L,
        /*   89 */ 0xC913_936D_D571_C84CL,
        /*   90 */ 0xFB58_7849_4ACE_3A5FL,
        /*   91 */ 0x9D17_4B2D_CEC0_E47BL,
        /*   92 */ 0xC45D_1DF9_4271_1D9AL,
        /*   93 */ 0xF574_6577_930D_6500L,
        /*   94 */ 0x9968_BF6A_BBE8_5F20L,
        /*   95 */ 0xBFC2_EF45_6AE2_76E8L,
        /*   96 */ 0xEFB3_AB16_C59B_14A2L,
        /*   97 */ 0x95D0_4AEE_3B80_ECE5L,
        /*   98 */ 0xBB44_5DA9_CA61_281FL,
        /*   99 */ 0xEA15_7514_3CF9_7226L,
        /*  100 */ 0x924D_692C_A61B_E758L,
        /*  101 */ 0xB6E0_C377_CFA2_E12EL,
        /*  102 */ 0xE498_F455_C38B_997AL,
        /*  103 */ 0x8EDF_98B5_9A37_3FECL,
        /*  104 */ 0xB297_7EE3_00C5_0FE7L,
        /*  105 */ 0xDF3D_5E9B_C0F6_53E1L,
        /*  106 */ 0x8B86_5B21_5899_F46CL,
        /*  107 */ 0xAE67_F1E9_AEC0_7187L,
        /*  108 */ 0xDA01_EE64_1A70_8DE9L,
        /*  109 */ 0x8841_34FE_9086_58B2L,
        /*  110 */ 0xAA51_823E_34A7_EEDEL,
        /*  111 */ 0xD4E5_E2CD_C1D1_EA96L,
        /*  112 */ 0x850F_ADC0_9923_329EL,
        /*  113 */ 0xA653_9930_BF6B_FF45L,
        /*  114 */ 0xCFE8_7F7C_EF46_FF16L,
        /*  115 */ 0x81F1_4FAE_158C_5F6EL,
        /*  116 */ 0xA26D_A399_9AEF_7749L,
        /*  117 */ 0xCB09_0C80_01AB_551CL,
        /*  118 */ 0xFDCB_4FA0_0216_2A63L,
        /*  119 */ 0x9E9F_11C4_014D_DA7EL,
        /*  120 */ 0xC646_D635_01A1_511DL,
        /*  121 */ 0xF7D8_8BC2_4209_A565L,
        /*  122 */ 0x9AE7_5759_6946_075FL,
        /*  123 */ 0xC1A1_2D2F_C397_8937L,
        /*  124 */ 0xF209_787B_B47D_6B84L,
        /*  125 */ 0x9745_EB4D_50CE_6332L,
        /*  126 */ 0xBD17_6620_A501_FBFFL,
        /*  127 */ 0xEC5D_3FA8_CE42_7AFFL,
        /*  128 */ 0x93BA_47C9_80E9_8CDFL,
        /*  129 */ 0xB8A8_D9BB_E123_F017L,
        /*  130 */ 0xE6D3_102A_D96C_EC1DL,
        /*  131 */ 0x9043_EA1A_C7E4_1392L,
        /*  132 */ 0xB454_E4A1_79DD_1877L,
        /*  133 */ 0xE16A_1DC9_D854_5E94L,
        /*  134 */ 0x8CE2_529E_2734_BB1DL,
        /*  135 */ 0xB01A_E745_B101_E9E4L,
        /*  136 */ 0xDC21_A117_1D42_645DL,
        /*  137 */ 0x8995_04AE_7249_7EBAL,
        /*  138 */ 0xABFA_45DA_0EDB_DE69L,
        /*  139 */ 0xD6F8_D750_9292_D603L,
        /*  140 */ 0x865B_8692_5B9B_C5C2L,
        /*  141 */ 0xA7F2_6836_F282_B732L,
        /*  142 */ 0xD1EF_0244_AF23_64FFL,
        /*  143 */ 0x8335_616A_ED76_1F1FL,
        /*  144 */ 0xA402_B9C5_A8D3_A6E7L,
        /*  145 */ 0xCD03_6837_1308_90A1L,
        /*  146 */ 0x8022_2122_6BE5_5A64L,
        /*  147 */ 0xA02A_A96B_06DE_B0FDL,
        /*  148 */ 0xC835_53C5_C896_5D3DL,
        /*  149 */ 0xFA42_A8B7_3ABB_F48CL,
        /*  150 */ 0x9C69_A972_84B5_78D7L,
        /*  151 */ 0xC384_13CF_25E2_D70DL,
        /*  152 */ 0xF465_18C2_EF5B_8CD1L,
        /*  153 */ 0x98BF_2F79_D599_3802L,
        /*  154 */ 0xBEEE_FB58_4AFF_8603L,
        /*  155 */ 0xEEAA_BA2E_5DBF_6784L,
        /*  156 */ 0x952A_B45C_FA97_A0B2L,
        /*  157 */ 0xBA75_6174_393D_88DFL,
        /*  158 */ 0xE912_B9D1_478C_EB17L,
        /*  159 */ 0x91AB_B422_CCB8_12EEL,
        /*  160 */ 0xB616_A12B_7FE6_17AAL,
        /*  161 */ 0xE39C_4976_5FDF_9D94L,
        /*  162 */ 0x8E41_ADE9_FBEB_C27DL,
        /*  163 */ 0xB1D2_1964_7AE6_B31CL,
        /*  164 */ 0xDE46_9FBD_99A0_5FE3L,
        /*  165 */ 0x8AEC_23D6_8004_3BEEL,
        /*  166 */ 0xADA7_2CCC_2005_4AE9L,
        /*  167 */ 0xD910_F7FF_2806_9DA4L,
        /*  168 */ 0x87AA_9AFF_7904_2286L,
        /*  169 */ 0xA995_41BF_5745_2B28L,
        /*  170 */ 0xD3FA_922F_2D16_75F2L,
        /*  171 */ 0x847C_9B5D_7C2E_09B7L,
        /*  172 */ 0xA59B_C234_DB39_8C25L,
        /*  173 */ 0xCF02_B2C2_1207_EF2EL,
        /*  174 */ 0x8161_AFB9_4B44_F57DL,
        /*  175 */ 0xA1BA_1BA7_9E16_32DCL,
        /*  176 */ 0xCA28_A291_859B_BF93L,
        /*  177 */ 0xFCB2_CB35_E702_AF78L,
        /*  178 */ 0x9DEF_BF01_B061_ADABL,
        /*  179 */ 0xC56B_AEC2_1C7A_1916L,
        /*  180 */ 0xF6C6_9A72_A398_9F5BL,
        /*  181 */ 0x9A3C_2087_A63F_6399L,
        /*  182 */ 0xC0CB_28A9_8FCF_3C7FL,
        /*  183 */ 0xF0FD_F2D3_F3C3_0B9FL,
        /*  184 */ 0x969E_B7C4_7859_E743L,
        /*  185 */ 0xBC46_65B5_9670_6114L,
        /*  186 */ 0xEB57_FF22_FC0C_7959L,
        /*  187 */ 0x9316_FF75_DD87_CBD8L,
        /*  188 */ 0xB7DC_BF53_54E9_BECEL,
        /*  189 */ 0xE5D3_EF28_2A24_2E81L,
        /*  190 */ 0x8FA4_7579_1A56_9D10L,
        /*  191 */ 0xB38D_92D7_60EC_4455L,
        /*  192 */ 0xE070_F78D_3927_556AL,
        /*  193 */ 0x8C46_9AB8_43B8_9562L,
        /*  194 */ 0xAF58_4166_54A6_BABBL,
        /*  195 */ 0xDB2E_51BF_E9D0_696AL,
        /*  196 */ 0x88FC_F317_F222_41E2L,
        /*  197 */ 0xAB3C_2FDD_EEAA_D25AL,
        /*  198 */ 0xD60B_3BD5_6A55_86F1L,
        /*  199 */ 0x85C7_0565_6275_7456L,
        /*  200 */ 0xA738_C6BE_BB12_D16CL,
        /*  201 */ 0xD106_F86E_69D7_85C7L,
        /*  202 */ 0x82A4_5B45_0226_B39CL,
        /*  203 */ 0xA34D_7216_42B0_6084L,
        /*  204 */ 0xCC20_CE9B_D35C_78A5L,
        /*  205 */ 0xFF29_0242_C833_96CEL,
        /*  206 */ 0x9F79_A169_BD20_3E41L,
        /*  207 */ 0xC758_09C4_2C68_4DD1L,
        /*  208 */ 0xF92E_0C35_3782_6145L,
        /*  209 */ 0x9BBC_C7A1_42B1_7CCBL,
        /*  210 */ 0xC2AB_F989_935D_DBFEL,
        /*  211 */ 0xF356_F7EB_F835_52FEL,
        /*  212 */ 0x9816_5AF3_7B21_53DEL,
        /*  213 */ 0xBE1B_F1B0_59E9_A8D6L,
        /*  214 */ 0xEDA2_EE1C_7064_130CL,
        /*  215 */ 0x9485_D4D1_C63E_8BE7L,
        /*  216 */ 0xB9A7_4A06_37CE_2EE1L,
        /*  217 */ 0xE811_1C87_C5C1_BA99L,
        /*  218 */ 0x910A_B1D4_DB99_14A0L,
        /*  219 */ 0xB54D_5E4A_127F_59C8L,
        /*  220 */ 0xE2A0_B5DC_971F_303AL,
        /*  221 */ 0x8DA4_71A9_DE73_7E24L,
        /*  222 */ 0xB10D_8E14_5610_5DADL,
        /*  223 */ 0xDD50_F199_6B94_7518L,
        /*  224 */ 0x8A52_96FF_E33C_C92FL,
        /*  225 */ 0xACE7_3CBF_DC0B_FB7BL,
        /*  226 */ 0xD821_0BEF_D30E_FA5AL,
        /*  227 */ 0x8714_A775_E3E9_5C78L,
        /*  228 */ 0xA8D9_D153_5CE3_B396L,
        /*  229 */ 0xD310_45A8_341C_A07CL,
        /*  230 */ 0x83EA_2B89_2091_E44DL,
        /*  231 */ 0xA4E4_B66B_68B6_5D60L,
        /*  232 */ 0xCE1D_E406_42E3_F4B9L,
        /*  233 */ 0x80D2_AE83_E9CE_78F3L,
        /*  234 */ 0xA107_5A24_E442_1730L,
        /*  235 */ 0xC949_30AE_1D52_9CFCL,
        /*  236 */ 0xFB9B_7CD9_A4A7_443CL,
        /*  237 */ 0x9D41_2E08_06E8_8AA5L,
        /*  238 */ 0xC491_798A_08A2_AD4EL,
        /*  239 */ 0xF5B5_D7EC_8ACB_58A2L,
        /*  240 */ 0x9991_A6F3_D6BF_1765L,
        /*  241 */ 0xBFF6_10B0_CC6E_DD3FL,
        /*  242 */ 0xEFF3_94DC_FF8A_948EL,
        /*  243 */ 0x95F8_3D0A_1FB6_9CD9L,
        /*  244 */ 0xBB76_4C4C_A7A4_440FL,
        /*  245 */ 0xEA53_DF5F_D18D_5513L,
        /*  246 */ 0x9274_6B9B_E2F8_552CL,
        /*  247 */ 0xB711_8682_DBB6_6A77L,
        /*  248 */ 0xE4D5_E823_92A4_0515L,
        /*  249 */ 0x8F05_B116_3BA6_832DL,
        /*  250 */ 0xB2C7_1D5B_CA90_23F8L,
        /*  251 */ 0xDF78_E4B2_BD34_2CF6L,
        /*  252 */ 0x8BAB_8EEF_B640_9C1AL,
        /*  253 */ 0xAE96_72AB_A3D0_C320L,
        /*  254 */ 0xDA3C_0F56_8CC4_F3E8L,
        /*  255 */ 0x8865_8996_17FB_1871L,
        /*  256 */ 0xAA7E_EBFB_9DF9_DE8DL,
        /*  257 */ 0xD51E_A6FA_8578_5631L,
        /*  258 */ 0x8533_285C_936B_35DEL,
        /*  259 */ 0xA67F_F273_B846_0356L,
        /*  260 */ 0xD01F_EF10_A657_842CL,
        /*  261 */ 0x8213_F56A_67F6_B29BL,
        /*  262 */ 0xA298_F2C5_01F4_5F42L,
        /*  263 */ 0xCB3F_2F76_4271_7713L,
        /*  264 */ 0xFE0E_FB53_D30D_D4D7L,
        /*  265 */ 0x9EC9_5D14_63E8_A506L,
        /*  266 */ 0xC67B_B459_7CE2_CE48L,
        /*  267 */ 0xF81A_A16F_DC1B_81DAL,
        /*  268 */ 0x9B10_A4E5_E991_3128L,
        /*  269 */ 0xC1D4_CE1F_63F5_7D72L,
        /*  270 */ 0xF24A_01A7_3CF2_DCCFL,
        /*  271 */ 0x976E_4108_8617_CA01L,
        /*  272 */ 0xBD49_D14A_A79D_BC82L,
        /*  273 */ 0xEC9C_459D_5185_2BA2L,
        /*  274 */ 0x93E1_AB82_52F3_3B45L,
        /*  275 */ 0xB8DA_1662_E7B0_0A17L,
        /*  276 */ 0xE710_9BFB_A19C_0C9DL,
        /*  277 */ 0x906A_617D_4501_87E2L,
        /*  278 */ 0xB484_F9DC_9641_E9DAL,
        /*  279 */ 0xE1A6_3853_BBD2_6451L,
        /*  280 */ 0x8D07_E334_5563_7EB2L,
        /*  281 */ 0xB049_DC01_6ABC_5E5FL,
        /*  282 */ 0xDC5C_5301_C56B_75F7L,
        /*  283 */ 0x89B9_B3E1_1B63_29BAL,
        /*  284 */ 0xAC28_20D9_623B_F429L,
        /*  285 */ 0xD732_290F_BACA_F133L,
        /*  286 */ 0x867F_59A9_D4BE_D6C0L,
        /*  287 */ 0xA81F_3014_49EE_8C70L,
        /*  288 */ 0xD226_FC19_5C6A_2F8CL,
        /*  289 */ 0x8358_5D8F_D9C2_5DB7L,
        /*  290 */ 0xA42E_74F3_D032_F525L,
        /*  291 */ 0xCD3A_1230_C43F_B26FL,
        /*  292 */ 0x8044_4B5E_7AA7_CF85L,
        /*  293 */ 0xA055_5E36_1951_C366L,
        /*  294 */ 0xC86A_B5C3_9FA6_3440L,
        /*  295 */ 0xFA85_6334_878F_C150L,
        /*  296 */ 0x9C93_5E00_D4B9_D8D2L,
        /*  297 */ 0xC3B8_3581_09E8_4F07L,
        /*  298 */ 0xF4A6_42E1_4C62_62C8L,
        /*  299 */ 0x98E7_E9CC_CFBD_7DBDL,
        /*  300 */ 0xBF21_E440_03AC_DD2CL,
        /*  301 */ 0xEEEA_5D50_0498_1478L,
        /*  302 */ 0x9552_7A52_02DF_0CCBL,
        /*  303 */ 0xBAA7_18E6_8396_CFFDL,
        /*  304 */ 0xE950_DF20_247C_83FDL,
        /*  305 */ 0x91D2_8B74_16CD_D27EL,
        /*  306 */ 0xB647_2E51_1C81_471DL,
        /*  307 */ 0xE3D8_F9E5_63A1_98E5L,
        /*  308 */ 0x8E67_9C2F_5E44_FF8FL,
        /*  309 */ 0xB201_833B_35D6_3F73L,
        /*  310 */ 0xDE81_E40A_034B_CF4FL,
        /*  311 */ 0x8B11_2E86_420F_6191L,
        /*  312 */ 0xADD5_7A27_D293_39F6L,
        /*  313 */ 0xD94A_D8B1_C738_0874L,
        /*  314 */ 0x87CE_C76F_1C83_0548L,
        /*  315 */ 0xA9C2_794A_E3A3_C69AL,
        /*  316 */ 0xD433_179D_9C8C_B841L,
        /*  317 */ 0x849F_EEC2_81D7_F328L,
        /*  318 */ 0xA5C7_EA73_224D_EFF3L,
        /*  319 */ 0xCF39_E50F_EAE1_6BEFL,
        /*  320 */ 0x8184_2F29_F2CC_E375L,
        /*  321 */ 0xA1E5_3AF4_6F80_1C53L,
        /*  322 */ 0xCA5E_89B1_8B60_2368L,
        /*  323 */ 0xFCF6_2C1D_EE38_2C42L,
        /*  324 */ 0x9E19_DB92_B4E3_1BA9L,
    };

}
