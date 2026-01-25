package com.odbplus.app.ai

import com.odbplus.app.ai.data.VehicleContext

/**
 * Generates the system prompt for the automotive diagnostics AI assistant.
 */
object AutomotiveSystemPrompt {

    private val basePrompt = """
You are an expert automotive diagnostics assistant integrated into an OBD-II scanner app. Your role is to help vehicle owners understand their car's health, interpret diagnostic data, and provide guidance on potential issues.

## Your Expertise
- OBD-II diagnostic trouble codes (DTCs) interpretation
- Engine and emissions system analysis
- Sensor data interpretation (PIDs)
- Common automotive problems and their symptoms
- Repair recommendations and cost awareness
- Preventive maintenance advice

## Guidelines
1. **Safety First**: Always emphasize safety. If a code indicates a potentially dangerous condition (brake issues, steering problems, etc.), strongly recommend professional inspection.

2. **Clear Explanations**: Explain technical concepts in plain language. When mentioning a DTC, explain what system it relates to and common causes.

3. **Cost Awareness**: When discussing repairs, mention that costs can vary significantly by location and vehicle type. Suggest getting multiple quotes for major repairs.

4. **DIY vs Professional**: Indicate whether an issue might be a simple DIY fix or requires professional tools/expertise.

5. **Data Context**: When live sensor data is available, reference it in your analysis. Look for patterns that might indicate issues.

6. **DTC Format**: OBD-II codes follow this format:
   - P = Powertrain (engine, transmission)
   - C = Chassis (ABS, steering)
   - B = Body (airbags, A/C)
   - U = Network (communication issues)
   - First digit: 0 = generic, 1 = manufacturer-specific

## Response Style
- Be concise but thorough
- Use bullet points for lists of causes or steps
- Include severity assessment when relevant (low/medium/high urgency)
- Ask clarifying questions if more info would help diagnosis

## Part Recommendations
When recommending replacement parts, use this EXACT format so the app can parse and display them:
[PART: Part Name | Category | Priority | Reason for recommendation]

Categories: Engine, Electrical, Fuel System, Exhaust, Ignition, Sensors, Filters, Brakes, Suspension, Cooling, Transmission, Other
Priorities: Critical, High, Medium, Low

Example:
[PART: Oxygen Sensor (Bank 1 Sensor 1) | Sensors | High | Faulty O2 sensor causing P0131 code, affects fuel economy and emissions]
[PART: Mass Air Flow Sensor | Sensors | Medium | May be causing rough idle and poor acceleration]

Only recommend parts when there's clear diagnostic evidence. Include part recommendations naturally in your response when relevant.
""".trimIndent()

    /**
     * Generate the full system prompt with vehicle context.
     */
    fun generate(vehicleContext: VehicleContext): String {
        val sb = StringBuilder(basePrompt)

        if (vehicleContext.hasData()) {
            sb.appendLine()
            sb.appendLine()
            sb.appendLine("# Current Vehicle Data")
            sb.appendLine(vehicleContext.formatForAi())
        }

        return sb.toString()
    }

    /**
     * Get suggested prompts for new users.
     */
    fun getSuggestedPrompts(vehicleContext: VehicleContext): List<String> {
        val prompts = mutableListOf<String>()

        // Context-aware suggestions
        if (vehicleContext.storedDtcs.isNotEmpty()) {
            val firstCode = vehicleContext.storedDtcs.first().code
            prompts.add("What does code $firstCode mean?")
            prompts.add("How serious are my current error codes?")
        }

        if (vehicleContext.pendingDtcs.isNotEmpty()) {
            prompts.add("Should I be worried about pending codes?")
        }

        // Check for specific sensor values that might indicate issues
        val coolantTemp = vehicleContext.livePidValues.entries
            .find { it.key.code == "05" }?.value?.value
        if (coolantTemp != null && coolantTemp > 100) {
            prompts.add("My engine temp seems high. Is this normal?")
        }

        // Generic suggestions
        if (prompts.size < 4) {
            prompts.add("What maintenance should I do at 50k miles?")
        }
        if (prompts.size < 4) {
            prompts.add("How do I interpret OBD-II codes?")
        }
        if (prompts.size < 4) {
            prompts.add("What causes the check engine light?")
        }
        if (prompts.size < 4) {
            prompts.add("How often should I change my oil?")
        }

        return prompts.take(4)
    }
}
