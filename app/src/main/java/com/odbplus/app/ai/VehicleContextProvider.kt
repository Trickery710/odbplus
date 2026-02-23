package com.odbplus.app.ai

import com.odbplus.app.ai.data.VehicleContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton state holder for the current vehicle context.
 *
 * [VehicleContextViewModel] updates this; [AiChatViewModel] reads from it when
 * building the AI system prompt.  Using a shared provider avoids ViewModel-to-ViewModel
 * injection while still decoupling the two concerns.
 */
@Singleton
class VehicleContextProvider @Inject constructor() {
    private val _context = MutableStateFlow(VehicleContext())
    val context: StateFlow<VehicleContext> = _context.asStateFlow()

    fun update(ctx: VehicleContext) { _context.value = ctx }
    fun current(): VehicleContext = _context.value
}
