package app.grapheneos.info.ui.releases

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.grapheneos.info.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.grapheneos.tls.ModernTLSSocketFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownServiceException
import java.time.format.DateTimeParseException
import javax.net.ssl.HttpsURLConnection

const val TAG = "ReleasesViewModel"

// Upper bound on the release feed we will buffer/parse. Guards against oversized or maliciously large
// responses causing excessive memory use.
private const val MAX_FEED_BYTES = 5 * 1024 * 1024

// Feed-parsing patterns, compiled once instead of per entry / per call.
private val entryRegex = Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL)
private val idRegex = Regex("<id>(.*?)</id>", RegexOption.DOT_MATCHES_ALL)
private val updatedRegex = Regex("<updated>(.*?)</updated>", RegexOption.DOT_MATCHES_ALL)
private val publishedRegex = Regex("<published>(.*?)</published>", RegexOption.DOT_MATCHES_ALL)
private val titleRegex = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
private val dashZeroRegex = Regex("-00(?=T)")
private val yearMonthRegex = Regex("\\d{4}-\\d{2}$")

private data class EntryData(
    val id: String,
    val xml: String,
    val updated: java.time.Instant,
    val title: String?,
)

private fun parseInstantSafe(dateStr: String?): java.time.Instant {
    if (dateStr.isNullOrBlank()) return java.time.Instant.EPOCH
    val normalized = dateStr.replace(dashZeroRegex, "-01")
    return try {
        java.time.Instant.parse(normalized)
    } catch (_: DateTimeParseException) {
        try {
            val alt = if (normalized.matches(yearMonthRegex)) "$normalized-01T00:00:00Z" else normalized
            java.time.Instant.parse(alt)
        } catch (_: Exception) {
            java.time.Instant.EPOCH
        }
    }
}

// Read at most [maxBytes] from the stream and decode as UTF-8, failing fast if the response is larger.
private fun InputStream.readBoundedUtf8(maxBytes: Int): String {
    val buffer = ByteArrayOutputStream()
    val chunk = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val read = read(chunk)
        if (read == -1) break
        total += read
        if (total > maxBytes) throw IOException("Release feed exceeds $maxBytes bytes")
        buffer.write(chunk, 0, read)
    }
    return String(buffer.toByteArray(), Charsets.UTF_8)
}

class ReleasesViewModel(
    private val application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val tlsSocketFactory = ModernTLSSocketFactory()
    private val _uiState = MutableStateFlow(ReleasesUiState(savedStateHandle))
    val uiState: StateFlow<ReleasesUiState> = _uiState.asStateFlow()

    // Tracks the in-flight fetch so overlapping triggers (lifecycle ON_START, pull-to-refresh) cannot
    // stack duplicate requests or let a stale response overwrite a newer one. The first load is driven
    // by ReleasesScreen's ON_START observer, so no fetch is started in init.
    private var updateJob: Job? = null

    fun updateChangelog(
        useCaches: Boolean,
        showSnackbarError: suspend (message: String) -> Unit,
        scrollChangelogLazyListTo: (scrollTo: Int) -> Unit,
        countAsInitialScroll: Boolean = true,
        onFinishedUpdating: () -> Unit = {},
    ) {
        // Cancel any in-flight fetch so the most recent request wins.
        updateJob?.cancel()
        updateJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = URL(application.getString(R.string.release_url).trim())
                val connection = url.openConnection() as? HttpsURLConnection ?: run {
                    val errorMessage =
                        application.getString(R.string.update_changelog_failed_to_create_httpsurlconnection_snackbar_message)
                    Log.e(TAG, errorMessage)
                    viewModelScope.launch { showSnackbarError(errorMessage) }
                    return@launch
                }

                connection.apply {
                    sslSocketFactory = tlsSocketFactory
                    connectTimeout = 10_000
                    readTimeout = 30_000
                }

                try {
                    connection.useCaches = useCaches

                    connection.connect()

                    val responseText = connection.inputStream.readBoundedUtf8(MAX_FEED_BYTES)
                    val entryList = entryRegex.findAll(responseText).map { it.groups[1]!!.value }.toList()

                    val entriesWithDates = entryList.map { entryXml ->
                        val id = idRegex.find(entryXml)?.groupValues?.get(1)
                            ?: entryXml.hashCode().toString()

                        val updatedStr = updatedRegex.find(entryXml)?.groupValues?.get(1)
                        val publishedStr = publishedRegex.find(entryXml)?.groupValues?.get(1)

                        EntryData(
                            id = id,
                            xml = entryXml,
                            updated = parseInstantSafe(updatedStr ?: publishedStr),
                            title = titleRegex.find(entryXml)?.groupValues?.get(1),
                        )
                    }

                    // Sort newest-first, then drop duplicate ids: the LazyColumn keys entries by id and
                    // would crash on a duplicate key (e.g. a malformed/MITM'd or copy-pasted feed entry).
                    val orderedEntries = entriesWithDates
                        .sortedByDescending { it.updated }
                        .distinctBy { it.id }

                    val orderedList = orderedEntries.map { it.id to it.xml }
                    val currentOsChangelogIndex = orderedEntries.indexOfLast { ed ->
                        ed.title == android.os.Build.VERSION.INCREMENTAL
                    }.let { if (it == -1) 0 else it }

                    withContext(Dispatchers.Main) {
                        _uiState.value.entries.clear()
                        _uiState.value.entries.addAll(orderedList)

                        if (countAsInitialScroll && !uiState.value.didInitialScroll) {
                            _uiState.value.didInitialScroll = true
                            scrollChangelogLazyListTo(currentOsChangelogIndex)
                        }
                    }
                } catch (e: SocketTimeoutException) {
                    val errorMessage =
                        application.getString(R.string.update_changelog_socket_timeout_exception_snackbar_message)
                    Log.e(TAG, errorMessage, e)
                    viewModelScope.launch {
                        showSnackbarError("$errorMessage: $e")
                    }
                } catch (e: IOException) {
                    val errorMessage =
                        application.getString(R.string.update_changelog_io_exception_snackbar_message)
                    Log.e(TAG, errorMessage, e)
                    viewModelScope.launch {
                        showSnackbarError("$errorMessage: $e")
                    }
                } catch (e: UnknownServiceException) {
                    val errorMessage =
                        application.getString(R.string.update_changelog_unknown_service_exception_snackbar_message)
                    Log.e(TAG, errorMessage, e)
                    viewModelScope.launch {
                        showSnackbarError("$errorMessage: $e")
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: IOException) {
                val errorMessage =
                    application.getString(R.string.update_changelog_failed_to_create_httpsurlconnection_snackbar_message)
                Log.e(TAG, errorMessage, e)
                viewModelScope.launch {
                    showSnackbarError("$errorMessage: $e")
                }
            } finally {
                // Marshal the completion callback (which mutates Compose state, e.g. the refresh
                // spinner) onto the main thread; NonCancellable so it still runs if this fetch was
                // superseded by a newer one.
                withContext(NonCancellable + Dispatchers.Main) {
                    onFinishedUpdating()
                }
            }
        }
    }
}
