package com.cagan.iptv.ui

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.cagan.iptv.R
import com.cagan.iptv.data.PlaylistRepository
import com.cagan.iptv.data.PlaylistResult
import com.cagan.iptv.data.RemotePlaylist
import com.cagan.iptv.model.Channel
import kotlinx.coroutines.launch

private val Bg = Color(0xFF050A12)
private val Panel = Color(0xFF0D1520)
private val Panel2 = Color(0xFF111D2B)
private val Accent = Color(0xFF56D6FF)
private val Soft = Color(0xFF8FA1AF)
private val FocusBg = Color(0xFF1E5F78)
private val FocusBorder = Color(0xFF9BE8FF)
private val SelectedBg = Color(0xFF16384A)

private enum class Screen {
    PLAYLISTS,
    CHANNELS,
    MANUAL
}

@Composable
fun CaganIpTvApp() {
    val context = LocalContext.current
    val repository = remember { PlaylistRepository(context) }
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf(Screen.PLAYLISTS) }
    var remotePlaylists by remember { mutableStateOf<List<RemotePlaylist>>(emptyList()) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var sourceName by remember { mutableStateOf("") }
    var selectedChannel by remember { mutableStateOf<Channel?>(null) }
    var selectedGroup by remember { mutableStateOf("Tüm Kanallar") }
    var loading by remember { mutableStateOf(true) }
    var loadingPlaylistName by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun applyPlaylistResult(result: PlaylistResult) {
        channels = result.channels
        sourceName = result.sourceName
        selectedChannel = null
        selectedGroup = "Tüm Kanallar"
        screen = Screen.CHANNELS
        error = null
    }

    fun refreshRemotePlaylists() {
        scope.launch {
            loading = true
            error = null
            try {
                remotePlaylists = repository.loadRemotePlaylists()
            } catch (e: Exception) {
                error = "Liste tanımları yüklenemedi: ${e.message ?: "Bilinmeyen hata"}"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            remotePlaylists = repository.loadRemotePlaylists()
        } catch (e: Exception) {
            error = "Liste tanımları yüklenemedi: ${e.message ?: "Bilinmeyen hata"}"
        } finally {
            loading = false
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Bg,
            surface = Panel,
            primary = Accent
        )
    ) {
        when {
            selectedChannel != null -> PlayerScreen(
                channel = selectedChannel!!,
                onBack = { selectedChannel = null }
            )

            loading && screen == Screen.PLAYLISTS -> LoadingScreen("Playlist seçenekleri hazırlanıyor...")

            screen == Screen.MANUAL -> AddPlaylistScreen(
                initialError = null,
                onLoadUser = { url -> repository.loadUserPlaylist(url) },
                onLoaded = { applyPlaylistResult(it) },
                onBack = { screen = Screen.PLAYLISTS }
            )

            screen == Screen.CHANNELS -> ChannelScreen(
                channels = channels,
                sourceName = sourceName,
                selectedGroup = selectedGroup,
                onSelectedGroupChange = { selectedGroup = it },
                onPlay = { selectedChannel = it },
                onBackToPlaylists = {
                    channels = emptyList()
                    sourceName = ""
                    selectedGroup = "Tüm Kanallar"
                    screen = Screen.PLAYLISTS
                }
            )

            else -> PlaylistSelectionScreen(
                playlists = remotePlaylists,
                error = error,
                loadingPlaylistName = loadingPlaylistName,
                onPlaylistClick = { playlist ->
                    scope.launch {
                        loadingPlaylistName = playlist.name
                        error = null
                        try {
                            applyPlaylistResult(repository.loadRemotePlaylist(playlist))
                        } catch (e: Exception) {
                            error = "${playlist.name} yüklenemedi: ${e.message ?: "Bilinmeyen hata"}"
                        } finally {
                            loadingPlaylistName = null
                        }
                    }
                },
                onManual = { screen = Screen.MANUAL },
                onRefresh = { refreshRemotePlaylists() }
            )
        }
    }
}

@Composable
private fun LoadingScreen(message: String) {
    Box(
        Modifier.fillMaxSize().background(Bg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator()
            Text(message, color = Soft)
        }
    }
}

@Composable
private fun tvFocusModifier(
    focused: Boolean,
    selected: Boolean = false,
    shape: RoundedCornerShape = RoundedCornerShape(18.dp)
): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.045f else 1f,
        label = "tvFocusScale"
    )
    val bg = when {
        focused -> FocusBg
        selected -> SelectedBg
        else -> Color.Transparent
    }
    val borderColor = when {
        focused -> FocusBorder
        selected -> Accent.copy(alpha = 0.7f)
        else -> Color.Transparent
    }
    return Modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .background(bg, shape)
        .border(if (focused) 3.dp else if (selected) 2.dp else 0.dp, borderColor, shape)
}

