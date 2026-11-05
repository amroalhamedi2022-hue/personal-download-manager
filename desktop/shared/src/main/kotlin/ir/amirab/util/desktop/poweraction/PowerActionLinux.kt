package ir.amirab.util.desktop.poweraction

import ir.amirab.util.execAndWait

class PowerActionLinux(
    private val execute: (Array<String>) -> Boolean = ::execAndWait,
) : PowerAction {
    override fun initiate(config: PowerActionConfig): Boolean {
        return when (config.type) {
            PowerActionConfig.Type.Shutdown -> shutdown(config.force)
            PowerActionConfig.Type.Hibernate -> transition("Hibernate", "hibernate", config.force)
            PowerActionConfig.Type.Sleep -> transition("Suspend", "suspend", config.force)
        }
    }

    private fun shutdown(force: Boolean): Boolean {
        return transition("PowerOff", "poweroff", force)
    }

    private fun transition(
        dbusMethod: String,
        systemctlVerb: String,
        force: Boolean,
    ): Boolean {
        val dbusCommand = arrayOf(
            "dbus-send", "--system", "--print-reply",
            "--dest=org.freedesktop.login1",
            "/org/freedesktop/login1",
            "org.freedesktop.login1.Manager.$dbusMethod",
            "boolean:$force",
        )
        val systemctlCommand = buildList {
            add("systemctl")
            if (force) add("--force")
            add(systemctlVerb)
        }.toTypedArray()

        return listOf(dbusCommand, systemctlCommand).any { command ->
            runCatching { execute(command) }.getOrElse { false }
        }
    }
}
