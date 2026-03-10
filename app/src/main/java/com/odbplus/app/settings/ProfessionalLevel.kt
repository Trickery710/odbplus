package com.odbplus.app.settings

enum class ProfessionalLevel(
    val displayName: String,
    val description: String
) {
    BEGINNER(
        displayName = "Beginner",
        description = "Learning the basics. Guidance-heavy suggestions, no special tools assumed."
    ),
    DIY(
        displayName = "DIY",
        description = "Home mechanic with standard hand tools. Step-by-step procedures shown."
    ),
    MECHANIC(
        displayName = "Mechanic",
        description = "Professional technician with a full tool set. Detailed technical data shown."
    ),
    TECHNICIAN(
        displayName = "Technician",
        description = "Advanced diagnostics specialist. All data, waveform analysis, and live stream modes enabled."
    )
}
