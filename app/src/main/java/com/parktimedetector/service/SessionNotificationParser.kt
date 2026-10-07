package com.parktimedetector.service

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

data class ParsedSessionResult(
    val endTimeMillis: Long,
    val zoneOrLot: String?,
    val detectedReason: String,
    val locationAddress: String? = null,
    val purchasedDurationText: String? = null,
    val costText: String? = null,
    val remainingTimeText: String? = null,
    val notesText: String? = null,
    val startTimeMillis: Long? = null
)

data class ParsedStopResult(
    val stopTimeMillis: Long,
    val durationParkedText: String?,
    val costOrRefundText: String?,
    val zoneOrLot: String?,
    val detectedReason: String,
    val locationAddress: String? = null,
    val stopReason: String? = null
)

object SessionNotificationParser {

    private val START_TIME_PATTERN = Pattern.compile(
        """(?i)\b(?:start(?:ed)?(?:\s+(?:time|at))?|from|entry(?:\s+time)?|parked\s+at|session\s+(?:started|time))\s*:?\s*(?:((?:(?:mon|tue|wed|thu|fri|sat|sun)[a-z]*[,\s]+)?(?:(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\s+\d{1,2}(?:st|nd|rd|th)?|\d{1,2}(?:st|nd|rd|th)?\s+(?:of\s+)?(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*|\d{4}[-/]\d{1,2}[-/]\d{1,2}))\s*(?:-|–|—|at|@|,)?\s*)?(\d{1,2}:\d{2}(?::\d{2})?(?:\s*(?:[ap]\.?m\.?|[ap]m))?)(?=[\s,;.)\]]|$)"""
    )

    private val TIME_RANGE_PATTERN = Pattern.compile(
        """(?i)\b(\d{1,2}:\d{2}(?::\d{2})?\s*(?:[ap]\.?m\.?|[ap]m)?)\s*(?:-|–|—|to|until)\s*(\d{1,2}:\d{2}(?::\d{2})?(?:\s*(?:[ap]\.?m\.?|[ap]m))\b)"""
    )

    // Regex for clock times: e.g. "4:30 PM", "04:30pm", "16:30", "4:30"
    // Preceded by indicator words like expires, expiry, until, ends, valid until, paid until, etc.
    // Regex for clock times: e.g. "4:30 PM", "04:30pm", "16:30", "06:00 a.m.", "Sunday, September 27 - 06:00 a.m."
    // Preceded by indicator words like expires, expiry, until, ends, end time, valid until, paid until, etc.
    private val EXPIRY_TIME_PATTERN = Pattern.compile(
        """(?i)\b(?:expires?(?:\s+(?:at|on|by))?|expiry|until|ends?(?:\s+(?:at|by))?|end\s*time|valid\s+(?:until|to|thru)|paid\s+(?:until|to|through)|active\s+(?:until|to))\s*:?\s*(?:((?:(?:mon|tue|wed|thu|fri|sat|sun)[a-z]*[,\s]+)?(?:(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\s+\d{1,2}(?:st|nd|rd|th)?|\d{1,2}(?:st|nd|rd|th)?\s+(?:of\s+)?(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*|\d{4}[-/]\d{1,2}[-/]\d{1,2}))\s*(?:-|–|—|at|@|,)?\s*)?(\d{1,2}:\d{2}(?::\d{2})?(?:\s*(?:[ap]\.?m\.?|[ap]m))?)(?=[\s,;.)\]]|$)"""
    )

    // Fallback: standalone 12-hour time with AM/PM e.g. "3:45 PM", "06:00 a.m."
    private val STANDALONE_TIME_PATTERN = Pattern.compile(
        """(?i)\b(\d{1,2}:\d{2}(?::\d{2})?\s*(?:[ap]\.?m\.?|[ap]m))(?=[\s,;.)\]]|$)"""
    )

    // Relative duration: e.g. "expires in 15 minutes", "valid for 2 hours", "13 hrs. 50 mins.", "1 hr 30 min", "duration: 2 hr"
    private val DURATION_HOURS_MINS = Pattern.compile(
        """(?i)\b(?:duration|for|in|time|purchased)?\s*:?\s*(\d+)\s*(?:hours?|hrs?\.?|h\.?)\s*(?:and\s*)?(?:(\d+)\s*(?:minutes?|mins?\.?|m\.?))?"""
    )

