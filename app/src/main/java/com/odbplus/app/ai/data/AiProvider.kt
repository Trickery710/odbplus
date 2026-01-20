package com.odbplus.app.ai.data

/**
 * Supported AI chatbot providers.
 */
enum class AiProvider(
    val displayName: String,
    val description: String,
    val costInfo: String,
    val setupUrl: String,
    val keyPrefix: String,
    val keyPlaceholder: String
) {
    CLAUDE(
        displayName = "Claude (Anthropic)",
        description = "Most capable for complex diagnostics. Best reasoning and explanation quality.",
        costInfo = "Pay-per-use: ~\$3/million input tokens, ~\$15/million output tokens. Typical chat ~\$0.01-0.05",
        setupUrl = "https://console.anthropic.com/settings/keys",
        keyPrefix = "sk-ant-",
        keyPlaceholder = "sk-ant-api..."
    ),
    GEMINI(
        displayName = "Gemini (Google)",
        description = "Good general-purpose AI with generous free tier. Great for everyday diagnostics.",
        costInfo = "FREE: 15 requests/minute, 1,500/day. Paid plans available for higher limits.",
        setupUrl = "https://aistudio.google.com/app/apikey",
        keyPrefix = "AIza",
        keyPlaceholder = "AIzaSy..."
    ),
    GROQ(
        displayName = "Groq (Llama)",
        description = "Fast inference with open-source Llama models. Good free tier.",
        costInfo = "FREE: 30 requests/minute, 14,400/day with rate limits. Very fast responses.",
        setupUrl = "https://console.groq.com/keys",
        keyPrefix = "gsk_",
        keyPlaceholder = "gsk_..."
    );

    /**
     * Get setup instructions for this provider.
     */
    fun getSetupInstructions(): List<String> = when (this) {
        CLAUDE -> listOf(
            "Go to console.anthropic.com",
            "Sign in or create an account",
            "Add payment method (required)",
            "Navigate to API Keys section",
            "Create a new API key"
        )
        GEMINI -> listOf(
            "Go to Google AI Studio",
            "Sign in with your Google account",
            "Click 'Get API key'",
            "Create a new API key (free)",
            "No payment method required"
        )
        GROQ -> listOf(
            "Go to console.groq.com",
            "Sign up for a free account",
            "Navigate to API Keys",
            "Create a new API key",
            "No payment method required"
        )
    }

    /**
     * Validate API key format for this provider.
     */
    fun isValidKeyFormat(key: String): Boolean {
        val trimmed = key.trim()
        return when (this) {
            CLAUDE -> trimmed.startsWith("sk-ant-") && trimmed.length > 20
            GEMINI -> trimmed.startsWith("AIza") && trimmed.length > 30
            GROQ -> trimmed.startsWith("gsk_") && trimmed.length > 20
        }
    }

    companion object {
        /**
         * Get the recommended free provider.
         */
        fun freeRecommendation(): AiProvider = GEMINI

        /**
         * Get providers sorted by cost (free first).
         */
        fun sortedByCost(): List<AiProvider> = listOf(GEMINI, GROQ, CLAUDE)
    }
}
