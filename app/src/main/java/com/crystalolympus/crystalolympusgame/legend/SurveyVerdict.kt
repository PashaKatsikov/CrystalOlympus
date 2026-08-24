package com.crystalolympus.crystalolympusgame.legend

/**
 * What came back from the config endpoint.
 *
 * [answered] draws the line between two different kinds of "no". If a server
 * responded at all — 404, `ok:false`, an empty body, anything carrying a status
 * line — then it has judged this install, and the judgement is worth writing
 * down. If the request never reached a server, nothing has been judged, and the
 * run must not be filed as native on that basis.
 */
data class SurveyVerdict(
    val active: Boolean,
    val destination: String?,
    val expiresAt: Long,
    val answered: Boolean
) {
    companion object {
        fun native(answered: Boolean = true) = SurveyVerdict(false, null, 0L, answered)
        fun unreachable() = native(answered = false)
        fun stream(url: String, exp: Long = 0L) = SurveyVerdict(true, url, exp, true)
    }
}
