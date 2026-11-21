package com.invarware.shineye.core

class OfficialDomains(domains: Collection<String>) {

    private val domains: Set<String> =
        domains.map { it.trim().lowercase().removePrefix("www.") }.filter { it.isNotEmpty() }.toSet()

    fun owns(host: String): Boolean = domains.any { host == it || host.endsWith(".$it") }

    companion object {
        val seed = OfficialDomains(
            listOf(
                "cbn.gov.ng", "nibss-plc.com.ng", "gtbank.com", "firstbanknigeria.com", "zenithbank.com",
                "accessbankplc.com", "ubagroup.com", "fidelitybank.ng", "opayweb.com", "kuda.com",
                "moniepoint.com", "palmpay.com",
            ),
        )

        fun parse(commaSeparated: String): OfficialDomains = OfficialDomains(commaSeparated.split(','))
    }
}
