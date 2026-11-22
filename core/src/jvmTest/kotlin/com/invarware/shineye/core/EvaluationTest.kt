package com.invarware.shineye.core

import java.io.File
import kotlin.test.Test

/**
 * Rules-only report over a private corpus. Nothing here is committed.
 *
 * Set SHINE_EVAL_CORPUS to a TSV file whose lines are:
 *
 *     scam<TAB>message text
 *     safe<TAB>message text
 *
 * Then run:
 *
 *     SHINE_EVAL_CORPUS=$HOME/private/scam-corpus.tsv \
 *       ./gradlew :core:jvmTest --tests '*EvaluationTest*' --info
 *
 * The test always passes; it prints counts so the number can go into the
 * submission without publishing the messages.
 */
class EvaluationTest {

    @Test
    fun rulesOnlyReport() {
        val path = System.getenv("SHINE_EVAL_CORPUS")
        if (path.isNullOrBlank()) {
            println("SHINE_EVAL_CORPUS is not set; skipping the corpus report.")
            return
        }
        val file = File(path)
        if (!file.exists()) {
            println("Corpus not found at $path; skipping.")
            return
        }

        var scams = 0
        var safe = 0
        var truePositive = 0
        var falsePositive = 0
        var falseNegative = 0
        var trueNegative = 0

        file.forEachLine { line ->
            if (line.isBlank() || line.startsWith("#")) return@forEachLine
            val tab = line.indexOf('\t')
            if (tab < 0) return@forEachLine
            val label = line.substring(0, tab).trim().lowercase()
            val text = line.substring(tab + 1)
            val isScam = label == "scam" || label == "fraud" || label == "1" || label == "yes"
            val isSafe = label == "safe" || label == "legit" || label == "0" || label == "no"
            if (!isScam && !isSafe) return@forEachLine

            val flagged = Analysis.of(Raw(text)).verdict(null).risk != Risk.LOW
            if (isScam) {
                scams++
                if (flagged) truePositive++ else falseNegative++
            } else {
                safe++
                if (flagged) falsePositive++ else trueNegative++
            }
        }

        val precision = if (truePositive + falsePositive == 0) 0.0 else
            truePositive.toDouble() / (truePositive + falsePositive)
        val recall = if (truePositive + falseNegative == 0) 0.0 else
            truePositive.toDouble() / (truePositive + falseNegative)

        println(
            """
            |rules-only corpus report
            |  scams: $scams   safe: $safe
            |  caught (TP): $truePositive   missed (FN): $falseNegative
            |  false alarms (FP): $falsePositive   correctly cleared (TN): $trueNegative
            |  precision: ${"%.3f".format(precision)}   recall: ${"%.3f".format(recall)}
            """.trimMargin(),
        )
    }
}
