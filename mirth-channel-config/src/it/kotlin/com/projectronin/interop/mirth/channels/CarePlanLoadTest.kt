package com.projectronin.interop.mirth.channels

import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.fhir.generators.datatypes.DynamicValues
import com.projectronin.interop.fhir.generators.datatypes.codeableConcept
import com.projectronin.interop.fhir.generators.datatypes.coding
import com.projectronin.interop.fhir.generators.datatypes.extension
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.datatypes.name
import com.projectronin.interop.fhir.generators.datatypes.period
import com.projectronin.interop.fhir.generators.datatypes.reference
import com.projectronin.interop.fhir.generators.primitives.date
import com.projectronin.interop.fhir.generators.primitives.dateTime
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.carePlan
import com.projectronin.interop.fhir.generators.resources.carePlanActivity
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.CarePlan
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channels.client.AidboxTestData
import com.projectronin.interop.mirth.channels.client.KafkaClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.fhirIdentifier
import com.projectronin.interop.mirth.channels.client.mirth.CARE_PLAN_LOAD_CHANNEL_NAME
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

class CarePlanLoadTest : BaseChannelTest(
    CARE_PLAN_LOAD_CHANNEL_NAME,
    listOf("Patient", "CarePlan", "Location"),
    listOf("Patient", "CarePlan", "Location"),
) {
    val twoDaysAgo = LocalDateTime.now().minusDays(2)
    val metadata1 =
        Metadata(
            runId = "123456",
            runDateTime = OffsetDateTime.now(),
            targetedResources = emptyList(),
        )

    private fun createFakeCarePlan(
        patientId: String,
        tenantInUse: String,
    ): CarePlan {
        return carePlan {
            category of
                listOf(
                    codeableConcept {
                        coding of
                            listOf(
                                coding {
                                    system of "http://hl7.org/fhir/us/core/CodeSystem/careplan-category"
                                    code of
                                        if (tenantInUse.contains("cern")) {
                                            "assess-plan"
                                        } else {
                                            "736378000"
                                        }
                                },
                            )
                    },
                )
            status of "active"
            subject of reference("Patient", patientId)
            period of
                period {
                    start of
                        dateTime {
                            year of twoDaysAgo.year
                            month of twoDaysAgo.month.value
                            day of twoDaysAgo.dayOfMonth
                        }
                    end of
                        dateTime {
                            year of twoDaysAgo.year
                            month of twoDaysAgo.month.value
                            day of twoDaysAgo.dayOfMonth
                        }
                }
        }
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `repeat patients are ignored`(testTenant: String) {
        tenantInUse = testTenant
        val fakePatient =
            patient {
                birthDate of
                    date {
                        year of 1990
                        month of 1
                        day of 3
                    }
                identifier of
                    listOf(
                        identifier {
                            system of "mockPatientInternalSystem"
                        },
                        identifier {
                            system of "mockEHRMRNSystem"
                            value of "1000000001"
                        },
                    )
                name of
                    listOf(
                        name {
                            use of "usual" // required
                        },
                    )
                gender of "male"
            }

        val fakePatientId = MockEHRTestData.add(fakePatient)
        val fakeAidboxPatientId = "$tenantInUse-$fakePatientId"
        val fakeAidboxPatient =
            fakePatient.copy(
                id = Id(fakeAidboxPatientId),
                identifier = fakePatient.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(fakePatientId),
            )
        AidboxTestData.add(fakeAidboxPatient)

        val fakeCarePlan = createFakeCarePlan(fakePatientId, tenantInUse)
        val fakeCarePlanId = MockEHRTestData.add(fakeCarePlan)
        MockOCIServerClient.createExpectations("CarePlan", fakeCarePlanId, tenantInUse)

        KafkaClient.testingClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(fakeAidboxPatient),
            metadata = metadata1,
        )

        // Care Plan now Listens to itself, so will generate 2 messages per plan.
        waitForMessage(2)
        assertEquals(1, getAidboxResourceCount("CarePlan"))

        // Now publish the same event.
        KafkaClient.testingClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(fakeAidboxPatient),
            metadata = metadata1,
        )

        waitForMessage(3)
        val messageList2 = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsStatus(messageList2)
        assertEquals(3, messageList2.size)
        assertEquals(1, getAidboxResourceCount("CarePlan"))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works with multiple patients and carePlans nightly`(testTenant: String) {
        tenantInUse = testTenant
        val fakePatient1 =
            patient {
                birthDate of
                    date {
                        year of 1990
                        month of 1
                        day of 3
                    }
                identifier of
                    listOf(
                        identifier {
                            system of "mockPatientInternalSystem"
                        },
                        identifier {
                            system of "mockEHRMRNSystem"
                            value of "1000000001"
                        },
                    )
                name of
                    listOf(
                        name {
                            use of "usual" // required
                        },
                    )
                gender of "male"
            }
        val fakePatient2 =
            patient {
                birthDate of
                    date {
                        year of 1990
                        month of 1
                        day of 3
                    }
                identifier of
                    listOf(
                        identifier {
                            system of "mockPatientInternalSystem"
                        },
                        identifier {
                            system of "mockEHRMRNSystem"
                            value of "1000000002"
                        },
                    )
                name of
                    listOf(
                        name {
                            use of "usual" // required
                        },
                    )
                gender of "male"
            }
        val fakePatient1Id = MockEHRTestData.add(fakePatient1)
        val fakePatient2Id = MockEHRTestData.add(fakePatient2)
        val fakeAidboxPatient1Id = "$tenantInUse-$fakePatient1Id"
        val fakeAidboxPatient2Id = "$tenantInUse-$fakePatient2Id"
        val fakeAidboxPatient1 =
            fakePatient1.copy(
                id = Id(fakeAidboxPatient1Id),
                identifier = fakePatient1.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(fakePatient1Id),
            )
        val fakeAidboxPatient2 =
            fakePatient2.copy(
                id = Id(fakeAidboxPatient2Id),
                identifier = fakePatient2.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(fakePatient2Id),
            )
        AidboxTestData.add(fakeAidboxPatient1)
        AidboxTestData.add(fakeAidboxPatient2)

        val fakeCarePlan1 = createFakeCarePlan(fakePatient1Id, tenantInUse)

        val fakeCarePlan2 = createFakeCarePlan(fakePatient2Id, tenantInUse)
        val appt1 = MockEHRTestData.add(fakeCarePlan1)
        val appt2 = MockEHRTestData.add(fakeCarePlan1)
        val appt3 = MockEHRTestData.add(fakeCarePlan1)
        val appt4 = MockEHRTestData.add(fakeCarePlan1)
        val appt5 = MockEHRTestData.add(fakeCarePlan1)
        val appt6 = MockEHRTestData.add(fakeCarePlan1)
        val patientAppt = MockEHRTestData.add(fakeCarePlan2)

        MockOCIServerClient.createExpectations("CarePlan", appt1, tenantInUse)
        MockOCIServerClient.createExpectations("CarePlan", appt2, tenantInUse)
        MockOCIServerClient.createExpectations("CarePlan", appt3, tenantInUse)
        MockOCIServerClient.createExpectations("CarePlan", appt4, tenantInUse)
        MockOCIServerClient.createExpectations("CarePlan", appt5, tenantInUse)
        MockOCIServerClient.createExpectations("CarePlan", appt6, tenantInUse)
        MockOCIServerClient.createExpectations("CarePlan", patientAppt, tenantInUse)

        KafkaClient.testingClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.AD_HOC,
            resources = listOf(fakeAidboxPatient1, fakeAidboxPatient2),
            metadata = metadata1,
        )

        waitForMessage(1)
        assertEquals(7, getAidboxResourceCount("CarePlan"))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works with ad-hoc requests`(testTenant: String) {
        tenantInUse = testTenant
        val fakePatient =
            patient {
                birthDate of
                    date {
                        year of 1990
                        month of 1
                        day of 3
                    }
                identifier of
                    listOf(
                        identifier {
                            system of "mockPatientInternalSystem"
                        },
                        identifier {
                            system of "mockEHRMRNSystem"
                            value of "1000000001"
                        },
                    )
                name of
                    listOf(
                        name {
                            use of "usual" // required
                        },
                    )
                gender of "male"
            }
        val fakePatientId = MockEHRTestData.add(fakePatient)
        val fakeAidboxPatientId = "$testTenant-$fakePatientId"
        val fakeAidboxPatient =
            fakePatient.copy(
                id = Id(fakeAidboxPatientId),
                identifier = fakePatient.identifier + tenantIdentifier(testTenant) + fhirIdentifier(fakePatientId),
            )
        AidboxTestData.add(fakeAidboxPatient)

        val fakeCarePlan = createFakeCarePlan(fakePatientId, tenantInUse)
        val fakeCarePlanId = MockEHRTestData.add(fakeCarePlan)
        MockOCIServerClient.createExpectations("CarePlan", fakeCarePlanId, testTenant)

        KafkaClient.testingClient.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf(fakeCarePlanId),
            resourceType = ResourceType.CarePlan,
            metadata = metadata1,
        )
        waitForMessage(1)
        assertEquals(1, getAidboxResourceCount("CarePlan"))
    }

    @Test
    fun `nothing found request results in error`() {
        KafkaClient.testingClient.pushLoadEvent(
            tenantId = TEST_TENANT,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf("nothing to see here"),
            resourceType = ResourceType.CarePlan,
            metadata = metadata1,
        )
        waitForMessage(1)
        assertEquals(0, getAidboxResourceCount("CarePlan"))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `check if channel works nightly with CarePlan cycle`(testTenant: String) {
        tenantInUse = testTenant
        val nowish = LocalDate.now().minusDays(1)
        val laterish = nowish.plusDays(1)
        val fakePatient =
            patient {
                birthDate of
                    date {
                        year of 1990
                        month of 1
                        day of 3
                    }
                identifier of
                    listOf(
                        identifier {
                            system of "mockPatientInternalSystem"
                        },
                        identifier {
                            system of "mockEHRMRNSystem"
                            value of "1000000001"
                        },
                    )
                name of
                    listOf(
                        name {
                            use of "usual" // required
                        },
                    )
                gender of "male"
            }

        val fakePatientId = MockEHRTestData.add(fakePatient)
        val fakeAidboxPatientId = "$tenantInUse-$fakePatientId"
        val fakeAidboxPatient =
            fakePatient.copy(
                id = Id(fakeAidboxPatientId),
                identifier = fakePatient.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(fakePatientId),
            )
        AidboxTestData.add(fakeAidboxPatient)

        // Create a Care Plan that will not be returned by the initial patient search,
        // so it can be loaded by the follow up call.
        val fakeChildPlan =
            carePlan {
                category of
                    listOf(
                        codeableConcept {
                            coding of
                                listOf(
                                    coding {
                                        system of "http://hl7.org/fhir/us/core/CodeSystem/careplan-category"
                                        code of
                                            if (tenantInUse.contains("cern")) {
                                                "assess-plan"
                                            } else {
                                                "736378000"
                                            }
                                    },
                                )
                        },
                    )
                status of "active"
                subject of
                    if (tenantInUse.contains("cern")) {
                        reference("Patient", fakePatientId)
                    } else {
                        // Since Epic doesn't support searching by time range,
                        // but do not return the children when searching by patient,
                        // we will fake that scenario by dropping a fake id in here.
                        // In the future MockEHR can be updated to replicate this strange behavior.
                        reference("Patient", "NotRightId")
                    }
                period of
                    period {
                        start of
                            dateTime {
                                year of laterish.minusYears(1).year
                                month of nowish.monthValue
                                day of nowish.dayOfMonth
                            }
                        end of
                            dateTime {
                                year of laterish.minusYears(1).year
                                month of laterish.monthValue
                                day of laterish.dayOfMonth
                            }
                    }
            }
        val fakeChildPlanId = MockEHRTestData.add(fakeChildPlan)

        val fakeCarePlan =
            carePlan {
                category of
                    listOf(
                        codeableConcept {
                            coding of
                                listOf(
                                    coding {
                                        system of "http://hl7.org/fhir/us/core/CodeSystem/careplan-category"
                                        code of
                                            if (tenantInUse.contains("cern")) {
                                                "assess-plan"
                                            } else {
                                                "736378000"
                                            }
                                    },
                                )
                        },
                    )
                status of "active"
                subject of reference("Patient", fakePatientId)
                period of
                    period {
                        start of
                            dateTime {
                                year of twoDaysAgo.year
                                month of twoDaysAgo.month.value
                                day of twoDaysAgo.dayOfMonth
                            }
                        end of
                            dateTime {
                                year of twoDaysAgo.year
                                month of twoDaysAgo.month.value
                                day of twoDaysAgo.dayOfMonth
                            }
                    }
                activity of
                    listOf(
                        carePlanActivity {
                            extension of
                                listOf(
                                    extension {
                                        url of "http://open.epic.com/FHIR/StructureDefinition/extension/cycle"
                                        value of
                                            DynamicValues.reference(
                                                reference("CarePlan", fakeChildPlanId),
                                            )
                                    },
                                )
                        },
                    )
            }
        val fakeCarePlanId = MockEHRTestData.add(fakeCarePlan)

        MockOCIServerClient.createExpectations("CarePlan", fakeCarePlanId, tenantInUse)
        MockOCIServerClient.createExpectations("CarePlan", fakeChildPlanId, tenantInUse)

        KafkaClient.testingClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(fakeAidboxPatient),
            metadata = metadata1,
        )

        // Message for patient, care plan, and the child care plan
        waitForMessage(3)
        assertEquals(2, getAidboxResourceCount("CarePlan"))
    }
}
