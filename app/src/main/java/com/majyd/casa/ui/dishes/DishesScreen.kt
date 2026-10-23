package com.majyd.casa.ui.dishes

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.majyd.casa.data.model.Dish
import com.majyd.casa.data.model.DishMealType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DishesScreen(
    onBack: () -> Unit,
    viewModel: DishesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var dishToDelete by remember { mutableStateOf<Dish?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Volver") } },
                title = { Text("Mis platos") },
                actions = { IconButton(onClick = viewModel::openNew) { Icon(Icons.Filled.Add, "Nuevo plato") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::updateQuery,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Buscar plato") },
                    singleLine = true,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(state.mealFilter == null, { viewModel.updateMealFilter(null) }, label = { Text("Todos") })
                    DishMealType.entries.forEach { type ->
                        FilterChip(state.mealFilter == type, { viewModel.updateMealFilter(type) }, label = { Text(type.label()) })
                    }
                }
            }
            if (state.dishes.isEmpty()) item { Text("Todavia no hay platos guardados.") }
            items(state.dishes, key = { it.id }) { dish ->
                DishCard(dish, state.usageStats[dish.id], onEdit = { viewModel.edit(dish) }, onDelete = { dishToDelete = dish })
            }
        }
    }
    if (state.editorOpen) DishEditor(state.draft, viewModel)
    dishToDelete?.let { dish ->
        AlertDialog(
            onDismissRequest = { dishToDelete = null },
            title = { Text("Eliminar plato") },
            text = { Text("Se ocultara el plato del catalogo, sin borrar asignaciones historicas.") },
            confirmButton = { TextButton(onClick = { viewModel.delete(dish); dishToDelete = null }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { dishToDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun DishCard(dish: Dish, usageStats: com.majyd.casa.data.model.DishUsageStats?, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            dish.imageBase64?.let { value ->
                decodeImage(value)?.let { bitmap ->
                    Image(bitmap.asImageBitmap(), "Imagen de ${dish.name}", Modifier.fillMaxWidth(), contentScale = ContentScale.Crop)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(dish.name, style = MaterialTheme.typography.titleMedium)
                    Text(dish.mealType.label(), style = MaterialTheme.typography.bodySmall)
                    usageStats?.let { stats ->
                        Text("Asignado ${stats.assignmentCount} veces", style = MaterialTheme.typography.bodySmall)
                    }
                }
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Editar") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Eliminar") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DishEditor(draft: DishDraft, viewModel: DishesViewModel) {
    val context = LocalContext.current
    var attachmentOptions by rememberSaveable { mutableStateOf(false) }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var ingredientName by rememberSaveable { mutableStateOf("") }
    var ingredientQuantity by rememberSaveable { mutableStateOf("") }
    var ingredientUnit by rememberSaveable { mutableStateOf("") }
    var variantName by rememberSaveable { mutableStateOf("") }
    var variantRecipe by rememberSaveable { mutableStateOf("") }
    var variantNotes by rememberSaveable { mutableStateOf("") }
    fun encode(input: java.io.InputStream) = decodeOriented(input)?.toBase64()
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.openInputStream(uri)?.use { encode(it) }?.let(viewModel::updateImage)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        if (saved && uri != null) context.contentResolver.openInputStream(uri)?.use { encode(it) }?.let(viewModel::updateImage)
        uri?.path?.let { File(it).delete() }
        cameraUri = null
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val file = File.createTempFile("dish_", ".jpg", context.cacheDir)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraUri = uri
            camera.launch(uri)
        }
    }
    AlertDialog(
        onDismissRequest = viewModel::closeEditor,
        title = { Text(if (draft.dishId == null) "Nuevo plato" else "Editar plato") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(draft.name, viewModel::updateName, label = { Text("Nombre") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DishMealType.entries.forEach { type ->
                        FilterChip(type == draft.mealType, { viewModel.updateMealType(type) }, label = { Text(type.label()) })
                    }
                }
                OutlinedTextField(draft.recipe, viewModel::updateRecipe, label = { Text("Receta") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(draft.preparationMinutes, viewModel::updatePreparationMinutes, Modifier.weight(1f), label = { Text("Minutos") }, singleLine = true)
                    OutlinedTextField(draft.servings, viewModel::updateServings, Modifier.weight(1f), label = { Text("Raciones") }, singleLine = true)
                }
                OutlinedTextField(draft.notes, viewModel::updateNotes, label = { Text("Notas") })
                Text("Ingredientes", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(ingredientName, { ingredientName = it }, Modifier.weight(1.3f), label = { Text("Ingrediente") }, singleLine = true)
                    OutlinedTextField(ingredientQuantity, { ingredientQuantity = it }, Modifier.weight(0.7f), label = { Text("Cant.") }, singleLine = true)
                    OutlinedTextField(ingredientUnit, { ingredientUnit = it }, Modifier.weight(0.7f), label = { Text("Unidad") }, singleLine = true)
                    IconButton(onClick = {
                        viewModel.addIngredient(ingredientName, ingredientQuantity, ingredientUnit)
                        ingredientName = ""
                        ingredientQuantity = ""
                        ingredientUnit = ""
                    }) { Icon(Icons.Filled.Add, "Añadir ingrediente") }
                }
                draft.ingredients.forEachIndexed { index, ingredient ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(listOf(ingredient.name, ingredient.quantity, ingredient.unit).filter { it.isNotBlank() }.joinToString(" "), Modifier.weight(1f))
                        IconButton(onClick = { viewModel.removeIngredient(index) }) { Icon(Icons.Filled.Delete, "Eliminar ingrediente") }
                    }
                }
                Text("Variantes", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(variantName, { variantName = it }, label = { Text("Nombre de variante") }, singleLine = true)
                OutlinedTextField(variantRecipe, { variantRecipe = it }, label = { Text("Receta de la variante") })
                OutlinedTextField(variantNotes, { variantNotes = it }, label = { Text("Notas de la variante") })
                Button(onClick = {
                    viewModel.addVariant(variantName, variantRecipe, variantNotes)
                    variantName = ""
                    variantRecipe = ""
                    variantNotes = ""
                }, enabled = variantName.isNotBlank()) { Text("Anadir variante") }
                draft.variants.forEachIndexed { index, variant ->
                    VariantEditor(
                        variant = variant,
                        onRemove = { viewModel.removeVariant(index) },
                        onAddIngredient = { name, quantity, unit -> viewModel.addVariantIngredient(index, name, quantity, unit) },
                        onRemoveIngredient = { ingredientIndex -> viewModel.removeVariantIngredient(index, ingredientIndex) },
                    )
                }
                draft.imageBase64?.let { value ->
                    decodeImage(value)?.let { bitmap -> Image(bitmap.asImageBitmap(), "Vista previa", Modifier.fillMaxWidth().size(140.dp), contentScale = ContentScale.Crop) }
                    TextButton(onClick = { viewModel.updateImage(null) }) { Text("Quitar foto") }
                }
                Button(onClick = { attachmentOptions = true }) { Icon(Icons.Filled.CameraAlt, null); Text("Foto") }
            }
        },
        confirmButton = { TextButton(onClick = viewModel::save, enabled = draft.name.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = viewModel::closeEditor) { Text("Cancelar") } },
    )
    if (attachmentOptions) {
        AlertDialog(
            onDismissRequest = { attachmentOptions = false },
            title = { Text("Imagen del plato") },
            text = { Text("Elige el origen de la imagen") },
            confirmButton = {
                TextButton(onClick = { attachmentOptions = false; gallery.launch("image/*") }) { Text("Galeria") }
            },
            dismissButton = {
                TextButton(onClick = {
                    attachmentOptions = false
                    if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        val file = File.createTempFile("dish_", ".jpg", context.cacheDir)
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        cameraUri = uri
                        camera.launch(uri)
                    } else permission.launch(Manifest.permission.CAMERA)
                }) { Text("Camara") }
            },
        )
    }
}

@Composable
private fun VariantEditor(
    variant: DraftVariant,
    onRemove: () -> Unit,
    onAddIngredient: (String, String, String) -> Unit,
    onRemoveIngredient: (Int) -> Unit,
) {
    var ingredientName by rememberSaveable(variant.id, variant.name) { mutableStateOf("") }
    var ingredientQuantity by rememberSaveable(variant.id, variant.name) { mutableStateOf("") }
    var ingredientUnit by rememberSaveable(variant.id, variant.name) { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(variant.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, "Eliminar variante") }
            }
            Text("Ingredientes de la variante", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(ingredientName, { ingredientName = it }, Modifier.weight(1.3f), label = { Text("Ingrediente") }, singleLine = true)
                OutlinedTextField(ingredientQuantity, { ingredientQuantity = it }, Modifier.weight(0.7f), label = { Text("Cant.") }, singleLine = true)
                OutlinedTextField(ingredientUnit, { ingredientUnit = it }, Modifier.weight(0.7f), label = { Text("Unidad") }, singleLine = true)
                IconButton(onClick = {
                    onAddIngredient(ingredientName, ingredientQuantity, ingredientUnit)
                    ingredientName = ""
                    ingredientQuantity = ""
                    ingredientUnit = ""
                }) { Icon(Icons.Filled.Add, "Añadir ingrediente de variante") }
            }
            variant.ingredients.forEachIndexed { index, ingredient ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(listOf(ingredient.name, ingredient.quantity, ingredient.unit).filter { it.isNotBlank() }.joinToString(" "), Modifier.weight(1f))
                    IconButton(onClick = { onRemoveIngredient(index) }) { Icon(Icons.Filled.Delete, "Eliminar ingrediente") }
                }
            }
        }
    }
}

private fun DishMealType.label() = when (this) {
    DishMealType.LUNCH -> "Comida"
    DishMealType.DINNER -> "Cena"
    DishMealType.BOTH -> "Comida y cena"
}

private fun decodeImage(value: String): Bitmap? = runCatching { BitmapFactory.decodeByteArray(Base64.decode(value, Base64.DEFAULT), 0, Base64.decode(value, Base64.DEFAULT).size) }.getOrNull()

private fun decodeOriented(input: java.io.InputStream): Bitmap? = runCatching {
    val bytes = input.readBytes()
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val orientation = ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    val matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
        }
    }
    if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}.getOrNull()

private fun Bitmap.toBase64(): String {
    val maxDimension = maxOf(width, height)
    val bitmap = if (maxDimension > 1600) {
        val scale = 1600f / maxDimension
        Bitmap.createScaledBitmap(this, (width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1), true)
    } else this
    val output = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 75, output)
    if (bitmap !== this) bitmap.recycle()
    return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
}
