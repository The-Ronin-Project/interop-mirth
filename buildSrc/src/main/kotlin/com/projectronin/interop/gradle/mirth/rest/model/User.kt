package com.projectronin.interop.gradle.mirth.rest.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Model for a Mirth User.
 */
data class User(
    val id: Int,
    val username: String,
    val email: String?,
    val firstName: String?,
    val lastName: String?,
    val organization: String?,
    val description: String?,
    val phoneNumber: String?,
    val industry: String?,
    val lastLogin: DateTime?,
    val gracePeriodStart: DateTime?,
    val strikeCount: Int?,
    val lastStrikeTime: DateTime?
)

/**
 * Wrapper for a User.
 */
data class UserWrapper(val user: User)

/**
 * Wrapper for Preferences.
 */
data class PreferenceWrapper(
    @JsonProperty("properties")
    val preferences: Preferences?
)

/**
 * Model for a User's Mirth Preferences.
 */
data class Preferences(
    @JsonProperty("property")
    val preferences: List<Preference>
)

/**
 * Model for a User's Preference.
 */
data class Preference(
    @JsonProperty("@name")
    val name: String,
    @JsonProperty("$")
    val value: String?
)
