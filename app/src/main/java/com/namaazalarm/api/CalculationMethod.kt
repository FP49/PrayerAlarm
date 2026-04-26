package com.namaazalarm.api

/**
 * Aladhan API calculation methods.
 * method value matches the Aladhan /calendar API `method` parameter.
 * school=1 (Hanafi Asr) is sent separately for all methods.
 *
 * Default: KARACHI (method=1) — most common for South Asian Muslims in the UK.
 */
enum class CalculationMethod(val id: Int, val displayName: String, val description: String) {
    KARACHI(
        id = 1,
        displayName = "Hanafi / Karachi",
        description = "University of Islamic Sciences, Karachi — standard for UK South Asian mosques"
    ),
    MUSLIM_WORLD_LEAGUE(
        id = 3,
        displayName = "Muslim World League",
        description = "Used by many mosques worldwide"
    ),
    UMM_AL_QURA(
        id = 4,
        displayName = "Umm Al-Qura (Makkah)",
        description = "Official Saudi Arabia method"
    ),
    ISNA(
        id = 2,
        displayName = "ISNA (North America)",
        description = "Islamic Society of North America"
    ),
    EGYPT(
        id = 5,
        displayName = "Egyptian Authority",
        description = "Egyptian General Authority of Survey"
    ),
    KUWAIT(
        id = 9,
        displayName = "Kuwait",
        description = "Kuwait standard"
    ),
    QATAR(
        id = 10,
        displayName = "Qatar",
        description = "Qatar standard"
    ),
    TURKEY(
        id = 13,
        displayName = "Turkey (Diyanet)",
        description = "Diyanet Isleri, Turkey"
    ),
    MOONSIGHTING(
        id = 15,
        displayName = "Moonsighting Committee",
        description = "Moonsighting Committee Worldwide"
    );

    companion object {
        fun fromId(id: Int): CalculationMethod =
            values().find { it.id == id } ?: KARACHI

        val DEFAULT = KARACHI
    }
}
