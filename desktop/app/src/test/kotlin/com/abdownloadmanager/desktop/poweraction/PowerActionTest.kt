package com.abdownloadmanager.desktop.poweraction

import ir.amirab.util.desktop.poweraction.PowerActionConfig
import ir.amirab.util.desktop.poweraction.PowerActionWindows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PowerActionTest {
    @Test
    fun windowsShutdownKeepsForceBehavior() {
        val commands = mutableListOf<List<String>>()
        val action = PowerActionWindows { command ->
            commands += command.toList()
            true
        }

        assertTrue(action.initiate(PowerActionConfig(PowerActionConfig.Type.Shutdown, force = true)))
        assertTrue(action.initiate(PowerActionConfig(PowerActionConfig.Type.Shutdown, force = false)))

        assertEquals(listOf("shutdown", "/s", "/t", "0", "/f"), commands[0])
        assertEquals(listOf("shutdown", "/s", "/t", "0"), commands[1])
    }

    @Test
    fun windowsHibernateUsesNativeCommandWithoutShutdownTimer() {
        val commands = mutableListOf<List<String>>()
        val action = PowerActionWindows { command ->
            commands += command.toList()
            true
        }

        assertTrue(action.initiate(PowerActionConfig(PowerActionConfig.Type.Hibernate, force = true)))

        assertEquals(listOf("shutdown", "/h"), commands.single())
    }

    @Test
    fun windowsSleepUsesSuspendStateWithHibernateDisabled() {
        val commands = mutableListOf<List<String>>()
        val action = PowerActionWindows { command ->
            commands += command.toList()
            true
        }

        assertTrue(action.initiate(PowerActionConfig(PowerActionConfig.Type.Sleep, force = false)))

        assertEquals("powershell.exe", commands[0].first())
        assertTrue(commands[0].contains("-NoProfile"))
        assertTrue(commands[0].contains("-NonInteractive"))
        assertFalse(commands[0].contains("-ExecutionPolicy"))
        assertFalse(commands[0].contains("Bypass"))
        assertTrue(commands[0].last().contains("SetSuspendState(\$false, \$false, \$false)"))
    }
}
