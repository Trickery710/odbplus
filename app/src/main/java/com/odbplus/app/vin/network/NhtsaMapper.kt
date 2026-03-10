package com.odbplus.app.vin.network

import com.odbplus.app.vin.domain.DecodedVin
import com.odbplus.app.vin.domain.DecodeSource
import com.odbplus.app.vin.domain.VerificationStatus

/**
 * Maps an [NhtsaDecodeResponse] to a [DecodedVin] domain model.
 *
 * NHTSA returns a flat key/value list. This mapper extracts well-known fields
 * by their "Variable" name string. If NHTSA renames a variable in a future API
 * version, only this file needs updating.
 */
object NhtsaMapper {

    // ── NHTSA variable name constants ─────────────────────────────────────────
    private const val V_VIN                    = "VIN"
    private const val V_MAKE                   = "Make"
    private const val V_MODEL                  = "Model"
    private const val V_MODEL_YEAR             = "Model Year"
    private const val V_TRIM                   = "Trim"
    private const val V_SERIES                 = "Series"
    private const val V_MANUFACTURER           = "Manufacturer Name"
    private const val V_VEHICLE_TYPE           = "Vehicle Type"
    private const val V_BODY_CLASS             = "Body Class"
    private const val V_ENGINE_MODEL           = "Engine Model"
    private const val V_ENGINE_CYLINDERS       = "Engine Number of Cylinders"
    private const val V_DISPLACEMENT_L         = "Displacement (L)"
    private const val V_FUEL_TYPE_PRIMARY      = "Fuel Type - Primary"
    private const val V_FUEL_TYPE_SECONDARY    = "Fuel Type - Secondary"
    private const val V_DRIVE_TYPE             = "Drive Type"
    private const val V_TRANSMISSION_STYLE     = "Transmission Style"
    private const val V_PLANT_COUNTRY          = "Plant Country"
    private const val V_PLANT_COMPANY          = "Plant Company Name"
    private const val V_PLANT_CITY             = "Plant City"
    private const val V_PLANT_STATE            = "Plant State"
    private const val V_GVWR_CLASS             = "Gross Vehicle Weight Rating From"
    private const val V_BRAKE_SYSTEM           = "Brake System Type"
    private const val V_AIR_BAGS              = "Air Bag Locations Side"

    /**
     * Convert the raw NHTSA response into a domain [DecodedVin].
     *
     * @param vin  The normalized VIN that was submitted.
     * @param dto  The raw response from the NHTSA API.
     * @return     Decoded domain model with preliminary confidence 0.0 (caller sets final score).
     */
    fun map(vin: String, dto: NhtsaDecodeResponse): DecodedVin {
        val fields: Map<String, String?> = dto.results.associate { item ->
            item.variable to item.value?.takeIf { it.isNotBlank() && it != "Not Applicable" }
        }

        return DecodedVin(
            vin              = fields[V_VIN] ?: vin,
            make             = fields[V_MAKE],
            model            = fields[V_MODEL],
            modelYear        = fields[V_MODEL_YEAR]?.toIntOrNull(),
            trim             = fields[V_TRIM],
            series           = fields[V_SERIES],
            manufacturer     = fields[V_MANUFACTURER],
            vehicleType      = fields[V_VEHICLE_TYPE],
            bodyClass        = fields[V_BODY_CLASS],
            engineModel      = fields[V_ENGINE_MODEL],
            engineCylinders  = fields[V_ENGINE_CYLINDERS]?.toIntOrNull(),
            displacementL    = fields[V_DISPLACEMENT_L]?.toDoubleOrNull(),
            fuelTypePrimary  = fields[V_FUEL_TYPE_PRIMARY],
            fuelTypeSecondary = fields[V_FUEL_TYPE_SECONDARY],
            driveType        = fields[V_DRIVE_TYPE],
            transmissionStyle = fields[V_TRANSMISSION_STYLE],
            plantCountry     = fields[V_PLANT_COUNTRY],
            plantCompany     = fields[V_PLANT_COMPANY],
            plantCity        = fields[V_PLANT_CITY],
            plantState       = fields[V_PLANT_STATE],
            gvwrClass        = fields[V_GVWR_CLASS],
            brakeSystemType  = fields[V_BRAKE_SYSTEM],
            airBagLocations  = fields[V_AIR_BAGS],
            source           = DecodeSource.NHTSA,
            decodeTimestamp  = System.currentTimeMillis(),
            confidence       = 0f, // Caller sets this after verification
            verificationStatus = VerificationStatus.PARTIAL // Preliminary; caller updates
        )
    }

    /**
     * Return the list of expected core field names that are blank in the decoded result.
     * Used to determine [NetworkDecodeResult.PartialData] vs [NetworkDecodeResult.Success].
     */
    fun missingCoreFields(decoded: DecodedVin): List<String> = buildList {
        if (decoded.make == null) add("make")
        if (decoded.model == null) add("model")
        if (decoded.modelYear == null) add("modelYear")
        if (decoded.manufacturer == null) add("manufacturer")
    }
}
