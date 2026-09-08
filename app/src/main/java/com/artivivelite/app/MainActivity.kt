package com.artivivelite.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.ar.core.*
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AugmentedImageNode
import io.github.sceneview.math.Size
import io.github.sceneview.node.VideoNode
import io.github.sceneview.node.ImageNode
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { App() } }
}

@Composable
fun App() {
    val context = LocalContext.current
    val store = remember { ProjectStore(context) }
    var projects by remember { mutableStateOf(store.load()) }
    var screen by remember { mutableStateOf("home") }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) screen = "scanner" }
    when (screen) {
        "home" -> Home(projects, { screen = "create" }) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) screen = "scanner"
            else camera.launch(Manifest.permission.CAMERA)
        }
        "create" -> CreateProject(store, { projects = store.load(); screen = "home" }) { screen = "home" }
        "scanner" -> ScannerScreen(projects) { screen = "home" }
    }
}

@Composable
private fun Home(projects: List<Project>, onCreate: () -> Unit, onScan: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Artivive Lite", style = MaterialTheme.typography.headlineMedium)
        Text("AR واقعی با تشخیص تصویر")
        Spacer(Modifier.height(20.dp))
        Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text("📷 اسکن AR") }
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("＋ ساخت پروژه") }
        Spacer(Modifier.height(20.dp))
        Text("پروژه‌ها: \${projects.size}", style = MaterialTheme.typography.titleLarge)
        LazyColumn { items(projects) { p -> ListItem({ Text(p.name) }, supportingContent = { Text("Target + \${p.contentType}") }) } }
    }
}

@Composable
private fun CreateProject(store: ProjectStore, onDone: () -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf<android.net.Uri?>(null) }
    var content by remember { mutableStateOf<android.net.Uri?>(null) }
    var contentType by remember { mutableStateOf("video") }
    val targetPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { target = it }
    val contentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { content = it }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("ساخت پروژه", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(name, { name = it }, label = { Text("نام پروژه") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Button(onClick = { targetPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text(if (target == null) "انتخاب Target از گالری" else "✓ Target انتخاب شد") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(contentType == "video", { contentType = "video" }, label = { Text("ویدئو") })
            FilterChip(contentType == "image", { contentType = "image" }, label = { Text("تصویر") })
        }
        Button(onClick = { contentPicker.launch(if (contentType == "video") "video/*" else "image/*") }, modifier = Modifier.fillMaxWidth()) { Text(if (content == null) "انتخاب محتوا از گالری" else "✓ محتوا انتخاب شد") }
        Spacer(Modifier.height(20.dp))
        Button(enabled = name.isNotBlank() && target != null && content != null, onClick = {
            val id = UUID.randomUUID().toString()
            val targetPath = store.copyIntoProject(id, target!!, "target")
            val contentPath = store.copyIntoProject(id, content!!, "content")
            val list = store.load()
            list.add(Project(id, name, targetPath, 0.30f, contentPath, contentType))
            store.save(list); onDone()
        }, modifier = Modifier.fillMaxWidth()) { Text("ساخت پروژه") }
        TextButton(onClick = onCancel) { Text("لغو") }
    }
}

@Composable
private fun ScannerScreen(projects: List<Project>, onBack: () -> Unit) {
    var detected by remember { mutableStateOf<List<AugmentedImage>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    if (projects.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("هنوز پروژه‌ای ساخته نشده"); TextButton(onClick = onBack) { Text("← برگشت") }
        }; return
    }
    Box(Modifier.fillMaxSize()) {
        ARSceneView(
            modifier = Modifier.fillMaxSize(),
            sessionConfiguration = { session, config ->
                try {
                    val db = AugmentedImageDatabase(session)
                    projects.forEach { project ->
                        BitmapFactory.decodeFile(project.targetPath)?.let { bitmap ->
                            db.addImage(project.id, bitmap, project.targetWidthMeters); bitmap.recycle()
                        }
                    }
                    config.augmentedImageDatabase = db
                    config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                } catch (t: Throwable) { error = t.message ?: "خطا در AR" }
            },
            onSessionUpdated = { _, frame ->
                detected = frame.getUpdatedTrackables(AugmentedImage::class.java).filter { it.trackingState == TrackingState.TRACKING }.toList()
            }
        ) {
            detected.forEach { image ->
                val project = projects.firstOrNull { it.id == image.name } ?: return@forEach
                if (project.contentType == "video") {
                    val player = remember(project.id) {
                        MediaPlayer().apply { setDataSource(project.contentPath); isLooping = true; prepare(); start() }
                    }
                    DisposableEffect(player) { onDispose { runCatching { player.stop() }; player.release() } }
                    AugmentedImageNode(augmentedImage = image, applyImageScale = true) { VideoNode(player = player, size = Size(x = 1f, y = 1f)) }
                } else {
                    val bitmap = remember(project.id) { BitmapFactory.decodeFile(project.contentPath) }
                    if (bitmap != null) AugmentedImageNode(augmentedImage = image, applyImageScale = true) {
                        ImageNode(materialLoader = materialLoader, bitmap = bitmap, size = Size(x = 1f, y = 1f))
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("ARCore • \${projects.size} Target")
            if (detected.isNotEmpty()) Text("✓ Target شناسایی شد")
            error?.let { Text("خطا: \$it", color = MaterialTheme.colorScheme.error) }
        }
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)) { Text("← برگشت") }
    }
}
