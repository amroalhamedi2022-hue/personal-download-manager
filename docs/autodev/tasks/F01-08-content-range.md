# F01-08 / P2: Reject malformed Content-Range responses safely

Modify **only** these exact paths:
1. downloader/core/src/commonMain/kotlin/ir/amirab/downloader/connection/response/headers/RangeHeaderExtractor.kt
2. downloader/core/src/desktopTest/kotlin/ir/amirab/downloader/connection/response/headers/RangeHeaderExtractorTest.kt (create it)

This is real production correctness work, NOT documentation-only or tests-only.

Read the source and relevant caller HttpPartDownloader.kt before editing.
The current getContentRange() builds LongRange even for reversed/negative ranges,
accepts extra slash/hyphen fields, and may accept an end beyond full size.
Harden the parser against these invalid headers. Preserve legitimate existing
behavior: valid "bytes 0-9/10", missing "bytes " prefix (legacy servers),
valid "bytes 0-9/*" and unsatisfied 416 response "bytes */10" (range=null,
fullSize=10). Reject start<0, end<start, end>=fullSize when fullSize is known,
negative full sizes, overflow, malformed separators, unexpected units,
multiple ranges, and blank/trailing junk. Do not use unchecked exceptions.

Add JVM desktop tests in the new RangeHeaderExtractorTest.kt.
MUST include EXACT Kotlin function name
  fun contentRangeRejectsReversedBounds()
and other meaningful tests for valid requests, unknown totals, unsatisfied
ranges, malicious/malformed input, Long overflow, and whitespace handling.
Construct actual HttpResponseInfo and exercise its contentRange property.

Do not modify downloader workflow, threshold, core benchmark, aria2, themes,
UI or any file other than the two allowlisted paths. Do not weaken the parser
to make a test pass, or change validation behavior outside Content-Range.
Use kotlin.test and project conventions. No claims of tests passing until
actual Windows CI completes. If task already done on latest main, no edit.
