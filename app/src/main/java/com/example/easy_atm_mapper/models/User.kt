package com.example.easy_atm_mapper.models

import java.util.UUID

data class User(
    val firstname : String? = null,
    val lastname : String? = null,
    val username : String? = null,
    val email : String? = null,
    val password : String? = null,
    val id : String? = UUID.randomUUID().toString()
    )