package com.seca.contacts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.seca.core.contacts.ContactsRepository
import com.seca.core.model.SecaContact
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

sealed interface ContactsUiState {
    data object Loading : ContactsUiState
    data class Loaded(val contacts: List<SecaContact>) : ContactsUiState
}

class ContactsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ContactsRepository(application.contentResolver)
    private val _state = MutableStateFlow<ContactsUiState>(ContactsUiState.Loading)
    val state: StateFlow<ContactsUiState> = _state.asStateFlow()
    private var watching: Job? = null

    /** Loads once access is granted, then follows every change to the provider. */
    fun start() {
        if (watching != null) return
        watching = viewModelScope.launch {
            load()
            repository.changes().conflate().collect { load() }
        }
    }

    private suspend fun load() {
        _state.value = ContactsUiState.Loaded(repository.contacts())
    }
}
