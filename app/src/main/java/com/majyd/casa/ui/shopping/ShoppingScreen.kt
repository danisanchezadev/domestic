package com.majyd.casa.ui.shopping

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.majyd.casa.data.model.ShoppingProduct
import com.majyd.casa.data.model.ShoppingSuggestion
import com.majyd.casa.util.Normalizer
import androidx.core.content.FileProvider
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream

private enum class ShoppingMode { CHOICE, CREATE, VIEW }

private val shoppingModeSaver = Saver<ShoppingMode, String>(
    save = { it.name },
    restore = { ShoppingMode.valueOf(it) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(
    onBack: () -> Unit,
    viewModel: ShoppingViewModel = hiltViewModel(),
) {
    var mode by rememberSaveable(stateSaver = shoppingModeSaver) { mutableStateOf(ShoppingMode.CHOICE) }
    var showAttachmentOptions by remember { mutableStateOf(false) }
    var editingCatalogItem by remember { mutableStateOf<ShoppingSuggestion?>(null) }
    var editingCatalogName by remember { mutableStateOf("") }
    var cameraOutputUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val productName = state.draft.name
    val imageBase64 = state.draft.imageBase64
    val products = (state.pending + state.purchased).sortedBy { it.name.lowercase() }
    val context = LocalContext.current
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.openInputStream(uri)?.use { input ->
            decodeOrientedBitmap(input)?.let { viewModel.updateDraftImage(it.toBase64()) }
        }
    }
    fun createCameraOutputUri(): Uri {
        val file = File.createTempFile("shopping_", ".jpg", context.cacheDir)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraOutputUri
        if (saved && uri != null) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                decodeOrientedBitmap(input)?.let { viewModel.updateDraftImage(it.toBase64()) }
            }
        }
        uri?.path?.let { File(it).delete() }
        cameraOutputUri = null
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = createCameraOutputUri()
            cameraOutputUri = uri
            cameraLauncher.launch(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Volver") }
                },
                title = { Text("Lista de la compra") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (mode) {
                        ShoppingMode.CHOICE -> {
                            Text("Compra", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            ActionButton(onClick = { mode = ShoppingMode.CREATE }) { Text("Crear lista") }
                            ActionButton(onClick = { mode = ShoppingMode.VIEW }) { Text("Ver lista") }
                        }
                        ShoppingMode.CREATE -> {
                            Button(onClick = { mode = ShoppingMode.CHOICE }, modifier = Modifier.fillMaxWidth()) { Text("Volver a compra") }
                            Text("Crear lista", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedTextField(
                                    value = productName,
                                    onValueChange = viewModel::updateDraftName,
                                    modifier = Modifier.weight(1f),
                                    label = { Text("Producto") },
                                    singleLine = true,
                                )
                                IconButton(onClick = { showAttachmentOptions = true }) {
                                    Icon(Icons.Filled.AddAPhoto, contentDescription = "Adjuntar imagen")
                                }
                                Button(
                                    onClick = {
                                        viewModel.addProduct(productName, imageBase64)
                                        viewModel.clearDraft()
                                    },
                                    enabled = productName.isNotBlank(),
                                ) { Text("Añadir") }
                            }
                            imageBase64?.let { encoded ->
                                decodeBase64Image(encoded)?.let { bitmap ->
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Vista previa del producto",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat()),
                                        contentScale = ContentScale.Fit,
                                    )
                                }
                            }
                            ActionButton(onClick = { mode = ShoppingMode.VIEW }) { Text("Ver lista") }
                            Text(
                                "Productos habituales",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (state.suggestions.isEmpty()) {
                                Text("Todavia no hay productos habituales.")
                            } else {
                                state.suggestions.forEach { suggestion ->
                                    val alreadyInList = products.any { product ->
                                        !product.deleted &&
                                            Normalizer.normalizeText(product.name) == suggestion.normalizedName
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Button(
                                            onClick = { viewModel.addSuggestionToList(suggestion) },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (alreadyInList) {
                                                    Color(0xFFB7D9B8)
                                                } else {
                                                    MaterialTheme.colorScheme.primary
                                                },
                                                contentColor = if (alreadyInList) {
                                                    Color(0xFF234D2A)
                                                } else {
                                                    MaterialTheme.colorScheme.onPrimary
                                                },
                                            ),
                                        ) {
                                            Text(suggestion.displayName)
                                        }
                                        IconButton(
                                            onClick = {
                                                editingCatalogItem = suggestion
                                                editingCatalogName = suggestion.displayName
                                            },
                                            colors = IconButtonDefaults.iconButtonColors(
                                                contentColor = MaterialTheme.colorScheme.primary,
                                            ),
                                        ) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Editar habitual")
                                        }
                                        CompactDeleteButton(
                                            contentDescription = "Eliminar habitual",
                                            onClick = { viewModel.deleteCatalogItem(suggestion.displayName) },
                                        )
                                    }
                                }
                            }
                        }
                        ShoppingMode.VIEW -> {
                            Button(onClick = { mode = ShoppingMode.CHOICE }, modifier = Modifier.fillMaxWidth()) { Text("Volver a compra") }
                            Text("Ver lista", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            if (products.isEmpty()) {
                                Text("La lista esta vacia.")
                            } else {
                                products.forEach { product ->
                                    ShoppingItem(product = product, onDelete = { viewModel.deleteProduct(product) })
                                }
                            }
                        }
                    }
                }
            }
            state.message?.let { message -> item { Text(message, style = MaterialTheme.typography.bodyMedium) } }
        }
    }
    if (showAttachmentOptions) {
        AlertDialog(
            onDismissRequest = { showAttachmentOptions = false },
            title = { Text("Adjuntar imagen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        showAttachmentOptions = false
                        if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            val uri = createCameraOutputUri()
                            cameraOutputUri = uri
                            cameraLauncher.launch(uri)
                        } else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }, modifier = Modifier.fillMaxWidth()) { Text("Abrir camara") }
                    Button(onClick = {
                        showAttachmentOptions = false
                        galleryLauncher.launch("image/*")
                    }, modifier = Modifier.fillMaxWidth()) { Text("Elegir de la galeria") }
                }
            },
            confirmButton = {},
        )
    }
    editingCatalogItem?.let { suggestion ->
        AlertDialog(
            onDismissRequest = { editingCatalogItem = null },
            title = { Text("Editar producto habitual") },
            text = {
                OutlinedTextField(
                    value = editingCatalogName,
                    onValueChange = { editingCatalogName = it },
                    label = { Text("Producto") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateCatalogItem(suggestion.normalizedName, editingCatalogName)
                        editingCatalogItem = null
                    },
                    enabled = editingCatalogName.isNotBlank(),
                ) { Text("Guardar") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            viewModel.deleteCatalogItem(suggestion.displayName)
                            editingCatalogItem = null
                        },
                    ) { Text("Eliminar", color = Color(0xFFD32F2F)) }
                    TextButton(onClick = { editingCatalogItem = null }) { Text("Cancelar") }
                }
            },
        )
    }
}

