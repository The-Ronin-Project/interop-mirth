package com.projectronin.interop.mirth.channel.base.kafka.request

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResourceRequestKeyTest {
    private val tenant =
        mockk<Tenant> {
            every { mnemonic } returns "tenant"
        }

    @Test
    fun `creates unlocalized resource ID`() {
        val key = ResourceRequestKey("run1", ResourceType.Location, tenant, "tenant-1234")
        assertEquals("1234", key.unlocalizedResourceId)
    }

    @Test
    fun `toString returns proper details`() {
        val key = ResourceRequestKey("run1", ResourceType.Location, tenant, "tenant-1234")
        assertEquals("run1:Location:null:tenant:1234", key.toString())
    }

    @Test
    fun `equals is true for exact object`() {
        val key = ResourceRequestKey("run1", ResourceType.Location, tenant, "tenant-1234")
        assertTrue(key.equals(key))
    }

    @Test
    fun `equals is false for different classes`() {
        val key = ResourceRequestKey("run1", ResourceType.Location, tenant, "tenant-1234")
        assertFalse(key.equals("Hello"))
    }

    @Test
    fun `uses run ID in equals`() {
        val key = ResourceRequestKey("run1", ResourceType.Location, tenant, "tenant-1234")
        val sameRunId = ResourceRequestKey("run1", ResourceType.Location, tenant, "tenant-1234")
        assertTrue(key.equals(sameRunId))

        val differentRunId = ResourceRequestKey("run2", ResourceType.Location, tenant, "tenant-1234")
        assertFalse(key.equals(differentRunId))
    }

    @Test
    fun `uses resource type in equals`() {
        val key = ResourceRequestKey("run1", ResourceType.Location, tenant, "tenant-1234")
        val sameResourceType = ResourceRequestKey("run1", ResourceType.Location, tenant, "tenant-1234")
        assertTrue(key.equals(sameResourceType))

        val differentResourceType = ResourceRequestKey("run1", ResourceType.Patient, tenant, "tenant-1234")
        assertFalse(key.equals(differentResourceType))
    }

    @Test
    fun `uses mnemonic from tenant in equals`() {
        val key = ResourceRequestKey("run1", ResourceType.Location, tenant, "tenant-1234")
        val sameTenant = ResourceRequestKey("run1", ResourceType.Location, tenant, "tenant-1234")
        assertTrue(key.equals(sameTenant))

        val tenant2 =
            mockk<Tenant> {
                every { mnemonic } returns "different"
            }
        val differentTenant = ResourceRequestKey("run1", ResourceType.Location, tenant2, "tenant-1234")
        assertFalse(key.equals(differentTenant))

        val tenant3 =
            mockk<Tenant> {
                every { mnemonic } returns tenant.mnemonic
            }
        val differentTenantWithSameMnemonic = ResourceRequestKey("run1", ResourceType.Location, tenant3, "tenant-1234")
        assertTrue(key.equals(differentTenantWithSameMnemonic))
    }

    @Test
    fun `uses unlocalized resource ID in equals`() {
        val key = ResourceRequestKey("run1", ResourceType.Location, tenant, "tenant-1234")
        val sameResourceId = ResourceRequestKey("run1", ResourceType.Location, tenant, "tenant-1234")
        assertTrue(key.equals(sameResourceId))

        val differentResourceId = ResourceRequestKey("run1", ResourceType.Patient, tenant, "tenant-5678")
        assertFalse(key.equals(differentResourceId))

        val sameUnlocalizedResourceId = ResourceRequestKey("run1", ResourceType.Location, tenant, "1234")
        assertTrue(key.equals(sameUnlocalizedResourceId))
    }

    @Test
    fun `only uses mnemonic from tenant in hashCode`() {
        val key = ResourceRequestKey("run1", ResourceType.Location, tenant, "tenant-1234")
        val sameTenant = ResourceRequestKey("run1", ResourceType.Location, tenant, "tenant-1234")
        assertEquals(key.hashCode(), sameTenant.hashCode())

        val tenant2 =
            mockk<Tenant> {
                every { mnemonic } returns "different"
            }
        val differentTenant = ResourceRequestKey("run1", ResourceType.Location, tenant2, "tenant-1234")
        assertNotEquals(key.hashCode(), differentTenant.hashCode())

        val tenant3 =
            mockk<Tenant> {
                every { mnemonic } returns tenant.mnemonic
            }
        val differentTenantWithSameMnemonic = ResourceRequestKey("run1", ResourceType.Location, tenant3, "tenant-1234")
        assertEquals(key.hashCode(), differentTenantWithSameMnemonic.hashCode())
    }

    @Test
    fun `uses unlocalized resource ID in hashCode`() {
        val key = ResourceRequestKey("run1", ResourceType.Location, tenant, "tenant-1234")
        val sameResourceId = ResourceRequestKey("run1", ResourceType.Location, tenant, "tenant-1234")
        assertEquals(key.hashCode(), sameResourceId.hashCode())

        val differentResourceId = ResourceRequestKey("run1", ResourceType.Patient, tenant, "tenant-5678")
        assertNotEquals(key.hashCode(), differentResourceId.hashCode())

        val sameUnlocalizedResourceId = ResourceRequestKey("run1", ResourceType.Location, tenant, "1234")
        assertEquals(key.hashCode(), sameUnlocalizedResourceId.hashCode())
    }
}
