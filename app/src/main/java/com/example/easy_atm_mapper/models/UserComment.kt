package com.example.easy_atm_mapper.models

import java.util.Date

data class UserComment(
    val userId: String? = null,
    val timeTaken: Int? = null,
    val working: String? = null,
    val comment: String? = null,
    val date: Date? = null
)