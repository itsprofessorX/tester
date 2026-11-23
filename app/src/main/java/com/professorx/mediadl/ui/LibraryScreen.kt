package com.professorx.mediadl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.professorx.mediadl.DlViewModel
import com.professorx.mediadl.LibraryCategory
import com.professorx.mediadl.LibrarySort
import com.professorx.mediadl.LocalMediaColors
import com.professorx.mediadl.MediaKind
import com.professorx.mediadl.MediaThumbnail
import com.professorx.mediadl.SavedMedia
import com.professorx.mediadl.formatBytes
import com.professorx.mediadl.formatRelativeTime

@Composable
fun LibraryScreen(vm: DlViewModel) {
    val ctx = LocalContext.current
    val colors = LocalMediaColors.current
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(LibraryCategory.ALL) }
    var platformFilter by remember { mutableStateOf("All") }
    var sort by remember { mutableStateOf(LibrarySort.NEWEST) }
    var isGrid by remember { mutableStateOf(true) }
    var deleteCandidate by remember { mutableStateOf<SavedMedia?>(null) }
    var toolboxCandidate by remember { mutableStateOf<SavedMedia?>(null) }
    var playingMedia by remember { mutableStateOf<SavedMedia?>(null) }

    LaunchedEffect(Unit) { vm.verifyGalleryFiles() }

    val platforms = remember(vm.history.toList()) {
        listOf("All") + vm.history.map { it.platform }.filter { it.isNotBlank() }.distinct()
    }

    val filtered = remember(vm.history.toList(), query, category, platformFilter, sort) {
        val base = vm.history.filter { item ->
            val matchCat = when (category) {
                LibraryCategory.ALL -> true
                LibraryCategory.PHOTOS -> item.kind == MediaKind.PHOTO || item.kind == MediaKind.CAROUSEL
                LibraryCategory.VIDEOS -> item.kind == MediaKind.VIDEO || item.kind == MediaKind.REEL
                LibraryCategory.AUDIO -> item.kind == MediaKind.AUDIO
                LibraryCategory.FAVORITES -> item.isFavorite
            }
            val matchPlat = platformFilter == "All" || item.platform.equals(platformFilter, ignoreCase = true)
            val matchQuery = query.isBlank() ||
                item.name.contains(query, ignoreCase = true) ||
                item.platform.contains(query, ignoreCase = true)
            matchCat && matchPlat && matchQuery
        }
        when (sort) {
            LibrarySort.NEWEST -> base.sortedByDescending { it.timestamp }
            LibrarySort.OLDEST -> base.sortedBy { it.timestamp }
            LibrarySort.NAME -> base.sortedBy { it.name.lowercase() }
            LibrarySort.SIZE -> base.sortedByDescending { it.sizeBytes }
        }
    }

    val missingCount = vm.history.count { !it.existsInGallery }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Media Library", color = colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("${filtered.size} item(s)", color = colors.textMuted, fontSize = 13.sp)
            }
            IconButton(onClick = { isGrid = !isGrid }) {
                Icon(
                    imageVector = if (isGrid) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                    contentDescription = "Toggle grid or list view",
                    tint = colors.textPrimary
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search downloads, platforms…", color = colors.textMuted) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.textMuted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.border,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary
            )
        )

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LibraryCategory.entries.forEach { cat ->
                FilterPill(cat.label, category == cat, { category = cat })
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            platforms.forEach { p ->
                FilterPill(p, platformFilter == p, { platformFilter = p })
            }
            Spacer(Modifier.width(8.dp))
            LibrarySort.entries.forEach { s ->
                FilterPill("Sort: ${s.label}", sort == s, { sort = s })
            }
        }

        if (missingCount > 0) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.warning.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = colors.warning, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("$missingCount missing gallery file(s)", color = colors.textPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.removeMissingHistoryItems() }) {
                    Text("Clean up", color = colors.accent, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (filtered.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Rounded.PhotoLibrary,
                title = "Nothing matches your filter",
                subtitle = "Try another category or download new media from Home."
            )
        } else if (isGrid) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(filtered, key = { it.id }) { item ->
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                            .clickable { playingMedia = item }
                            .padding(10.dp)
                    ) {
                        Box {
                            MediaThumbnail(
                                uri = item.uri,
                                kind = item.kind,
                                exists = item.existsInGallery,
                                modifier = Modifier.fillMaxWidth().aspectRatio(1.1f).clip(RoundedCornerShape(12.dp))
                            )
                            IconButton(
                                onClick = { vm.toggleFavorite(item.id) },
                                modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (item.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (item.isFavorite) colors.accent else colors.textPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(item.name, color = colors.textPrimary, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${item.platform} · ${formatBytes(item.sizeBytes)}",
                            color = if (item.existsInGallery) colors.textMuted else colors.error,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton(onClick = { toolboxCandidate = item }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Rounded.Build, contentDescription = "Toolbox", tint = colors.textMuted, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { shareSavedMedia(ctx, item) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Rounded.Share, contentDescription = "Share", tint = colors.textMuted, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { deleteCandidate = item }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = colors.error, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(filtered, key = { it.id }) { item ->
                    SurfaceCard(onClick = { playingMedia = item }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MediaThumbnail(
                                uri = item.uri,
                                kind = item.kind,
                                exists = item.existsInGallery,
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${item.platform} · ${item.kind.label} · ${formatBytes(item.sizeBytes)} · ${formatRelativeTime(item.timestamp)}",
                                    color = if (item.existsInGallery) colors.textMuted else colors.error,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                            IconButton(onClick = { vm.toggleFavorite(item.id) }) {
                                Icon(
                                    imageVector = if (item.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (item.isFavorite) colors.accent else colors.textMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(onClick = { toolboxCandidate = item }) {
                                Icon(Icons.Rounded.Build, contentDescription = "Toolbox", tint = colors.textMuted, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { shareSavedMedia(ctx, item) }) {
                                Icon(Icons.Rounded.Share, contentDescription = "Share", tint = colors.textMuted, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { deleteCandidate = item }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = colors.error, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    playingMedia?.let { item ->
        InbuiltMediaPlayerDialog(item = item, onDismiss = { playingMedia = null })
    }

    deleteCandidate?.let { item ->
        DeleteMediaDialog(
            item = item,
            onDismiss = { deleteCandidate = null },
            onDeleteHistoryOnly = {
                vm.deleteSavedMedia(item, deleteFromDevice = false)
                deleteCandidate = null
            },
            onDeleteFromDevice = {
                vm.deleteSavedMedia(item, deleteFromDevice = true)
                deleteCandidate = null
            }
        )
    }

    toolboxCandidate?.let { item ->
        MediaToolboxDialog(
            item = item,
            busy = vm.toolboxBusy,
            message = vm.toolboxMessage,
            onDismiss = {
                vm.toolboxMessage = null
                toolboxCandidate = null
            },
            onRun = { action, ext, start, end, crf ->
                vm.runToolbox(item, action, ext, start, end, crf)
            }
        )
    }
}
