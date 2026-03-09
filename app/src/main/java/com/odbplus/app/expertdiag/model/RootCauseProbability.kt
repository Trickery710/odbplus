package com.odbplus.app.expertdiag.model

/**
 * Probability estimate for one root cause associated with a DTC.
 *
 * [probability] ranges 0.0–1.0. Values are relative — display as percentage bars.
 */
data class RootCauseProbability(
    val cause: String,
    val probability: Float,
    val supportingEvidence: List<String> = emptyList(),
)