    private val DURATION_ONLY_MINS = Pattern.compile(
        """(?i)\b(?:duration|for|in|expires\s+in|purchased)?\s*:?\s*(\d+)\s*(?:minutes?|mins?\.?|m\.?)(?=[\s,;.)\]]|$)"""
    )

    // Zone or Lot pattern: e.g. "Zone 4022", "Zone #1234", "Zone Number: 1198", "Lot B", "Lot: 4", "Area 1205", "Location 402"
    private val ZONE_PATTERN = Pattern.compile(
        """(?i)\b(Zone|Lot|Stall|Area|Location)(?:\s+(?:number|num|no\.?|id)\b|\s*#)?\s*[:#]?\s*([A-Za-z0-9_-]+)\b"""
    )

    // On-screen countdown: e.g. "Time Remaining 01:24:30" or "Remaining: 45:00"
    private val SCREEN_COUNTDOWN_PATTERN = Pattern.compile(
        """(?i)\b(?:remaining|left|time\s+remaining|session\s+time|time\s+left|countdown|timer|expires\s+in)\s*:?\s*(\d{1,2}):(\d{2})(?::(\d{2}))?\b"""
    )

    private val SCREEN_COUNTDOWN_SUFFIX_PATTERN = Pattern.compile(
        """(?i)\b(\d{1,2}):(\d{2})(?::(\d{2}))?\s*(?:remaining|left)\b"""
    )

    private val MONTH_NAMES = mapOf(
        "jan" to Calendar.JANUARY, "january" to Calendar.JANUARY,
        "feb" to Calendar.FEBRUARY, "february" to Calendar.FEBRUARY,
        "mar" to Calendar.MARCH, "march" to Calendar.MARCH,
        "apr" to Calendar.APRIL, "april" to Calendar.APRIL,
        "may" to Calendar.MAY,
        "jun" to Calendar.JUNE, "june" to Calendar.JUNE,
        "jul" to Calendar.JULY, "july" to Calendar.JULY,
        "aug" to Calendar.AUGUST, "august" to Calendar.AUGUST,
        "sep" to Calendar.SEPTEMBER, "sept" to Calendar.SEPTEMBER, "september" to Calendar.SEPTEMBER,
        "oct" to Calendar.OCTOBER, "october" to Calendar.OCTOBER,
        "nov" to Calendar.NOVEMBER, "november" to Calendar.NOVEMBER,
        "dec" to Calendar.DECEMBER, "december" to Calendar.DECEMBER
    )

    private val DATE_WITH_MONTH = Pattern.compile(
        """(?i)\b(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\s+(\d{1,2})\b"""
    )
    private val DATE_DAY_FIRST = Pattern.compile(
        """(?i)\b(\d{1,2})\s+(?:of\s+)?(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\b"""
    )

    // Patterns for session stop detection
    private val STOP_TIME_PATTERN = Pattern.compile(
        """(?i)\b(?:stopped|ended|stop\s*time|end\s*time|left(?:\s*at)?|completed(?:\s*at)?|time)\s*:?\s*(?:((?:(?:mon|tue|wed|thu|fri|sat|sun)[a-z]*[,\s]+)?(?:(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\s+\d{1,2}(?:st|nd|rd|th)?|\d{1,2}(?:st|nd|rd|th)?\s+(?:of\s+)?(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*|\d{4}[-/]\d{1,2}[-/]\d{1,2}))\s*(?:-|–|—|at|@|,)?\s*)?(\d{1,2}:\d{2}(?::\d{2})?(?:\s*(?:[ap]\.?m\.?|[ap]m))?)\b"""
    )

    // Completed stop indicators (past tense, receipts, completed events)
    private val STOP_COMPLETED_INDICATOR_WORDS = listOf(
        "session ended", "parking ended", "session stopped", "parking stopped",
        "parking completed", "session completed", "session summary", "parking summary",
        "stopped at", "left parking", "parking finished", "session finished",
        "session has been deactivated", "session deactivated", "parking deactivated",
        "has been deactivated", "deactivated", "deactivation"
    )

    // Action button labels (these indicate buttons to click to stop, NOT that the session has ended!)
    private val STOP_ACTION_BUTTON_WORDS = listOf(
        "stop parking", "end session", "stop session"
    )

    // Active session screen indicators
    private val ACTIVE_SESSION_INDICATOR_WORDS = listOf(
        "active session", "time remaining", "time left", "valid until", "active parking", "parking active"
    )

