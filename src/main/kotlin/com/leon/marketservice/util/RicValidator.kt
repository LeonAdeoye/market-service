package com.leon.marketservice.util

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Utility class for validating RIC (Reuters Instrument Code) formats
 * Provides validation for different RIC formats used by various exchanges
 * 
 * This utility helps ensure that RIC codes are in the correct format
 * before making API calls to external data sources.
 */
@Component
class RicValidator {
    
    // Logger for this utility
    private val logger = LoggerFactory.getLogger(RicValidator::class.java)
    
    // RIC patterns for different exchanges
    private val ricPatterns = mapOf(
        "HK" to Regex("^\\d{4}\\.HK$"), // Hong Kong: 4 digits + .HK
        "T" to Regex("^\\d{4}\\.T$"),   // Japan: 4 digits + .T
        "US" to Regex("^[A-Z]{1,5}$"),  // US: 1-5 letters
        "LSE" to Regex("^[A-Z]{1,4}\\.L$"), // London: 1-4 letters + .L
        "ASX" to Regex("^[A-Z]{3}\\.AX$")   // Australia: 3 letters + .AX
    )

    /**
     * Validate a single RIC code
     * Checks if the RIC code matches any known pattern
     * 
     * @param ric The RIC code to validate
     * @return true if valid, false otherwise
     */
    fun isValidRic(ric: String): Boolean {
        if (ric.isBlank()) {
            logger.debug("Empty RIC code provided")
            return false
        }
        
        val upperRic = ric.uppercase()
        
        // Check against all known patterns
        val isValid = ricPatterns.values.any { pattern ->
            pattern.matches(upperRic)
        }
        
        if (!isValid) {
            logger.debug("Invalid RIC format: $ric")
        }
        
        return isValid
    }

    /**
     * Validate multiple RIC codes
     * Checks if all RIC codes in the list are valid
     * 
     * @param rics List of RIC codes to validate
     * @return Pair of valid and invalid RIC codes
     */
    fun validateRics(rics: List<String>): Pair<List<String>, List<String>> {
        val validRics = mutableListOf<String>()
        val invalidRics = mutableListOf<String>()
        
        for (ric in rics) {
            if (isValidRic(ric)) {
                validRics.add(ric)
            } else {
                invalidRics.add(ric)
            }
        }
        
        logger.debug("Validated ${rics.size} RICs: ${validRics.size} valid, ${invalidRics.size} invalid")
        
        return Pair(validRics, invalidRics)
    }

    /**
     * Get RIC format information
     * Returns information about the format of a RIC code
     * 
     * @param ric The RIC code to analyze
     * @return Map containing format information
     */
    fun getRicFormatInfo(ric: String): Map<String, Any> {
        val upperRic = ric.uppercase()
        
        val matchedPattern = ricPatterns.entries.find { (_, pattern) ->
            pattern.matches(upperRic)
        }
        
        return if (matchedPattern != null) {
            mapOf(
                "ric" to ric,
                "exchange" to matchedPattern.key,
                "format" to "Valid",
                "pattern" to matchedPattern.value.pattern
            )
        } else {
            mapOf(
                "ric" to ric,
                "exchange" to "Unknown",
                "format" to "Invalid",
                "pattern" to "No matching pattern found"
            )
        }
    }

    /**
     * Get supported RIC formats
     * Returns information about all supported RIC formats
     * 
     * @return Map containing supported formats
     */
    fun getSupportedFormats(): Map<String, Any> {
        return mapOf(
            "supportedExchanges" to ricPatterns.keys.toList(),
            "patterns" to ricPatterns.mapValues { (_, pattern) ->
                mapOf(
                    "pattern" to pattern.pattern,
                    "description" to getPatternDescription(pattern.pattern)
                )
            }
        )
    }

    /**
     * Get pattern description
     * Returns a human-readable description of a regex pattern
     * 
     * @param pattern The regex pattern
     * @return Human-readable description
     */
    private fun getPatternDescription(pattern: String): String {
        return when (pattern) {
            "^\\d{4}\\.HK$" -> "Hong Kong stocks: 4 digits followed by .HK (e.g., 0700.HK)"
            "^\\d{4}\\.T$" -> "Japan stocks: 4 digits followed by .T (e.g., 7203.T)"
            "^[A-Z]{1,5}$" -> "US stocks: 1-5 letters (e.g., AAPL, MSFT)"
            "^[A-Z]{1,4}\\.L$" -> "London stocks: 1-4 letters followed by .L (e.g., VOD.L)"
            "^[A-Z]{3}\\.AX$" -> "Australia stocks: 3 letters followed by .AX (e.g., BHP.AX)"
            else -> "Unknown pattern"
        }
    }

    /**
     * Normalize RIC code
     * Converts RIC code to standard format
     * 
     * @param ric The RIC code to normalize
     * @return Normalized RIC code
     */
    fun normalizeRic(ric: String): String {
        return ric.trim().uppercase()
    }

    /**
     * Extract exchange from RIC
     * Determines the exchange from a RIC code
     * 
     * @param ric The RIC code
     * @return Exchange code or null if not found
     */
    fun extractExchange(ric: String): String? {
        val upperRic = ric.uppercase()
        
        return ricPatterns.entries.find { (_, pattern) ->
            pattern.matches(upperRic)
        }?.key
    }
}
