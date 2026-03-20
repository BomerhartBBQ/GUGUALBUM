
package com.gugu.gallery.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.gugu.gallery.network.SambaScanner
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gugu.gallery.data.ServerEntity
import com.gugu.gallery.ui.viewmodel.GalleryViewModel
import com.gugu.gallery.ui.viewmodel.SmbFileItem
import kotlinx.coroutines.flow.first
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Preview(showBackground = true)
@Composable
fun SettingsScreen(viewModel: GalleryViewModel = viewModel()) {
    var showScanDialog by remember { mutableStateOf(false) }
    var showAuthDialog by remember { mutableStateOf(false) }
    var showFolderSelectionDialog by remember { mutableStateOf(false) }
    var showServerDetailsDialog by remember { mutableStateOf<ServerEntity?>(null) }
    
    var selectedIp by remember { mutableStateOf("") }
    var selectedUser by remember { mutableStateOf("zhou") }
    var selectedPass by remember { mutableStateOf("wsbbnyaw11") }
    var editingServer by remember { mutableStateOf<ServerEntity?>(null) }
    var isManualEntry by remember { mutableStateOf(false) }

    var isScanning by remember { mutableStateOf(false) }
    var scannedServers by remember { mutableStateOf(emptyList<String>()) }
    
    var currentLevelFiles by remember { mutableStateOf(emptyList<SmbFileItem>()) }
    val pathHistory = remember { mutableStateListOf<String>() } 
    val selectedPaths = remember { mutableStateListOf<String>() }
    
    val connectedServers by viewModel.allServers.collectAsState(initial = emptyList())
    val indexingState by viewModel.isIndexing.collectAsState()
    val indexingProgress by viewModel.progressValue.collectAsState()
    val indexingStatus by viewModel.currentStatus.collectAsState()
    
    val coroutineScope = rememberCoroutineScope()

    BackHandler(enabled = showScanDialog || showAuthDialog || showFolderSelectionDialog || showServerDetailsDialog != null) {
        showScanDialog = false
        showAuthDialog = false
        showFolderSelectionDialog = false
        showServerDetailsDialog = null
        editingServer = null
        isManualEntry = false
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E))) {
        Column(modifier = Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
            Text(text = "设置", style = MaterialTheme.typography.headlineLarge, color = Color.White)
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text(text = "NAS 配置", style = MaterialTheme.typography.titleLarge, color = Color.Gray)
                    
                    FocusableButton(
                        onClick = { 
                            showScanDialog = true
                            isScanning = true
                            scannedServers = emptyList()
                            coroutineScope.launch { 
                                scannedServers = SambaScanner.scanNetworkForServers()
                                isScanning = false 
                            } 
                        },
                        text = "扫描网络寻找 NAS",
                        modifier = Modifier.fillMaxWidth().height(60.dp)
                    )

                    FocusableButton(
                        onClick = { 
                            editingServer = null 
                            selectedIp = ""
                            selectedUser = "zhou"
                            selectedPass = "wsbbnyaw11"
                            isManualEntry = true
                            showAuthDialog = true 
                        },
                        text = "手动添加服务器",
                        modifier = Modifier.fillMaxWidth().height(60.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "图库偏好", style = MaterialTheme.typography.titleLarge, color = Color.Gray)
                    
                    FocusableButton(
                        onClick = { viewModel.startIndexing() },
                        text = if (indexingState) "正在索引..." else "立即更新图库索引",
                        enabled = !indexingState,
                        modifier = Modifier.fillMaxWidth().height(60.dp)
                    )

                    if (indexingState || indexingStatus.isNotEmpty()) {
                         Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            if(indexingState) {
                                LinearProgressIndicator(
                                    progress = { indexingProgress },
                                    modifier = Modifier.fillMaxWidth().height(8.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = Color.DarkGray
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            Text(
                                text = indexingStatus,
                                color = Color.LightGray,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1.2f).fillMaxHeight().background(Color(0xFF2A2A2A), shape = MaterialTheme.shapes.large).padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(text = "已连接的服务器", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    connectedServers.forEach { server ->
                        FocusableListItem(
                            onClick = { showServerDetailsDialog = server },
                            headlineContent = { Text(server.name) }, 
                            supportingContent = { Text("IP: ${server.ipAddress}") }
                        )
                    }
                }
            }
        }

        // --- DIALOGS ---

        if (showScanDialog) {
            DialogBase(onDismiss = { showScanDialog = false }) {
                Text("正在扫描...", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(scannedServers) { ip ->
                                FocusableListItem(
                                    onClick = {
                                        editingServer = null
                                        selectedIp = ip
                                        selectedUser = "zhou"
                                        selectedPass = "wsbbnyaw11"
                                        isManualEntry = true 
                                        showScanDialog = false
                                        showAuthDialog = true
                                    },
                                    headlineContent = { Text(ip) }
                                )
                            }
                        }
                    }
                }
                FocusableButton(onClick = { showScanDialog = false }, text = "退出", modifier = Modifier.align(Alignment.End))
            }
        }

        if (showAuthDialog) {
             DialogBase(onDismiss = { showAuthDialog = false }, width = 450.dp) {
                Text(text = if (isManualEntry) "添加服务器" else "修改服务器", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    if (isManualEntry) {
                        ThemedOutlinedTextField(value = selectedIp, onValueChange = { selectedIp = it }, label = "服务器 IP 地址")
                    }
                    ThemedOutlinedTextField(value = selectedUser, onValueChange = { selectedUser = it }, label = "用户名 (可选)")
                    ThemedOutlinedTextField(value = selectedPass, onValueChange = { selectedPass = it }, label = "密码 (可选)", isPassword = true)
                }

                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    FocusableButton(onClick = { showAuthDialog = false }, text = "退出")
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!isManualEntry && editingServer != null) {
                            FocusableButton(
                                onClick = {
                                    val updatedServer = editingServer!!.copy(username = selectedUser, password = selectedPass)
                                    viewModel.updateServer(updatedServer)
                                    showAuthDialog = false
                                },
                                text = "保存凭据"
                            )
                        }

                        FocusableButton(
                            onClick = {
                                coroutineScope.launch {
                                    val ip = if (isManualEntry) selectedIp else editingServer!!.ipAddress
                                    val rootPath = "smb://$ip/"
                                    currentLevelFiles = viewModel.getSmbFiles(rootPath, selectedUser, selectedPass)
                                    pathHistory.clear(); pathHistory.add(rootPath)
                                    selectedPaths.clear()
                                    
                                    editingServer?.let { server ->
                                        val existingFolders = viewModel.getFoldersForServer(server.id).first()
                                        selectedPaths.addAll(existingFolders.map { it.smbPath })
                                    }
                                    
                                    showAuthDialog = false
                                    showFolderSelectionDialog = true
                                }
                            },
                            text = if (isManualEntry) "浏览目录" else "修改目录"
                        )
                    }
                }
            }
        }

        if (showFolderSelectionDialog) {
            DialogBase(onDismiss = { showFolderSelectionDialog = false }, width = 700.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (pathHistory.size > 1) {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                pathHistory.removeAt(pathHistory.size - 1)
                                currentLevelFiles = viewModel.getSmbFiles(pathHistory.last(), selectedUser, selectedPass)
                            }
                        }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
                    }
                    Text(pathHistory.last(), color = Color.Gray, modifier = Modifier.weight(1f), maxLines = 1)
                }

                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(currentLevelFiles.filter { it.isDirectory }) { file ->
                        var isRowFocused by remember { mutableStateOf(false) }
                        var isCheckFocused by remember { mutableStateOf(false) }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedPaths.contains(file.path),
                                onCheckedChange = { isChecked -> 
                                    if (isChecked) selectedPaths.add(file.path) else selectedPaths.remove(file.path) 
                                },
                                modifier = Modifier
                                    .onFocusChanged { isCheckFocused = it.isFocused }
                                    .background(if (isCheckFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent)
                            )
                            
                            Box(modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { isRowFocused = it.isFocused }
                                .background(if (isRowFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                    coroutineScope.launch {
                                        pathHistory.add(file.path)
                                        currentLevelFiles = viewModel.getSmbFiles(file.path, selectedUser, selectedPass)
                                    }
                                }
                                .padding(12.dp)
                            ) {
                                Text(file.name, color = Color.White)
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    FocusableButton(onClick = { showFolderSelectionDialog = false }, text = "退出")
                    FocusableButton(
                        onClick = {
                            if (editingServer == null) {
                                viewModel.addServer(selectedIp, selectedUser, selectedPass, selectedPaths.toList())
                            } else {
                                val updatedServer = editingServer!!.copy(username = selectedUser, password = selectedPass)
                                viewModel.updateServer(updatedServer)
                                viewModel.updateServerFolders(updatedServer, selectedPaths.toList())
                            }
                            showFolderSelectionDialog = false
                        },
                        text = "确认保存"
                    )
                }
            }
        }
        
        showServerDetailsDialog?.let { server ->
             DialogBase(onDismiss = { showServerDetailsDialog = null }) {
                 val folders by viewModel.getFoldersForServer(server.id).collectAsState(initial = emptyList())
                 Text(text = "服务器详情: ${server.name}", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                 Text(text = "已索引目录:", color = Color.Gray)
                 Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                     folders.forEach { folder -> Text(text = "• ${folder.displayName}", color = Color.White, modifier = Modifier.padding(vertical = 4.dp)) }
                 }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FocusableButton(
                        onClick = {
                            editingServer = server
                            selectedUser = server.username
                            selectedPass = server.password
                            isManualEntry = false
                            showServerDetailsDialog = null
                            showAuthDialog = true
                        },
                        text = "修改",
                        modifier = Modifier.weight(1f)
                    )
                    FocusableButton(
                        onClick = { viewModel.deleteServer(server); showServerDetailsDialog = null }, 
                        text = "删除",
                        modifier = Modifier.weight(1f),
                        unfocusedContainerColor = Color.Red
                    )
                    FocusableButton(onClick = { showServerDetailsDialog = null }, text = "退出", modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun FocusableButton(onClick: () -> Unit, text: String, modifier: Modifier = Modifier, enabled: Boolean = true, unfocusedContainerColor: Color = Color.DarkGray) {
    var isFocused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick, 
        modifier = modifier.onFocusChanged { isFocused = it.isFocused }, 
        enabled = enabled, 
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFocused) Color.White else unfocusedContainerColor, 
            contentColor = if (isFocused) Color.Black else Color.White, 
            disabledContainerColor = Color.Gray.copy(alpha = 0.5f)
        ), 
        shape = MaterialTheme.shapes.medium
    ) {
        Text(text = text)
    }
}

@Composable
fun FocusableListItem(onClick: () -> Unit, headlineContent: @Composable () -> Unit, supportingContent: @Composable (() -> Unit)? = null) {
    var isFocused by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .focusable()
            .background(if (isFocused) Color.White else Color.Transparent)
            .padding(4.dp),
        headlineContent = headlineContent,
        supportingContent = supportingContent,
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = if (isFocused) Color.Black else Color.White,
            supportingColor = if (isFocused) Color.DarkGray else Color.Gray
        )
    )
}

@Composable
private fun ThemedOutlinedTextField(value: String, onValueChange: (String) -> Unit, label: String, isPassword: Boolean = false) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color.White, unfocusedBorderColor = Color.Gray))
}

@Composable
private fun DialogBase(onDismiss: () -> Unit, width: Dp = 500.dp, content: @Composable ColumnScope.() -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .width(width)
                    .fillMaxHeight(0.85f)
                    .padding(16.dp),
                shape = MaterialTheme.shapes.large,
                color = Color(0xFF2E2E2E),
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    verticalArrangement = Arrangement.Top
                ) {
                    content()
                }
            }
        }
    }
}