    private val COST_OR_REFUND_PATTERN = Pattern.compile(
        """(?i)\b(?:total(?:\s*(?:cost|amount|paid|fee))?|cost(?:\s+for\s+this\s+session)?(?:\s+(?:was|is))?|paid|amount|refund|charge(?:d)?)\s*:?\s*(\$\s*\d+(?:\.\d{2})?)\b"""
    )

    private val STANDALONE_PRICE_PATTERN = Pattern.compile(
        """(\$\s*\d+\.\d{2})"""
    )

    private val STANDALONE_PRICE_IN_PARENS_PATTERN = Pattern.compile(
        """\(\s*(\$\s*\d*(?:\.\d{2})?)\s*\)|(\$\s*\d+(?:\.\d{2})?)"""
    )

    private val DURATION_PARKED_PATTERN = Pattern.compile(
        """(?i)\b(?:total\s*(?:time|duration)|time\s*parked|duration|parked\s*for)\s*:?\s*(\d+\s*(?:hours?|hrs?\.?|h\.?)(?:\s*(?:and\s*)?\d+\s*(?:minutes?|mins?\.?|m\.?))?|\d+\s*(?:minutes?|mins?\.?|m\.?))\b"""
    )

    // Countdown with words e.g. "Remaining: 19 mins : 57 secs", "Remaining: 1 hr : 20 mins"
    private val SCREEN_COUNTDOWN_WORDS_PATTERN = Pattern.compile(
        """(?i)\b(?:remaining|left|time\s+remaining)\s*:?\s*(?:(\d+)\s*(?:hours?|hrs?\.?|h)\s*(?::\s*)?)?(\d+)\s*(?:minutes?|mins?\.?|m)(?:\s*[:]\s*(\d+)\s*(?:seconds?|secs?\.?|s))?\b"""
    )

    private val REMAINING_PREFIX_PATTERN = Pattern.compile(
        """(?i)^(?:remaining|left|time\s+remaining)\s*:?\s*"""
    )

    private val PURCHASED_DURATION_PATTERN = Pattern.compile(
        """(?i)\b(?:duration\s*:?\s*)?(\d+\s*(?:hours?|hrs?\.?|h\.?)\s*(?:and\s*)?\d+\s*(?:minutes?|mins?\.?|m\.?)|\d+\s*(?:hours?|hrs?\.?|h\.?)|\d+\s*(?:minutes?|mins?\.?|m\.?))(?=[\s,.)\]]|$)"""
    )

    // Street Address pattern (e.g. "50 Av SW , Fr 6 St SW To ELBOW Dr SW", "Lot 58 - 935 - 4 Av SW")
    private val STREET_ADDRESS_PATTERN = Pattern.compile(
        """(?i)\b(?:\d+.*?(?:Av(?:e|enue)?|St(?:reet)?|Dr(?:ive)?|Blvd|Boulevard|Rd|Road|Way|Trail|Tr|Cres(?:cent)?|Lane|Ln|Place|Pl)\b.*?(?:SW|SE|NW|NE)?|Fr\s+.*?\s+To\s+.*|Lot\s+\d+\s*-.*)"""
    )

    fun isStartEndActiveSessionScreen(texts: List<String>): Boolean {
        val combined = texts.joinToString(" ").lowercase()
        val hasStartEndHeader = combined.contains("start/end session") || combined.contains("start / end session")
        val hasEndParkingButton = combined.contains("end parking session")
        val hasCountdown = combined.contains("remaining:") || combined.contains("end time:")
        return (hasStartEndHeader || hasEndParkingButton) && hasCountdown
    }

    /**
     * Determines whether the current screen content or window class in MyParking corresponds
     * to the map exploration or zone search view.
     */
    fun isMyParkingMapScreen(texts: List<String>, className: String? = null): Boolean {
        // If an active session countdown or receipt stop screen is present, it is not a pure map search screen
        if (isStartEndActiveSessionScreen(texts) || isStopScreen(texts)) {
            return false
        }

        val combined = texts.joinToString(" ").lowercase()

        // 1. Check class name for map indicators
        val lowerClass = className?.lowercase() ?: ""
        if (lowerClass.contains("map") || lowerClass.contains("mockmyparking")) {
            return true
        }

        // 2. Check for map / search / zone keywords
        val hasMapKeywords = combined.contains("map") ||
            combined.contains("search zone") ||
            combined.contains("search lot") ||
            combined.contains("select pin") ||
            combined.contains("interactive map") ||
            combined.contains("calgary parking") ||
            combined.contains("press pin") ||
            combined.contains("zone number") ||
            combined.contains("lot number") ||
            combined.contains("find parking") ||
            combined.contains("nearby")

        val hasZoneOrLot = combined.contains("zone") || combined.contains("lot")

        return hasMapKeywords && hasZoneOrLot
    }

