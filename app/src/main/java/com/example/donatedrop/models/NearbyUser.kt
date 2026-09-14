package com.example.donatedrop.models

import java.util.Date

data class NearbyUser(
    val id: String,
    val name: String,
    val bloodGroup: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Double,
    val available: Boolean,
    val phone: String,
    val lastupdatedat: Date?,
    val image: String?


)