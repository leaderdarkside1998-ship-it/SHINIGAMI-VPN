package com.v2ray.ang.shinigami

/**
 * Classifies free-form chat text (Persian or English) into one of the five presets, fully
 * offline. This runs before any AI call, so intent detection is never a single point of failure:
 * even with SHINIGAMI AI unavailable, typing "برای بازی کدوم سرور بهتره؟" still routes correctly.
 */
object ShinigamiIntentClassifier {

    private val keywords: Map<ShinigamiPreset, List<String>> = mapOf(
        ShinigamiPreset.GAMING to listOf(
            "بازی", "گیم", "game", "gaming", "pubg", "valorant", "cod", "fortnite"
        ),
        ShinigamiPreset.INSTAGRAM to listOf(
            "اینستا", "اینستاگرام", "instagram", "insta", "استوری", "ریلز", "reels"
        ),
        ShinigamiPreset.DOWNLOAD to listOf(
            "دانلود", "download", "دانلودی", "حجم", "فایل بزرگ"
        ),
        ShinigamiPreset.VOICE_CALL to listOf(
            "ویس", "تماس صوتی", "کال", "voice", "call", "تماس", "مکالمه"
        ),
        ShinigamiPreset.STREAMING to listOf(
            "استریم", "پخش", "streaming", "stream", "یوتیوب", "youtube", "نتفلیکس", "netflix", "فیلم"
        )
    )

    /** Returns the best-matching preset, or null if nothing in [text] matches a known keyword. */
    fun classify(text: String): ShinigamiPreset? {
        val normalized = text.trim().lowercase()
        if (normalized.isEmpty()) return null

        var best: ShinigamiPreset? = null
        var bestHits = 0
        keywords.forEach { (preset, terms) ->
            val hits = terms.count { normalized.contains(it.lowercase()) }
            if (hits > bestHits) {
                bestHits = hits
                best = preset
            }
        }
        return best
    }
}
