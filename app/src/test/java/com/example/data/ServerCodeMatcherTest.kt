package com.example.data

import com.example.model.NordServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerCodeMatcherTest {

    private fun server(
        number: Int,
        city: String,
        country: String = "United States",
        iso: String = "US",
        hostPrefix: String = iso.lowercase(),
        load: Int = 20
    ): NordServer = NordServer(
        id = number,
        name = "$country #$number",
        hostname = "$hostPrefix$number.nordvpn.com",
        stationIp = "198.51.100.${number % 250 + 1}",
        loadPercent = load,
        countryName = country,
        countryCode = iso,
        cityName = city,
        wireGuardPublicKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    )

    private val sampleFleet: List<NordServer> = listOf(
        // Los Angeles
        server(13084, "Los Angeles", load = 18),
        server(13089, "Los Angeles", load = 24),
        server(13269, "Los Angeles", load = 31),
        server(13373, "Los Angeles", load = 15),
        server(13388, "Los Angeles", load = 22),
        server(13390, "Los Angeles", load = 29),
        server(14123, "Los Angeles", load = 12),
        // Charlotte
        server(12790, "Charlotte", load = 19),
        server(12791, "Charlotte", load = 21),
        server(12792, "Charlotte", load = 14),
        server(12797, "Charlotte", load = 25),
        server(12802, "Charlotte", load = 17),
        server(13047, "Charlotte", load = 22),
        server(13048, "Charlotte", load = 28),
        server(13610, "Charlotte", load = 16),
        server(13611, "Charlotte", load = 20),
        // Dallas
        server(9898, "Dallas", load = 19),
        server(9899, "Dallas", load = 23),
        server(10538, "Dallas", load = 15),
        server(10631, "Dallas", load = 27),
        server(13631, "Dallas", load = 12),
        server(13632, "Dallas", load = 14),
        server(13640, "Dallas", load = 21),
        server(13642, "Dallas", load = 26),
        server(13643, "Dallas", load = 20),
        server(13665, "Dallas", load = 18),
        server(13613, "Dallas", load = 22),
        server(13748, "Dallas", load = 11),
        // Miami
        server(11814, "Miami", load = 20),
        server(12215, "Miami", load = 25),
        server(13220, "Miami", load = 17),
        server(13221, "Miami", load = 19),
        server(13336, "Miami", load = 22),
        server(13337, "Miami", load = 24),
        server(14171, "Miami", load = 13),
        server(14177, "Miami", load = 15),
        server(14188, "Miami", load = 18),
        server(14189, "Miami", load = 16),
        // Phoenix
        server(9555, "Phoenix", load = 14),
        server(9556, "Phoenix", load = 19),
        server(12179, "Phoenix", load = 21),
        server(12180, "Phoenix", load = 23),
        server(12181, "Phoenix", load = 17),
        server(12194, "Phoenix", load = 20),
        server(12195, "Phoenix", load = 22),
        server(12197, "Phoenix", load = 15),
        server(13152, "Phoenix", load = 18),
        server(13153, "Phoenix", load = 25),
        // Denver
        server(13229, "Denver", load = 16),
        server(13230, "Denver", load = 19),
        server(13231, "Denver", load = 20),
        server(13245, "Denver", load = 24),
        server(13250, "Denver", load = 14),
        server(13251, "Denver", load = 17),
        server(13257, "Denver", load = 21),
        server(13936, "Denver", load = 12),
        server(13937, "Denver", load = 18),
        server(14136, "Denver", load = 15),
        // Seattle
        server(9948, "Seattle", load = 19),
        server(9998, "Seattle", load = 22),
        server(11615, "Seattle", load = 15),
        server(12945, "Seattle", load = 17),
        server(12946, "Seattle", load = 21),
        server(12958, "Seattle", load = 25),
        server(13569, "Seattle", load = 14),
        server(13583, "Seattle", load = 18),
        server(13584, "Seattle", load = 20),
        server(13585, "Seattle", load = 23),
        server(13887, "Seattle", load = 16),
        // Chicago
        server(11519, "Chicago", load = 21),
        server(11527, "Chicago", load = 17),
        server(11530, "Chicago", load = 19),
        server(14161, "Chicago", load = 14),
        server(14163, "Chicago", load = 15),
        server(14164, "Chicago", load = 18),
        server(14165, "Chicago", load = 22),
        server(14166, "Chicago", load = 24),
        // Atlanta
        server(11594, "Atlanta", load = 19),
        server(11576, "Atlanta", load = 22),
        server(11577, "Atlanta", load = 16),
        server(11578, "Atlanta", load = 20),
        server(11582, "Atlanta", load = 18),
        server(11592, "Atlanta", load = 14),
        server(13455, "Atlanta", load = 17),
        server(13964, "Atlanta", load = 15),
        server(13967, "Atlanta", load = 21),
        server(14118, "Atlanta", load = 12),
        // Ashburn
        server(11640, "Ashburn", load = 19),
        server(11641, "Ashburn", load = 21),
        server(11659, "Ashburn", load = 16),
        server(12394, "Ashburn", load = 15),
        server(12395, "Ashburn", load = 17),
        server(12396, "Ashburn", load = 22),
        server(12397, "Ashburn", load = 18),
        server(12398, "Ashburn", load = 20),
        server(13524, "Ashburn", load = 14),
        server(13533, "Ashburn", load = 13),
        // International servers to verify global country prefixes & UK/GB handling
        server(17, "Tirana", country = "Albania", iso = "AL", hostPrefix = "al", load = 11),
        server(2410, "London", country = "United Kingdom", iso = "GB", hostPrefix = "uk", load = 15)
    )

    @Test
    fun plainTextQueriesReturnNullSoCountryAndCitySearchStaysNormal() {
        assertNull(ServerCodeMatcher.parseQuery(""))
        assertNull(ServerCodeMatcher.parseQuery("   "))
        assertNull(ServerCodeMatcher.parseQuery("#"))
        assertNull(ServerCodeMatcher.parseQuery("Albania"))
        assertNull(ServerCodeMatcher.parseQuery("Los Angeles"))
        assertNull(ServerCodeMatcher.parseQuery("US"))
        assertNull(ServerCodeMatcher.parseQuery("Tirana"))
    }

    @Test
    fun acceptsBothPlainNumberAndHashNumberAcrossAllCities() {
        val cases = listOf(
            13084 to "Los Angeles",
            14123 to "Los Angeles",
            12790 to "Charlotte",
            13611 to "Charlotte",
            9898 to "Dallas",
            13748 to "Dallas",
            11814 to "Miami",
            14189 to "Miami",
            9555 to "Phoenix",
            13153 to "Phoenix",
            13229 to "Denver",
            14136 to "Denver",
            9948 to "Seattle",
            13887 to "Seattle",
            11519 to "Chicago",
            14166 to "Chicago",
            11594 to "Atlanta",
            11592 to "Atlanta",
            11640 to "Ashburn",
            13533 to "Ashburn",
            17 to "Tirana"
        )

        for ((num, expectedCity) in cases) {
            // Plain number (e.g. "13084")
            val parsedPlain = ServerCodeMatcher.parseQuery("$num")
            assertNotNull("Expected plain number $num to parse", parsedPlain)
            val plainMatches = ServerCodeMatcher.matchServers(sampleFleet, parsedPlain!!)
            assertTrue("Expected match for $num", plainMatches.isNotEmpty())
            assertEquals(num, plainMatches.first().serverNumber)
            assertEquals(expectedCity, plainMatches.first().cityName)
            assertEquals("#$num", plainMatches.first().serverCodeTag)

            // Hash + number (e.g. "#13084")
            val parsedHash = ServerCodeMatcher.parseQuery("#$num")
            assertNotNull("Expected #$num to parse", parsedHash)
            val hashMatches = ServerCodeMatcher.matchServers(sampleFleet, parsedHash!!)
            assertTrue("Expected match for #$num", hashMatches.isNotEmpty())
            assertEquals(num, hashMatches.first().serverNumber)
            assertEquals(expectedCity, hashMatches.first().cityName)
        }
    }

    @Test
    fun supportsServerNumberRangesWithAndWithoutHash() {
        val charlotteRange = ServerCodeMatcher.parseQuery("#12790-12792")!!
        assertEquals(setOf(12790, 12791, 12792), charlotteRange.numbers)
        val charlotteMatches = ServerCodeMatcher.matchServers(sampleFleet, charlotteRange)
        assertEquals(listOf(12790, 12791, 12792), charlotteMatches.map { it.serverNumber })
        assertTrue(charlotteMatches.all { it.cityName == "Charlotte" })

        val ashburnRange = ServerCodeMatcher.parseQuery("#12394-12398")!!
        assertEquals(setOf(12394, 12395, 12396, 12397, 12398), ashburnRange.numbers)
        val ashburnMatches = ServerCodeMatcher.matchServers(sampleFleet, ashburnRange)
        assertEquals(listOf(12394, 12395, 12396, 12397, 12398), ashburnMatches.map { it.serverNumber })
        assertTrue(ashburnMatches.all { it.cityName == "Ashburn" })

        val phoenixRange = ServerCodeMatcher.parseQuery("12179-12181")!!
        val phoenixMatches = ServerCodeMatcher.matchServers(sampleFleet, phoenixRange)
        assertEquals(listOf(12179, 12180, 12181), phoenixMatches.map { it.serverNumber })
        assertTrue(phoenixMatches.all { it.cityName == "Phoenix" })
    }

    @Test
    fun supportsMultipleCommaSeparatedAndCityPrefixedQueries() {
        val chicagoList = ServerCodeMatcher.parseQuery("Chicago (#11519, #11527, #11530)")!!
        assertEquals(setOf(11519, 11527, 11530), chicagoList.numbers)
        assertEquals("Chicago", chicagoList.locationTextHint)
        val chicagoMatches = ServerCodeMatcher.matchServers(sampleFleet, chicagoList)
        assertEquals(listOf(11519, 11527, 11530), chicagoMatches.map { it.serverNumber })
        assertTrue(chicagoMatches.all { it.locationLabel == "Chicago, United States" })

        val dallasQuery = ServerCodeMatcher.parseQuery("Dallas (#9898-9899, 10538)")!!
        assertEquals(setOf(9898, 9899, 10538), dallasQuery.numbers)
        assertEquals("Dallas", dallasQuery.locationTextHint)
        val dallasMatches = ServerCodeMatcher.matchServers(sampleFleet, dallasQuery)
        assertEquals(listOf(9898, 9899, 10538), dallasMatches.map { it.serverNumber })
    }

    @Test
    fun supportsCountryPrefixedAndHostnameQueriesIncludingUkToGbNormalization() {
        val usHost = ServerCodeMatcher.parseQuery("us13084.nordvpn.com")!!
        assertEquals("US", usHost.countryCodeHint)
        assertEquals(setOf(13084), usHost.numbers)
        val usMatches = ServerCodeMatcher.matchServers(sampleFleet, usHost)
        assertEquals("Los Angeles, United States", usMatches.first().locationLabel)

        val ukCode = ServerCodeMatcher.parseQuery("uk2410")!!
        assertEquals("UK", ukCode.countryCodeHint)
        val ukMatches = ServerCodeMatcher.matchServers(sampleFleet, ukCode)
        assertEquals(2410, ukMatches.first().serverNumber)
        assertEquals("London, United Kingdom", ukMatches.first().locationLabel)
    }

    @Test
    fun supportsPrefixAutocompleteWhileTypingServerCode() {
        val partial = ServerCodeMatcher.parseQuery("1308")!!
        val matches = ServerCodeMatcher.matchServers(sampleFleet, partial)
        assertEquals(listOf(13084, 13089), matches.map { it.serverNumber })
        assertTrue(matches.all { it.cityName == "Los Angeles" })
    }
}
