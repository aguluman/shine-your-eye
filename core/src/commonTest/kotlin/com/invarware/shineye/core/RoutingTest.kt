package com.invarware.shineye.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutingTest {

    private val everySituation: List<Situation> =
        Risk.entries.flatMap { floor ->
            Language.entries.flatMap { language ->
                listOf(true, false).flatMap { fits ->
                    OnDevice.entries.flatMap { device ->
                        Network.entries.flatMap { network ->
                            CloudConsent.entries.map { consent ->
                                Situation(floor, language, fits, Environment(device, network, consent))
                            }
                        }
                    }
                }
            }
        }

    private val everyOpinion = Risk.entries.flatMap { risk ->
        listOf(true, false).map { Opinion(risk, it, emptyList(), Source.ON_DEVICE) }
    }

    @Test
    fun theSpaceIsCoveredExhaustively() {
        assertEquals(144, everySituation.size)
    }

    @Test
    fun aCertainScamNeedsNoModel() {
        for (s in everySituation.filter { it.floor == Risk.HIGH }) assertEquals(Settle, firstStep(s))
    }

    @Test
    fun theDeviceIsAskedOnlyWhenItCanAnswer() {
        for (s in everySituation) if (firstStep(s) == AskOnDevice) {
            assertTrue(s.environment.onDevice == OnDevice.AVAILABLE && s.fitsOnDevice)
        }
    }

    @Test
    fun theDeviceIsPreferredForEnglishItCanHold() {
        for (s in everySituation) {
            val eligible = s.floor != Risk.HIGH && s.language == Language.ENGLISH &&
                s.fitsOnDevice && s.environment.onDevice == OnDevice.AVAILABLE
            if (eligible) assertEquals(AskOnDevice, firstStep(s))
        }
    }

    @Test
    fun theCloudIsReachedOnlyWithConsentAndANetwork() {
        for (s in everySituation) {
            val steps = listOf(firstStep(s)) + everyOpinion.map { afterDevice(s, it) }
            for (step in steps) {
                if (step == AskCloud) assertTrue(s.environment.network == Network.ONLINE && s.environment.consent == CloudConsent.ALWAYS)
                if (step == OfferCloud) assertTrue(s.environment.network == Network.ONLINE && s.environment.consent == CloudConsent.ASK)
            }
        }
    }

    @Test
    fun aConfidentEnglishAnswerFromTheDeviceIsFinal() {
        for (s in everySituation.filter { it.language == Language.ENGLISH }) {
            for (opinion in everyOpinion.filter { it.confident }) assertEquals(Settle, afterDevice(s, opinion))
        }
    }
}
