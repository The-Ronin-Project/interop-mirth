package com.projectronin.interop.mirth.channels

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.fhir.generators.datatypes.reference
import com.projectronin.interop.fhir.generators.resources.carePlan
import com.projectronin.interop.fhir.generators.resources.carePlanActivity
import com.projectronin.interop.fhir.generators.resources.requestGroup
import com.projectronin.interop.fhir.r4.datatype.primitive.Code
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channels.client.KafkaClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.mirth.requestGroupLoadChannelName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RequestGroupLoadTest : BaseChannelTest(
    requestGroupLoadChannelName,
    listOf("CarePlan", "RequestGroup"),
    listOf("CarePlan", "RequestGroup")
) {
    @Test
    fun `channel works`() {
        tenantInUse = testTenant

        val fakerRequestGroup = requestGroup {
            intent of Code("plan")
            status of Code("active")
            subject of reference("Patient", "123")
        }
        val fakeRequestGroupId = MockEHRTestData.add(fakerRequestGroup)

        val fakeCarePlan = carePlan {
            id of Id("123") // why isn't this automatically generated?
            activity of listOf(
                carePlanActivity {
                    reference of reference("RequestGroup", fakeRequestGroupId)
                }
            )
            status of Code("active") // generator isn't set up right so we need to override this
        }
        MockEHRTestData.add(fakeCarePlan)
        MockOCIServerClient.createExpectations("RequestGroup", fakeRequestGroupId, tenantInUse)

        KafkaClient.testingClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(fakeCarePlan)
        )

        waitForMessage(1)
        assertEquals(1, getAidboxResourceCount("RequestGroup"))
    }

    @Test
    fun `channel works for ad-hoc requests`() {
        tenantInUse = testTenant
        // mock: practitioner at the EHR
        val fakerRequestGroup = requestGroup {
            intent of Code("plan")
            status of Code("active")
            subject of reference("Patient", "123")
        }
        val fakeRequestGroupId = MockEHRTestData.add(fakerRequestGroup)
        MockOCIServerClient.createExpectations("RequestGroup", fakeRequestGroupId, tenantInUse)

        KafkaClient.testingClient.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf(fakeRequestGroupId),
            resourceType = ResourceType.RequestGroup
        )
        waitForMessage(1)
        assertEquals(1, getAidboxResourceCount("RequestGroup"))
    }

    @Test
    fun `cerner is not supported`() {
        tenantInUse = "cernmock"
        KafkaClient.testingClient.pushLoadEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf("123"),
            resourceType = ResourceType.RequestGroup
        )
        waitForMessage(1)
        assertEquals(0, getAidboxResourceCount("RequestGroup"))
    }

    @Test
    fun `non-existent request errors`() {
        KafkaClient.testingClient.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf("doesn't exists"),
            resourceType = ResourceType.RequestGroup
        )
        waitForMessage(1)
        assertEquals(0, getAidboxResourceCount("RequestGroup"))
    }
}
