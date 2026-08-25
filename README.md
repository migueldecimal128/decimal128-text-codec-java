# decimal128-text-codec-java

A thin codec between the two IEEE 754-2019 decimal128 interchange encodings —
BID and DPD — and decimal character sequences. Nothing else: no arithmetic, no
comparison, no value type, no context, no status flags.

One Java file, one class, zero runtime dependencies. The point is that a caller
who holds interchange bits or a string, and wants the other one, can depend on
this without pulling in a full IEEE 754-2019 engine.

For the complete decimal128 implementation see `decimal128-java` at
<https://decimal128.com>.

## API

```java
import com.decimal128.textcodec.Decimal128TextCodec;

// bits -> text
String s = Decimal128TextCodec.bid128ToString(hi, lo);   // cannot fail
String d = Decimal128TextCodec.dpd128ToString(hi, lo);

byte[] buf = new byte[Decimal128TextCodec.MAX_LEN];
int n = Decimal128TextCodec.bid128ToAscii(buf, hi, lo);   // the text is buf[0..n)

// text -> bits; returns null on success, an error message on failure
long[] bits = new long[2];
String err = Decimal128TextCodec.bid128Parse(bits, "1.23E+45");
String errD = Decimal128TextCodec.dpd128Parse(bits, "1.23E+45");
```

An encoding is a pair of `long`s: index 0 is the high 64 bits, index 1 the low
64. The sign bit is the top bit of index 0, and the rest is a combination field
and a trailing significand field laid out per IEEE 754-2019 §3.5.2. That is the
entire boundary; there is no value type, no context, no status flags. Both
parse methods **always write** `out` — the canonical positive quiet NaN on
failure — so a caller who ignores the return value still observes a legal IEEE
754-2019 datum. The error message is human-readable; its exact wording is not
part of the contract.

The `ToString` renderers return an owned `String`; their `ToAscii` peers write
into a caller-owned `byte[]` and return a length, for callers who want no
allocation. `MAX_LEN` is 42, the exact bound on a rendered decimal128, and the
buffer must be at least that long.

## Behavior

**Rendering cannot fail.** Every 128-bit pattern maps to a valid decimal
character sequence. A non-canonical BID coefficient (≥ 10³⁴) renders as zero at
the encoded exponent, per IEEE 754-2019 §3.5.2, and a NaN payload ≥ 10³³
renders as no payload. Those patterns never arise from this codec's own encode
direction — they arise from network and file data, which is why the codec
handles them.

**Output is the GDAS `to-scientific-string` form, and only that.** Integer form
when the quantum exponent is zero, plain form when the adjusted exponent is in
`[-6, 0]`, scientific otherwise. The exponent sign is always written:
`6.02E+23`, never `6.02E23`. Cohort information is preserved — `1.00` and `1.0`
are distinct outputs from distinct encodings of the same numerical value, and
nothing is ever normalized or stripped.

**Accepted input** matches what the full `decimal128-{lang}` ports accept, with
one deliberate exception below, so a caller can move between this codec and the
full library without the grammar shifting under them: plain and scientific
notation with `E` or `e`, a leading `+` or `-`, `_` digit separators *between
two digits*, and the case-insensitive words `Infinity`, `Inf`, `NaN`, `qNaN`,
`sNaN` with an optional sign and an optional payload of decimal digits.

**The exception: a NaN payload may be parenthesised** — `NaN(123)` as well as
`NaN123`. C's own `strtod` reads `NAN(n-char-sequence)`, so the spelling
reaches a caller from the platform itself. The form is positional and balanced:
`(` immediately after the word, at least one digit, `)` last. So `NaN()`,
`NaN(1`, `NaN(1]`, `NaN(1)2(3)`, `NaN (1)`, `NaN[1]` and `NaN{1}` are all
rejected. The resemblance to `strtod` stops at the parentheses — its sequence
is letters, digits and underscores read as an implementation-defined integer,
so `NAN(abc)` and `NAN(0x1c8)` are legal there and rejected here. Rendering
never emits parentheses; `NaN(123)` parses and renders back as `NaN123`.

The full ports accept the same spelling — `decimal128-java` took it on
2026-08-23, closing what had been the codecs' one grammar divergence from them.

**Rounding is ties-to-even, hardcoded**, and reachable only from the text →
bits direction: more than 34 significant digits, overflow to Infinity, or
underflow to a subnormal or zero. There is no rounding parameter and no format
selector — the premise is a four-function surface with no configuration. IEEE
754-2019 §5.4.2 places `convertFromDecimalCharacter` under the applicable
rounding-direction attribute; this codec deliberately implements only the
ties-to-even case, and is therefore a conforming **subset** of that operation.
Callers needing another rounding direction want the full library.

