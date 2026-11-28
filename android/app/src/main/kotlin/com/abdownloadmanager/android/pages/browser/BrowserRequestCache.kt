package com.abdownloadmanager.android.pages.browser

/** Bounded, single-consumption request metadata, isolated by browser tab and exact URL. */
internal class BrowserRequestCache<T>(
    private val maxEntries: Int = 512,
    private val ttlMillis: Long = 20_000,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private data class Key(val tabId: String, val url: String)
    private data class Entry<T>(val value: T, val time: Long)
    private val entries = LinkedHashMap<Key, Entry<T>>()

    init {
        require(maxEntries > 0 && ttlMillis > 0)
    }

    @Synchronized
    fun put(tabId: String, url: String, value: T) {
        val now = nowMillis()
        entries.entries.removeAll { now - it.value.time >= ttlMillis }
        val key = Key(tabId, url)
        entries.remove(key)
        entries[key] = Entry(value, now)
        while (entries.size > maxEntries) entries.remove(entries.keys.first())
    }

    @Synchronized
    fun take(tabId: String, url: String): T? {
        val entry = entries.remove(Key(tabId, url)) ?: return null
        return entry.value.takeIf { nowMillis() - entry.time < ttlMillis }
    }

    @Synchronized
    fun clearTab(tabId: String) {
        entries.keys.removeAll { it.tabId == tabId }
    }
}
