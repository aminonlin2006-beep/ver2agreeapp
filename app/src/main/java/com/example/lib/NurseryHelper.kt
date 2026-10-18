package com.example.lib

data class NurseryPlotData(
    val saplingCount: Int? = null,
    val scionVariety: String = "",
    val graftYear: String = "",
    val generalNote: String = ""
)

object NurseryHelper {
    private const val PREFIX = "[[نهالستان]]"

    fun parse(note: String): NurseryPlotData {
        if (!note.contains(PREFIX)) {
            return NurseryPlotData(generalNote = note)
        }

        var saplingCount: Int? = null
        var scionVariety = ""
        var graftYear = ""
        val remainingLines = mutableListOf<String>()

        note.lines().forEach { line ->
            when {
                line.startsWith("تعداد_نهال:") -> {
                    saplingCount = line.removePrefix("تعداد_نهال:").trim().toIntOrNull()
                }
                line.startsWith("رقم_پیوندک:") -> {
                    scionVariety = line.removePrefix("رقم_پیوندک:").trim()
                }
                line.startsWith("سال_پیوند:") -> {
                    graftYear = line.removePrefix("سال_پیوند:").trim()
                }
                line.trim() == PREFIX -> {
                    // skip marker
                }
                else -> {
                    remainingLines.add(line)
                }
            }
        }

        return NurseryPlotData(
            saplingCount = saplingCount,
            scionVariety = scionVariety,
            graftYear = graftYear,
            generalNote = remainingLines.joinToString("\n").trim()
        )
    }

    fun format(saplingCount: Int?, scionVariety: String, graftYear: String, generalNote: String): String {
        val hasNurseryData = saplingCount != null || scionVariety.isNotBlank() || graftYear.isNotBlank()
        if (!hasNurseryData) {
            return generalNote.trim()
        }

        val sb = StringBuilder()
        sb.appendLine(PREFIX)
        if (saplingCount != null && saplingCount > 0) {
            sb.appendLine("تعداد_نهال: $saplingCount")
        }
        if (scionVariety.isNotBlank()) {
            sb.appendLine("رقم_پیوندک: ${scionVariety.trim()}")
        }
        if (graftYear.isNotBlank()) {
            sb.appendLine("سال_پیوند: ${graftYear.trim()}")
        }
        if (generalNote.isNotBlank()) {
            sb.appendLine(generalNote.trim())
        }
        return sb.toString().trim()
    }
}