## Building

```
./gradlew build
./gradlew test
```

Java 21, no runtime dependencies. The only version-sensitive call is
`Math.unsignedMultiplyHigh`, which arrived in Java 18.

Internal-consistency claims — the ones unreachable for any input — are spelled
as `assert`, which is this language's form of the compile-time `verify` flag
the sibling ports use, and shares the property that matters: the expression is
not evaluated when the checks are off. The default is off, because a consumer
compiles this class into their own program and must not take a throw inside a
library on data they do not control; Gradle arms assertions on test tasks, and
the build states it rather than relying on the default, so the whole corpus
runs with every claim live. The one check that must survive into a shipping
build — the coefficient-width invariant standing in for the removed
precision-truncation step — is an unconditional throw instead.

## Tests

Seven tests across two classes in one file. The canonical table is its own
class but shares a file with the other tables, so the two table runners they
both use stay package-private to it — the same layout the C, Swift, Kotlin and
C# ports use.

| file | what it covers |
|---|---|
| `src/test/java/.../TestCorpusVectors.java` | `TestCorpusVectors` — the four conformance tables over the shared corpus, 2,601 rows, both directions, both formats. Also holds the corpus reader, the loader and the hex helpers, its only consumer. |
| | `TestCanonicalCorpus` — the full 84,783-row canonical table |

That is the whole suite, and the shape is deliberate: **every test vector in
this repo comes from the shared corpus.** Nothing in the suite reaches past the
public surface.

The repo is standalone: it depends on no decimal128 port, at runtime or in
tests. The independent oracle is the shared `text_bid_dpd` corpus, generated by
the `decimal128-c` engine through its public API; that engine reaches its
answers by different algorithms, so agreement is a cross-check rather than a
tautology.

The vectors are read from `resources/text_bid_dpd`, where `resources` is the
`decimal128-resources` submodule — the same mount every decimal128 port uses.
Nothing is copied into this repo, so there is no drift class to police, and
nothing is copied onto the test classpath either: the loader reads the
submodule working tree directly. The build hands the test task the absolute
corpus root in `DECIMAL128_RESOURCES`, so the runner's working directory does
not matter.

Two things worth knowing if you touch the tests:

- **The corpus quotes two of its five files.** `parse` and `error` double-quote
  the text column with `\\` and `\"` escapes; the other three leave it bare. A
  naive comma split silently corrupts four rows and still passes, because a
  malformed input fails to parse either way. The reader honours the quoting,
  the row counts are asserted, and `quotedColumnRoundTrips` checks those four
  rows by hand.
- **The submodule must be checked out.** With `resources/text_bid_dpd` empty
  the loader fails with a message naming the `git submodule update --init
  --recursive` that fixes it, rather than a suite that quietly tests nothing.
  Clone with `--recurse-submodules`.

## Provenance

Ported from `decimal128-text-codec-kotlin`, which is the reference
implementation for the family and itself descends from
`decimal128-kotlin-legacy/decimal128-bid-string-codec`. The design spec is
`TextCodecLayer.md` in the `decimal128-workorder` repository.

Where this port differs from the reference, in each case because the JDK
already had the answer:

- **`unsignedMulHi` is `Math.unsignedMultiplyHigh`.** The reference and most of
  the siblings carry a hand-rolled 32×32 decomposition because their targets
  have no such primitive; HotSpot intrinsifies the JDK one to the machine's
  widening multiply. Every call site is identical to theirs; only that one body
  is not.
- **The state assertions are `assert`,** not a build-flag-gated `verify`. It is
  the same contract — off by default, not evaluated when off — spelled the way
  the language spells it.

Everything else, including the signed `Long` dwords and the `long[]` the
finalize chain threads as scratch, is the reference's shape unchanged: the JVM
is the platform both were written for.

The finalizer's precision-truncation step is removed, in this port and in the
reference alike. It descends from a finalizer built for an arithmetic engine,
whose multiply and FMA hand it a double-width product; a codec's only caller is
a parser that accumulates at most 19 + 15 = 34 significant digits and turns
everything past them into a residue. Measured on the C port: zero executions
across the shared corpus, 400,000 random numerals, and 76,280 finalize calls
from inputs of up to 5,000 digits. A dead path is an unverified path, so it is
replaced by an unconditional throw. It stays live in every full port's
`Finalize`, where multiply and FMA do reach it; a caller here that is not a
parser needs it back.

The codec stands on the interchange formats alone. It holds no intermediate
value type: parsing yields loose scalars — sign, quantum exponent, coefficient,
residue — which are finalized and written *toward* an encoding. It does not
pivot through any engine's in-memory representation, and must not be changed
to.

## License

MIT.