@Composable
private fun PlaylistSelectionScreen(
    playlists: List<RemotePlaylist>,
    error: String?,
    loadingPlaylistName: String?,
    onPlaylistClick: (RemotePlaylist) -> Unit,
    onManual: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.cagan_logo),
                contentDescription = "Cagan IP TV",
                modifier = Modifier.width(150.dp).height(74.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Cagan IP TV", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("İzlemek istediğiniz listeyi seçin", color = Soft)
            }
            FilledTonalButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text("Yenile")
            }
            Spacer(Modifier.width(10.dp))
            FilledTonalButton(onClick = onManual) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Manuel M3U")
            }
        }

        error?.let {
            Spacer(Modifier.height(18.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    it,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        if (playlists.isEmpty() && error == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Aktif playlist bulunamadı.", color = Soft)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(280.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(playlists, key = { it.name + it.url }) { playlist ->
                    val isLoading = loadingPlaylistName == playlist.name
                    var focused by remember { mutableStateOf(false) }
                    Card(
                        onClick = { if (loadingPlaylistName == null) onPlaylistClick(playlist) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (focused) FocusBg else Panel2
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .height(150.dp)
                            .onFocusChanged { focused = it.isFocused }
                            .then(tvFocusModifier(focused, shape = RoundedCornerShape(20.dp)))
                            .focusable()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(22.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
                            } else {
                                Icon(Icons.Rounded.LiveTv, null, tint = Accent, modifier = Modifier.size(34.dp))
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                playlist.name,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2
                            )
                            Text(
                                if (isLoading) "Kanallar yükleniyor..." else "Açmak için seçin",
                                color = Soft,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddPlaylistScreen(
    initialError: String?,
    onLoadUser: suspend (String) -> PlaylistResult,
    onLoaded: (PlaylistResult) -> Unit,
    onBack: () -> Unit
) {
    var playlistUrl by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember(initialError) { mutableStateOf(initialError) }
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.width(720.dp),
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(Modifier.padding(36.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("Manuel M3U Ekle", fontSize = 28.sp, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = playlistUrl,
                    onValueChange = { playlistUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("M3U / M3U8 URL") },
                    placeholder = { Text("https://.../playlist.m3u") }
                )

                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        enabled = playlistUrl.isNotBlank() && !loading,
                        onClick = {
                            scope.launch {
                                loading = true
                                error = null
                                try {
                                    onLoaded(onLoadUser(playlistUrl.trim()))
                                } catch (e: Exception) {
                                    error = "Playlist yüklenemedi: ${e.message ?: "Bilinmeyen hata"}"
                                } finally {
                                    loading = false
                                }
                            }
                        }
                    ) {
                        if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (loading) "Yükleniyor..." else "Listeyi Aç")
                    }

                    TextButton(onClick = onBack) { Text("Geri") }
                }
            }
        }
    }
}

@Composable
private fun ChannelScreen(
    channels: List<Channel>,
    sourceName: String,
    selectedGroup: String,
    onSelectedGroupChange: (String) -> Unit,
    onPlay: (Channel) -> Unit,
    onBackToPlaylists: () -> Unit
) {
    BackHandler(onBack = onBackToPlaylists)

    val groups = remember(channels) {
        channels.groupBy { it.group }.toSortedMap()
    }
    val visibleChannels = if (selectedGroup == "Tüm Kanallar") {
        channels
    } else {
        groups[selectedGroup].orEmpty()
    }

    Row(Modifier.fillMaxSize().background(Bg)) {
        Column(
            modifier = Modifier
                .width(270.dp)
                .fillMaxHeight()
                .background(Panel)
                .padding(20.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.cagan_logo),
                contentDescription = "Cagan IP TV",
                modifier = Modifier.fillMaxWidth().height(82.dp),
                contentScale = ContentScale.Fit
            )
            Text(sourceName, color = Soft, fontSize = 12.sp, maxLines = 2)

            Spacer(Modifier.height(24.dp))

            var allFocused by remember { mutableStateOf(false) }
            val allSelected = selectedGroup == "Tüm Kanallar"
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { allFocused = it.isFocused }
                    .then(tvFocusModifier(allFocused, allSelected, RoundedCornerShape(14.dp)))
                    .focusable(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        allFocused -> FocusBg
                        allSelected -> SelectedBg
                        else -> Accent.copy(alpha = 0.75f)
                    }
                ),
                onClick = { onSelectedGroupChange("Tüm Kanallar") }
            ) { Text("Tüm Kanallar") }

            Spacer(Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groups.keys.forEach { group ->
                    item(key = group) {
                        var focused by remember { mutableStateOf(false) }
                        val selected = selectedGroup == group
                        FilledTonalButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focused = it.isFocused }
                                .then(tvFocusModifier(focused, selected, RoundedCornerShape(14.dp)))
                                .focusable(),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = when {
                                    focused -> FocusBg
                                    selected -> SelectedBg
                                    else -> Panel2
                                }
                            ),
                            onClick = { onSelectedGroupChange(group) }
                        ) {
                            Text(
                                group,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onBackToPlaylists
            ) {
                Text("← Playlistler")
            }
        }

        Column(Modifier.fillMaxSize().padding(28.dp)) {
            Text(selectedGroup, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("${visibleChannels.size} kanal • $sourceName", color = Soft)
            Spacer(Modifier.height(20.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(230.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(
                    items = visibleChannels,
                    key = { it.url + it.name }
                ) { channel ->
                    var focused by remember { mutableStateOf(false) }
                    Card(
                        onClick = { onPlay(channel) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (focused) FocusBg else Panel2
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .onFocusChanged { focused = it.isFocused }
                            .then(tvFocusModifier(focused, shape = RoundedCornerShape(18.dp)))
                            .focusable()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp).height(95.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Rounded.LiveTv,
                                null,
                                tint = if (focused) Color.White else Accent
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                channel.name,
                                fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1
                            )
                            Text(channel.group, color = Soft, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

private data class StreamRequest(
    val uri: Uri,
    val headers: Map<String, String>
)

private fun parseStreamRequest(rawUrl: String): StreamRequest {
    val parts = rawUrl.trim().split("|", limit = 2)
    val uri = Uri.parse(parts[0].trim())
    val headers = mutableMapOf<String, String>()

    if (parts.size == 2) {
        parts[1].split("&").forEach { pair ->
            val p = pair.split("=", limit = 2)
            if (p.size == 2 && p[0].isNotBlank()) {
                headers[p[0].trim()] = Uri.decode(p[1].trim())
            }
        }
    }

    if (headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
        headers["User-Agent"] = "Cagan-IP-TV/0.5 (Android)"
    }

    return StreamRequest(uri, headers)
}

private fun createPlayer(context: android.content.Context, channel: Channel): ExoPlayer {
    val request = parseStreamRequest(channel.url)

    val httpFactory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(20_000)
        .setDefaultRequestProperties(request.headers)

    val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
    val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(mediaSourceFactory)
        .build()
        .apply {
            setMediaItem(MediaItem.fromUri(request.uri))
            prepare()
            playWhenReady = true
        }
}

@Composable
private fun PlayerScreen(
    channel: Channel,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var player by remember(channel.url) { mutableStateOf<ExoPlayer?>(null) }
    var playbackError by remember(channel.url) { mutableStateOf<String?>(null) }
    var buffering by remember(channel.url) { mutableStateOf(true) }

    BackHandler(onBack = onBack)

    DisposableEffect(channel.url) {
        val createdPlayer = try {
            createPlayer(context, channel)
        } catch (e: Throwable) {
            playbackError = "Yayın başlatılamadı: ${e.message ?: e.javaClass.simpleName}"
            null
        }

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
            }

            override fun onPlayerError(error: PlaybackException) {
                buffering = false
                playbackError = "Yayın açılamadı: ${error.message ?: error.errorCodeName}"
            }
        }

        createdPlayer?.addListener(listener)
        player = createdPlayer

        onDispose {
            createdPlayer?.removeListener(listener)
            createdPlayer?.release()
            player = null
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                PlayerView(it).apply {
                    useController = true
                    keepScreenOn = true
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view -> view.player = player }
        )

        if (buffering && playbackError == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        playbackError?.let { message ->
            Card(
                modifier = Modifier.align(Alignment.Center).padding(40.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xE6222222))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(message, color = Color.White)
                    Button(onClick = onBack) { Text("Kanal Listesine Dön") }
                }
            }
        }

        Button(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(20.dp)
        ) {
            Text("← ${channel.name}")
        }
    }
}
