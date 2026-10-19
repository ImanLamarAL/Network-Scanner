package com.chogo.networkscanner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScanUiState(
    val localIp: String? = null,
    val isScanning: Boolean = false,
    val scanned: Int = 0,
    val total: Int = 254,
    val devices: List<Device> = emptyList(),
    val error: String? = null
)

class ScannerViewModel(app: Application) : AndroidViewModel(app) {

    private val scanner = NetworkScanner(app)
    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun startScan() {
        if (_state.value.isScanning) return

        val ip = scanner.getLocalIp()
        if (ip == null) {
            _state.value = ScanUiState(error = "Not connected to a network.")
            return
        }

        _state.value = ScanUiState(localIp = ip, isScanning = true)
        job = viewModelScope.launch {
            scanner.scan(
                localIp = ip,
                onProgress = { scanned, total ->
                    _state.update { it.copy(scanned = scanned, total = total) }
                },
                onDeviceFound = { device ->
                    _state.update { current ->
                        current.copy(
                            devices = (current.devices + device)
                                .sortedBy { it.ip.substringAfterLast(".").toInt() }
                        )
                    }
                }
            )
            _state.update { it.copy(isScanning = false) }
        }
    }

    fun stopScan() {
        job?.cancel()
        _state.update { it.copy(isScanning = false) }
    }
}