// SPDX-License-Identifier: MIT

package com.decimal128.textcodec;

import java.nio.charset.StandardCharsets;

/**
 * Thin codec between the two IEEE 754-2019 decimal128 interchange encodings
 * — BID (Binary Integer Decimal) and DPD (Densely Packed Decimal) — and
 * decimal character sequences.
 *
 * <p>A decimal128 encoding is 128 bits: a sign bit, a combination field and a
 * trailing significand field, laid out per IEEE 754-2019 §3.5.2. It crosses
 * this API as a pair of 64-bit {@code long}s, the high 64 bits and the low
 * 64. That is the entire boundary; there is no value type, no context, no
 * status flags.
 *
 * <p>This codec provides no arithmetic, no comparison and no field extraction.
 * For the full IEEE 754-2019 decimal128 implementation — arithmetic,
 * comparison, every rounding-direction attribute, IEEE status flags — see
 * https://decimal128.com.
 *
 * <h2>Rounding</h2>
 *
 * Rounding, where the text → bits direction requires it, is the IEEE
 * 754-2019 {@code roundTiesToEven} attribute and is not configurable. IEEE
 * 754-2019 §5.4.2 places {@code convertFromDecimalCharacter} under the
 * applicable rounding-direction attribute; this codec deliberately
 * implements only the ties-to-even case and is therefore a conforming
 * subset of that operation. Callers needing another rounding direction want
 * the full library.
 *
 * <p>Output form is the GDAS {@code to-scientific-string} form and is not
 * configurable. Cohort information is preserved: {@code "1.00"} and
 * {@code "1.0"} are distinct outputs from distinct encodings of the same
 * numerical value.
 *
 * <h2>Round-trip behavior</h2>
 *
 * The renderer's output round-trips exactly: parsing rendered text and
 * rendering it again reproduces that text, and rendering a canonical
 * encoding and parsing the result reproduces the original bits. Three
 * classes of input do not round-trip:
 *
 * <ul>
 *  <li><b>More than 34 significant digits</b> are accepted and rounded to 34
 *    with {@code roundTiesToEven}. The rounded value is encoded; the input
 *    text is not preserved.
 *
 *  <li><b>Magnitudes outside the representable range</b> overflow to
 *    {@code Infinity} or underflow to a subnormal or zero, per IEEE
 *    754-2019.
 *
 *  <li><b>Non-canonical bit patterns</b> render as something the encoder
 *    would never have emitted, so re-parsing yields the canonical pattern
 *    instead.
 * </ul>
 *
 * <h2>Non-canonical bit patterns</h2>
 *
 * Not every 128-bit pattern is canonical. Each encoding has its own
 * non-canonical forms — patterns the encoder would never emit — and what
 * makes a pattern non-canonical is a property of that encoding, documented
 * where that encoding is decoded.
 *
 * <p>Neither renderer can fail on one: every 128-bit pattern renders as the
 * value IEEE 754-2019 assigns it. Decoding is one-way for such a pattern,
 * because the parsers emit only canonical encodings, so re-parsing the
 * rendered text yields bits that differ from the original. Non-canonical
 * patterns never arise from this codec's own parsers; they appear only when
 * encodings come from outside, such as network or file data.
 *
 * <h2>Thread safety</h2>
 *
 * All functions are pure and safe for concurrent use.
 *
 * <h2>In this port</h2>
 *
 * The renderers return a {@code String}; their {@code bid128ToAscii} and
 * {@code dpd128ToAscii} peers write into a caller-owned {@code byte[]} and
 * return a length, for callers who want no allocation. The parsers write
 * the encoding into a caller-supplied {@code long[]} and return
 * {@code null} on success, or a message on failure. The implementation is
 * one file with no external runtime dependencies. Internal-consistency
 * claims — the ones unreachable for any input — are spelled as Java
 * {@code assert}, so they cost nothing unless the JVM is started with
 * {@code -ea}; the sibling ports spell the same claims as
 * {@code verify(...)} behind a compile-time flag.
 */
public final class Decimal128TextCodec {

    private Decimal128TextCodec() {
    }

    /**
     * Render a BID128 encoding as a decimal character sequence.
     *
     * <p>The output is the GDAS {@code to-scientific-string} form: integer
     * notation when the quantum exponent is zero (e.g. {@code "123"}), plain
     * notation when the adjusted exponent is in the range -6 to 0 (e.g.
     * {@code "123.45"}), normalized scientific notation otherwise (e.g.
     * {@code "1.234E+5"}). The exponent sign is always written, as in
     * {@code "6.02E+23"}. Cohort information is preserved: {@code "1.00"} and
     * {@code "1.0"} are distinct outputs from distinct encodings of the same
     * numerical value.
     *
     * <p>Special values render as {@code "Infinity"}, {@code "-Infinity"},
     * {@code "NaN"} and {@code "sNaN"}, each with an optional sign and an
     * optional payload suffix such as {@code "NaN123"}.
     *
     * <p>The two interchange encodings differ only in how the coefficient is
     * stored: BID128 as a binary integer, DPD128 as a leading digit plus
     * eleven 10-bit declets. The sign and the 14-bit biased exponent are the
     * same in both, and peer encodings of the same value render to identical
     * text.
     *
     * <p>This cannot fail. Every 128-bit pattern renders: a non-canonical BID128
     * coefficient (≥ 10^34) renders as zero at the encoded exponent, and a NaN
     * payload ≥ 10^33 renders as no payload, both per IEEE 754-2019 §3.5.2.
     *
     * @param bid128Hi high 64 bits of the BID128 encoding
     * @param bid128Lo low 64 bits of the BID128 encoding
     * @return the decimal character sequence for the BID128 encoding
     */
    public static String bid128ToString(long bid128Hi, long bid128Lo) {
        byte[] ascii = new byte[MAX_LEN];
        int len = bid128ToAscii(ascii, bid128Hi, bid128Lo);
        return new String(ascii, 0, len, StandardCharsets.ISO_8859_1);
    }

    /**
     * Render a DPD128 encoding as a decimal character sequence.
     *
     * <p>The output is the GDAS {@code to-scientific-string} form: integer
     * notation when the quantum exponent is zero (e.g. {@code "123"}), plain
     * notation when the adjusted exponent is in the range -6 to 0 (e.g.
     * {@code "123.45"}), normalized scientific notation otherwise (e.g.
     * {@code "1.234E+5"}). The exponent sign is always written, as in
     * {@code "6.02E+23"}. Cohort information is preserved: {@code "1.00"} and
     * {@code "1.0"} are distinct outputs from distinct encodings of the same
     * numerical value.
     *
     * <p>Special values render as {@code "Infinity"}, {@code "-Infinity"},
     * {@code "NaN"} and {@code "sNaN"}, each with an optional sign and an
     * optional payload suffix such as {@code "NaN123"}.
     *
     * <p>The two interchange encodings differ only in how the coefficient is
     * stored: BID128 as a binary integer, DPD128 as a leading digit plus
     * eleven 10-bit declets. The sign and the 14-bit biased exponent are the
     * same in both, and peer encodings of the same value render to identical
     * text.
     *
     * <p>This cannot fail. Every 128-bit pattern renders: a DPD128 coefficient is
     * always in range, since a leading digit of at most 9 followed by eleven
     * declets cannot reach 10^34; a NaN payload, carried in those same
     * declets, is likewise always in range; and non-canonical declets decode
     * to their value, per IEEE 754-2019 §3.5.2.
     *
     * @param dpd128Hi high 64 bits of the DPD128 encoding
     * @param dpd128Lo low 64 bits of the DPD128 encoding
     * @return the decimal character sequence for the DPD128 encoding
     */
    public static String dpd128ToString(long dpd128Hi, long dpd128Lo) {
        byte[] ascii = new byte[MAX_LEN];
        int len = dpd128ToAscii(ascii, dpd128Hi, dpd128Lo);
        return new String(ascii, 0, len, StandardCharsets.ISO_8859_1);
    }

    /**
     * The exact bound on a rendered decimal128, in ASCII character bytes.
     *
     * <p>The widest scientific form {@code -9.999...E-6143} is 1 sign, 1 digit, 1
     * radix point, 33 digits, 1 {@code E}, 1 sign and 4 exponent digits; the
     * widest plain form {@code -0.00000999...} is 1 sign, 1 zero, 1 radix
     * point, 5 leading zeros and 34 digits. Each form reaches exactly 42.
     *
     * <p>The plain form's five leading zeros are pinned by the GDAS rule that
     * selects it only when the adjusted exponent is at least -6, which puts
     * five zeros between the radix point and the first significant digit. The
     * exponent field cannot exceed four digits inside decimal128's range, so
     * neither number can drift without the format itself changing.
     *
     * <p>Every write in the renderers is guarded against this bound. Sizing it
     * exactly rather than generously is what turns those guards into real
     * checks: with slack, an error in the arithmetic above would quietly
     * consume the slack instead of firing.
     *
     * <p>A buffer passed to {@code bid128ToAscii} must be at least this long. The
     * C port's {@code D128TEXT_MAX_LEN} is one larger, because it writes a NUL
     * and this does not.
     */
    public static final int MAX_LEN = 42;

    /**
     * Render a BID128 encoding into a caller-supplied buffer, allocating
     * nothing.
     *
     * <p>Writes the ASCII text into {@code out} starting at index 0 and returns
     * its length; the text is {@code out[0]} through {@code out[n - 1]}. No
     * terminator is written. {@code out} must be at least {@code MAX_LEN}
     * long; a shorter one throws {@code ArrayIndexOutOfBoundsException} on the
     * first write past its end rather than corrupting anything, so an
     * undersized buffer is a caller bug that surfaces immediately.
     *
     * <p>The text is identical to what {@code bid128ToString} returns, and this
     * form is the one to reach for when the destination is bytes — a stream, a
     * socket, a CSV or JSON writer — because it leaves nothing for the
     * collector.
     *
     * <p>The two interchange encodings differ only in how the coefficient is
     * stored: BID128 as a binary integer, DPD128 as a leading digit plus
     * eleven 10-bit declets. The sign and the 14-bit biased exponent are the
     * same in both, and peer encodings of the same value render to identical
     * text.
     *
     * @param out the destination, at least {@code MAX_LEN} bytes
     * @param bid128Hi high 64 bits of the BID128 encoding
     * @param bid128Lo low 64 bits of the BID128 encoding
     * @return the number of bytes written
     */
    public static int bid128ToAscii(byte[] out, long bid128Hi, long bid128Lo) {
        // IEEE754-2019 Table 3.6-Decimal2 Interchange format parameters -- p 23
        final int k = 128; // storage width in bits
        // final int p = 34; // precision in digits
        // final int emax = 6144;
        final int bias = 6176;
        final int w5 = 17; // w+5, combination field width in bits
        final int t = 110; // trailing significand field width in bits
        assert 1 + w5 + t == k;

        // 1 + 17 + 110 == 128
        // 1 bit for the sign
        boolean sign = (bid128Hi < 0L);
        // w5 bit combination field ... for bid128 0x1FFFF
        int combination = ((int) (bid128Hi >> (t - 64))) & 0x1FFFF;
        long coeffTHi = bid128Hi & ((1L << t) - 1L);

        long coeffHi;
        int qExp;
        // if the top 2 bits are not 0b11
        if ((combination >> (w5 - 2)) != 0b11) {
            //  IEEE754-2019 3.5.2 c) 2) i) -- p 21
            //   If G0 and G1 together are one of 00, 01, or 10, then the biased
            //   exponent E is formed from G0 through Gw+1 and the significand
            //   is formed from bits Gw+2 through the end of the encoding (including T).
            //
            // w+5=17 w=12 Gw+1=G13 Gw+2=G14
            // the top 14 bits of the combination field represent the biased exponent ...
            int biasedExponentE = combination >> 3;
            qExp = biasedExponentE - bias;
            // ... and the bottom 3 bits are the most significant bits of the significand
            // coeff/significand is 3 + 110 == 113 ... but that's not all ... see below
            coeffHi = (long) (combination & 0x07);
        } else if ((combination >> (w5 - 4)) != 0b1111) {
            // IEEE754-2019 3.5.2 c) 2) ii) -- p 21
            //  If G0 and G1 together are 11 and G2 and G3 together are one
            //  of 00, 01, or 10, then the biased exponent E is formed from
            //  G2 through Gw+3 and the significand is formed by prefixing
            //  the 4 bits (8 + Gw+4) to T.
            //
            // w+5=17 w=12 Gw+3=G15 Gw+4=G16
            //
            int biasedExponentE = (combination >>> 1) & ((1 << (w5 - 5 + 2)) - 1);
            qExp = biasedExponentE - bias;
            coeffHi = 8L + (long) (combination & 1);
        } else if ((combination >> (w5 - 5)) == 0b11110) {
            // if the top 5 bits are 0b11110 then Infinity
            return infBytes(out, sign);
        } else if ((combination >> (w5 - 5)) == 0b11111) {
            // if the top 5 bits are 0x11111 then NaN
            // with the next bit determining signaling NaN
            long payloadHi = coeffTHi;
            long payloadLo = bid128Lo;
            boolean isSignaling = (((combination >> (w5 - 6)) & 1) != 0);
            return nanBytes(out, sign, isSignaling, payloadHi, payloadLo);
        } else {
            // all possible cases were covered above
            throw new IllegalStateException();
        }
        long dw1 = ((coeffHi << (t - 64)) | coeffTHi);
        long dw0 = bid128Lo;
        // IEEE754-2019 3.5.2 p21
        //  If the value exceeds the maximum, the significand c is
        //  non-canonical and the value used for c is zero.
        if ((dw1 | dw0) == 0L ||
            unsignedLT(MAXX_COEFF_34_HI, dw1) ||
            (dw1 == MAXX_COEFF_34_HI && unsignedLT(MAXX_COEFF_34_LO - 1L, dw0))
        )
            return zerBytes(out, sign, qExp);
        return fnzBytes(out, sign, qExp, dw1, dw0);
    }