    fun extractLocationAddress(nodes: List<String>): String? {
        val nonAddressIndicators = listOf(
            "start/end session", "end parking session", "start parking", "stop parking",
            "take photo", "find my car", "local deals", "note:", "please remember",
            "active session", "session ended", "parking ended", "navigate up"
        )
        for (raw in nodes) {
            val node = raw.trim()
            if (node.isBlank()) continue
            val lower = node.lowercase()
            if (nonAddressIndicators.any { lower.startsWith(it) || lower == it }) continue
            if (lower.startsWith("remaining:") || lower.startsWith("end time:") || lower.startsWith("duration")) continue
            if (lower.startsWith("zone") && (lower.contains("mins") || lower.contains("$"))) continue

            if (STREET_ADDRESS_PATTERN.matcher(node).find()) {
                return if (node.startsWith("Lot", ignoreCase = true) && node.contains("-")) {
                    node.substringAfter("-").trim()
                } else {
                    node
                }
            }
        }
        return null
    }

    fun extractPurchasedDuration(nodes: List<String>, combined: String): String? {
        val m = PURCHASED_DURATION_PATTERN.matcher(combined)
        while (m.find()) {
            val candidate = m.group(1)?.trim() ?: continue
            val startIdx = m.start()
            val prefix = combined.substring(0.coerceAtLeast(startIdx - 15), startIdx).lowercase()
            if (!prefix.contains("remaining") && !prefix.contains("left")) {
                return candidate
            }
        }
        return null
    }

    fun extractCostOrRate(nodes: List<String>, combined: String): String? {
        val m = COST_OR_REFUND_PATTERN.matcher(combined)
        if (m.find()) {
            return m.group(1)?.trim() ?: m.group(0)?.trim()
        }
        val standalone = STANDALONE_PRICE_IN_PARENS_PATTERN.matcher(combined)
        if (standalone.find()) {
            val found = (standalone.group(1) ?: standalone.group(2))?.trim()
            if (found == "$.00") return "$0.00"
            return found
        }
        return null
    }

    fun extractRemainingCountdown(nodes: List<String>, combined: String): String? {
        // 1. Inspect discrete nodes directly (e.g. "Remaining: 19 mins : 57 secs")
        for (raw in nodes) {
            val node = raw.trim()
            val m = REMAINING_PREFIX_PATTERN.matcher(node)
            if (m.find()) {
                val candidate = node.substring(m.end()).trim()
                if (candidate.isNotBlank() && (candidate.contains("min", ignoreCase = true) || candidate.contains("sec", ignoreCase = true) || candidate.contains(":"))) {
                    return candidate
                }
            }
        }

        // 2. Fall back to precompiled words countdown pattern on combined text (e.g. "19 mins : 57 secs")
        val wordsMatcher = SCREEN_COUNTDOWN_WORDS_PATTERN.matcher(combined)
        if (wordsMatcher.find()) {
            val fullMatch = wordsMatcher.group(0)?.trim()
            if (!fullMatch.isNullOrBlank()) {
                val prefixMatch = REMAINING_PREFIX_PATTERN.matcher(fullMatch)
                val stripped = if (prefixMatch.find()) fullMatch.substring(prefixMatch.end()).trim() else fullMatch
                if (stripped.isNotBlank()) return stripped
            }
        }

        // 3. Fall back to digital timer pattern (e.g. "01:24:30")
        val digitalMatcher = SCREEN_COUNTDOWN_PATTERN.matcher(combined)
        if (digitalMatcher.find()) {
            val h = digitalMatcher.group(1)
            val m = digitalMatcher.group(2)
            val s = digitalMatcher.group(3)
            return if (s != null) "$h:$m:$s" else "$h:$m"
        }

        return null
    }

    fun extractNotes(nodes: List<String>): String? {
        for (raw in nodes) {
            val node = raw.trim()
            if (node.startsWith("Note:", ignoreCase = true) || node.startsWith("Please remember", ignoreCase = true)) {
                return node
            }
        }
        return null
    }

    fun containsStopActionButton(texts: List<String>): Boolean {
        val combined = texts.joinToString(" ").lowercase()
        return STOP_ACTION_BUTTON_WORDS.any { combined.contains(it) }
    }

