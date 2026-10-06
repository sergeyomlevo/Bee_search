package org.beesearch.app.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.beesearch.app.data.backuprepository.AndroidBackupTreeAccess
import org.beesearch.app.data.backuprepository.BackupAccessCoordinator
import org.beesearch.app.data.backuprepository.BackupLocation

/**
 * Screen state holder for `Настройки → Резервное копирование`.
 *
 * It owns no repository semantics: the decision tree lives in
 * [org.beesearch.app.data.backuprepository.BackupAccessCoordinator], and this class only translates
 * its typed results into screen state. Field capture never depends on anything here.
 */
internal class BackupAccessViewModel(
    private val coordinator: BackupAccessCoordinator,
    private val treeAccess: AndroidBackupTreeAccess,
    private val location: BackupLocation,
) : ViewModel() {

    private val _state = MutableStateFlow<BackupScreenState>(BackupScreenState.Loading)
    val state: StateFlow<BackupScreenState> = _state.asStateFlow()

    private val displayPath: String get() = "$USER_VISIBLE_DOWNLOADS/${location.relativePath}"

    /** The fixed Bee Search Backup folder of this build variant, for the system picker. */
    fun pickerInitialUri(): Uri = treeAccess.pickerInitialUri(location)

    /** Re-reads the current access state; never binds, adopts or initialises anything. */
    fun refresh() {
        viewModelScope.launch {
            _state.value = BackupScreenState.Working
            _state.value = coordinator.status().toScreenState(displayPath)
        }
    }

    /**
     * Handles the picker result.
     *
     * A cancelled picker returns nothing and is not an error: the screen simply re-reads the
     * current state. Only a supported tree has its grant persisted, so a foreign URI cannot leave
     * a stray persisted permission behind.
     */
    fun onTreePicked(uri: Uri?) {
        if (uri == null) {
            refresh()
            return
        }
        viewModelScope.launch {
            _state.value = BackupScreenState.Working
            val selection = treeAccess.describe(uri)
            // The coordinator validates the exact folder first and only then asks for the grant to be
            // persisted, so a rejected selection never becomes durable access to a foreign folder:
            // measured on the target device, the rejected tree keeps only its transient session grant
            // (persisted=false) while the accepted fixed folder gets a persisted one.
            _state.value = coordinator.connect(selection) { treeAccess.remember(uri) }
                .toScreenState(displayPath)
        }
    }

    companion object {
        fun factory(
            coordinator: BackupAccessCoordinator,
            treeAccess: AndroidBackupTreeAccess,
            location: BackupLocation,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                BackupAccessViewModel(coordinator, treeAccess, location) as T
        }
    }
}
