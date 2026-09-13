package com.cagan.iptv.data

import android.content.Context
import com.cagan.iptv.BuildConfig
import com.cagan.iptv.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class PlaylistResult(
    val channels: List<Channel>,
    val sourceName: String,
    val sourceUrl: String? = null,
    val fromCache: Boolean = false
)

class PlaylistRepository(context: Context) {
    private val storage = PlaylistStorage(context.applicationContext)

    /**
     * Açılış ekranında gösterilecek uzaktaki aktif playlist tanımlarını getirir.
     * Bu metot M3U içeriklerini indirmez; yalnızca config.json dosyasını okur.
     */
    suspend fun loadRemotePlaylists(): List<RemotePlaylist> = withContext(Dispatchers.IO) {
        val configUrl = BuildConfig.DEFAULT_CONFIG_URL.trim()
        if (configUrl.isBlank()) {
            throw IllegalStateException("CAGAN_CONFIG_URL yapılandırılmamış.")
        }

        val json = PlaylistLoader.loadBlocking(configUrl)
        val config = parseConfig(json)
        val enabled = config.playlists.filter { it.enabled && it.url.isNotBlank() }

        if (enabled.isEmpty()) {
            throw IllegalStateException("Config içinde aktif playlist bulunamadı.")
        }

        enabled
    }

    /** Kullanıcının ana ekrandan seçtiği tek bir remote playlist'i yükler. */
    suspend fun loadRemotePlaylist(playlist: RemotePlaylist): PlaylistResult = withContext(Dispatchers.IO) {
        if (!playlist.enabled || playlist.url.isBlank()) {
            throw IllegalStateException("Playlist aktif değil veya URL boş.")
        }
        loadAndCacheBlocking(
            playlist.url.trim(),
            playlist.name.ifBlank { "Playlist" }
        )
    }

    suspend fun loadUserPlaylist(url: String): PlaylistResult = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) throw IllegalArgumentException("Playlist URL boş olamaz.")

        val result = loadAndCacheBlocking(cleanUrl, "Kullanıcı Listesi")
        storage.userPlaylistUrl = cleanUrl
        result
    }

    fun clearSavedUserPlaylist() {
        storage.clearUserPlaylist()
    }

    private fun loadAndCacheBlocking(url: String, name: String): PlaylistResult {
        val text = PlaylistLoader.loadBlocking(url)
        val channels = M3uParser.parse(text)
        if (channels.isEmpty()) throw IllegalStateException("Playlist içinde kanal bulunamadı.")

        storage.cachedPlaylistText = text
        storage.cachedSourceName = name
        return PlaylistResult(
            channels = channels,
            sourceName = name,
            sourceUrl = url,
            fromCache = false
        )
    }

    private fun parseConfig(text: String): PlaylistConfig {
        val root = JSONObject(text)
        val array = root.optJSONArray("playlists")
            ?: throw IllegalStateException("Config playlists alanı bulunamadı.")

        val items = buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    RemotePlaylist(
                        name = item.optString("name", "Playlist"),
                        url = item.optString("url", ""),
                        enabled = item.optBoolean("enabled", true)
                    )
                )
            }
        }

        return PlaylistConfig(
            version = root.optInt("version", 1),
            playlists = items
        )
    }
}
