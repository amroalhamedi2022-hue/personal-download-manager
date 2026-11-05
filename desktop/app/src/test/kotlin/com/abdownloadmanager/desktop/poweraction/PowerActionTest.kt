package com.abdownloadmanager.desktop.poweraction

import ir.amirab.util.desktop.poweraction.PowerActionConfig
import ir.amirab.util.desktop.poweraction.PowerActionLinux
import ir.amirab.util.desktop.poweraction.PowerActionMac
import ir.amirab.util.desktop.poweraction.PowerActionWindows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PowerActionTest {
    @Test
    fun windowsActionsUseNativeCommandsAndForceShutdown() {
        val commands = mutableListOf<List<String>>()
        val action = PowerActionWindows { command ->
            commands += command.toList()
            true
        }

        assertTrue(action.initiate(PowerActionConfig(PowerActionConfig.Type.Shutdown, force = true)))
        assertTrue(action.initiate(PowerActionConfig(PowerActionConfig.Type.Hibernate, force = false)))
        assertTrue(action.initiate(PowerActionConfig(PowerActionConfig.Type.Sleep, force = false)))

        assertEquals(listOf("shutdown", "/s", "/t", "0", "/f"), commands[0])
        assertEquals(listOf("shutdown", "/h", "/t", "0"), commands[1])
        assertEquals(
            listOf("rundll32.exe", "powrprof.dll,SetSuspendState", "0", "1", "0"),
            commands[2],
        )
    }

    @Test
    fun linuxActionsTryLogin1BeforeSystemctlAndPassForce() {
        val commands = mutableListOf<List<String>>()
        val action = PowerActionLinux { command ->
            commands += command.toList()
            commands.last().first() == "systemctl"
        }

        assertTrue(action.initiate(PowerActionConfig(PowerActionConfig.Type.Hibernate, force = true)))

        assertEquals("dbus-send", commands[0].first())
        assertTrue(commands[0].contains("org.freedesktop.login1.Manager.Hibernate"))
        assertTrue(commands[0].contains("boolean:true"))
        assertEquals(listOf("systemctl", "--force", "hibernate"), commands[1])
    }

    @Test
    fun macActionsMapSleepAndHibernateToSupportedNativeCommands() {
        val commands = mutableListOf<List<String>>()
        val action = PowerActionMac { command ->
            commands += command.toList()
            true
        }

        assertTrue(action.initiate(PowerActionConfig(PowerActionConfig.Type.Hibernate, force = false)))
        assertTrue(action.initiate(PowerActionConfig(PowerActionConfig.Type.Sleep, force = false)))

        assertEquals(listOf("pmset", "sleepnow"), commands[0])
        assertEquals("osascript", commands[1].first())
        assertTrue(commands[1].last().contains("sleep"))
    }
}
