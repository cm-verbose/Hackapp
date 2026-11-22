package com.example.hackapp

import android.app.Application
import com.example.hackapp.citypack.ActiveCityPack
import com.example.hackapp.services.CityServices
import com.example.hackapp.services.CityServicesFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Owns the single [CityServices] container for this build's City Pack. */
class HackappApplication : Application() {
    val cityServices: CityServices by lazy { CityServicesFactory.create(this, ActiveCityPack) }

    /** For short background work that must finish even if the current screen closes. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
