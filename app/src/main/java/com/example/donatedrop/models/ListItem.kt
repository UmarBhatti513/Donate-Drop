package com.example.donatedrop.models

sealed class ListItem {
    data class SectionHeader(val title: String): ListItem()
    data class NotificationEntry(val data: Notification): ListItem()
}