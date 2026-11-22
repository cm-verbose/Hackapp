package com.example.hackapp

import android.app.Application
import com.example.hackapp.citypack.ActiveCityPack
import com.example.hackapp.services.CityServices
import com.example.hackapp.services.CityServicesFactory

/** Owns the single [CityServices] container for this build's City Pack. */
class HackappApplication : Application() {
    val cityServices: CityServices by lazy { CityServicesFactory.create(this, ActiveCityPack) }
}
