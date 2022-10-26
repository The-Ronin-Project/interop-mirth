package com.projectronin.interop.mirth.connector.util

import com.projectronin.interop.datalake.DatalakePublishService
import com.projectronin.interop.datalake.oci.client.OCIClient
import com.projectronin.interop.fhir.ronin.conceptmap.ConceptMapClient

object OciUtil {
    private val tenancyOCID = EnvironmentReader.readRequired("OCI_TENANCY_OCID")
    private val userOCID = EnvironmentReader.readRequired("OCI_USER_OCID")
    private val fingerPrint = EnvironmentReader.readRequired("OCI_FINGERPRINT")
    private val privateKey = EnvironmentReader.readRequired("OCI_PRIVATE_KEY_BASE64")
    private val namespace = EnvironmentReader.readRequired("OCI_NAMESPACE")
    private val datalakeBucket = EnvironmentReader.readRequired("OCI_PUBLISH_BUCKET_NAME")
    private val infxBucket = EnvironmentReader.readRequired("OCI_CONCEPTMAP_BUCKET_NAME")
    private val region = EnvironmentReader.readRequired("OCI_REGION")

    private val ociClient = OCIClient(
        tenancyOCID = tenancyOCID,
        userOCID = userOCID,
        fingerPrint = fingerPrint,
        privateKey = privateKey,
        namespace = namespace,
        infxBucket = infxBucket,
        datalakeBucket = datalakeBucket,
        regionId = region
    )

    val datalakePublishService = DatalakePublishService(ociClient)
    val conceptMapClient = ConceptMapClient(ociClient)
}
