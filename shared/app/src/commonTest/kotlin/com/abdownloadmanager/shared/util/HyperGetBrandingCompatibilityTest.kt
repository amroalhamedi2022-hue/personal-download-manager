package com.abdownloadmanager.shared.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * HyperGet is a display-only rename until the Windows upgrade and native-messaging
 * compatibility gates are exercised. Do not conflate user-facing copy with stable IDs.
 */
class HyperGetBrandingCompatibilityTest {
    @Test
    fun userFacingDisplayNameIsHyperGet() {
        assertEquals("HyperGet", SharedConstants.appDisplayName)
    }

    @Test
    fun legacyRuntimeAndStorageIdentifiersAreNotRebranded() {
        assertNotEquals("HyperGet", SharedConstants.appName)
        assertNotEquals("HyperGet", SharedConstants.dataDirName)
        assertNotEquals("HyperGet", SharedConstants.packageName)
        assertTrue(SharedConstants.appName.isNotBlank())
        assertTrue(SharedConstants.dataDirName.isNotBlank())
        assertTrue(SharedConstants.packageName.isNotBlank())
    }
}
