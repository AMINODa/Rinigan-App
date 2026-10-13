package com.rinigan.app.ui

import android.app.Application
import android.media.MediaMetadataRetriever
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rinigan.app.ConfigManager
import com.rinigan.app.VideoLinkResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

data class VideoItem(
    val name: String,
    val file: File,
    val size: Long,
    val durationMs: Long,
    val sourceUrl: String? = null
)

data class AudioItem(
    val name: String,
    val file: File,
    val size: Long,
    val durationMs: Long
)

data class MediaUiState(
    val videos: List<VideoItem> = emptyList(),
    val audios: List<AudioItem> = emptyList(),
    val selectedVideoName: String? = null,
    val selectedAudioName: String? = null,
    val isLoading: Boolean = false,
    val totalVideoSizeMb: Double = 0.0,
    val totalVideoDurationSec: Long = 0,
    val linkDownloading: Boolean = false,
    val linkDownloadProgress: Int = 0,
    val linkDownloadError: String? = null
)

class MediaViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(MediaUiState())
    val uiState: StateFlow<MediaUiState> = _uiState.asStateFlow()

    private val videoDir = File("/storage/emulated/0/DCIM/Camera1/")
    private val configManager = ConfigManager().apply { setContext(application) }

    init {
        if (!videoDir.exists()) videoDir.mkdirs()
        loadMedia()
    }

    fun loadMedia() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }

            val videoExts = setOf("mp4", "mov", "avi", "mkv")
            val videoFiles = videoDir.listFiles { f ->
                videoExts.contains(f.extension.lowercase())
            }?.sorted() ?: emptyList()

            val videoItems = videoFiles.map { f ->
                var dur = 0L
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(f.absolutePath)
                    dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    retriever.release()
                } catch (_: Exception) {}
                VideoItem(f.name, f, f.length(), dur, VideoLinkResolver.readSourceLink(f))
            }

            val audioExts = setOf("mp3", "wav", "aac", "m4a", "ogg", "flac")
            val audioFiles = videoDir.listFiles { f ->
                audioExts.contains(f.extension.lowercase())
            }?.sorted() ?: emptyList()

            val audioItems = audioFiles.map { f ->
                var dur = 0L
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(f.absolutePath)
                    dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    retriever.release()
                } catch (_: Exception) {}
                AudioItem(f.name, f, f.length(), dur)
            }

            configManager.reload()
            val selectedVideo = configManager.getString(ConfigManager.KEY_SELECTED_VIDEO, null)
            val selectedAudio = configManager.getString(ConfigManager.KEY_SELECTED_AUDIO, null)

            val totalSize = videoFiles.sumOf { it.length() }
            val totalDur = videoItems.sumOf { it.durationMs }

            _uiState.update {
                it.copy(
                    videos = videoItems,
                    audios = audioItems,
                    selectedVideoName = selectedVideo?.let { n ->
                        if (n.startsWith("/")) File(n).name else n
                    },
                    selectedAudioName = selectedAudio,
                    isLoading = false,
                    totalVideoSizeMb = totalSize / 1048576.0,
                    totalVideoDurationSec = totalDur / 1000
                )
            }
        }
    }

    fun selectVideo(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.reload()
            val current = configManager.getString(ConfigManager.KEY_SELECTED_VIDEO, null)
            val currentName = if (current != null && current.startsWith("/")) File(current).name else current

            if (currentName == name) {
                configManager.setString(ConfigManager.KEY_SELECTED_VIDEO, "")
                _uiState.update { it.copy(selectedVideoName = null) }
            } else {
                val targetPath = File(videoDir, name).absolutePath
                configManager.setString(ConfigManager.KEY_SELECTED_VIDEO, targetPath)
                _uiState.update { it.copy(selectedVideoName = name) }
            }
        }
    }

    fun selectAudio(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.reload()
            val current = configManager.getString(ConfigManager.KEY_SELECTED_AUDIO, null)
            if (current == name) {
                configManager.setString(ConfigManager.KEY_SELECTED_AUDIO, "")
                _uiState.update { it.copy(selectedAudioName = null) }
            } else {
                configManager.setString(ConfigManager.KEY_SELECTED_AUDIO, name)
                _uiState.update { it.copy(selectedAudioName = name) }
            }
        }
    }

    fun deleteFile(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            file.delete()
            // v6: also clean the video-link marker if present
            try {
                val marker = File(file.parentFile, file.name + VideoLinkResolver.LINK_MARKER_SUFFIX)
                if (marker.exists()) marker.delete()
            } catch (_: Exception) {}
            loadMedia()
        }
    }

    fun importFiles(uris: List<android.net.Uri>, isAudio: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            for (uri in uris) {
                try {
                    var name = "import_${System.currentTimeMillis()}"
                    app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0) name = cursor.getString(idx) ?: name
                        }
                    }
                    if (!name.contains(".")) name += if (isAudio) ".mp3" else ".mp4"
                    val dest = File(videoDir, name)
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(dest).use { output -> input.copyTo(output) }
                    }
                    dest.setReadable(true, false)
                } catch (_: Exception) {}
            }
            loadMedia()
        }
    }

    // ===== v6: video link download =====

    /** Downloads a remote video link into the Camera1 directory and selects nothing automatically. */
    fun addVideoFromUrl(rawUrl: String) {
        val url = rawUrl.trim()
        if (!isValidVideoUrl(url)) {
            _uiState.update { it.copy(linkDownloadError = "invalid_url") }
            return
        }
        if (_uiState.value.linkDownloading) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(linkDownloading = true, linkDownloadProgress = 0, linkDownloadError = null) }
            var partFile: File? = null
            try {
                val baseName = fileNameFromUrl(url)
                val dest = File(videoDir, baseName)

                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                try {
                    conn.instanceFollowRedirects = true
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 30_000
                    conn.setRequestMethod("GET")
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Rinigan/6.0")
                    val code = conn.responseCode
                    if (code !in 200..299) throw java.io.IOException("HTTP $code")

                    val total = conn.contentLengthLong
                    val part = File(videoDir, "$baseName.part")
                    partFile = part
                    java.io.BufferedInputStream(conn.inputStream).use { input ->
                        FileOutputStream(part).use { output ->
                            val buf = ByteArray(64 * 1024)
                            var read = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n == -1) break
                                output.write(buf, 0, n)
                                read += n
                                if (total > 0) {
                                    val pct = ((read * 100) / total).toInt().coerceIn(0, 100)
                                    _uiState.update { it.copy(linkDownloadProgress = pct) }
                                }
                            }
                            output.flush()
                        }
                    }
                    if (part.length() <= 0) throw java.io.IOException("empty file")
                    if (dest.exists()) dest.delete()
                    if (!part.renameTo(dest)) {
                        part.copyTo(dest, overwrite = true)
                        part.delete()
                    }
                    dest.setReadable(true, false)
                    VideoLinkResolver.writeSourceLink(dest, url)
                } finally {
                    try { conn.disconnect() } catch (_: Exception) {}
                }
                _uiState.update { it.copy(linkDownloading = false, linkDownloadProgress = 100) }
                loadMedia()
            } catch (e: Exception) {
                try { partFile?.delete() } catch (_: Exception) {}
                _uiState.update {
                    it.copy(
                        linkDownloading = false,
                        linkDownloadProgress = 0,
                        linkDownloadError = e.message ?: "error"
                    )
                }
            }
        }
    }

    /** Acknowledges the last link-download error (dialog shows it once). */
    fun clearLinkError() {
        _uiState.update { it.copy(linkDownloadError = null) }
    }

    companion object {
        private val VIDEO_EXT = Regex("\\.(mp4|mov|avi|mkv)$", RegexOption.IGNORE_CASE)

        fun isValidVideoUrl(url: String): Boolean {
            if (!url.startsWith("http://") && !url.startsWith("https://")) return false
            return try {
                java.net.URL(url).toURI()
                true
            } catch (_: Exception) {
                false
            }
        }

        fun fileNameFromUrl(url: String): String {
            val clean = url.substringBefore("?")
            val last = clean.substringAfterLast('/')
            var name = if (last.isNotEmpty() && VIDEO_EXT.containsMatchIn(last)) last else ""
            if (name.isEmpty()) name = "link_${System.currentTimeMillis()}.mp4"
            // sanitize
            name = name.replace(Regex("[^A-Za-z0-9._\\-() ]"), "_")
            if (name.length > 80) {
                val ext = name.substringAfterLast('.', "mp4")
                name = name.take(70) + "." + ext
            }
            return name
        }
    }
}