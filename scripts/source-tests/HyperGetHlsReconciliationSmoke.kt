package ir.amirab.downloader.downloaditem.hls

/**
 * Source-only Kotlin/JVM regression harness. Compile ONLY this file and
 * HlsPlaylistReconciliation.kt with kotlinc; do not invoke Gradle or package.
 */
fun main() {
    var checks = 0
    fun verify(value: Boolean, label: String) { check(value) { label }; checks++ }
    val old = listOf(
        HlsSegmentSnapshot(7, "s7.ts?token=old", 4.0, completed = true, length = 42, current = 42),
        HlsSegmentSnapshot(8, "s8.ts", 5.0, completed = true, length = 50, current = 50),
    )
    val same = reconcileHlsSegments(old, listOf(HlsSegmentSnapshot(7, "s7.ts?token=old", 4.0)))
    verify(same.single().completed, "same segment keeps completion")
    verify(same.single().length == 42L, "same segment keeps length")
    verify(same.single().current == 42L, "same segment keeps progress")
    val changedUrl = reconcileHlsSegments(old, listOf(HlsSegmentSnapshot(7, "s7.ts?token=new", 4.0)))
    verify(!changedUrl.single().completed, "changed signed URI must reset completion")
    verify(changedUrl.single().length == null && changedUrl.single().current == 0L, "changed URI must reset metadata")
    val changedDuration = reconcileHlsSegments(old, listOf(HlsSegmentSnapshot(8, "s8.ts", 5.5)))
    verify(!changedDuration.single().completed, "changed duration must reset completion")
    val differentSequence = reconcileHlsSegments(old, listOf(HlsSegmentSnapshot(9, "s8.ts", 5.0)))
    verify(!differentSequence.single().completed, "different sequence must not reuse completion")
    val reorder = reconcileHlsSegments(old, listOf(HlsSegmentSnapshot(8, "s8.ts", 5.0), HlsSegmentSnapshot(7, "s7.ts?token=old", 4.0)))
    verify(reorder.map { it.sequence } == listOf(8L, 7L), "incoming order must be adopted")
    verify(reorder.all { it.completed }, "safe reuse in reordered playlist")
    verify(reconcileHlsSegments(old, emptyList()).isEmpty(), "empty playlist removes obsolete segments")
    verify(reconcileHlsSegments(emptyList(), listOf(HlsSegmentSnapshot(1, "a", 1.0))).size == 1, "new playlist populated")
    println("PASS $checks/$checks HyperGet HLS reconciliation source-only tests")
}
