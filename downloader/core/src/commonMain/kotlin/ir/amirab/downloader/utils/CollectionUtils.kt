package ir.amirab.downloader.utils

internal fun <T>MutableList<T>.swap(
    index: Int,toPosition: Int
): MutableList<T> =apply {
    val p=set(toPosition,this[index])
    set(index,p)
}

internal fun <T>List<T>.swapped(index:Int, toPosition: Int): List<T> {
    val l=toMutableList()
    l.swap(index,toPosition)
    return l.toList()
}
internal fun <T>Set<T>.swapped(a:T, b: T): Set<T> {
    val l=toMutableList()
    val indexA=indexOf(a)
    val indexB=indexOf(b)
    // A missing member must not turn a harmless swap into an index error.
    // Preserve the original set when either element is absent.
    if (indexA < 0 || indexB < 0) return this
    val tmp=l.set(indexB,l[indexA])
    l.set(indexA,tmp)
    return l.toList().toSet()
}