    /**
     * Render a DPD128 encoding into a caller-supplied buffer, allocating
     * nothing.
     *
     * <p>Writes the ASCII text into {@code out} starting at index 0 and returns
     * its length; the text is {@code out[0]} through {@code out[n - 1]}. No
     * terminator is written. {@code out} must be at least {@code MAX_LEN}
     * long; a shorter one throws {@code ArrayIndexOutOfBoundsException} on the
     * first write past its end rather than corrupting anything, so an
     * undersized buffer is a caller bug that surfaces immediately.
     *
     * <p>The text is identical to what {@code dpd128ToString} returns, and this
     * form is the one to reach for when the destination is bytes — a stream, a
     * socket, a CSV or JSON writer — because it leaves nothing for the
     * collector.
     *
     * <p>The two interchange encodings differ only in how the coefficient is
     * stored: BID128 as a binary integer, DPD128 as a leading digit plus
     * eleven 10-bit declets. The sign and the 14-bit biased exponent are the
     * same in both, and peer encodings of the same value render to identical
     * text.
     *
     * @param out the destination, at least {@code MAX_LEN} bytes
     * @param dpd128Hi high 64 bits of the DPD128 encoding
     * @param dpd128Lo low 64 bits of the DPD128 encoding
     * @return the number of bytes written
     */
    public static int dpd128ToAscii(byte[] out, long dpd128Hi, long dpd128Lo) {
        final int bias = 6176;
        final int w = 12;  // exponent continuation width
        final int w5 = 17; // combination field width in bits
        final int t = 110; // trailing significand field width in bits

        boolean sign = (dpd128Hi < 0L);
        int combination = ((int) (dpd128Hi >> (t - 64))) & 0x1FFFF;
        // in DPD the low w bits of the biased exponent always sit at the bottom of G
        int expContinuation = combination & 0x0FFF;

        long d0;
        int qExp;
        boolean isNaN = false;
        boolean isSignaling = false;

        if ((combination >> (w5 - 2)) != 0b11) {
            // G0G1 is 00, 01 or 10: leading digit 0..7, exponent MSBs in G0G1
            int expMSB = combination >> (w5 - 2);
            qExp = ((expMSB << w) | expContinuation) - bias;
            d0 = (long) ((combination >> w) & 0b111);
        } else if ((combination >> (w5 - 4)) != 0b1111) {
            // G0G1 is 11 and G2G3 is not 11: leading digit 8..9, exponent MSBs in G2G3
            int expMSB = (combination >> (w5 - 4)) & 0b11;
            qExp = ((expMSB << w) | expContinuation) - bias;
            d0 = 8L + (long) ((combination >> w) & 0b1);
        } else if ((combination >> (w5 - 5)) == 0b11110) {
            return infBytes(out, sign);
        } else {
            assert (combination >> (w5 - 5)) == 0b11111;
            isNaN = true;
            isSignaling = (((combination >> (w5 - 6)) & 1) != 0);
            d0 = 0L;
            qExp = 0;
        }

        // Rebuild the binary coefficient from the declets. The leading digit
        // rides along as a sixth declet of the high group: values 0..9 encode
        // to themselves as a declet, so it decodes back unchanged.
        long decletsHi = (d0 << 50) | ((dpd128Hi & 0x3FFFFFFFFFFFL) << 4) | (dpd128Lo >>> 60);
        long decletsLo = dpd128Lo & 0x0FFFFFFFFFFFFFFFL; // low 60 bits, six declets
        long binHi = binFromDeclets(decletsHi);
        long binLo = binFromDeclets(decletsLo);

        long dw1;
        long dw0;
        if (binHi == 0L) {
            dw1 = 0L;
            dw0 = binLo;
        } else {
            dw0 = (binHi * TEN_POW_18) + binLo;
            long carry = (unsignedLT(dw0, binLo)) ? 1L : 0L;
            dw1 = unsignedMulHi(binHi, TEN_POW_18) + carry;
        }

        if (isNaN)
            return nanBytes(out, sign, isSignaling, dw1, dw0);
        if ((dw1 | dw0) == 0L)
            return zerBytes(out, sign, qExp);
        return fnzBytes(out, sign, qExp, dw1, dw0);
    }

    /**
     * Parse a decimal character sequence into a BID128 encoding.
     *
     * <p>Returns {@code null} on success, with the encoding written to
     * {@code out}: {@code out[0]} takes the high 64 bits, {@code out[1]} the
     * low 64. On failure returns a human-readable message and writes the
     * canonical positive quiet NaN, so a caller who discards the message still
     * observes a legal IEEE 754-2019 datum rather than stale or partial data.
     * The exact wording of the message is not part of the contract and may
     * change between versions.
     *
     * <p>Accepted input:
     * <ul>
     *  <li>Plain notation: {@code "0"}, {@code "-1.5"}, {@code "123.456"},
     *    {@code "0.0001"}.
     *
     *  <li>Scientific notation: {@code "1.5E10"}, {@code "-2.3e-7"},
     *    {@code "6.02E+23"}. Both {@code E} and {@code e} are accepted as the
     *    exponent separator.
     *
     *  <li>Digit separators: {@code _} between two digits, in the coefficient or
     *    the exponent ({@code "1_000"}, {@code "1.234_567"}, {@code "1e1_0"}).
     *    An underscore must be both preceded and followed by a digit, so
     *    {@code "1_"}, {@code "_1"}, {@code "1__2"}, {@code "1_.5"} and
     *    {@code "1._5"} are all rejected.
     *
     *  <li>The case-insensitive words {@code Infinity}, {@code Inf},
     *    {@code NaN}, {@code qNaN} and {@code sNaN}, each with an optional
     *    sign and an optional NaN payload of decimal digits ({@code "NaN123"},
     *    {@code "-sNaN42"}). A payload may also be parenthesised, as
     *    {@code "NaN(123)"}.
     * </ul>
     *
     * <p>The parenthesised payload form is positional and balanced: {@code (}
     * immediately after the word, at least one digit, {@code )} last.
     * {@code "NaN()"}, {@code "NaN(1"}, {@code "NaN(1)2(3)"}, {@code "NaN[1]"}
     * and {@code "NaN{1}"} are rejected. Rendering never emits parentheses,
     * and the full decimal128 ports accept the same spelling.
     *
     * <p>More than 34 significant digits round to 34, ties-to-even. Magnitudes
     * above the representable range become {@code Infinity}; magnitudes below
     * it become a subnormal or zero. None of those are failures.
     *
     * <p>Failures are structural: an empty sequence, unrecognized characters,
     * missing digits around the radix point, a malformed exponent field.
     *
     * <p>The two interchange encodings differ only in how the coefficient is
     * stored: BID128 as a binary integer, DPD128 as a leading digit plus
     * eleven 10-bit declets. The same text parses to peer encodings of the
     * same value in either format, and the canonical positive quiet NaN is the
     * same bit pattern in both.
     *
     * @param out receives the BID128 encoding, or the canonical positive
     *   quiet NaN on failure; length ≥ 2
     * @param str the character sequence to parse
     * @return {@code null} on success, or a human-readable message on
     *   failure
     * @throws IllegalArgumentException if {@code out.length < 2}
     */
    public static String bid128Parse(long[] out, String str) {
        if (out.length < 2)
            throw new IllegalArgumentException("out must have length >= 2, was " + out.length);
        String errorStr = encodeStringOrError(out, true, str);
        if (errorStr != null) {
            out[0] = 0x7C00000000000000L;
            out[1] = 0x0000000000000000L;
        }
        return errorStr;
    }

    /**
     * Parse a decimal character sequence into a DPD128 encoding.
     *
     * <p>Returns {@code null} on success, with the encoding written to
     * {@code out}: {@code out[0]} takes the high 64 bits, {@code out[1]} the
     * low 64. On failure returns a human-readable message and writes the
     * canonical positive quiet NaN, so a caller who discards the message still
     * observes a legal IEEE 754-2019 datum rather than stale or partial data.
     * The exact wording of the message is not part of the contract and may
     * change between versions.
     *
     * <p>Accepted input:
     * <ul>
     *  <li>Plain notation: {@code "0"}, {@code "-1.5"}, {@code "123.456"},
     *    {@code "0.0001"}.
     *
     *  <li>Scientific notation: {@code "1.5E10"}, {@code "-2.3e-7"},
     *    {@code "6.02E+23"}. Both {@code E} and {@code e} are accepted as the
     *    exponent separator.
     *
     *  <li>Digit separators: {@code _} between two digits, in the coefficient or
     *    the exponent ({@code "1_000"}, {@code "1.234_567"}, {@code "1e1_0"}).
     *    An underscore must be both preceded and followed by a digit, so
     *    {@code "1_"}, {@code "_1"}, {@code "1__2"}, {@code "1_.5"} and
     *    {@code "1._5"} are all rejected.
     *
     *  <li>The case-insensitive words {@code Infinity}, {@code Inf},
     *    {@code NaN}, {@code qNaN} and {@code sNaN}, each with an optional
     *    sign and an optional NaN payload of decimal digits ({@code "NaN123"},
     *    {@code "-sNaN42"}). A payload may also be parenthesised, as
     *    {@code "NaN(123)"}.
     * </ul>
     *
     * <p>The parenthesised payload form is positional and balanced: {@code (}
     * immediately after the word, at least one digit, {@code )} last.
     * {@code "NaN()"}, {@code "NaN(1"}, {@code "NaN(1)2(3)"}, {@code "NaN[1]"}
     * and {@code "NaN{1}"} are rejected. Rendering never emits parentheses,
     * and the full decimal128 ports accept the same spelling.
     *
     * <p>More than 34 significant digits round to 34, ties-to-even. Magnitudes
     * above the representable range become {@code Infinity}; magnitudes below
     * it become a subnormal or zero. None of those are failures.
     *
     * <p>Failures are structural: an empty sequence, unrecognized characters,
     * missing digits around the radix point, a malformed exponent field.
     *
     * <p>The two interchange encodings differ only in how the coefficient is
     * stored: BID128 as a binary integer, DPD128 as a leading digit plus
     * eleven 10-bit declets. The same text parses to peer encodings of the
     * same value in either format, and the canonical positive quiet NaN is the
     * same bit pattern in both.
     *
     * @param out receives the DPD128 encoding, or the canonical positive
     *   quiet NaN on failure; length ≥ 2
     * @param str the character sequence to parse
     * @return {@code null} on success, or a human-readable message on
     *   failure
     * @throws IllegalArgumentException if {@code out.length < 2}
     */
    public static String dpd128Parse(long[] out, String str) {
        if (out.length < 2)
            throw new IllegalArgumentException("out must have length >= 2, was " + out.length);
        String errorStr = encodeStringOrError(out, false, str);
        if (errorStr != null) {
            out[0] = 0x7C00000000000000L;
            out[1] = 0x0000000000000000L;
        }
        return errorStr;
    }

