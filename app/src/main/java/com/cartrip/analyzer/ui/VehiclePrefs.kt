package com.cartrip.analyzer.ui

import android.content.Context
import com.cartrip.analyzer.analysis.FuelEstimator

/**
 * Persists the single active [FuelEstimator.Vehicle] profile used for fuel/cost estimates.
 * Seeded with the owner's 2023 Hyundai Tucson + a representative GTA fuel price; all editable in
 * [VehicleScreen]. As real car-reported economy comes in, adjust the ratings or [Vehicle.calibration].
 */
object VehiclePrefs {
    private const val NAME = "cartrip_vehicle"
    private fun p(c: Context) = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    private val D = FuelEstimator.DEFAULT

    fun load(c: Context): FuelEstimator.Vehicle {
        val sp = p(c)
        return FuelEstimator.Vehicle(
            year = sp.getInt("year", D.year),
            make = sp.getString("make", D.make) ?: D.make,
            model = sp.getString("model", D.model) ?: D.model,
            cityL100 = sp.getFloat("city", D.cityL100.toFloat()).toDouble(),
            hwyL100 = sp.getFloat("hwy", D.hwyL100.toFloat()).toDouble(),
            pricePerL = sp.getFloat("price", D.pricePerL.toFloat()).toDouble(),
            calibration = sp.getFloat("cal", D.calibration.toFloat()).toDouble(),
            fuelType = sp.getString("fuel", D.fuelType) ?: D.fuelType,
        )
    }

    fun save(c: Context, v: FuelEstimator.Vehicle) {
        p(c).edit()
            .putInt("year", v.year)
            .putString("make", v.make)
            .putString("model", v.model)
            .putFloat("city", v.cityL100.toFloat())
            .putFloat("hwy", v.hwyL100.toFloat())
            .putFloat("price", v.pricePerL.toFloat())
            .putFloat("cal", v.calibration.toFloat())
            .putString("fuel", v.fuelType)
            .apply()
    }
}
