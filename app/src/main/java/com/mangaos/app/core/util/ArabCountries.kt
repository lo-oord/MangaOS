package com.mangaos.app.core.util

/**
 * The countries the account picker offers: the Arab League members, and nothing else.
 *
 * Kept as data so the list can be tested and so no screen invents its own subset. Display names
 * are resolved from the active locale, so the same code reads "السعودية" or "Saudi Arabia".
 */
object ArabCountries {

    /** ISO 3166-1 alpha-2 codes of the Arab states, in the order they are shown. */
    val codes: List<String> = listOf(
        "SA",
        "AE",
        "KW",
        "QA",
        "BH",
        "OM",
        "YE",
        "JO",
        "LB",
        "SY",
        "PS",
        "IQ",
        "EG",
        "SD",
        "LY",
        "TN",
        "DZ",
        "MA",
        "MR",
        "SO",
        "DJ",
        "KM",
    )

    fun contains(code: String?): Boolean = code != null && code.uppercase() in codes
}