    // -- decode to String --------------------------------------------------------

    // 10**34 ... has 35 digits ... maxx = max eXclusive
    private static final long MAXX_COEFF_34_HI = 0x0001ED09BEAD87C0L;
    private static final long MAXX_COEFF_34_LO = 0x378D8E6400000000L;

    // 10**33 is the smallest 34-digit coefficient
    private static final long MIN_PRECISION_34_COEFF_HI = 0x0000314DC6448D93L;
    private static final long MIN_PRECISION_34_COEFF_LO = 0x38C15B0A00000000L;

    // 33 nines
    private static final long MAX_PAYLOAD_HI = MIN_PRECISION_34_COEFF_HI;
    private static final long MAX_PAYLOAD_LO = MIN_PRECISION_34_COEFF_LO - 1L;

    private static final byte ascii_minus = 0x2D;
    private static final byte ascii_plus = 0x2B;
    private static final byte ascii_0 = 0x30;
    private static final byte ascii_dot = 0x2E;
    private static final byte ascii_E = 0x45;

    /* ----------------------------------------------------------------------
     * Rendering into the caller's buffer
     *
     * The only renderers. Both output forms land here: the caller-buffer
     * methods return what these write, and the String methods wrap it.
     *
     * These leaf renderers know nothing about BID or DPD: both decoders
     * reduce an encoding to (sign, qExp, coefficient) and hand it here.
     *
     * The two once had String-producing peers, so that "Infinity", a bare
     * NaN and every plain-form zero could come back as interned constants at
     * zero bytes allocated. That cost a second copy of the field extraction
     * and the class dispatch in each encoding -- the same forty lines written
     * twice, kept in step only by the corpus running both forms past the same
     * expected text. One renderer per encoding is worth more than the
     * constants; a String result now always allocates a MAX_LEN scratch buffer
     * and a String.
     *
     * None of the optional leading characters live in the string data. The
     * sign, and the 's' that marks a signaling NaN, are each written
     * unconditionally and then overwritten by whatever follows when they are
     * not wanted. The one exception is a TRAILING '+' in the exponent-form
     * zero, noted where it happens.
     * -------------------------------------------------------------------- */

    private static final byte ascii_s = 0x73;




