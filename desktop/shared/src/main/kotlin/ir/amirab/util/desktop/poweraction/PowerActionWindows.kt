package ir.amirab.util.desktop.poweraction

import ir.amirab.util.execAndWait

class PowerActionWindows(
    private val execute: (Array<String>) -> Boolean = ::execAndWait,
) : PowerAction {
    override fun initiate(config: PowerActionConfig): Boolean {
        return when (config.type) {
            PowerActionConfig.Type.Shutdown -> shutdown(config.force)
            PowerActionConfig.Type.Hibernate -> hibernate()
            PowerActionConfig.Type.Sleep -> sleep()
        }
    }

    private fun shutdown(force: Boolean): Boolean {
        val command = buildList {
            add("shutdown")
            add("/s")
            add("/t")
            add("0")
            if (force) add("/f")
        }.toTypedArray()
        return execute(command)
    }

    private fun hibernate(): Boolean {
        return execute(arrayOf("shutdown", "/h"))
    }

    /**
     * Requests suspend (sleep) via SetSuspendState with hibernate=false.
     * Windows power policy can still block sleep, and hybrid/power-management settings must permit suspend.
     */
    private fun sleep(): Boolean {
        return execute(
            arrayOf(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-Command",
                WINDOWS_SLEEP_COMMAND,
            )
        )
    }

    private companion object {
        const val WINDOWS_SLEEP_COMMAND =
            "Add-Type -Name HyperGetSleepBridge -Namespace HyperGet -MemberDefinition '[System.Runtime.InteropServices.DllImport(\"powrprof.dll\", SetLastError=true)] public static extern bool SetSuspendState(bool hibernate, bool forceCritical, bool disableWakeEvent);'; if (-not [HyperGet.HyperGetSleepBridge]::SetSuspendState(\$false, \$false, \$false)) { exit 1 }"
    }
}
