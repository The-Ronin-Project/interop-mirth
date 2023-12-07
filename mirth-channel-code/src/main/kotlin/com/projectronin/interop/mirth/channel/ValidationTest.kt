package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.http.FhirJson
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.fhir.generators.datatypes.DynamicValues
import com.projectronin.interop.fhir.generators.datatypes.attachment
import com.projectronin.interop.fhir.generators.datatypes.codeableConcept
import com.projectronin.interop.fhir.generators.datatypes.coding
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.datatypes.internalIdentifier
import com.projectronin.interop.fhir.generators.datatypes.name
import com.projectronin.interop.fhir.generators.datatypes.participant
import com.projectronin.interop.fhir.generators.datatypes.period
import com.projectronin.interop.fhir.generators.datatypes.quantity
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
import com.projectronin.interop.fhir.generators.resources.diagnosticReport
import com.projectronin.interop.fhir.generators.resources.documentReference
import com.projectronin.interop.fhir.generators.resources.documentReferenceContent
import com.projectronin.interop.fhir.generators.resources.encounter
import com.projectronin.interop.fhir.generators.resources.ingredient
import com.projectronin.interop.fhir.generators.resources.location
import com.projectronin.interop.fhir.generators.resources.medAdminDosage
import com.projectronin.interop.fhir.generators.resources.medication
import com.projectronin.interop.fhir.generators.resources.medicationAdministration
import com.projectronin.interop.fhir.generators.resources.medicationRequest
import com.projectronin.interop.fhir.generators.resources.medicationStatement
import com.projectronin.interop.fhir.generators.resources.observation
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.generators.resources.practitioner
import com.projectronin.interop.fhir.generators.resources.requestGroup
import com.projectronin.interop.fhir.generators.resources.serviceRequest
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.CodeableConcept
import com.projectronin.interop.fhir.r4.datatype.Coding
import com.projectronin.interop.fhir.r4.datatype.primitive.Code
import com.projectronin.interop.fhir.r4.datatype.primitive.DateTime
import com.projectronin.interop.fhir.r4.datatype.primitive.FHIRString
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
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
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Component
class ValidationTest(
    val httpClient: HttpClient,
    val tenantService: TenantService,
    validationDestination: ValidationTestDestination,
    val loadService: KafkaLoadService,
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
                val validationList = mutableListOf<String>()
                val mockEHR =
                    MockEHRUtil(
                        httpClient,
                        tenant.vendor.serviceEndpoint,
                        ignoreTypeList,
                        validationList,
                    )
                val nowish = LocalDate.now().minusDays(1)
                val laterish = nowish.plusDays(1)

                val patientID =
                    mockEHR.addResourceAndValidate(
                        patient {
                            identifier of
                                listOf(
                                    internalIdentifier { system of "mockPatientInternalSystem" },
                                    identifier { system of "mockEHRMRNSystem" },
                                )
                            name of
                                listOf(
                                    name { use of "official" },
                                )
                            telecom of emptyList()
                        },
                    )

                val locationID =
                    mockEHR.addResourceAndValidate(
                        location {
                            identifier of
                                listOf(
                                    identifier {
                                        system of "mockEHRDepartmentInternalSystem"
                                    },
                                )
                        },
                    )

                // simulate if this had been populated in proxy
                loadService.pushLoadEvent(
                    tenantMnemonic,
                    DataTrigger.AD_HOC,
                    listOf(locationID),
                    ResourceType.Location,
                    generateMetadata(),
                )

                // Appointment Section
                if ("Appointment" !in ignoreTypeList) {
                    val practitionerID =
                        mockEHR.addResourceAndValidate(
                            practitioner {
                                identifier of
                                    listOf(
                                        identifier {
                                            system of "mockEHRProviderSystem"
                                        },
                                    )
                            },
                        )
                    mockEHR.addResourceAndValidate(
                        appointment {
                            status of "booked"
                            minutesDuration of 1440
                            start of 2.daysFromNow()
                            end of 3.daysFromNow()
                            participant of
                                listOf(
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
                                    },
                                )
                        },
                    )
                }

                val encounter1 =
                    encounter {
                        type of
                            listOf(
                                codeableConcept {
                                    text of "type"
                                    coding of
                                        listOf(
                                            coding {
                                                display of "display"
                                            },
                                        )
                                },
                            )
                        period of
                            period {
                                start of
                                    dateTime {
                                        year of nowish.year
                                        month of nowish.monthValue
                                        day of nowish.dayOfMonth
                                    }
                                end of
                                    dateTime {
                                        year of laterish.year
                                        month of laterish.monthValue
                                        day of laterish.dayOfMonth
                                    }
                            }
                        status of "planned"
                        `class` of coding { display of "test" }
                        subject of reference("Patient", patientID)
                        location of listOf(EncounterLocation(location = reference("Location", locationID)))
                    }
                mockEHR.addResourceAndValidate(encounter1)

                // Care Plan Section
                if ("CarePlan" !in ignoreTypeList) {
                    val requestGroupID =
                        mockEHR.addResourceAndValidate(
                            requestGroup {
                                status of Code("active")
                                intent of Code("plan")
                                subject of reference("Patient", patientID)
                            },
                        )

                    // careplans can be tied to specific conditions, observations or itself
                    mockEHR.addResourceAndValidate(
                        carePlan {
                            status of Code("active")
                            intent of Code("plan")
                            subject of reference("Patient", patientID)
                            category of
                                listOf(
                                    codeableConcept {
                                        coding of
                                            listOf(
                                                coding {
                                                    code of Code("736378000")
                                                },
                                                coding {
                                                    code of Code("assess-plan")
                                                },
                                            )
                                    },
                                )
                            activity of
                                listOf(
                                    carePlanActivity {
                                        reference of reference("RequestGroup", requestGroupID)
                                    },
                                )
                        },
                    )
                }
                mockEHR.addResourceAndValidate(
                    observation {
                        subject of reference("Patient", patientID)
                        status of "final"
                        effective of
                            DynamicValues.dateTime(
                                dateTime {
                                    year of nowish.year
                                    month of nowish.monthValue
                                    day of nowish.dayOfMonth
                                },
                            )
                        category of
                            listOf(
                                codeableConcept {
                                    coding of
                                        listOf(
                                            coding {
                                                system of CodeSystem.OBSERVATION_CATEGORY.uri
                                                code of ObservationCategoryCodes.VITAL_SIGNS.code
                                            },
                                        )
                                },
                            )
                        code of
                            codeableConcept {
                                coding of
                                    listOf(
                                        coding {
                                            system of CodeSystem.LOINC.uri
                                            display of "Body Weight"
                                            code of Code("29463-7")
                                        },
                                    )
                            }
                    },
                )

                mockEHR.addResourceAndValidate(
                    // needs category + stuff
                    condition {
                        clinicalStatus of
                            codeableConcept {
                                coding of
                                    listOf(
                                        coding {
                                            system of "http://terminology.hl7.org/CodeSystem/condition-clinical"
                                            code of "active"
                                            display of "Active"
                                        },
                                    )
                                text of "Active"
                            }
                        category of
                            listOf(
                                codeableConcept {
                                    coding of
                                        listOf(
                                            coding {
                                                system of "http://terminology.hl7.org/CodeSystem/condition-category"
                                                code of "problem-list-item"
                                                display of "Problem list item"
                                            },
                                        )
                                    text of "Problem List Item"
                                },
                            )
                        subject of reference("Patient", patientID)
                        code of
                            codeableConcept {
                                coding of
                                    listOf(
                                        coding {
                                            system of "http://snomed.info/sct"
                                            code of "1023001"
                                            display of "Apnea"
                                        },
                                    )
                                text of "Apnea"
                            }
                    },
                )

                // Medication Request Section
                if ("MedicationRequest" !in ignoreTypeList) {
                    val ingredientMedicationID =
                        mockEHR.addResourceAndValidate(
                            medication {
                                code of
                                    codeableConcept {
                                        coding of
                                            listOf(
                                                coding {
                                                    system of "ok"
                                                    code of "yeah"
                                                },
                                            )
                                    }
                            },
                        )

                    val medicationID =
                        mockEHR.addResourceAndValidate(
                            medication {
                                code of
                                    codeableConcept {
                                        coding of
                                            listOf(
                                                coding {
                                                    system of "ok"
                                                    code of "yeah"
                                                },
                                            )
                                    }
                                ingredient of
                                    listOf(
                                        ingredient {
                                            item of
                                                DynamicValues.reference(
                                                    reference(
                                                        "Medication",
                                                        ingredientMedicationID,
                                                    ),
                                                )
                                        },
                                    )
                            },
                        )

                    mockEHR.addResourceAndValidate(
                        medicationRequest {
                            subject of reference("Patient", patientID)
                            medication of DynamicValues.reference(reference("Medication", medicationID))
                            requester of reference("Practitioner", "12345")
                        },
                    )
                }

                // Medication Statement Section
                if ("MedicationStatement" !in ignoreTypeList) {
                    val statementMedicationID =
                        mockEHR.addResourceAndValidate(
                            medication {
                                code of
                                    codeableConcept {
                                        coding of
                                            listOf(
                                                coding {
                                                    system of "ok"
                                                    code of "yeah"
                                                },
                                            )
                                    }
                            },
                        )

                    mockEHR.addResourceAndValidate(
                        medicationStatement {
                            subject of reference("Patient", patientID)
                            medication of DynamicValues.reference(reference("Medication", statementMedicationID))
                            status of "completed"
                        },
                        useSTU3 = true,
                    )
                }

                // Medication Administration Section
                if ("MedicationAdministration" !in ignoreTypeList) {
                    val medicationCodeableConcept =
                        codeableConcept {
                            coding of
                                listOf(
                                    coding {
                                        system of "ok"
                                        code of "yeah"
                                    },
                                )
                            text of "medication"
                        }

                    val encounterId =
                        mockEHR.addResourceAndValidate(
                            encounter {
                                type of
                                    listOf(
                                        codeableConcept {
                                            text of "type"
                                            coding of
                                                listOf(
                                                    coding {
                                                        display of "display"
                                                    },
                                                )
                                        },
                                    )
                                period of
                                    period {
                                        start of
                                            dateTime {
                                                year of nowish.year
                                                month of nowish.monthValue
                                                day of nowish.dayOfMonth
                                            }
                                        end of
                                            dateTime {
                                                year of laterish.year
                                                month of laterish.monthValue
                                                day of laterish.dayOfMonth
                                            }
                                    }
                                status of "planned"
                                `class` of coding { display of "test" }
                                subject of reference("Patient", patientID)
                                location of listOf(EncounterLocation(location = reference("Location", locationID)))
                                identifier plus identifier { system of "mockEncounterCSNSystem" }
                            },
                            skipValidation = "Encounter" in ignoreTypeList,
                        )

                    val orderIdentifier =
                        identifier {
                            system of "mockEHROrderSystem"
                        }
                    val medicationRequestID =
                        mockEHR.addResourceAndValidate(
                            medicationRequest {
                                subject of reference("Patient", patientID)
                                medication of DynamicValues.codeableConcept(medicationCodeableConcept)
                                requester of reference("Practitioner", "12345")
                                identifier plus orderIdentifier
                                encounter of reference("Encounter", encounterId)
                            },
                            skipValidation = "MedicationRequest" in ignoreTypeList,
                        )

                    val dateTime = "${nowish.year}-${nowish.monthValue.toString().padStart(2, '0')}-${
                        nowish.dayOfMonth.toString().padStart(2, '0')
                    }T10:15:30.00Z"
                    val medAdminId =
                        mockEHR.addResourceAndValidate(
                            medicationAdministration {
                                subject of reference("Patient", patientID)
                                medication of DynamicValues.codeableConcept(medicationCodeableConcept)
                                effective of DynamicValues.dateTime(DateTime(dateTime))
                                request of reference("MedicationRequest", medicationRequestID)
                                dosage of
                                    medAdminDosage {
                                        dose of
                                            quantity {
                                                value of BigDecimal.TEN
                                                unit of "mg"
                                            }
                                    }
                                status of "completed"
                            },
                            useBaseFhir = true,
                            skipValidation = true,
                        )

                    val validationId =
                        if (mockEHR.vendorType == "epic") {
                            "${orderIdentifier.value!!.value!!}-${Instant.parse(dateTime).epochSecond}"
                        } else {
                            medAdminId
                        }

                    validationList.add("MedicationAdministration/$validationId")
                }

                val binaryID = mockEHR.addResourceAndValidate(binary { })
                mockEHR.addResourceAndValidate(
                    documentReference {
                        date of 2.daysAgo()
                        type of
                            codeableConcept {
                                coding of
                                    listOf(
                                        coding {
                                            system of "http://loinc.org"
                                            code of "34806-0"
                                        },
                                    )
                            }
                        subject of reference("Patient", patientID)
                        category of
                            listOf(
                                codeableConcept {
                                    coding of
                                        listOf(
                                            coding {
                                                system of "http://hl7.org/fhir/us/core/CodeSystem/us-core-documentreference-category"
                                                code of "clinical-note"
                                            },
                                        )
                                },
                            )
                        content of
                            listOf(
                                documentReferenceContent {
                                    attachment of
                                        attachment {
                                            url of Url("Binary/$binaryID")
                                        }
                                },
                            )
                    },
                )
                mockEHR.addResourceAndValidate(
                    serviceRequest {
                        identifier of
                            listOf(
                                identifier {
                                    system of "mockServiceRequestSystem"
                                    value of "ServiceRequest/2"
                                },
                            )
                        category of
                            listOf(
                                CodeableConcept(
                                    coding =
                                        listOf(
                                            Coding(
                                                system = Uri("http://projectronin.io/fhir/CodeSystem/ServiceRequestCategory"),
                                                code = Code("1"),
                                                display = FHIRString("Procedures"),
                                            ),
                                        ),
                                ),
                            )
                        code of
                            CodeableConcept(
                                coding =
                                    listOf(
                                        Coding(
                                            system = Uri("http://projectronin.io/fhir/CodeSystem/ServiceRequestCode"),
                                            code = Code("1"),
                                            display = FHIRString("Procedures"),
                                        ),
                                    ),
                            )
                        subject of reference("Patient", patientID)
                    },
                )
                mockEHR.addResourceAndValidate(
                    diagnosticReport {
                        subject of reference("Patient", patientID)
                        status of "registered"
                        code of
                            codeableConcept {
                                coding of
                                    listOf(
                                        coding {
                                            system of "http://loinc.org"
                                            code of "58410-2"
                                            display of "Complete blood count (hemogram) panel - Blood by Automated count"
                                        },
                                    )
                                text of "Complete Blood Count"
                            }
                        category of
                            listOf(
                                codeableConcept {
                                    coding of
                                        listOf(
                                            coding {
                                                system of "http://terminology.hl7.org/CodeSystem/v2-0074"
                                                code of "LAB"
                                            },
                                        )
                                },
                            )
                    },
                )

                MirthMessage(
                    message = JacksonUtil.writeJsonValue(listOf(locationID)),
                    dataMap =
                        mapOf(
                            MirthKey.FHIR_ID_LIST.code to validationList,
                            MirthKey.EVENT_METADATA.code to generateSerializedMetadata(),
                            MirthKey.TENANT_MNEMONIC.code to tenantMnemonic,
                            "MockEHRURL" to tenant.vendor.serviceEndpoint,
                        ),
                )
            }
    }

    class MockEHRUtil(
        val httpClient: HttpClient,
        vendorUrl: String,
        val ignoreTypeList: List<String> = emptyList(),
        val validationList: MutableList<String> = mutableListOf(),
    ) {
        val logger = KotlinLogging.logger { }

        val vendorType =
            when {
                vendorUrl.contains("epic") -> "epic"
                vendorUrl.contains("cerner") -> "cerner"
                else -> null
            }

        val vendorSpecificUrl =
            when {
                vendorUrl.contains("epic") -> "$vendorUrl/api/FHIR/R4"
                vendorUrl.contains("cerner") -> vendorUrl
                else -> "$vendorUrl/fhir/r4"
            }

        val fhirUrl =
            when {
                vendorUrl.contains("epic") -> vendorUrl.replace("/epic", "/fhir/r4")
                vendorUrl.contains("cerner") -> vendorUrl.replace("/cerner", "")
                else -> vendorUrl
            }

        fun <T : Resource<T>> addResourceAndValidate(
            resource: Resource<T>,
            useBaseFhir: Boolean = false,
            useSTU3: Boolean = false,
            skipValidation: Boolean = false,
        ): String {
            return if (resource.resourceType in ignoreTypeList) {
                "IGNORED"
            } else {
                runBlocking {
                    val fhirUrl = if (useBaseFhir) fhirUrl else vendorSpecificUrl
                    val resourceUrl =
                        "$fhirUrl/%s".format(resource.resourceType).replace("R4", if (useSTU3) "STU3" else "R4")
                    val response =
                        httpClient.post(resourceUrl) {
                            contentType(ContentType.Application.FhirJson)
                            accept(ContentType.Application.FhirJson)
                            setBody<Resource<*>>(resource)
                        }
                    val location = response.headers["Content-Location"]
                    logger.debug { "$location" }

                    val id = location!!.removePrefix("$resourceUrl/")
                    if (!skipValidation) {
                        validationList.add("${resource.resourceType}/$id")
                    }
                    id
                }
            }
        }

        fun deleteResource(resourceReference: String) =
            runBlocking {
                val url = "$fhirUrl/$resourceReference"
                httpClient.delete(url)
            }
    }
}