@Composable
private fun ActionButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(), content = content)
}

@Composable
private fun ShoppingItem(product: ShoppingProduct, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(product.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
                if (product.purchased) Text("Comprado", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary)
            }
            CompactDeleteButton(onClick = onDelete)
        }
        product.imageBase64?.let { encoded ->
            decodeBase64Image(encoded)?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Imagen de ${product.name}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                        .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat()),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

@Composable
private fun CompactDeleteButton(
    contentDescription: String = "Eliminar",
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Delete,
            contentDescription = contentDescription,
            tint = Color(0xFFD32F2F),
            modifier = Modifier.size(24.dp),
        )
    }
}

private fun Bitmap.toBase64(): String {
    val maxDimension = maxOf(width, height)
    val bitmap = if (maxDimension > MAX_IMAGE_DIMENSION) {
        val scale = MAX_IMAGE_DIMENSION.toFloat() / maxDimension.toFloat()
        Bitmap.createScaledBitmap(
            this,
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        this
    }
    val output = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 75, output)
    if (bitmap !== this) bitmap.recycle()
    return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
}

private const val MAX_IMAGE_DIMENSION = 1600

private fun decodeOrientedBitmap(input: java.io.InputStream): Bitmap? = runCatching {
    val bytes = input.readBytes()
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val orientation = ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL,
    )
    val matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
        }
    }
    if (matrix.isIdentity) bitmap
    else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}.getOrNull()

private fun decodeBase64Image(value: String): Bitmap? = runCatching {
    val bytes = Base64.decode(value, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()
