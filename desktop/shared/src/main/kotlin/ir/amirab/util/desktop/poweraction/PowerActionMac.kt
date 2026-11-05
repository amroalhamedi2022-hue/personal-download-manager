package ir.amirab.util.desktop.poweraction

import ir.amirab.util.execAndWait

class PowerActionMac(
    private val execute: (Array<String>) -> Boolean = ::execAndWait,
) : PowerAction {
    override fun initiate(config: PowerActionConfig): Boolean {
        return when (config.type) {
            PowerActionConfig.Type.Shutdown -> shutdown()
            PowerActionConfig.Type.Hibernate -> execute(arrayOf("pmset", "sleepnow"))
            PowerActionConfig.Type.Sleep -> execute(
                arrayOf(
                    "osascript", "-e",
                    "tell application \"System Events\" to sleep",
                )
            )
        }
    }

    private fun shutdown(): Boolean {
        return execute(
            arrayOf("osascript", "-e", "tell application \"System Events\" to shut down")
        )
    }
}
