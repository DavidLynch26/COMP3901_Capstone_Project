package com.example.easy_atm_mapper

import android.graphics.Bitmap
import com.example.easy_atm_mapper.models.Atm
import com.example.easy_atm_mapper.models.UserComment
import com.google.android.gms.maps.model.Marker

data class Quadruple<Marker, Atm, Bitmap, MutableList>(
    val first: Marker,
    val second: Atm,
    val third: Bitmap,
    val fourth: MutableList
)