    fun isStopScreen(texts: List<String>): Boolean {
        val cleanNodes = texts.map { it.trim() }.filter { it.isNotBlank() }
        if (cleanNodes.isEmpty()) return false

        val combined = cleanNodes.joinToString(" ").lowercase()

        // 1. Check if it has a past-tense completion indicator
        val matchedCompletion = STOP_COMPLETED_INDICATOR_WORDS.any { combined.contains(it) }
        if (!matchedCompletion) return false

        // 2. If it explicitly states "Active Session" or "Time Remaining" without "ended" / "stopped at" / "deactivated",
        // it is an active dashboard that might merely have a "Session Summary" tab or button.
        val hasActiveIndicator = ACTIVE_SESSION_INDICATOR_WORDS.any { combined.contains(it) }
        val hasExplicitEnded = combined.contains("ended") || combined.contains("stopped at") || combined.contains("left parking") || combined.contains("deactivated")
        if (hasActiveIndicator && !hasExplicitEnded) {
            return false
        }

        return true
    }

    fun parseStopScreenText(
        nodes: List<String>,
        postTimeMillis: Long = System.currentTimeMillis()
    ): ParsedStopResult? {
        val cleanNodes = nodes.map { it.trim() }.filter { it.isNotBlank() }
        if (cleanNodes.isEmpty()) return null

        if (!isStopScreen(cleanNodes)) return null

        val combined = cleanNodes.joinToString(" ")
        val combinedLower = combined.lowercase()

        // Stop Time
        var stopTime = postTimeMillis
        val stopTimeMatcher = STOP_TIME_PATTERN.matcher(combined)
        if (stopTimeMatcher.find()) {
            val dateContext = stopTimeMatcher.group(1)
            val timeString = stopTimeMatcher.group(2)
            val parsed = parseClockTime(timeString, postTimeMillis, dateContext)
            if (parsed != null) {
                stopTime = parsed
            }
        } else {
            // Fallback: Standalone time if present
            val standaloneMatcher = STANDALONE_TIME_PATTERN.matcher(combined)
            if (standaloneMatcher.find()) {
                val parsed = parseClockTime(standaloneMatcher.group(1), postTimeMillis)
                if (parsed != null) {
                    stopTime = parsed
                }
            }
        }

        // Duration parked
        var durationParkedText: String? = null
        val dH = DURATION_HOURS_MINS.matcher(combined)
        if (dH.find()) {
            val h = dH.group(1)?.toIntOrNull() ?: 0
            val m = dH.group(2)?.toIntOrNull() ?: 0
            durationParkedText = if (m > 0) "${h}h ${m}m" else "${h}h"
        } else {
            val dM = DURATION_ONLY_MINS.matcher(combined)
            if (dM.find()) {
                durationParkedText = "${dM.group(1)}m"
            } else {
                val durationMatcher = DURATION_PARKED_PATTERN.matcher(combined)
                if (durationMatcher.find()) {
                    durationParkedText = durationMatcher.group(1)?.trim()
                }
            }
        }

        // Cost / Refund
        var costOrRefundText: String? = null
        val costMatcher = COST_OR_REFUND_PATTERN.matcher(combined)
        if (costMatcher.find()) {
            costOrRefundText = costMatcher.group(1)?.trim() ?: costMatcher.group(0)?.trim()
        } else {
            val priceMatcher = STANDALONE_PRICE_PATTERN.matcher(combined)
            if (priceMatcher.find()) {
                costOrRefundText = priceMatcher.group(1)?.trim()
            }
        }

        val zone = extractZone(combined)
        val matchedIndicator = STOP_COMPLETED_INDICATOR_WORDS.firstOrNull { combinedLower.contains(it) } ?: "Session stopped"

        return ParsedStopResult(
            stopTimeMillis = stopTime,
            durationParkedText = durationParkedText,
            costOrRefundText = costOrRefundText,
            zoneOrLot = zone,
            detectedReason = "Stop screen detected: $matchedIndicator",
            locationAddress = extractLocationAddress(cleanNodes)
        )
    }

    fun parseStopNotification(
        title: String?,
        text: String?,
        subText: String? = null,
        postTimeMillis: Long = System.currentTimeMillis()
    ): ParsedStopResult? {
        val combined = listOfNotNull(title, text, subText).joinToString(" ").trim()
        if (combined.isBlank()) return null
        return parseStopScreenText(listOf(combined), postTimeMillis)
    }

