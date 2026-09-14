# Tiamat — Sleela edition

Sleela (Wrapper™) versions of the Tiamat Java source files. Each `.sleela` file
is the counterpart of the like-named `.java` file in the parent `source/`
directory, written in the [Sleela language](https://github.com/mearvk/SLeeLa)
— a small, Java-like language that compiles to the SLeeLa C/C++ core.

| Sleela file | Java counterpart | What it ports |
|-------------|------------------|---------------|
| `Main.sleela` | `Main.java` | Entry point + project-lineage constants, orchestration narrative |
| `CommonRails.sleela` | `CommonRails.java` | Professional component line + 21×21 progress square + single pixel |
| `AIModule.sleela` | `AIModule.java` | Origins/description document generation (dragon-lore training entries) |
| `AIVocabulary.sleela` | `AIVocabulary.java` | 100,000-word domain model, learner strips, `suggestFilename`, `getStats` |
| `ImageClassifier.sleela` | `ImageClassifier.java` | Relevance scoring model + accept/reject filter |
| `ImageDownloadStrategy.sleela` | `ImageDownloadStrategy.java` | Download-path relevance scorer + dimension/size filter gate |
| `SearchEngineClient.sleela` | `SearchEngineClient.java` | Importance→strategy selection + bounded redirect-follow loop |
| `PageImageDiagnostic.sleela` | `PageImageDiagnostic.java` | Image-yield diagnostic: yield %, missed-reason breakdown |
| `PagePrinter.sleela` | `PagePrinter.java` | Page summary + image diagnostic + skip/success/failed lines |
| `RedirectPrinter.sleela` | `RedirectPrinter.java` | Redirect-chain event lines |

## Running

Build the Sleela toolchain from the [SLeeLa repo](https://github.com/mearvk/SLeeLa)
(`cd impl && make`), then, from that repo:

```sh
./impl/build/sleela check <path>/AIVocabulary.sleela   # validate (incl. #sleela version)
./impl/build/sleela run   <path>/AIVocabulary.sleela   # run on the C/C++ core
```

Every file declares `#sleela 1.1` and passes `sleela check`; each has a `main()`
and runs.

## Fidelity notes — what was adapted, and why

The Sleela surface (per the SLeeLa spec / `impl/README.md`) is a small Java
subset: `int/double/boolean/String/void`, `if/else`, `while/for`, arithmetic and
comparisons, `&&/||/!`, `+` string concatenation, `print(...)`, recursion, and
class fields. It has **no** arrays, collections, objects, regex, file/network
I/O, ANSI escapes, or substring/length string operations — the string lexer
decodes only `\n \t \r \\ \"`.

Ports therefore preserve each class's **algorithmic core** rather than its
library plumbing:

- **Numeric models are exact.** The relevance scorers (`ImageClassifier`,
  `ImageDownloadStrategy`), the yield math (`PageImageDiagnostic`), the
  importance→strategy bands (`SearchEngineClient`), and the vocabulary totals
  (`AIVocabulary`) use the identical weights, bonuses, penalties, `min`/`max`
  clamps, and thresholds as the Java code.
- **String scanning becomes signals.** Where Java derived facts by scanning a
  URL/page with `String.contains()`/regex, the Sleela method takes those
  detected signals (keyword-hit counts, boolean flags, an inferred quality tier,
  a matched-strip id) as parameters — so the computation is identical for
  identical inputs, without needing substring search.
- **I/O becomes `print`.** File listing, JSON parsing, HTTP fetching, and
  append-only Markdown writes are represented by passed-in counts and `print`
  output (stdout stands in for the file writes / network responses).
- **ANSI color becomes text.** Color tiers (green/yellow/red yield, orange/white
  square cells) are rendered as text labels (`GOOD`/`MODERATE`/`POOR`) or raw
  UTF-8 block glyphs (`█`/`░`), since the core has no color output.

Each file's header comment states its specific adaptations.
