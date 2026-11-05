package ir.amirab.util.desktop.poweraction

import ir.amirab.util.execAndWait

class PowerActionWindows(
    private val execute: (Array<String>) -> Boolean = ::execAndWait,
) : PowerAction {
    override fun initiate(config: PowerActionConfig): Boolean {
        return when (config.type) {
            PowerActionConfig.Type.Shutdown -> shutdown(config.force)
            PowerActionConfig.Type.Hibernate -> execute(shutdownCommand("/h", config.force))
            PowerActionConfig.Type.Sleep -> execute(
                arrayOf("rundll32.exe", "powrprof.dll,SetSuspendState", "0", "1", "0")
            )
        }
    }

    private fun shutdown(force: Boolean): Boolean {
        return execute(shutdownCommand("/s", force))
    }

    private fun shutdownCommand(mode: String, force: Boolean): Array<String> {
        return buildList {
            add("shutdown")
            add(mode)
            add("/t")
            add("0")
            if (force) add("/f")
        }.toTypedArray()
    }
}
