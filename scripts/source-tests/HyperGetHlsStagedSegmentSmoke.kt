import ir.amirab.downloader.destination.isStagedSegmentValid

fun main() {
    val cases = listOf(
        Triple(true, 1024L, 1024L) to true,
        Triple(true, 1023L, 1024L) to false,
        Triple(true, 1025L, 1024L) to false,
        Triple(true, 0L, 0L) to false,
        Triple(true, 123L, null) to true,
        Triple(true, 123L, -1L) to true,
        Triple(false, 123L, 123L) to false,
        Triple(true, -1L, null) to false,
    )
    cases.forEachIndexed { index, (input, expected) ->
        check(isStagedSegmentValid(input.first, input.second, input.third) == expected) {
            "HLS staged-segment validation case ${index + 1} failed"
        }
    }
    println("PASS ${cases.size}/${cases.size} isolated Kotlin staged-segment cases (NO APP BUILD)")
}
