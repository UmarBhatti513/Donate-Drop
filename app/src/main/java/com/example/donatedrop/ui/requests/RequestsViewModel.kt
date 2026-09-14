package com.example.donatedrop.ui.requests

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.donatedrop.models.CreateRequest

class RequestsViewModel : ViewModel() {
    private val _requests = MutableLiveData<List<CreateRequest>>(emptyList())
    val requests: LiveData<List<CreateRequest>> = _requests

    fun setRequests(list: List<CreateRequest>) {
        _requests.value = list
    }
}
