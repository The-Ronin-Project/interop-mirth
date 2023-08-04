package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.http.FhirJson
import com.projectronin.interop.fhir.generators.datatypes.DynamicValues
import com.projectronin.interop.fhir.generators.datatypes.attachment
import com.projectronin.interop.fhir.generators.datatypes.codeableConcept
import com.projectronin.interop.fhir.generators.datatypes.coding
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.datatypes.name
import com.projectronin.interop.fhir.generators.datatypes.participant
import com.projectronin.interop.fhir.generators.datatypes.period
import com.projectronin.interop.fhir.generators.datatypes.reference
import com.projectronin.interop.fhir.generators.primitives.dateTime
import com.projectronin.interop.fhir.generators.primitives.daysAgo
import com.projectronin.interop.fhir.generators.primitives.daysFromNow
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.appointment
import com.projectronin.interop.fhir.generators.resources.binary
import com.projectronin.interop.fhir.generators.resources.carePlan
import com.projectronin.interop.fhir.generators.resources.carePlanActivity
import com.projectronin.interop.fhir.generators.resources.condition
import com.projectronin.interop.fhir.generators.resources.documentReference
import com.projectronin.interop.fhir.generators.resources.documentReferenceContent
import com.projectronin.interop.fhir.generators.resources.encounter
import com.projectronin.interop.fhir.generators.resources.ingredient
import com.projectronin.interop.fhir.generators.resources.location
import com.projectronin.interop.fhir.generators.resources.medication
import com.projectronin.interop.fhir.generators.resources.medicationRequest
import com.projectronin.interop.fhir.generators.resources.observation
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.generators.resources.practitioner
import com.projectronin.interop.fhir.generators.resources.requestGroup
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.primitive.Code
import com.projectronin.interop.fhir.r4.datatype.primitive.Url
import com.projectronin.interop.fhir.r4.resource.EncounterLocation
import com.projectronin.interop.fhir.r4.resource.Resource
import com.projectronin.interop.fhir.r4.valueset.ObservationCategoryCodes
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.base.TenantlessSourceService
import com.projectronin.interop.mirth.channel.destinations.ValidationTestDestination
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.channel.util.generateMetadata
import com.projectronin.interop.mirth.channel.util.generateSerializedMetadata
import com.projectronin.interop.mirth.spring.SpringUtil
import com.projectronin.interop.tenant.config.TenantService
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class ValidationTest(
    val httpClient: HttpClient,
    val tenantService: TenantService,
    validationDestination: ValidationTestDestination,
    val loadService: KafkaLoadService
) : TenantlessSourceService() {
    override val rootName = "ValidationTest"
    override val destinations = mapOf("ValidationTest" to validationDestination)

    companion object {
        fun create() = SpringUtil.applicationContext.getBean(ValidationTest::class.java)
    }

    override fun channelSourceReader(serviceMap: Map<String, Any>): List<MirthMessage> {
        return serviceMap["validationTestTenants"].toString().split(",")
            .map { tenantMnemonic -> // set in Mirth Settings > Configuration Map
                val tenant = tenantService.getTenantForMnemonic(tenantMnemonic) ?: return emptyList()
                val ignoreTypeList = serviceMap["$tenantMnemonic-validationIgnoreTypes"].toString().split(",")
                val mockEHR = MockEHRUtil(
                    httpClient,
                    tenant.vendor.serviceEndpoint,
                    ignoreTypeList
                )
                val patientID =
                    mockEHR.addResource(
                        patient {
                            identifier of listOf(
                                identifier { system of "mockPatientInternalSystem" },
                                identifier { system of "mockEHRMRNSystem" }
                            )
                            name of listOf(
                                name { use of "official" }
                            )
                            telecom of emptyList()
                        }
                    )
                val locationID = mockEHR.addResource(
                    location {
                        identifier of listOf(
                            identifier {
                                system of "mockEHRDepartmentInternalSystem"
                            }
                        )
                    }
                )
                // simulate if this had been populated in proxy
                loadService.pushLoadEvent(
                    tenantMnemonic,
                    DataTrigger.AD_HOC,
                    listOf(locationID),
                    ResourceType.Location,
                    generateMetadata()
                )
                val practitionerID = mockEHR.addResource(
                    practitioner {
                        identifier of listOf(
                            identifier {
                                system of "mockEHRProviderSystem"
                            }
                        )
                    }
                )
                val appointmentID = mockEHR.addResource(
                    appointment {
                        status of "booked"
                        minutesDuration of 1440
                        start of 2.daysFromNow()
                        end of 3.daysFromNow()
                        participant of listOf(
                            participant {
                                status of "accepted"
                                actor of reference("Location", locationID)
                            },
                            participant {
                                status of "accepted"
                                actor of reference("Patient", patientID)
                            },
                            participant {
                                status of "accepted"
                                actor of reference("Practitioner", practitionerID)
                            }
                        )
                    }
                )

                val nowish = LocalDate.now().minusDays(1)
                val laterish = nowish.plusDays(1)
                val encounter1 = encounter {
                    type of listOf(
                        codeableConcept {
                            text of "type"
                            coding of listOf(
                                coding {
                                    display of "display"
                                }
                            )
                        }
                    )
                    period of period {
                        start of dateTime {
                            year of nowish.year
                            month of nowish.monthValue
                            day of nowish.dayOfMonth
                        }
                        end of dateTime {
                            year of laterish.year
                            month of laterish.monthValue
                            day of laterish.dayOfMonth
                        }
                    }
                    status of "planned"
                    `class` of coding { display of "test" }
                    subject of reference("Patient", patientID)
                    // location is not part of fhir-generators
                }.copy(location = listOf(EncounterLocation(location = reference("Location", locationID))))
                val encounterID = mockEHR.addResource(
                    encounter1
                )

                val requestGroupID = mockEHR.addResource(
                    requestGroup {
                        status of Code("active")
                        intent of Code("plan")
                        subject of reference("Patient", patientID)
                    }
                )

                val carePlanID =
                    mockEHR.addResource( // careplans can be tied to specific conditions, observations or itself
                        carePlan {
                            status of Code("active")
                            intent of Code("plan")
                            subject of reference("Patient", patientID)
                            category of listOf(
                                codeableConcept {
                                    coding of listOf(
                                        coding {
                                            code of Code("736378000")
                                        },
                                        coding {
                                            code of Code("assess-plan")
                                        }
                                    )
                                }
                            )
                            activity of listOf(
                                carePlanActivity {
                                    reference of reference("RequestGroup", requestGroupID)
                                }
                            )
                        }
                    )

                val observation1ID = mockEHR.addResource(
                    observation {
                        subject of reference("Patient", patientID)
                        encounter of reference("Encounter", encounterID)
                        status of "final"
                        effective of DynamicValues.dateTime(
                            dateTime {
                                year of nowish.year
                                month of nowish.monthValue
                                day of nowish.dayOfMonth
                            }
                        )
                        category of listOf(
                            codeableConcept {
                                coding of listOf(
                                    coding {
                                        system of CodeSystem.OBSERVATION_CATEGORY.uri
                                        code of ObservationCategoryCodes.VITAL_SIGNS.code
                                    }
                                )
                            }
                        )
                        code of codeableConcept {
                            coding of listOf(
                                coding {
                                    system of CodeSystem.LOINC.uri
                                    display of "Body Weight"
                                    code of Code("29463-7")
                                }
                            )
                        }
                    }
                )
                // TODO: observation not found in initial search but found on condition.staging

                val conditionID = mockEHR.addResource( // needs category + stuff
                    condition {
                        clinicalStatus of codeableConcept {
                            coding of listOf(
                                coding {
                                    system of "http://terminology.hl7.org/CodeSystem/condition-clinical"
                                    code of "active"
                                    display of "Active"
                                }
                            )
                            text of "Active"
                        }
                        category of listOf(
                            codeableConcept {
                                coding of listOf(
                                    coding {
                                        system of "http://terminology.hl7.org/CodeSystem/condition-category"
                                        code of "problem-list-item"
                                        display of "Problem list item"
                                    }
                                )
                                text of "Problem List Item"
                            }
                        )
                        subject of reference("Patient", patientID)
                        code of codeableConcept {
                            coding of listOf(
                                coding {
                                    system of "http://snomed.info/sct"
                                    code of "1023001"
                                    display of "Apnea"
                                }
                            )
                            text of "Apnea"
                        }
                    }
                )

                val ingredientMedicationId = mockEHR.addResource(
                    medication {
                        code of codeableConcept {
                            coding of listOf(
                                coding {
                                    system of "ok"
                                    code of "yeah"
                                }
                            )
                        }
                    }
                )

                val medicationID = mockEHR.addResource(
                    medication {
                        code of codeableConcept {
                            coding of listOf(
                                coding {
                                    system of "ok"
                                    code of "yeah"
                                }
                            )
                        }
                        ingredient of listOf(
                            ingredient {
                                item of DynamicValues.reference(reference("Medication", ingredientMedicationId))
                            }
                        )
                    }
                )

                val medicationRequestID = mockEHR.addResource(
                    medicationRequest {
                        subject of reference("Patient", patientID)
                        medication of DynamicValues.reference(reference("Medication", medicationID))
                        requester of reference("Practitioner", practitionerID)
                    }
                )
                val binaryID = mockEHR.addResource(binary { })
                val documentReferenceID = mockEHR.addResource(
                    documentReference {
                        date of 2.daysAgo()
                        type of codeableConcept {
                            coding of listOf(
                                coding {
                                    system of "http://loinc.org"
                                    code of "34806-0"
                                }
                            )
                        }
                        subject of reference("Patient", patientID)
                        category of listOf(
                            codeableConcept {
                                coding of listOf(
                                    coding {
                                        system of "http://hl7.org/fhir/us/core/CodeSystem/us-core-documentreference-category"
                                        code of "clinical-note"
                                    }
                                )
                            }
                        )
                        content of listOf(
                            documentReferenceContent {
                                attachment of attachment {
                                    url of Url("Binary/$binaryID")
                                }
                            }
                        )
                    }
                )

                val resources = listOf(
                    "Location/$locationID",
                    "Patient/$patientID",
                    "Appointment/$appointmentID",
                    "Encounter/$encounterID",
                    "Practitioner/$practitionerID",
                    "Observation/$observation1ID",
                    "Condition/$conditionID",
                    "CarePlan/$carePlanID",
                    "RequestGroup/$requestGroupID",
                    "MedicationRequest/$medicationRequestID",
                    "Medication/$medicationID",
                    "Medication/$ingredientMedicationId",
                    "DocumentReference/$documentReferenceID"
                ).filterNot { it.split("/").first() in ignoreTypeList }

                MirthMessage(
                    message = locationID,
                    dataMap = mapOf(
                        MirthKey.FHIR_ID_LIST.code to resources,
                        MirthKey.EVENT_METADATA.code to generateSerializedMetadata(),
                        MirthKey.TENANT_MNEMONIC.code to tenantMnemonic,
                        "MockEHRURL" to tenant.vendor.serviceEndpoint
                    )
                )
            }
    }

    class MockEHRUtil(val httpClient: HttpClient, URL: String, val ignoreTypeList: List<String> = emptyList()) {

        val logger = KotlinLogging.logger { }

        private val BASE_URL = URL

        private val FHIR_URL = when {
            BASE_URL.contains("epic") -> "$BASE_URL/api/FHIR/R4"
            BASE_URL.contains("cerner") -> BASE_URL
            else -> "$BASE_URL/fhir/r4"
        }
        val RESOURCES_FORMAT = "$FHIR_URL/%s"

        inline fun <reified T : Resource<T>> addResource(resource: Resource<T>): String {
            return if (resource.resourceType in ignoreTypeList) {
                "IGNORED"
            } else {
                runBlocking {
                    val resourceUrl = RESOURCES_FORMAT.format(resource.resourceType)
                    val response = httpClient.post(resourceUrl) {
                        contentType(ContentType.Application.FhirJson)
                        accept(ContentType.Application.FhirJson)
                        setBody(resource)
                    }
                    val location = response.headers["Content-Location"]
                    logger.debug { "$location" }
                    location!!.removePrefix("$resourceUrl/")
                }
            }
        }

        fun deleteResource(resourceReference: String) = runBlocking {
            val url = RESOURCES_FORMAT.format(resourceReference)
            httpClient.delete(url)
        }
    }
}