    private static final byte[] INFINITY_TEXT = "Infinity".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] NAN_TEXT = "NaN".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] ZERO_EXP_TEXT = "0E".getBytes(StandardCharsets.ISO_8859_1);

    /**
     * Every plain-form zero is a prefix of this literal. {@code k = -qExp} is
     * the count of zeros after the radix point, so {@code k == 0} renders
     * {@code "0"} with no radix point at all and {@code k >= 1} renders
     * {@code "0."} followed by {@code k} zeros -- lengths 1 and {@code k + 2}.
     */
    private static final byte[] PLAIN_ZERO = "0.000000".getBytes(StandardCharsets.ISO_8859_1);

    private static int infBytes(byte[] out, boolean sign) {
        int signLen = (sign) ? 1 : 0;
        out[0] = ascii_minus; // overwritten by the 'I' when unsigned
        System.arraycopy(INFINITY_TEXT, 0, out, signLen, INFINITY_TEXT.length);
        return signLen + INFINITY_TEXT.length;
    }

    private static int nanBytes(
        byte[] out, boolean sign, boolean isSignaling,
        long payloadHi, long payloadLo
    ) {
        int signLen = (sign) ? 1 : 0;
        int sLen = (isSignaling) ? 1 : 0;
        int wordOff = signLen + sLen;
        int baseLen = wordOff + NAN_TEXT.length;
        out[0] = ascii_minus;   // overwritten by the 's' or the 'N' when unsigned
        out[signLen] = ascii_s; // overwritten by the 'N' when quiet
        System.arraycopy(NAN_TEXT, 0, out, wordOff, NAN_TEXT.length);
        if ((payloadHi | payloadLo) == 0L ||
            // non canonical IEEE 754-2019 3.5.2 c) 2)
            unsignedLT(MAX_PAYLOAD_HI, payloadHi) ||
            (payloadHi == MAX_PAYLOAD_HI && unsignedLT(MAX_PAYLOAD_LO, payloadLo))
        )
            return baseLen;
        int digitLen = calcDigitLen128(payloadHi, payloadLo);
        u128ToAscii(digitLen, payloadHi, payloadLo, out, baseLen);
        return baseLen + digitLen;
    }

    private static int zerBytes(byte[] out, boolean sign, int qExp) {
        int signLen = (sign) ? 1 : 0;
        out[0] = ascii_minus; // overwritten by the '0' when unsigned
        if (qExp <= 0 && qExp >= -6) {
            int k = -qExp;
            int zeroLen = (k == 0) ? 1 : k + 2;
            System.arraycopy(PLAIN_ZERO, 0, out, signLen, zeroLen);
            return signLen + zeroLen;
        }
        // A positive exponent needs an explicit '+'; a negative one prints its
        // own '-' from appendIntBytes. Write the '+' unconditionally and let
        // appendIntBytes land on top of it when qExp < 0 -- the same discipline
        // as the leading sign, pointed the other way. It is correct only
        // because appendIntBytes emits a '-' for a negative value and emits
        // nothing for a positive one, and because the plain form above has
        // already taken every qExp in [-6, 0], so this branch never sees zero.
        int plusLen = (qExp > 0) ? 1 : 0;
        int baseLen = signLen + ZERO_EXP_TEXT.length + plusLen;
        System.arraycopy(ZERO_EXP_TEXT, 0, out, signLen, ZERO_EXP_TEXT.length);
        out[signLen + ZERO_EXP_TEXT.length] = ascii_plus; // overwritten by the '-' when qExp < 0
        return baseLen + appendIntBytes(out, baseLen, qExp);
    }

    /**
     * Write a signed decimal exponent at {@code off}, returning its length.
     * The magnitude never exceeds 6176, so it is at most four digits.
     */
    private static int appendIntBytes(byte[] out, int off, int v) {
        int signLen = 0;
        int magnitude = v;
        if (v < 0) {
            out[off] = ascii_minus;
            signLen = 1;
            magnitude = -v;
        }
        int digitLen;
        if (magnitude >= 1000)
            digitLen = 4;
        else if (magnitude >= 100)
            digitLen = 3;
        else if (magnitude >= 10)
            digitLen = 2;
        else
            digitLen = 1;
        renderTailDigitsBeforeIndex(digitLen, (long) magnitude, out, off + signLen + digitLen);
        return signLen + digitLen;
    }

    private static int fnzBytes(byte[] out, boolean sign, int qExp, long dw1, long dw0) {
        int digitLen = calcDigitLen128(dw1, dw0);
        int eExp = qExp + (digitLen - 1);
        if (qExp == 0)
            return fnzToIntegerBytes(out, sign, digitLen, dw1, dw0);
        if (qExp <= 0 && eExp >= -6)
            return fnzToDecimalPointBytes(out, sign, qExp, digitLen, dw1, dw0);
        return fnzToNormalizedScientificBytes(out, sign, qExp, digitLen, dw1, dw0);
    }

    private static int fnzToIntegerBytes(byte[] out, boolean sign, int digitLen, long dw1, long dw0) {
        assert digitLen > 0;
        int signLen = (sign) ? 1 : 0;
        out[0] = ascii_minus; // overwritten by the first digit when unsigned
        u128ToAscii(digitLen, dw1, dw0, out, signLen);
        return signLen + digitLen;
    }

    private static int fnzToDecimalPointBytes(
        byte[] out, boolean sign, int qExp,
        int digitLen, long dw1, long dw0
    ) {
        int digitsRightOfDecimal = -qExp;
        int leadingZeroCount = Math.max(1 + digitsRightOfDecimal - digitLen, 0);
        int signLen = (sign) ? 1 : 0;
        int decimalPointLen = 1;
        int totalLen = signLen + leadingZeroCount + decimalPointLen + digitLen;
        out[0] = ascii_minus;
        for (int i = signLen; i <= leadingZeroCount; ++i) // there is one extra here
            out[i] = ascii_0;
        u128ToAscii(digitLen, dw1, dw0, out, signLen + leadingZeroCount);
        for (int i = totalLen - 1; i >= totalLen - digitsRightOfDecimal; --i)
            out[i] = out[i - 1];
        out[totalLen - digitsRightOfDecimal - 1] = ascii_dot;
        return totalLen;
    }

    private static int fnzToNormalizedScientificBytes(
        byte[] out, boolean sign, int qExp,
        int digitLen, long dw1, long dw0
    ) {
        assert digitLen > 0;
        int eExp = qExp + (digitLen - 1);
        int eExpAbs = (eExp ^ (eExp >> 31)) - (eExp >> 31);
        int signLen = (sign) ? 1 : 0;
        int decimalPointLen = (digitLen > 1) ? 1 : 0;
        int expELen = 1;
        int expSignLen = 1;
        byte expSignByte = (eExp < 0) ? ascii_minus : ascii_plus;
        int expDigitLen;
        if (eExpAbs >= 1000)
            expDigitLen = 4;
        else if (eExpAbs >= 100)
            expDigitLen = 3;
        else if (eExpAbs >= 10)
            expDigitLen = 2;
        else
            expDigitLen = 1;
        int totalLen = signLen + decimalPointLen + digitLen + expELen + expSignLen + expDigitLen;
        out[0] = ascii_minus;
        u128ToAscii(digitLen, dw1, dw0, out, signLen + decimalPointLen);
        if (decimalPointLen > 0) {
            out[signLen] = out[signLen + 1];
            out[signLen + 1] = ascii_dot;
        }
        int iE = signLen + decimalPointLen + digitLen;
        out[iE] = ascii_E;
        out[iE + 1] = expSignByte;
        renderTailDigitsBeforeIndex(expDigitLen, (long) eExpAbs, out, totalLen);
        return totalLen;
    }

    // ------- render an integer -------------------------------------------

    /**
     * Reciprocal-multiplication constants for unsigned {@code ÷ 10^8} on a 64-bit
     * dividend, exact for all non-negative inputs.
     *
     * <p>Compute {@code n / 10^8} as {@code unsignedMulHi(n, M_U64_DIV_1E8) >>> S_U64_DIV_1E8}.
     * The {@code S_U64_DIV_1E8} shift is "+ 64 high" because {@code unsignedMulHi} already
     * returns the upper 64 bits of the 128-bit product; the named shift
     * combines with that to give the effective magic-number divide.
     */
    private static final long M_U64_DIV_1E8 = Long.MIN_VALUE | 0x2BCC77118461CEFDL; // -6067343680855748867
    private static final int S_U64_DIV_1E8 = 26; // + 64 high

    private static final long MASK32L = 0x0000_0000_FFFF_FFFFL;

    /**
     * Render a 128-bit unsigned integer as {@code digitPrintCount} decimal digits
     * into {@code ascii}, ending at offset {@code off + digitPrintCount}.
     *
     * <p>Three phases:
     *
     * <ol>
     *  <li>While {@code dw1T != 0}: peel 8 digits per outer iteration via three
     *     {@code ÷ 10^8} divisions on rotating 96-bit chunks, using
     *     {@link #unsignedMulHi} for the high half of each multi-word divide.
     *
     *  <li>Once the value fits in 64 bits: peel 8 digits at a time via a
     *     single {@code ÷ 10^8} per iteration.
     *
     *  <li>For 1–7 leftover digits: delegate to {@link #renderTailDigitsBeforeIndex}.
     * </ol>
     *
     * <p>Each 8-digit chunk is rendered by {@link #render8DigitsBeforeIndex}. The
     * caller must have allocated a buffer with at least
     * {@code off + digitPrintCount} bytes, and is responsible for any leading-zero
     * padding outside the rendered range.
     *
     * @return {@code digitPrintCount}, unchanged from the input
     */
    private static int u128ToAscii(
        int digitPrintCount, long dw1, long dw0,
        byte[] ascii, int off
    ) {
        long dw1T = dw1;
        long dw0T = dw0;
        int ich = off + digitPrintCount;
        int remainingDigitCount = digitPrintCount;
        while (dw1T != 0L) {
            long q2 = unsignedMulHi(dw1T, M_U64_DIV_1E8) >>> S_U64_DIV_1E8;
            long r2 = dw1T - (q2 * 1_0000_0000L);
            long s2 = (r2 << 32) | (dw0T >>> 32);
            long q1 = unsignedMulHi(s2, M_U64_DIV_1E8) >>> S_U64_DIV_1E8;
            long r1 = s2 - (q1 * 1_0000_0000L);
            long s1 = (r1 << 32) | (dw0T & MASK32L);
            long q0 = unsignedMulHi(s1, M_U64_DIV_1E8) >>> S_U64_DIV_1E8;
            long r0 = s1 - (q0 * 1_0000_0000L);
            dw0T = (q1 << 32) + q0;
            dw1T = q2;
            render8DigitsBeforeIndex(r0, ascii, ich);
            ich -= 8;
            remainingDigitCount -= 8;
        }
        while (remainingDigitCount >= 8) {
            long t0 = unsignedMulHi(dw0T, M_U64_DIV_1E8) >>> S_U64_DIV_1E8;
            long r0 = dw0T - (t0 * 1_0000_0000L);
            dw0T = t0;
            render8DigitsBeforeIndex(r0, ascii, ich);
            ich -= 8;
            remainingDigitCount -= 8;
        }
        if (remainingDigitCount > 0)
            renderTailDigitsBeforeIndex(remainingDigitCount, dw0T, ascii, ich);
        return digitPrintCount;
    }

    /**
     * Reciprocal-multiplication constants for unsigned division by small
     * powers of 10 on 32-bit dividends.
     *
     * <p>{@code M_U32_DIV_1E1} / {@code S_U32_DIV_1E1}: divide by 10, valid for
     * dividends up to 2^32 - 1.
     *
     * <p>{@code M_U32_DIV_1E2}: divide by 100, same range.
     *
     * <p>{@code M_U64_DIV_1E4}: divide by 10000 on 64-bit dividends. Used to split
     * an 8-digit value into two 4-digit halves.
     */
    private static final long M_U32_DIV_1E1 = 0xCCCCCCCDL;
    private static final int S_U32_DIV_1E1 = 35;

    private static final long M_U32_DIV_1E2 = 0x51EB851FL;
    private static final int S_U32_DIV_1E2 = 37;

    private static final long M_U64_DIV_1E4 = 0x346DC5D63886594BL;
    private static final int S_U64_DIV_1E4 = 11; // + 64 high

    /**
     * Render exactly 8 decimal digits of {@code dw} into {@code ascii}, ending at
     * {@code offMaxx}. Caller must ensure {@code dw < 10^8}; the function pads with
     * leading zeros if {@code dw} has fewer than 8 significant digits.
     *
     * <p>Implemented as a binary cascade: {@code ÷ 10^4} once to split into two
     * 4-digit halves, then {@code ÷ 10^2} twice to split each half into two
     * 2-digit pairs, then {@code ÷ 10} four times to extract individual digits.
     * The 8 byte stores at the end are independent and benefit from a
     * hoisted bounds check that lets the JIT elide per-store checks.
     *
     * <p>Always writes exactly 8 bytes at offsets {@code offMaxx - 8} through
     * {@code offMaxx - 1}. Throws {@link IndexOutOfBoundsException} if those offsets
     * are not within {@code ascii}.
     */
    private static void render8DigitsBeforeIndex(long dw, byte[] ascii, int offMaxx) {
        long abcd = unsignedMulHi(dw, M_U64_DIV_1E4) >>> S_U64_DIV_1E4;
        long efgh = dw - (abcd * 10000L);

        long ab = (abcd * M_U32_DIV_1E2) >>> S_U32_DIV_1E2;
        long cd = abcd - (ab * 100L);

        long ef = (efgh * M_U32_DIV_1E2) >>> S_U32_DIV_1E2;
        long gh = efgh - (ef * 100L);

        long a = (ab * M_U32_DIV_1E1) >>> S_U32_DIV_1E1;
        long b = ab - (a * 10L);

        long c = (cd * M_U32_DIV_1E1) >>> S_U32_DIV_1E1;
        long d = cd - (c * 10L);

        long e = (ef * M_U32_DIV_1E1) >>> S_U32_DIV_1E1;
        long f = ef - (e * 10L);

        long g = (gh * M_U32_DIV_1E1) >>> S_U32_DIV_1E1;
        long h = gh - (g * 10L);

        // Explicit bounds check to enable elimination of individual checks
        int offMin = offMaxx - 8;
        if (offMin >= 0 && offMaxx <= ascii.length) {
            ascii[offMaxx - 8] = (byte) (a + '0');
            ascii[offMaxx - 7] = (byte) (b + '0');
            ascii[offMaxx - 6] = (byte) (c + '0');
            ascii[offMaxx - 5] = (byte) (d + '0');
            ascii[offMaxx - 4] = (byte) (e + '0');
            ascii[offMaxx - 3] = (byte) (f + '0');
            ascii[offMaxx - 2] = (byte) (g + '0');
            ascii[offMaxx - 1] = (byte) (h + '0');
        } else {
            throw new IndexOutOfBoundsException();
        }
    }

    /**
     * Render exactly {@code renderDigitCount} decimal digits of {@code dw} into
     * {@code ascii}, ending at {@code offMaxx}. Pads with leading zeros if {@code dw}
     * has fewer significant digits than {@code renderDigitCount}.
     *
     * <p>Two phases:
     *
     * <ol>
     *  <li>While at least 4 digits remain: peel 4 digits per iteration
     *     via {@code ÷ 10^4} followed by a 2-digit / 1-digit cascade. Hoisted
     *     bounds check enables bounds check elimination within the
     *     iteration.
     *
     *  <li>For the remaining 0–3 digits: peel one digit at a time via
     *     {@code ÷ 10} thru reciprocal multiplication.
     * </ol>
     *
     * <p>Always writes exactly {@code renderDigitCount} bytes at offsets
     * {@code offMaxx - renderDigitCount} through {@code offMaxx - 1}.
     *
     * @throws IllegalArgumentException if the 4-digit phase would write
     *         outside {@code ascii}
     */
    private static void renderTailDigitsBeforeIndex(int renderDigitCount, long dw, byte[] ascii, int offMaxx) {
        long t = dw;
        int remainingDigitCount = renderDigitCount;
        int ib = offMaxx;
        while (remainingDigitCount >= 4) {
            long t0 = unsignedMulHi(t, M_U64_DIV_1E4) >>> S_U64_DIV_1E4;
            long abcd = t - (t0 * 10000L);
            t = t0;
            long ab = (abcd * M_U32_DIV_1E2) >>> S_U32_DIV_1E2;
            long cd = abcd - (ab * 100L);
            long a = (ab * M_U32_DIV_1E1) >>> S_U32_DIV_1E1;
            long b = ab - (a * 10L);
            long c = (cd * M_U32_DIV_1E1) >>> S_U32_DIV_1E1;
            long d = cd - (c * 10L);
            if (ib - 4 >= 0 && ib <= ascii.length) {
                ascii[ib - 4] = (byte) (a + '0');
                ascii[ib - 3] = (byte) (b + '0');
                ascii[ib - 2] = (byte) (c + '0');
                ascii[ib - 1] = (byte) (d + '0');
                ib -= 4;
                remainingDigitCount -= 4;
            } else {
                throw new IllegalArgumentException();
            }
        }
        while (remainingDigitCount > 0) {
            long divTen = (t * M_U32_DIV_1E1) >>> S_U32_DIV_1E1;
            int digit = (int) (t - (divTen * 10L));
            ascii[--ib] = (byte) ('0' + digit);
            t = divTen;
            --remainingDigitCount;
        }
        assert offMaxx - ib == renderDigitCount;
    }

    // -- encode from String --------------------------------------------------------

    /**
     * The character at {@code index}, or NUL past the end of the string. The
     * parsers walk a NUL-terminated view of the input, so end-of-input is one
     * more character value rather than a bound test at every read.
     */
    private static char charOrNul(String str, int index) {
        return (index < str.length()) ? str.charAt(index) : '\0';
    }

    /**
     * Top-level dispatcher for parsing a decimal character sequence into an
     * encoding. Reads optional sign, then dispatches by leading character
     * to the finite-value, infinity, or NaN parser.
     *
     * <p>The leading character is also passed through to the dispatched
     * parser so it doesn't have to re-read what the dispatcher already
     * consumed.
     *
     * @return {@code null} on success (with {@code out} populated), or a
     *         human-readable error string on failure
     */
    private static String encodeStringOrError(long[] out, boolean formatIsBid, String str) {
        int pos = 0;
        char chFirst = charOrNul(str, pos++);
        char ch = chFirst;
        boolean sign = (ch == '-');
        if (ch == '+' || ch == '-')
            ch = charOrNul(str, pos++);
        if ((ch >= '0' && ch <= '9') || ch == '.')
            return parseFiniteValueText(out, formatIsBid, sign, ch, str, pos);
        char chLower = (char) (ch | 0x20);
        if (chLower == 'i')
            return parseInfinityText(out, sign, ch, str, pos);
        if (chLower == 'n' || chLower == 's' || chLower == 'q')
            return parseNanText(out, formatIsBid, sign, ch, str, pos);
        if (chFirst == '\0')
            return "empty string";
        return "invalid format";
    }

    /**
     * Parse the keyword {@code Infinity} (or its 3-letter abbreviation {@code Inf}),
     * case-insensitively, and write the corresponding encoding into
     * {@code out}. Returns an error string for any other input.
     */
    private static String parseInfinityText(long[] out, boolean sign, char chFirst, String str, int startPos) {
        int pos = startPos;
        char ch = chFirst;
        int chPrevCode = 0;
        final String target = "infinity";
        for (int i = 0; i < 8; ++i) {
            int targetCode = target.charAt(i);
            if ((ch | 0x20) != targetCode) {
                if (ch == '\0')
                    break;
                else
                    return "failure parsing infinity";
            }
            chPrevCode = ch | 0x20;
            ch = charOrNul(str, pos++);
        }
        if (ch != '\0' || (chPrevCode != 'f' && chPrevCode != 'y'))
            return "failure parsing Infinity";
        out[0] = 0x7800000000000000L | ((sign) ? Long.MIN_VALUE : 0L);
        out[1] = 0L;
        return null;
    }

    /**
     * Parse {@code NaN}, {@code qNaN}, or {@code sNaN} (case-insensitive) with an
     * optional decimal payload, and write the corresponding encoding into
     * {@code out}. Payloads of 34+ digits AS WRITTEN are non-canonical and produce
     * a zero payload per IEEE 754-2019 §3.5.2.
     *
     * <p>The payload may be written bare ({@code NaN123}) or enclosed in parentheses
     * ({@code NaN(123)}). The parenthesised form is positional and balanced: the
     * {@code (} must follow the word immediately, at least one digit must appear,
     * and the {@code )} must be the last character of the input. So {@code NaN(1)2(3)},
     * {@code NaN(1}, {@code NaN(1]} and {@code NaN()} are all rejected, and
     * {@code NaN[1]} and {@code NaN{1}} are not this form at all.
     *
     * <p>The full decimal128 ports accept the same spelling; the codecs had it
     * first, and the ports took it on 2026-08-23. The reason it exists at all
     * is that C's own {@code strtod} reads
     * {@code NAN(n-char-sequence)}, so the spelling reaches a caller from the
     * platform itself. The resemblance stops at the parentheses -- strtod's
     * sequence is letters, digits and underscores read as an
     * implementation-defined integer ({@code NAN(abc)} and {@code NAN(0x1c8)} are legal
     * there), while a decimal128 payload is decimal digits. This is a
     * paren-tolerant spelling of our own payload syntax, not C's.
     *
     * <p>The legacy base skipped the six bracket characters ANYWHERE, so it
     * read {@code NaN(1)2(3)} as payload 123. That is not this, and stays rejected.
     */
    private static String parseNanText(long[] out, boolean formatIsBid, boolean sign, char chFirst, String str, int startPos) {
        int pos = startPos;
        char ch = chFirst;
        boolean hasS = ((ch | 0x20) == 's');
        boolean hasQ = ((ch | 0x20) == 'q');
        if (hasQ || hasS)
            ch = charOrNul(str, pos++);
        if (((ch | 0x20) != 'n') ||
            ((charOrNul(str, pos++) | 0x20) != 'a') ||
            ((charOrNul(str, pos++) | 0x20) != 'n')
        )
            return "failure parsing NaN";
        long payloadDw0 = 0L;
        long payloadDw1 = 0L;
        ch = charOrNul(str, pos++);
        boolean hasParen = (ch == '(');
        if (hasParen)
            ch = charOrNul(str, pos++);
        if (hasParen || ch != '\0') {
            int accumDigitCount = 0;
            long accum19a = 0L;
            long accum19b = 0L;
            while (ch >= '0' && ch <= '9') {
                int d = ch - '0';
                // Count every digit written, leading zeros included. The
                // 33-digit limit below is a limit on the payload as spelled,
                // not on its significant digits, so "NaN" followed by forty
                // zeros and a one is over the limit and reads as zero.
                // Leading zeros contribute nothing to the accumulators, so
                // the value of a payload within the limit is unaffected.
                ++accumDigitCount;
                if (accumDigitCount <= 19)
                    accum19a = (accum19a * 10L) + (long) d;
                else
                    accum19b = (accum19b * 10L) + (long) d;
                ch = charOrNul(str, pos++);
            }
            if (accumDigitCount == 0)
                return (hasParen) ? "failure parsing NaN" : "non-digit NaN payload character";
            if (hasParen) {
                // the ')' must be present and must end the input
                if (ch != ')')
                    return (ch == '\0') ? "failure parsing NaN" : "non-digit NaN payload character";
                if (charOrNul(str, pos++) != '\0')
                    return "failure parsing NaN";
            } else if (ch != '\0') {
                return "non-digit NaN payload character";
            }
            if (accumDigitCount > 33) {
                // IEEE754-2019 3.5.2 Encodings
                //  ... as the payload of a NaN). If the value exceeds the maximum,
                //  the significand c is non-canonical and the value used for c is zero.
                payloadDw0 = 0L;
                payloadDw1 = 0L;
            } else {
                payloadDw0 = accum19a;
                payloadDw1 = 0L;
                if (accumDigitCount > 19) {
                    int pow10 = accumDigitCount - 19;
                    u128FmaPow10(out, accum19a, pow10, accum19b);
                    payloadDw1 = out[0];
                    payloadDw0 = out[1];
                }
            }
        }
        setNaN(out, formatIsBid, sign, hasS, payloadDw1, payloadDw0);
        return null;
    }

    /**
     * Pack a NaN into whichever interchange format the caller asked for.
     *
     * <p>A NaN payload is a coefficient, so the formats diverge here exactly as
     * they do for finite values: BID keeps it as a binary integer, DPD as
     * declets. Infinity by contrast has no coefficient, which is why it is
     * byte-identical in both and needs no such split.
     */
    private static void setNaN(long[] out, boolean formatIsBid, boolean sign,
                               boolean isSignaling, long payloadDw1, long payloadDw0) {
        if (formatIsBid)
            bid128SetNaN(out, sign, isSignaling, payloadDw1, payloadDw0);
        else
            dpd128SetNaN(out, sign, isSignaling, payloadDw1, payloadDw0);
    }

    private static void bid128SetNaN(long[] out, boolean sign,
                                     boolean isSignaling, long payloadDw1, long payloadDw0) {
        long withoutPayload =
            ((isSignaling) ? 0x7E00000000000000L : 0x7C00000000000000L) | ((sign) ? Long.MIN_VALUE : 0L);
        out[0] = withoutPayload | payloadDw1;
        out[1] = payloadDw0;
    }

    /**
     * Pack a NaN into DPD128. The payload occupies the whole 110-bit trailing
     * field as eleven declets -- 33 digits, with no leading digit in the
     * combination field, since G0..G4 are fixed at 11111 and G5 carries the
     * signaling flag.
     *
     * <p>{@code out} doubles as scratch for the two divisions before it is written.
     */
    private static void dpd128SetNaN(long[] out, boolean sign,
                                     boolean isSignaling, long payloadDw1, long payloadDw0) {
        assert calcDigitLen128(payloadDw1, payloadDw0) <= 33;

        long r1 = u113DivModPow10Max9(out, payloadDw1, payloadDw0, 9);
        long r2 = u113DivModPow10Max9(out, out[0], out[1], 9);
        long binLo = (r2 * 1_000_000_000L) + r1;
        assert out[0] == 0L; // a 33-digit payload leaves 15 digits up top
        long declets5Hi = decletsFromBin(out[1]);
        long declets6Lo = decletsFromBin(binLo);

        int gField = (0b11111 << 12) | ((isSignaling) ? (1 << 11) : 0);
        int signCombo = (((sign) ? 1 : 0) << 17) | gField;
        out[0] = (((long) signCombo) << 46) | (declets5Hi >>> 4);
        out[1] = (declets5Hi << 60) | declets6Lo;
    }

    /**
     * Parse a finite decimal numeral and write the corresponding
     * encoding into {@code out}. Accepts an optional decimal point, an
     * optional {@code E}/{@code e} exponent (with optional {@code +}/{@code -} sign), and
     * any number of significant digits.
     *
     * <p>Coefficients with more than 34 significant digits are accepted;
     * leading zeros before a non-zero digit are flushed; the digits past
     * 34 are properly tracked as {@code residue} for subsequent
     * {@code roundTiesToEven} rounding. Excess exponent magnitudes are clamped
     * to 9999, which is sufficient for the rounding logic to detect
     * overflow or underflow.
     *
     * <p>Sign is taken from the {@code sign} parameter (already consumed by the
     * dispatcher); the leading character {@code chFirst} is the first digit or
     * {@code .} and is processed by the main loop. Returns {@code null} on success or
     * a human-readable error string on syntax errors.
     */
    private static String parseFiniteValueText(
        long[] out,
        boolean formatIsBid,
        boolean sign,
        char chFirst,
        String str,
        int startPos
    ) {
        int pos = startPos;
        int residue = EXACT;

        boolean hasCoefficientDigit = false; // have we seen any digits at all, including zero
        int significantDigitCount = 0; // does not count leading zeros
        boolean hasDot = false;
        boolean expSign = false;
        // an underscore is a digit separator: legal only between two digits,
        // so it must follow a digit and the next character must be a digit too
        boolean prevWasDigit = false;
        boolean afterUnderscore = false;

        assert chFirst != '\0';
        char ch = chFirst;
        int fractionalDigitCount = 0;
        long accum19a = 0L;
        long accum19b = 0L;
        int exp = 0;

        while ((ch >= '0' && ch <= '9') || ch == '.' || ch == '_') {
            if (ch >= '0' && ch <= '9') {
                int d = ch - '0';
                hasCoefficientDigit = true;
                // count while flushing leading zeros
                significantDigitCount += (-(significantDigitCount | d)) >>> 31;
                if (significantDigitCount <= 19)
                    accum19a = (accum19a * 10L) + (long) d;
                else if (significantDigitCount <= 34)
                    accum19b = (accum19b * 10L) + (long) d;
                else if (significantDigitCount == 34 + 1)
                    residue = residueFromDecimalDigit(d);
                else
                    residue = residueMerge(residue, residueFromDecimalDigit(d));
                if (hasDot)
                    ++fractionalDigitCount;
                prevWasDigit = true;
                afterUnderscore = false;
            } else if (ch == '_') {
                if (!prevWasDigit)
                    return "invalid underscore location";
                prevWasDigit = false;
                afterUnderscore = true;
            } else { // ch == '.'
                if (afterUnderscore)
                    return "invalid underscore location";
                if (hasDot)
                    return "double radix dot";
                hasDot = true;
                prevWasDigit = false;
            }
            ch = charOrNul(str, pos++);
        }
        if (afterUnderscore)
            return "invalid underscore location";
        if (!hasCoefficientDigit)
            return "no coefficient digit";
        // this path has at least one digit
        if (ch == 'E' || ch == 'e') {
            ch = charOrNul(str, pos++);
            if (ch == '+' || ch == '-') {
                expSign = (ch == '-');
                ch = charOrNul(str, pos++);
            }
            boolean hasExpDigit = false;
            int expSignificantDigitCount = 0;
            prevWasDigit = false;
            afterUnderscore = false;
            while ((ch >= '0' && ch <= '9') || ch == '_') {
                if (ch == '_') {
                    if (!prevWasDigit)
                        return "invalid underscore location";
                    prevWasDigit = false;
                    afterUnderscore = true;
                } else {
                    hasExpDigit = true;
                    int eDigit = ch - '0';
                    // count while flushing leading zeros
                    expSignificantDigitCount +=
                        (-(expSignificantDigitCount | eDigit)) >>> 31;
                    exp = exp * 10 + eDigit;
                    prevWasDigit = true;
                    afterUnderscore = false;
                }
                ch = charOrNul(str, pos++);
            }
            if (afterUnderscore)
                return "invalid underscore location";
            if (!hasExpDigit)
                return "no exponent digit";
            // clamp exp to 9999 once after the loop
            if (expSignificantDigitCount > 4)
                exp = 9999;
        }
        if (ch != '\0')
            return "unexpected char";
        // we have at least one digit
        long dw0 = accum19a;
        long dw1 = 0L;
        if (significantDigitCount > 19) {
            int pow10 = Math.min(34, significantDigitCount) - 19;
            u128FmaPow10(out, accum19a, pow10, accum19b);
            dw1 = out[0];
            dw0 = out[1];
        }
        // at this point, our coeff <= precision digits
        // but we need to deal with residue and rounding
        // rounding rollover could affect the exponent
        //
        // when we accept oversize coefficients, then whether the excess
        // digits are to the right or left of the exponent will affect
        // the qExp ...
        int signedExp = (expSign) ? -exp : exp;
        int qExp = signedExp - fractionalDigitCount + Math.max(0, significantDigitCount - 34);
        roundAndFinalizeFinite(out, formatIsBid, sign, qExp, dw1, dw0, residue);
        return null;
    }

    /**
     * Round and finalize a finite parsed coefficient/exponent
     * pair into the encoding stored in {@code out}.
     *
     * <p>Implements {@code roundTiesToEven} rounding with full handling of the
     * decimal128 boundary conditions: oversized coefficient, underflow to
     * subnormal or zero, overflow to infinity, and the clamped-near-Emax
     * region where a finite value with too-large quantum exponent can be
     * representable by absorbing the excess into the coefficient.
     *
     * <p>Step 1 (fast path): if the input already fits within precision and
     * range and no rounding is required (residue &le; LT_HALF, or HALF with
     * an even retained low bit), encode and return immediately.
     * This picks up the vast majority of cases.
     *
     * <p>Step 2 (zero coefficient): zeros that are out of qExp range get
     * clamped to the valid range.
     *
     * <p>-Step 3 (special values): Not applicable here — special values
     * are handled by callers before reaching this function.
     *
     * <p>Step 4 (underflow): if the exponent is below Emin, divert to
     * {@link #finalizeUnderflowRegion} for subnormal handling.
     *
     * <p>Step 5 (precision truncation): REMOVED as unreachable from a codec's
     * callers -- see the note in the body, which also states what would have
     * to change for it to be needed again.
     *
     * <p>Step 6 (rounding): apply {@code roundTiesToEven}. If rounding causes the
     * coefficient to roll over to 10^34, normalize to 10^33 and increment
     * the exponent.
     *
     * <p>Step 7 (overflow / clamping): if the exponent exceeds Emax, either
     * absorb the excess into the coefficient (clamping near Emax) or
     * overflow to infinity.
     */
    private static void roundAndFinalizeFinite(
        long[] out,
        boolean formatIsBid,
        boolean sign,
        int qExpIn,
        long dw1In,
        long dw0In,
        int residueIn
    ) {
        // Step 1: Fast path: already in valid decimal128 range ...
        //         ... and no roundTiesToEven rounding
        if (residueIn <= LT_HALF || (residueIn == HALF && (dw0In & 1L) == 0L)) {
            if (coeffQExpFit(dw1In, dw0In, qExpIn)) {
                setFinite(out, formatIsBid, sign, qExpIn, dw1In, dw0In);
                return;
            }
            // Step 2: zero coefficient with out-of-range qExp ... clamp qExp
            if ((dw1In | dw0In) == 0L) {
                int qClamped = Math.max(Math.min(qExpIn, 6111), -6176);
                setFinite(out, formatIsBid, sign, qClamped, 0L, 0L);
                return;
            }
        }

        // Step 3: special values ... not applicable ... only Finite

        // Step 5 -- precision truncation -- IS NOT HERE, and this is the reason.
        //
        // This finalize chain descends from one built for an arithmetic engine,
        // whose callers hand it coefficients wider than the 34-digit precision
        // limit: multiply and FMA produce a double-width product, and the engine
        // truncates it here, accumulating the discarded digits into the residue
        // before rounding. A codec has no such caller.
        //
        // parseFiniteValueText accumulates at most 19 + 15 = 34 significant
        // digits and turns every digit past them into a residue instead, so the
        // coefficient it hands over is never wider than the limit. The only
        // other entry to this function is the swamped branch of
        // finalizeUnderflowRegion, which passes a zero coefficient. So the
        // truncation step is unreachable, and rather than carry a path that has
        // never once executed -- and therefore has never been verified to work
        // -- it is removed and the invariant is enforced.
        //
        // Measured on the C port, which is a translation of this file: zero
        // executions across the 87k-row shared corpus, 400,000 random
        // numerals, and 76,280 finalize calls driven by inputs of up to 5,000
        // digits, where the widest coefficient ever seen here was 34.
        //
        // This is an unconditional throw, not an `assert`: a JVM started without
        // -ea would otherwise fall through and encode an out-of-range
        // coefficient silently. It cannot fire on a caller's data -- no input
        // reaches it -- only on a future caller that breaks the invariant, which
        // is exactly when a loud failure is wanted. A caller that is not a parser
        // needs the truncation step back; it is live in every full port's
        // Finalize, where multiply and FMA do reach it.
        if (calcDigitLen128(dw1In, dw0In) > 34)
            throw new IllegalStateException("coefficient wider than the 34-digit precision limit");

        // Step 4: underflow. With no precision truncation to outrank it, the
        // test is simply whether the exponent is below the minimum.
        if (qExpIn < -6176) {
            finalizeUnderflowRegion(out, formatIsBid, sign, qExpIn, dw1In, dw0In, residueIn);
            return;
        }

        int totalResidue = residueIn;
        long dw1 = dw1In;
        long dw0 = dw0In;
        int qExp = qExpIn;

        // step 6: rounding ... in this world only roundTiesToEven
        boolean applyRounding = (totalResidue == GT_HALF || (totalResidue == HALF && (dw0 & 1L) != 0L));
        if (applyRounding) {
            // step 6.1: increment
            ++dw0;
            dw1 += (dw0 == 0L) ? 1L : 0L;

            // step 6.2: rollover
            if (dw1 == MAXX_COEFF_34_HI && dw0 == MAXX_COEFF_34_LO) {
                dw1 = MIN_PRECISION_34_COEFF_HI;
                dw0 = MIN_PRECISION_34_COEFF_LO;
                ++qExp;
            }
        }

        // step 7: check final bounds
        assert qExp >= -6176;
        assert calcDigitLen128(dw1, dw0) <= 34;
        if (qExp > 6111) {
            int qExcess = qExp - 6111;
            int digitLen = calcDigitLen128(dw1, dw0);
            if (digitLen + qExcess <= 34)
                finalizeClamping(out, formatIsBid, sign, qExp, dw1, dw0);
            else
                finalizeOverflow(out, sign);
            return;
        }
        setFinite(out, formatIsBid, sign, qExp, dw1, dw0);
    }

    /**
     * Pack a finite value into whichever interchange format the caller asked
     * for. BID and DPD are peers: both carry the same sign, the same 14-bit
     * biased exponent and the same 34-digit coefficient, and differ only in
     * how that coefficient is laid out. Everything upstream of this point --
     * rounding, clamping and the underflow region -- is format-neutral.
     */
    private static void setFinite(long[] out, boolean formatIsBid, boolean sign, int qExp, long dw1, long dw0) {
        if (formatIsBid)
            bid128SetFinite(out, sign, qExp, dw1, dw0);
        else
            dpd128SetFinite(out, sign, qExp, dw1, dw0);
    }

    /**
     * Pack a finite value into DPD128. The peer of {@link #bid128SetFinite}: same
     * inputs, same sign bit, same 14-bit biased exponent, and a coefficient
     * of the same 34 digits -- laid out as a leading digit carried in the
     * combination field plus eleven declets.
     *
     * <p>The leading digit cannot be packed independently of the exponent: DPD
     * puts it in the same five-bit prefix as the exponent's top two bits, so
     * unlike BID there is no shared "pack sign and exponent" step to factor out.
     *
     * <p>{@code out} doubles as scratch for the two divisions before it is written.
     */
    private static void dpd128SetFinite(long[] out, boolean sign, int qExp, long dw1, long dw0) {
        assert qExp >= -6176 && qExp <= 6111;
        assert calcDigitLen128(dw1, dw0) <= 34;

        // Split the coefficient into its low 18 digits and its high 16.
        // Two divisions by 10^9 rather than one by 10^18, so the existing
        // 128-bit kernel does the work and no wider machinery is needed.
        long r1 = u113DivModPow10Max9(out, dw1, dw0, 9);
        long r2 = u113DivModPow10Max9(out, out[0], out[1], 9);
        long binLo = (r2 * 1_000_000_000L) + r1;
        assert out[0] == 0L; // the high 16 digits fit in one word
        long binHi = out[1];

        // the top digit rides in the combination field; the other 15 are declets
        long d0 = binHi / TEN_POW_15;
        long declets5Hi = decletsFromBin(binHi - (d0 * TEN_POW_15));
        long declets6Lo = decletsFromBin(binLo);
        assert d0 >= 0L && d0 <= 9L;

        int biasedQExp = qExp + 6176;
        int expMSB = (biasedQExp >> 12) & 0x3;
        int expLSB = biasedQExp & 0x0FFF;
        int gPrefix =
            (d0 <= 7L)
                ? (expMSB << 3) | (int) d0                        // G0G1 = exponent MSBs
                : 0b11000 | (expMSB << 1) | (((int) d0) & 1);     // G0G1 = 11, digit is 8 or 9
        int signCombo = (((sign) ? 1 : 0) << 17) | (gPrefix << 12) | expLSB;

        out[0] = (((long) signCombo) << 46) | (declets5Hi >>> 4);
        out[1] = (declets5Hi << 60) | declets6Lo;
    }

    /**
     * Pack the sign, biased quantum exponent, and 113-bit coefficient into
     * the BID128 encoding stored in {@code out}.
     *
     * <p>Always uses the short-form encoding (combination field top bits not
     * {@code 11}), which is correct for any canonical coefficient whose top 3
     * bits fit in 0..7.
     *
     * <p>Preconditions are checked by {@code assert}: the exponent must be in
     * [-6176, 6111] and the coefficient must have at most 34 decimal digits.
     */
    private static void bid128SetFinite(long[] out, boolean sign, int qExp, long dw1, long dw0) {
        assert qExp >= -6176 && qExp <= 6111;
        assert calcDigitLen128(dw1, dw0) <= 34;
        long qBiased = (long) (qExp + 6176);
        long s = (sign) ? Long.MIN_VALUE : 0L;
        long g = qBiased << (3 + 46);
        out[0] = s | g | dw1;
        out[1] = dw0;
    }

    /**
     * Encode {@code Infinity} (or {@code -Infinity}) into {@code out}.
     */
    private static void finalizeOverflow(long[] out, boolean sign) {
        out[0] = ((sign) ? Long.MIN_VALUE : 0L) | 0x7800000000000000L;
        out[1] = 0L;
    }

    /**
     * Encode a finite value whose natural quantum exponent exceeds qMax
     * (6111) but whose coefficient has enough leading capacity to absorb
     * the excess.
     *
     * <p>Multiplies the coefficient by 10^qExcess (where qExcess is qExp
     * minus 6111), reducing the exponent to exactly 6111. This preserves
     * the value while bringing the encoding into representable range.
     *
     * <p>Caller must have verified that the coefficient has enough room
     * (digitLen + qExcess &le; 34); this function asserts the invariant
     * and produces the clamped encoding.
     */
    private static void finalizeClamping(long[] out, boolean formatIsBid, boolean sign, int qExp, long dw1, long dw0) {
        int qExcess = qExp - 6111;
        assert qExcess > 0 && qExcess <= 34 - calcDigitLen128(dw1, dw0);
        int pow10Offset = qExcess << 1;
        long y1 = POW10[pow10Offset + 1];
        long y0 = POW10[pow10Offset];
        long pp00Hi = unsignedMulHi(dw0, y0);
        long pp00Lo = dw0 * y0;
        long pp10Lo = dw1 * y0;
        long pp01Lo = dw0 * y1;
        long dw0Scaled = pp00Lo;
        long dw1Scaled = pp00Hi + pp10Lo + pp01Lo;
        assert coeffQExpFit(dw1Scaled, dw0Scaled, 6111);
        setFinite(out, formatIsBid, sign, 6111, dw1Scaled, dw0Scaled);
    }

    /**
     * Test whether a coefficient/exponent pair fits as a canonical encoding:
     * exponent in [-6176, 6111] and coefficient in [0, 10^34).
     *
     * <p>Fast path uses the leading-zero bit count of the high word to
     * cheaply confirm the coefficient has at most 113 bits, in which case
     * it is necessarily less than 10^34 (since 2^113 &gt; 10^34). Slow path
     * compares against the precomputed POW10[34] constant.
     */
    private static boolean coeffQExpFit(long dw1, long dw0, int qExp) {
        if (qExp < -6176 || qExp > 6111)
            return false;
        if (Long.numberOfLeadingZeros(dw1) > 128 - 113)
            return true;
        int pow10Offset = 34 << 1;
        long maxxHi = POW10[pow10Offset + 1];
        return unsignedLT(dw1, maxxHi) || (dw1 == maxxHi && unsignedLT(dw0, POW10[pow10Offset]));
    }

    /**
     * Dispatch underflow handling for a value whose exponent is below
     * Emin (-6176). Three cases:
     *
     * <ul>
     *  <li>Truncation needed exceeds the digit count: result is zero
     *    (the value is below half the smallest subnormal).
     *  <li>Truncation equals the digit count: boundary case where the
     *    leading digit of the coefficient determines rounding.
     *  <li>Truncation is less than the digit count: representable as a
     *    subnormal; some precision is preserved, with rounding.
     * </ul>
     */
    private static void finalizeUnderflowRegion(
        long[] out,
        boolean formatIsBid,
        boolean sign,
        int qExp,
        long dw1,
        long dw0,
        int residue
    ) {
        // IEEE 754-2019 7.5 Underflow -- the subnormal region
        assert qExp < -6176;
        int truncationNeeded = -6176 - qExp;
        int digitLen = calcDigitLen128(dw1, dw0);
        assert digitLen <= 34;
        if (truncationNeeded > digitLen) {
            // Result is swamped - becomes zero with roundTiesToEven
            // This is always inexact
            roundAndFinalizeFinite(out, formatIsBid, sign, -6176, 0L, 0L, LT_HALF);
        } else if (truncationNeeded == digitLen) {
            finalizeUnderflowBoundary(out, formatIsBid, sign, dw1, dw0, residue);
        } else {
            finalizeSubnormal(out, formatIsBid, sign, qExp, dw1, dw0, residue);
        }
    }

    /**
     * Handle the underflow boundary case where the most-significant digit
     * of the coefficient sits exactly one position to the right of the
     * representable range.
     *
     * <p>The coefficient value itself determines rounding: comparing the
     * coefficient against {@code (10^digitLen) / 2} yields the residue class.
     * Combined with any incoming residue, this drives a {@code roundTiesToEven}
     * decision between zero (always even, the default outcome) and 1 ulp
     * (the smallest subnormal, only when total residue is GT_HALF).
     */
    private static void finalizeUnderflowBoundary(
        long[] out, boolean formatIsBid, boolean sign,
        long dw1, long dw0, int residueIn
    ) {
        int digitLen = calcDigitLen128(dw1, dw0);
        assert digitLen != 0;
        int scaleResidue = residueFromValuePow10(dw1, dw0, digitLen);
        int totalResidue = residueMerge(scaleResidue, residueIn);
        // roundTiesToEven ...
        // only if we are GT_HALF since otherwise the result is 0 ... which is even
        setFinite(out, formatIsBid, sign, -6176, 0L, (totalResidue == GT_HALF) ? 1L : 0L);
    }

    private static void finalizeSubnormal(
        long[] out, boolean formatIsBid, boolean sign, int qExpIn,
        long dw1In, long dw0In, int residueIn
    ) {
        int truncationNeeded = -6176 - qExpIn;
        assert truncationNeeded > 0 && truncationNeeded < calcDigitLen128(dw1In, dw0In);

        int scaleResidue = u128ScaleDownPow10(out, dw1In, dw0In, truncationNeeded);
        int totalResidue = residueMerge(scaleResidue, residueIn);
        long dw1T = out[0];
        long dw0T = out[1];
        int qExpT = -6176;

        boolean roundUp =
            (totalResidue == GT_HALF || (totalResidue == HALF && (dw0T & 1L) != 0L));
        if (roundUp) {
            // apply rounding
            ++dw0T;
            dw1T += (dw0T == 0L) ? 1L : 0L;
            if (dw1T == MAXX_COEFF_34_HI && dw0T == MAXX_COEFF_34_LO) {
                dw1T = MIN_PRECISION_34_COEFF_HI;
                dw0T = MIN_PRECISION_34_COEFF_LO;
                ++qExpT;
            }
        }
        setFinite(out, formatIsBid, sign, qExpT, dw1T, dw0T);
    }

    /**
     * Residue classification for rounding decisions.
     *
     * <p>When discarding low-order digits during truncation to 34 significant
     * digits, the discarded part falls into one of four categories relative
     * to half a ulp of the retained value:
     *
     * <ul>
     *  <li>{@code EXACT}   — discarded part is zero; no rounding needed
     *  <li>{@code LT_HALF} — strictly less than half; round down (toward zero)
     *  <li>{@code HALF}    — exactly half; tie-break per active rounding direction
     *  <li>{@code GT_HALF} — strictly greater than half; round up (away from zero)
     * </ul>
     *
     * <p>For {@code roundTiesToEven}, {@code HALF} rounds to the nearest even retained
     * digit; {@code LT_HALF} and {@code GT_HALF} are unambiguous; {@code EXACT} is a no-op.
     */
    private static final int EXACT = 0;
    private static final int LT_HALF = 1;
    private static final int HALF = 2;
    private static final int GT_HALF = 3;

    /**
     * Lookup table mapping a discarded decimal digit (0..9) to its residue
     * class. Two bits per digit, packed low-to-high:
     *
     * <ul>
     *  <li>0       → {@code EXACT}   (discarded digit is zero)
     *  <li>1..4    → {@code LT_HALF} (digit &lt; 5)
     *  <li>5       → {@code HALF}    (digit is exactly 5)
     *  <li>6..9    → {@code GT_HALF} (digit &gt; 5)
     * </ul>
     */
    private static final int DIGIT_MAP = 0b11_11_11_11_10_01_01_01_01_00;

    // ---- DPD declet conversion -------------------------------------------
    //
    // A declet is 10 bits holding three decimal digits, 0..999. This is the
    // one place BID and DPD genuinely differ: BID stores the 34-digit
    // coefficient as a binary integer, DPD stores it as a leading digit plus
    // eleven declets.
    //
    // Both directions are pure boolean arithmetic (Cowlishaw's dpd2bcd and
    // bcd2dpd) rather than lookup tables. Measured on this codebase, the
    // arithmetic compiles to 2,814 bytes against 16,784 for the equivalent
    // tables -- the JVM has no array-literal constant, so a table becomes
    // per-element initialisation bytecode -- and it allocates nothing at
    // runtime and transcribes unchanged into the sibling ports.

    private static final long TEN_POW_15 = 1_000_000_000_000_000L;
    private static final long TEN_POW_18 = 1_000_000_000_000_000_000L;

    /**
     * Decode up to seven packed DPD declets into a binary integer.
     *
     * <p>Declets are ten bits each, least significant first. The topmost may be
     * narrower than ten bits, which is how the coefficient's leading digit is
     * carried: 0..9 encodes to itself as a declet.
     */
    private static long binFromDeclets(long declets) {
        long bin = 0L;
        long rest = declets;
        long multiplier = 1L;
        while (rest != 0L) {
            bin += dpdDecodeDeclet(rest & 0x3FFL) * multiplier;
            // The last iteration multiplies past 10^18 and the product is never
            // used, since rest is zero by then. The reference wraps here too.
            multiplier *= 1000L;
            rest = rest >>> 10;
        }
        return bin;
    }

    /**
     * Encode a binary integer of at most 18 digits into six packed declets,
     * least significant first. The inverse of {@link #binFromDeclets} over that
     * range.
     */
    private static long decletsFromBin(long bin) {
        assert bin >= 0L && bin < TEN_POW_18;
        long rest = bin;
        long declets = 0L;
        int shift = 0;
        while (rest != 0L) {
            // reciprocal-multiply divide by 1000
            long q = unsignedMulHi(rest, 0x020C49BA5E353F7DL) >>> 3;
            declets = declets | (dpdEncodeDeclet(rest - (q * 1000L)) << shift);
            shift += 10;
            rest = q;
        }
        return declets;
    }

    /**
     * Decode a 10-bit DPD declet (bits 9..0) into {@code 0..999}.
     *
     * <p>Mapping: bit9..bit0 as {@code p q r s t u v w x y}. Accepts the 24
     * non-canonical declets as well as the 1000 canonical ones, per
     * Cowlishaw's dpd2bcd.
     */
    private static long dpdDecodeDeclet(long declet) {
        assert declet >= 0L && declet <= 1023L;

        if (declet == 0L)
            return 0L;

        // Unpack to bits p..y
        long p = (declet >>> 9) & 1;
        long q = (declet >>> 8) & 1;
        long r = (declet >>> 7) & 1;
        long s = (declet >>> 6) & 1;
        long t = (declet >>> 5) & 1;
        long u = (declet >>> 4) & 1;
        long v = (declet >>> 3) & 1;
        long w = (declet >>> 2) & 1;
        long x = (declet >>> 1) & 1;
        long y = declet & 1;

        long ns = s ^ 1;
        long nx = x ^ 1;
        long nv = v ^ 1;
        long nw = w ^ 1;
        long nt = t ^ 1;

        long a = (v & w) & (ns | t | nx);
        long b = p & (nv | nw | (s & ~t & x));
        long c = q & (nv | nw | (s & ~t & x));
        long d = r;

        long e = v & ((nw & x) | (~t & x) | (s & x));
        long f = (s & (nv | nx)) | (p & ns & t & v & w & x);
        long g = (t & (nv | nx)) | (q & ns & t & w);
        long h = u;

        long i = v & ((nw & nx) | (w & x & (s | t)));
        long j = ((nv & w)
                | (s & v & nw & x)
                | (p & w & (nx | (ns & ~t))));
        long k = (nv & x) | (t & nw & x) | (q & v & w & (nx | (ns & nt)));
        long m = y;

        // Pack BCD digit bits → three digits
        long d2 = (a << 3) | (b << 2) | (c << 1) | d;
        long d1 = (e << 3) | (f << 2) | (g << 1) | h;
        long d0 = (i << 3) | (j << 2) | (k << 1) | m;

        return d2 * 100 + d1 * 10 + d0;
    }

    /**
     * Encode {@code 0..999} into a 10-bit DPD declet, per Cowlishaw's bcd2dpd.
     *
     * <p>The inverse of {@link #dpdDecodeDeclet} over canonical declets.
     * Non-canonical declets decode to a value that re-encodes to the canonical
     * declet for that value, so encode(decode(x)) == x only for canonical x.
     */
    private static long dpdEncodeDeclet(long n) {
        assert n >= 0L && n <= 999L;

        if (n == 0L)
            return 0L;

        // reciprocal-multiply digit extraction (optimized for n <= 999)
        long q10 = (n * 205) >>> 11;      // n / 10
        long q100 = (n * 41) >>> 12;      // n / 100

        long d2 = q100;
        long d1 = q10 - (q100 * 10);
        long d0 = n - (q10 * 10);

        // BCD bits (MSB→LSB: 8,4,2,1)
        long a = (d2 >> 3) & 1;
        long b = (d2 >> 2) & 1;
        long c = (d2 >> 1) & 1;
        long d = d2 & 1;

        long e = (d1 >> 3) & 1;
        long f = (d1 >> 2) & 1;
        long g = (d1 >> 1) & 1;
        long h = d1 & 1;

        long i = (d0 >> 3) & 1;
        long j = (d0 >> 2) & 1;
        long k = (d0 >> 1) & 1;
        long m = d0 & 1;

        long na = a ^ 1;
        long ne = e ^ 1;
        long ni = i ^ 1;

        long p = b | ((a & j) | ((a & f) & i));
        long q = c | ((a & k) | ((a & g) & i));
        long r = d;
        long s = (f & (na | ni)) | ((na & (e & j)) | (e & i));
        long t = g | ((na & (e & k)) | (a & i));
        long u = h;
        long v = a | (e | i);
        long w = a | ((e & i) | (ne & j));
        long x = e | ((a & i) | (na & k));
        long y = m;

        return (p << 9) | (q << 8) | (r << 7) | (s << 6) |
                (t << 5) | (u << 4) | (v << 3) | (w << 2) |
                (x << 1) | y;
    }

    /**
     * Classify a single discarded decimal digit (0..9) as {@code EXACT},
     * {@code LT_HALF}, {@code HALF}, or {@code GT_HALF} via lookup in
     * {@code DIGIT_MAP}.
     */
    private static int residueFromDecimalDigit(int digit) {
        return (DIGIT_MAP >> (digit << 1)) & 0x03;
    }

    /**
     * Combine a primary {@code roundResidue} with a {@code stickyResidue} from
     * less-significant digits, in the <b>round-bit-and-sticky-bit</b> style.
     * The sticky residue is collapsed to a single "nonzero" bit and OR'd
     * into the low bit of the round residue.
     */
    private static int residueMerge(int roundResidue, int stickyResidue) {
        int s = (stickyResidue & 1) | (stickyResidue >>> 1);
        int r = (roundResidue | s) & 0x03;
        return r;
    }

    /**
     * Classify a 128-bit unsigned value relative to half of {@code 10^pow10},
     * returning one of {@code EXACT}, {@code LT_HALF}, {@code HALF}, or
     * {@code GT_HALF}.
     *
     * <p>Used in the underflow boundary case where the entire coefficient is
     * being discarded by truncation: the residue is determined by where
     * the coefficient falls relative to half-of-the-truncation-divisor.
     *
     * <p>Looks up {@code 10^pow10} in the {@code POW10} table, halves it via a
     * 128-bit shift right by 1, and compares against the input as an unsigned
     * 128-bit value.
     */
    private static int residueFromValuePow10(long dw1, long dw0, int pow10) {
        if ((dw1 | dw0) == 0L)
            return EXACT;
        int pow10Index = pow10 << 1;
        long pow10Hi = POW10[pow10Index + 1];
        long pow10Lo = POW10[pow10Index];

        long halfHi = pow10Hi >>> 1;
        long halfLo = (pow10Hi << 63) | (pow10Lo >>> 1);
        assert dw1 >= 0 && halfHi >= 0;
        if (dw1 != halfHi)
            return (dw1 < halfHi) ? LT_HALF : GT_HALF;
        if (dw0 != halfLo)
            return (unsignedLT(dw0, halfLo)) ? LT_HALF : GT_HALF;
        return HALF;
    }

    /**
     * Powers of 10 from {@code 10^0} through {@code 10^34} as 128-bit unsigned
     * values in little-endian order: {@code POW10[2*i]} is the low 64 bits,
     * {@code POW10[2*i + 1]} the high 64 bits, of {@code 10^i}.
     *
     * <p>Sized to the coefficient bound and indexed directly -- no mask, no
     * padding. Every reachable index is at most 34: the binding case is
     * {@link #calcDigitLen128}, whose input is always a coefficient or NaN
     * payload below {@code 10^34}, so {@code bitLen <= 113} and
     * {@code (bitLen * 1233) >>> 12 <= 34}. It doubles as the {@code 5^n}
     * source for the divide kernel, since {@code 10^n >>> n} is exactly
     * {@code 5^n}; there is no POW5 table.
     */
    private static final long[] POW10 = {
        // little-endian order
        1L, 0L,                     // 1 (0)
        0x000000000000000AL, 0L,    // 10 (10**1)
        0x0000000000000064L, 0L,    // 100 (10**2)
        0x00000000000003E8L, 0L,    // 1000 (10**3)
        0x0000000000002710L, 0L,    // 10000 (10**4)
        0x00000000000186A0L, 0L,    // 100000 (10**5)
        0x00000000000F4240L, 0L,    // 1000000 (10**6)
        0x0000000000989680L, 0L,    // 10000000 (10**7)
        0x0000000005F5E100L, 0L,    // 100000000 (10**8)
        0x000000003B9ACA00L, 0L,    // 1000000000 (10**9)
        0x00000002540BE400L, 0L,    // 10000000000 (10**10)
        0x000000174876E800L, 0L,    // 100000000000 (10**11)
        0x000000E8D4A51000L, 0L,    // 1000000000000 (10**12)
        0x000009184E72A000L, 0L,    // 10000000000000 (10**13)
        0x00005AF3107A4000L, 0L,    // 100000000000000 (10**14)
        0x00038D7EA4C68000L, 0L,    // 1000000000000000 (10**15)
        0x002386F26FC10000L, 0L,    // 10000000000000000 (10**16)
        0x016345785D8A0000L, 0L,    // 100000000000000000 (10**17)
        0x0DE0B6B3A7640000L, 0L,    // 1000000000000000000 (10**18)
        0x8AC7230489E80000L, 0L,    // 10000000000000000000 (10**19)
        // minBitCount:64  maxBitCount:128
        0x6BC75E2D63100000L, 0x0000000000000005L, // 10**20
        0x35C9ADC5DEA00000L, 0x0000000000000036L, // 10**21
        0x19E0C9BAB2400000L, 0x000000000000021EL, // 10**22
        0x02C7E14AF6800000L, 0x000000000000152DL, // 10**23
        0x1BCECCEDA1000000L, 0x000000000000D3C2L, // 10**24
        0x161401484A000000L, 0x0000000000084595L, // 10**25
        0xDCC80CD2E4000000L, 0x000000000052B7D2L, // 10**26
        0x9FD0803CE8000000L, 0x00000000033B2E3CL, // 10**27
        0x3E25026110000000L, 0x00000000204FCE5EL, // 10**28
        0x6D7217CAA0000000L, 0x00000001431E0FAEL, // 10**29
        0x4674EDEA40000000L, 0x0000000C9F2C9CD0L, // 10**30
        0xC0914B2680000000L, 0x0000007E37BE2022L, // 10**31
        0x85ACEF8100000000L, 0x000004EE2D6D415BL, // 10**32
        0x38C15B0A00000000L, 0x0000314DC6448D93L, // 10**33
        0x378D8E6400000000L, 0x0001ED09BEAD87C0L, // 10**34
    };

    /**
     * Compute the bit-length of a 128-bit unsigned value: the position of
     * the highest set bit, plus one. Returns 0 for the zero value.
     */
    private static int calcBitLen128(long dw1, long dw0) {
        if (dw1 != 0L)
            return 128 - Long.numberOfLeadingZeros(dw1);
        if (dw0 != 0L)
            return 64 - Long.numberOfLeadingZeros(dw0);
        return 0;
    }

    /**
     * Compute the decimal digit count of a 128-bit unsigned value.
     *
     * <p>Uses the standard {@code log10/log2} approximation: for any non-zero
     * {@code bitLen}, {@code (bitLen * 1233) >>> 12} is {@code floor(bitLen * log10(2))},
     * which is either the exact digit count or one too small. A single
     * unsigned 128-bit compare against the next-larger power of 10 in
     * {@code POW10} resolves the off-by-one.
     */
    private static int calcDigitLen128(long dw1, long dw0) {
        int bitLen = calcBitLen128(dw1, dw0);
        int loDigitCount = (bitLen * 1233) >>> 12;
        int hiDigitCount = loDigitCount + 1;
        int pow10Offset = (loDigitCount << 1);
        long p1 = POW10[pow10Offset + 1];
        long p0 = POW10[pow10Offset];
        int cmp1 = unsignedCmp(dw1, p1);
        if (cmp1 != 0)
            return hiDigitCount - (cmp1 >>> 31);
        int cmp0 = unsignedCmp(dw0, p0);
        return hiDigitCount - (cmp0 >>> 31);
    }

    // -------- low level unsigned arithmetic functions --------------------------------------

    /**
     * Unsigned compare of two {@code long} values, by flipping the sign bit
     * before doing a signed compare.
     */
    private static int unsignedCmp(long x, long y) {
        return Long.compare(x ^ Long.MIN_VALUE, y ^ Long.MIN_VALUE);
    }

    /**
     * Unsigned less-than test for two {@code long} values.
     */
    private static boolean unsignedLT(long x, long y) {
        return (x ^ Long.MIN_VALUE) < (y ^ Long.MIN_VALUE);
    }

    /**
     * The high 64 bits of the 128-bit unsigned product {@code x * y}.
     *
     * <p>The sibling ports carry a hand-rolled 32x32 decomposition here, because
     * their targets have no such primitive to reach for. The JDK does:
     * {@code Math.unsignedMultiplyHigh} is this exact function, and HotSpot
     * intrinsifies it to the machine's widening multiply. Every call site is
     * unchanged; only the body is.
     */
    private static long unsignedMulHi(long x, long y) {
        return Math.unsignedMultiplyHigh(x, y);
    }

    /**
     * Multiply a 128-bit unsigned value by {@code 10^pow10} and add a 64-bit
     * integer {@code a}, writing the 128-bit result into {@code out[0]} (high)
     * and {@code out[1]} (low).
     */
    private static void u128FmaPow10(long[] out, long x, int pow10, long a) {
        long y = POW10[pow10 << 1];
        long prodLo = x * y;
        long prodHi = unsignedMulHi(x, y);
        long sumLo = prodLo + a;
        long sumHi = prodHi + ((unsignedLT(sumLo, prodLo)) ? 1L : 0L);
        out[0] = sumHi;
        out[1] = sumLo;
    }

    /**
     * Divide a 128-bit unsigned coefficient by {@code 10^precisionTruncationNeeded},
     * returning the quotient via {@code out} and the round-and-sticky
     * residue class as the return value. Truncates in chunks of up to 9
     * digits via {@link #u113DivModPow10Max9}.
     */
    private static int u128ScaleDownPow10(long[] out, long dw1, long dw0, int precisionTruncationNeeded) {
        assert (dw1 | dw0) != 0L;
        assert precisionTruncationNeeded > 0 && precisionTruncationNeeded < calcDigitLen128(dw1, dw0);
        int scaleResidue = EXACT;
        long dw1T = dw1;
        long dw0T = dw0;
        int digitsRemaining = precisionTruncationNeeded;
        do {
            int stepPow10 = Math.min(digitsRemaining, 9);
            long rem = u113DivModPow10Max9(out, dw1T, dw0T, stepPow10);
            dw1T = out[0];
            dw0T = out[1];
            int stepResidue = residueFromValuePow10(0L, rem, stepPow10);
            scaleResidue = residueMerge(stepResidue, scaleResidue);
            digitsRemaining -= stepPow10;
        } while (digitsRemaining > 0);
        return scaleResidue;
    }

    /**
     * Divide a 113-bit unsigned coefficient by {@code 10^pow10} for {@code pow10} in
     * {@code 1..9}. Factors {@code 10^pow10} as {@code 2^pow10 * 5^pow10}: the low
     * {@code pow10} bits are extracted directly as the low part of the remainder,
     * then the shifted value is divided by {@code 5^pow10} using two hardware
     * {@code long} divides.
     */
    private static long u113DivModPow10Max9(long[] quotLongs, long dwHi, long dwLo, int pow10) {
        assert pow10 >= 1 && pow10 <= 9;
        assert Long.numberOfLeadingZeros(dwHi) >= 15;

        long mask = (1L << pow10) - 1;
        long b = dwLo & mask;
        long sHi = dwHi >>> pow10;
        long sLo = (dwLo >>> pow10) | (dwHi << (64 - pow10));

        int k = 50 - pow10;
        long maskK = (1L << k) - 1;
        long hi63 = (sHi << (14 + pow10)) | (sLo >>> k);
        long loK = sLo & maskK;
        // remember ... this POW10 table has 2 slots per power, so double the index
        long d = POW10[pow10 << 1] >>> pow10;     // = 5^n
        long q1 = hi63 / d;
        long r1 = hi63 % d;
        long mid = (r1 << k) | loK;
        long q0 = mid / d;
        long r0 = mid % d;

        long qLo = (q1 << k) + q0;
        long qHi = q1 >>> (14 + pow10);
        long remainder = (r0 << pow10) | b;

        quotLongs[0] = qHi;
        quotLongs[1] = qLo;
        return remainder;
    }
}