    fun parseScreenText(
        nodes: List<String>,
        postTimeMillis: Long = System.currentTimeMillis()
    ): ParsedSessionResult? {
        val cleanNodes = nodes.map { it.trim() }.filter { it.isNotBlank() }
        if (cleanNodes.isEmpty()) return null

        val combined = cleanNodes.joinToString(" ")
        val address = extractLocationAddress(cleanNodes)
        val duration = extractPurchasedDuration(cleanNodes, combined)
        val cost = extractCostOrRate(cleanNodes, combined)
        val countdown = extractRemainingCountdown(cleanNodes, combined)
        val notes = extractNotes(cleanNodes)
        val explicitStartTime = extractStartTime(combined, postTimeMillis)

        // 1. Try combined text
        val parsedCombined = parse(null, combined, null, postTimeMillis)
        if (parsedCombined != null) {
            return parsedCombined.copy(
                locationAddress = address ?: parsedCombined.locationAddress,
                purchasedDurationText = duration ?: parsedCombined.purchasedDurationText,
                costText = cost ?: parsedCombined.costText,
                remainingTimeText = countdown ?: parsedCombined.remainingTimeText,
                notesText = notes ?: parsedCombined.notesText,
                startTimeMillis = explicitStartTime ?: parsedCombined.startTimeMillis
            )
        }

        // 2. Check for on-screen countdown pattern with words (e.g. "Remaining: 19 mins : 57 secs")
        val wordsCountdownMatcher = SCREEN_COUNTDOWN_WORDS_PATTERN.matcher(combined)
        if (wordsCountdownMatcher.find()) {
            val part1 = wordsCountdownMatcher.group(1)?.toLongOrNull() ?: 0L
            val part2 = wordsCountdownMatcher.group(2)?.toLongOrNull() ?: 0L
            val part3 = wordsCountdownMatcher.group(3)?.toLongOrNull() ?: 0L

            val durationMillis = ((part1 * 3600) + (part2 * 60) + part3) * 1000L
            if (durationMillis > 0) {
                return ParsedSessionResult(
                    endTimeMillis = postTimeMillis + durationMillis,
                    zoneOrLot = extractZone(combined),
                    detectedReason = "Screen countdown timer: ${wordsCountdownMatcher.group(0)}",
                    locationAddress = address,
                    purchasedDurationText = duration,
                    costText = cost,
                    remainingTimeText = countdown,
                    notesText = notes,
                    startTimeMillis = explicitStartTime ?: postTimeMillis
                )
            }
        }

        // 3. Check for on-screen countdown pattern (prefix mm:ss or hh:mm:ss)
        val countdownMatcher = SCREEN_COUNTDOWN_PATTERN.matcher(combined)
        if (countdownMatcher.find()) {
            val part1 = countdownMatcher.group(1)?.toLongOrNull() ?: 0L
            val part2 = countdownMatcher.group(2)?.toLongOrNull() ?: 0L
            val part3 = countdownMatcher.group(3)?.toLongOrNull()

            val durationMillis = if (part3 != null) {
                ((part1 * 3600) + (part2 * 60) + part3) * 1000L
            } else {
                ((part1 * 60) + part2) * 1000L
            }

            if (durationMillis > 0) {
                return ParsedSessionResult(
                    endTimeMillis = postTimeMillis + durationMillis,
                    zoneOrLot = extractZone(combined),
                    detectedReason = "Screen countdown timer: ${countdownMatcher.group(0)}",
                    locationAddress = address,
                    purchasedDurationText = duration,
                    costText = cost,
                    remainingTimeText = countdown,
                    notesText = notes
                )
            }
        }

        // 4. Check for on-screen countdown pattern (suffix: e.g. "01:25:00 remaining")
        val suffixMatcher = SCREEN_COUNTDOWN_SUFFIX_PATTERN.matcher(combined)
        if (suffixMatcher.find()) {
            val part1 = suffixMatcher.group(1)?.toLongOrNull() ?: 0L
            val part2 = suffixMatcher.group(2)?.toLongOrNull() ?: 0L
            val part3 = suffixMatcher.group(3)?.toLongOrNull()

            val durationMillis = if (part3 != null) {
                ((part1 * 3600) + (part2 * 60) + part3) * 1000L
            } else {
                ((part1 * 60) + part2) * 1000L
            }

            if (durationMillis > 0) {
                return ParsedSessionResult(
                    endTimeMillis = postTimeMillis + durationMillis,
                    zoneOrLot = extractZone(combined),
                    detectedReason = "Screen countdown timer: ${suffixMatcher.group(0)}",
                    locationAddress = address,
                    purchasedDurationText = duration,
                    costText = cost,
                    remainingTimeText = countdown,
                    notesText = notes
                )
            }
        }

        // 5. Check adjacent node pairs (handles split labels across UI views)
        for (i in 0 until cleanNodes.size - 1) {
            val pair = "${cleanNodes[i]} ${cleanNodes[i + 1]}"
            val pairResult = parse(null, pair, null, postTimeMillis)
            if (pairResult != null) {
                val zone = pairResult.zoneOrLot ?: extractZone(combined)
                return pairResult.copy(
                    zoneOrLot = zone,
                    locationAddress = address ?: pairResult.locationAddress,
                    purchasedDurationText = duration ?: pairResult.purchasedDurationText,
                    costText = cost ?: pairResult.costText,
                    remainingTimeText = countdown ?: pairResult.remainingTimeText,
                    notesText = notes ?: pairResult.notesText
                )
            }
        }

        return null
    }

