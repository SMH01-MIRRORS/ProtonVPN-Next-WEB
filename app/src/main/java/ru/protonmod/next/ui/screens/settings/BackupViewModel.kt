/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ru.protonmod.next.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.protonmod.next.data.model.BackupCategory
import ru.protonmod.next.data.repository.BackupRepository
import ru.protonmod.next.utils.ProtonLogger
import javax.inject.Inject

data class BackupUiState(
    val selectedCategories: Set<BackupCategory> = BackupCategory.entries.toSet(),
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val lastError: String? = null,
    val showSuccessExport: Boolean = false,
    val showSuccessImport: Boolean = false
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupRepository: BackupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState = _uiState.asStateFlow()

    fun toggleCategory(category: BackupCategory) {
        _uiState.update { state ->
            val newSelected = if (state.selectedCategories.contains(category)) {
                state.selectedCategories - category
            } else {
                state.selectedCategories + category
            }
            state.copy(selectedCategories = newSelected)
        }
    }

    fun exportToUri(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, lastError = null) }
            try {
                val json = backupRepository.exportData(_uiState.value.selectedCategories)
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toByteArray())
                }
                _uiState.update { it.copy(isExporting = false, showSuccessExport = true) }
            } catch (e: Exception) {
                ProtonLogger.e("BackupViewModel", "Export failed", e)
                _uiState.update { it.copy(isExporting = false, lastError = e.message) }
            }
        }
    }

    fun importFromUri(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, lastError = null) }
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().readText()
                } ?: throw Exception("Failed to read file")
                
                backupRepository.importData(json, _uiState.value.selectedCategories)
                _uiState.update { it.copy(isImporting = false, showSuccessImport = true) }
            } catch (e: Exception) {
                ProtonLogger.e("BackupViewModel", "Import failed", e)
                _uiState.update { it.copy(isImporting = false, lastError = e.message) }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(showSuccessExport = false, showSuccessImport = false, lastError = null) }
    }
}
