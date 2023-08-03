package com.projectronin.interop.mirth.channel.util

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.fhir.r4.datatype.Reference

fun Reference.isForType(resourceType: ResourceType) = isForType(resourceType.name)