    fun extractStartTime(combined: String, postTimeMillis: Long): Long? {
        val startMatcher = START_TIME_PATTERN.matcher(combined)
        if (startMatcher.find()) {
            val dateContext = startMatcher.group(1)
            val timeStr = startMatcher.group(2)
            val parsed = parseClockTime(timeStr, postTimeMillis, dateContext)
            if (parsed != null) return parsed
        }
        val rangeMatcher = TIME_RANGE_PATTERN.matcher(combined)
        if (rangeMatcher.find()) {
            val time1 = rangeMatcher.group(1)
            val parsed = parseClockTime(time1, postTimeMillis)
            if (parsed != null) return parsed
        }
        return null
    }

    fun parse(
        title: String?,
        text: String?,
        subText: String? = null,
        postTimeMillis: Long = System.currentTimeMillis()
    ): ParsedSessionResult? {
        val combined = listOfNotNull(title, text, subText).joinToString(" ").trim()
        if (combined.isBlank()) return null

        val zone = extractZone(combined)
        val startTime = extractStartTime(combined, postTimeMillis)

        // 1. Try explicit expiry indicator with clock time and optional date context
        val explicitMatcher = EXPIRY_TIME_PATTERN.matcher(combined)
        if (explicitMatcher.find()) {
            val dateContext = explicitMatcher.group(1)
            val timeString = explicitMatcher.group(2)
            val parsedMillis = parseClockTime(timeString, postTimeMillis, dateContext)
            if (parsedMillis != null) {
                return ParsedSessionResult(
                    endTimeMillis = parsedMillis,
                    zoneOrLot = zone,
                    detectedReason = "Explicit expiry time: $timeString",
                    startTimeMillis = startTime
                )
            }
        }

        // 2. Try duration formats (e.g. "2 hours 30 mins", "13 hrs. 50 mins.")
        val durationMatcher = DURATION_HOURS_MINS.matcher(combined)
        if (durationMatcher.find()) {
            val hours = durationMatcher.group(1)?.toLongOrNull() ?: 0L
            val mins = durationMatcher.group(2)?.toLongOrNull() ?: 0L
            val totalMillis = ((hours * 60) + mins) * 60 * 1000L
            if (totalMillis > 0) {
                return ParsedSessionResult(
                    endTimeMillis = postTimeMillis + totalMillis,
                    zoneOrLot = zone,
                    detectedReason = "Duration: ${hours}h ${mins}m",
                    startTimeMillis = startTime ?: postTimeMillis
                )
            }
        }

        // 3. Try duration with only minutes (e.g. "30 mins", "expires in 45 min")
        val minMatcher = DURATION_ONLY_MINS.matcher(combined)
        if (minMatcher.find()) {
            val mins = minMatcher.group(1)?.toLongOrNull() ?: 0L
            if (mins > 0) {
                return ParsedSessionResult(
                    endTimeMillis = postTimeMillis + (mins * 60 * 1000L),
                    zoneOrLot = zone,
                    detectedReason = "Duration: ${mins} minutes",
                    startTimeMillis = startTime ?: postTimeMillis
                )
            }
        }

        // 4. Try time range (e.g. "10:00 AM - 12:00 PM" or "10:00 AM to 12:00 PM")
        val rangeMatcher = TIME_RANGE_PATTERN.matcher(combined)
        if (rangeMatcher.find()) {
            val startStr = rangeMatcher.group(1)
            val endStr = rangeMatcher.group(2)
            val parsedEnd = parseClockTime(endStr, postTimeMillis)
            val parsedStart = parseClockTime(startStr, postTimeMillis)
            if (parsedEnd != null) {
                return ParsedSessionResult(
                    endTimeMillis = parsedEnd,
                    zoneOrLot = zone,
                    detectedReason = "Time range: $startStr - $endStr",
                    startTimeMillis = parsedStart ?: startTime
                )
            }
        }

        // 5. Try standalone clock time with AM/PM
        val standaloneMatcher = STANDALONE_TIME_PATTERN.matcher(combined)
        if (standaloneMatcher.find()) {
            val timeString = standaloneMatcher.group(1)
            val parsedMillis = parseClockTime(timeString, postTimeMillis)
            if (parsedMillis != null) {
                return ParsedSessionResult(
                    endTimeMillis = parsedMillis,
                    zoneOrLot = zone,
                    detectedReason = "Detected time: $timeString",
                    startTimeMillis = startTime
                )
            }
        }

        return null
    }

