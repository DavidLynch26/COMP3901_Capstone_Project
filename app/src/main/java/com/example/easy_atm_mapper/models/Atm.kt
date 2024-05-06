package com.example.easy_atm_mapper.models

import android.graphics.Bitmap
import com.google.firebase.firestore.IgnoreExtraProperties

data class Atm(
//    val name: String,
    val id: String? = null,
    val address: String? = null,
    val location: MyLatLng? = null,
    val busyness: String? = null,
    val waitTime: Double? = null,
    val working: String? = null,
    val bank: String? = null,
    val photoReference: String? = null
    )

@IgnoreExtraProperties
//@JvmOverloads
data class MyLatLng(
    val latitude: Double,
    val longitude: Double
){
    // Add a no-argument constructor
    constructor() : this(0.0, 0.0)
}