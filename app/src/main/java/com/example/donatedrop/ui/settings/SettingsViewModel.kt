package com.example.donatedrop.ui.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SettingsViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "Welcome to Settings"
    }
    val text: LiveData<String> = _text

    // Add more LiveData / functions for settings state as needed
}
