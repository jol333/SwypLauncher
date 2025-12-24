package com.joyal.swyplauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.joyal.swyplauncher.domain.model.AppInfo
import com.joyal.swyplauncher.domain.usecase.GetInstalledAppsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutEditorScreen(
    initialShortcut: String,
    initialSelectedApps: Set<String>,
    existingShortcutNames: Set<String>,
    getInstalledAppsUseCase: GetInstalledAppsUseCase,
    onSave: (String, Set<String>) -> Unit,
    onCancel: () -> Unit,
    scrollState: LazyListState = rememberLazyListState()
) {
    var shortcutName by remember { mutableStateOf(initialShortcut) }
    var selectedApps by remember { mutableStateOf(initialSelectedApps) }
    var allApps by remember { mutableStateOf(listOf<AppInfo>()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }
    var showOverwriteDialog by remember { mutableStateOf(false) }
    var showMagicWordError by remember { mutableStateOf(false) }

    val searchInteractionSource = remember { MutableInteractionSource() }
    val isSearchFocused by searchInteractionSource.collectIsFocusedAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val nameFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(500)
        val apps = withContext(Dispatchers.IO) { getInstalledAppsUseCase() }
        allApps = apps
        selectedApps = selectedApps.filter { it in apps.map { app -> app.getIdentifier() } }.toSet()
        isLoading = false
    }

    LaunchedEffect(isLoading) {
        if (!isLoading && initialShortcut.isEmpty()) {
            nameFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(isSearchVisible) {
        if (isSearchVisible) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val filteredApps = remember(allApps, searchQuery) {
        if (searchQuery.isBlank()) allApps
        else allApps.filter { it.label.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initialShortcut.isEmpty()) "New shortcut" else "Edit shortcut") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = {
            Box(modifier = Modifier.padding(bottom = 80.dp)) {
                SnackbarHost(snackbarHostState)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "MAGIC WORD",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = shortcutName,
                            onValueChange = {
                                shortcutName = it
                                // Clear error as soon as user types
                                if (showMagicWordError) showMagicWordError = false
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(nameFocusRequester),
                            placeholder = { Text("Type shortcut (min 2 chars)") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.extraLarge,
                            isError = showMagicWordError,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF111111),
                                unfocusedContainerColor = Color(0xFF111111),
                                focusedBorderColor = if (showMagicWordError) Color(0xFFE53935) else Color.White,
                                unfocusedBorderColor = if (showMagicWordError) Color(0xFFE53935) else Color(0xFF363636),
                                errorBorderColor = Color(0xFFE53935),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
                                unfocusedPlaceholderColor = Color.White.copy(alpha = 0.5f)
                            )
                        )

                        Text(
                            if (shortcutName.isEmpty()) "Type this word in the launcher to see these apps as results."
                            else "Type '$shortcutName' in the launcher to see these apps as results.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                        )
                    }

                    if (selectedApps.isNotEmpty()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "APPS TO SHOW (${selectedApps.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(selectedApps.toList()) { appId ->
                                    allApps.find { it.getIdentifier() == appId }?.let { app ->
                                        SelectedAppChip(app = app) {
                                            selectedApps = selectedApps - appId
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    Column(modifier = Modifier.fillMaxSize()) {
                        if (!isSearchVisible) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "SELECT APPS TO SHOW",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = { isSearchVisible = true }) {
                                    Icon(
                                        Icons.Default.Search,
                                        "Search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            androidx.compose.material3.Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .border(
                                        width = if (isSearchFocused) 1.dp else 0.dp,
                                        color = if (isSearchFocused) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(24.dp)
                                    ),
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = 16.dp,
                                        vertical = 12.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        modifier = Modifier
                                            .weight(1f)
                                            .focusRequester(searchFocusRequester),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                            onSearch = { keyboardController?.hide() }
                                        ),
                                        interactionSource = searchInteractionSource,
                                        decorationBox = { innerTextField ->
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    "Search apps to add...",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                        alpha = 0.7f
                                                    )
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )
                                    Icon(
                                        Icons.Default.Close,
                                        "Clear",
                                        modifier = Modifier.clickable {
                                            if (searchQuery.isNotEmpty()) searchQuery = ""
                                            else isSearchVisible = false
                                        },
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val listState = scrollState
                        val showTopFade by remember { derivedStateOf { listState.canScrollBackward } }
                        val showBottomFade by remember { derivedStateOf { listState.canScrollForward } }

                        LaunchedEffect(listState.isScrollInProgress) {
                            if (listState.isScrollInProgress) {
                                keyboardController?.hide()
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(filteredApps, key = { it.getIdentifier() }) { app ->
                                    val isSelected = app.getIdentifier() in selectedApps
                                    AppSelectionItem(
                                        app = app,
                                        isSelected = isSelected,
                                        onClick = {
                                            selectedApps = if (isSelected) {
                                                selectedApps - app.getIdentifier()
                                            } else {
                                                selectedApps + app.getIdentifier()
                                            }
                                        }
                                    )
                                }
                            }

                            if (showTopFade) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(24.dp)
                                        .align(Alignment.TopCenter)
                                        .background(
                                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.surface,
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                )
                            }

                            if (showBottomFade) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(24.dp)
                                        .align(Alignment.BottomCenter)
                                        .background(
                                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    MaterialTheme.colorScheme.surface
                                                )
                                            )
                                        )
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    SaveButton(
                        onClick = {
                            when {
                                shortcutName.isEmpty() -> {
                                    showMagicWordError = true
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Please type a magic word")
                                    }
                                }

                                shortcutName.length < 2 -> {
                                    showMagicWordError = true
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Shortcut name must be at least 2 characters")
                                    }
                                }

                                selectedApps.isEmpty() -> scope.launch {
                                    snackbarHostState.showSnackbar("Please select at least one app")
                                }

                                shortcutName in existingShortcutNames && shortcutName != initialShortcut ->
                                    showOverwriteDialog = true

                                else -> onSave(shortcutName, selectedApps)
                            }
                        }
                    )
                }
            }
        }
    }

    if (showOverwriteDialog) {
        AlertDialog(
            onDismissRequest = { showOverwriteDialog = false },
            title = { Text("Shortcut already exists") },
            text = { Text("A shortcut with the name \"$shortcutName\" already exists. Do you want to replace it?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOverwriteDialog = false
                        onSave(shortcutName, selectedApps)
                    }
                ) {
                    Text("Replace")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverwriteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SaveButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(56.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Save shortcut",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun SelectedAppChip(app: AppInfo, onRemove: () -> Unit) {
    val context = LocalContext.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(64.dp)
    ) {
        Box {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(app)
                    .size(144)
                    .memoryCacheKey("app_icon_${app.getIdentifier()}")
                    .diskCacheKey("app_icon_${app.getIdentifier()}")
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            app.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun AppSelectionItem(
    app: AppInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(app)
                .size(120)
                .memoryCacheKey("app_icon_${app.getIdentifier()}")
                .diskCacheKey("app_icon_${app.getIdentifier()}")
                .build(),
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}