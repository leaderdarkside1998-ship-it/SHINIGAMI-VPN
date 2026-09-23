package com.v2ray.ang.shinigami

/**
 * Produces the short Persian explanation shown under the recommended server.
 *
 * This is intentionally a local, deterministic generator rather than a remote AI call: per the
 * spec, SHINIGAMI AI's only job is interpreting numbers the Scoring Engine already produced
 * ("AI فقط نتایج را تفسیر می‌کند... AI حق ندارد Server جدیدی اختراع کند"), and AI availability
 * must never be a single point of failure for choosing a server. Wiring this generator up to a
 * remote model later is possible (see [ShinigamiAiClient]) but not required for SHINIGAMI to
 * work correctly or explain itself.
 */
object ShinigamiExplainer {

    fun presetTitle(preset: ShinigamiPreset): String = when (preset) {
        ShinigamiPreset.GAMING -> "🎮 Gaming"
        ShinigamiPreset.INSTAGRAM -> "📱 Instagram"
        ShinigamiPreset.DOWNLOAD -> "⬇️ Download"
        ShinigamiPreset.VOICE_CALL -> "🎙️ Voice Call"
        ShinigamiPreset.STREAMING -> "📺 Streaming"
    }

    fun explain(preset: ShinigamiPreset, best: ShinigamiServerScore?): String {
        if (best == null || best.score == null) {
            return "هیچ‌کدام از سرورهای موجود در حال حاضر قابل تست نبودند. لطفاً بعداً دوباره امتحان کنید یا وضعیت اتصال اینترنت را بررسی کنید."
        }

        val m = best.metrics
        val reasons = mutableListOf<String>()
        when (preset) {
            ShinigamiPreset.GAMING ->
                reasons += "کمترین Latency ترکیبی با Packet Loss پایین در تست فعلی"
            ShinigamiPreset.INSTAGRAM ->
                reasons += "ترکیب مناسب پایداری، Latency پایین و Packet Loss کم"
            ShinigamiPreset.DOWNLOAD ->
                reasons += "پایداری بالا و Packet Loss پایین، مناسب برای انتقال حجم زیاد"
            ShinigamiPreset.VOICE_CALL ->
                reasons += "Latency و Jitter پایین برای یک ارتباط صوتی پایدار"
            ShinigamiPreset.STREAMING ->
                reasons += "پایداری بالا در تست فعلی، نه صرفاً Ping پایین"
        }
        if (m.lossPercent == 0) reasons += "بدون Packet Loss در نمونه‌برداری اخیر"

        return "در تست فعلی، سرور «${best.server.remarks}» ${reasons.joinToString("؛ ")} داشته است."
    }

    /** Used when SHINIGAMI AI is unavailable - the Local Scoring Engine result is still shown. */
    fun offlineFallbackPrefix(): String = "SHINIGAMI AI در دسترس نیست، اما بر اساس تست شبکه، نتیجه زیر توسط موتور محلی محاسبه شده است:"
}