    fun extractZone(text: String): String? {
        val matcher = ZONE_PATTERN.matcher(text)
        while (matcher.find()) {
            val label = matcher.group(1) ?: continue
            val value = matcher.group(2) ?: continue
            val lowerVal = value.lowercase()
            if (lowerVal in listOf("number", "num", "no", "id", "#")) continue
            return "$label $value"
        }
        return null
    }

    private fun parseClockTime(rawTime: String?, baseTimeMillis: Long, rawDateContext: String? = null): Long? {
        if (rawTime == null) return null
        val cleaned = rawTime.trim()
            .replace(".", "")
            .replace("a m", "am", ignoreCase = true)
            .replace("p m", "pm", ignoreCase = true)
            .replace(Regex("\\s+"), " ")

        val hasAmPm = cleaned.contains("am", ignoreCase = true) || cleaned.contains("pm", ignoreCase = true)
        val formats = if (hasAmPm) {
            listOf(
                "h:mm a",
                "hh:mm a",
                "h:mma",
                "hh:mma",
                "h:mm:ss a",
                "hh:mm:ss a"
            )
        } else {
            listOf(
                "H:mm",
                "HH:mm",
                "H:mm:ss",
                "HH:mm:ss"
            )
        }

        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.isLenient = false
                val date = sdf.parse(cleaned) ?: continue

                val timeCal = Calendar.getInstance().apply { time = date }
                val targetCal = Calendar.getInstance().apply {
                    timeInMillis = baseTimeMillis
                    set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
                    set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                var explicitDateSet = false
                if (!rawDateContext.isNullOrBlank()) {
                    val m1 = DATE_WITH_MONTH.matcher(rawDateContext)
                    if (m1.find()) {
                        val month = m1.group(1)?.lowercase()?.let { MONTH_NAMES[it] }
                        val day = m1.group(2)?.toIntOrNull()
                        if (month != null && day != null) {
                            targetCal.set(Calendar.MONTH, month)
                            targetCal.set(Calendar.DAY_OF_MONTH, day)
                            explicitDateSet = true
                        }
                    } else {
                        val m2 = DATE_DAY_FIRST.matcher(rawDateContext)
                        if (m2.find()) {
                            val day = m2.group(1)?.toIntOrNull()
                            val month = m2.group(2)?.lowercase()?.let { MONTH_NAMES[it] }
                            if (month != null && day != null) {
                                targetCal.set(Calendar.MONTH, month)
                                targetCal.set(Calendar.DAY_OF_MONTH, day)
                                explicitDateSet = true
                            }
                        }
                    }
                }

                if (!explicitDateSet) {
                    // If target time is earlier than base time by more than 10 minutes,
                    // it likely wraps around midnight to the next day!
                    if (targetCal.timeInMillis < baseTimeMillis - (10 * 60 * 1000L)) {
                        targetCal.add(Calendar.DAY_OF_YEAR, 1)
                    }
                } else {
                    // If explicit date was set but time has already passed (e.g. year rollover)
                    if (targetCal.timeInMillis < baseTimeMillis - (10 * 60 * 1000L)) {
                        targetCal.add(Calendar.YEAR, 1)
                    }
                }

                return targetCal.timeInMillis
            } catch (_: Exception) {
                // Try next pattern
            }
        }

        return null
    }
}
