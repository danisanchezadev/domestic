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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
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
                title = { Text("Catalogo de platos") },
                actions = {
                    androidx.compose.material3.TextButton(onClick = viewModel::openNew) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("Nuevo plato")
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.padding(padding).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::updateQuery,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Buscar plato") },
                    singleLine = true,
                )
            }
            if (state.dishes.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("Todavia no hay platos guardados.") }
            state.dishes.groupBy { it.category.ifBlank { "Sin categoria" } }
                .toSortedMap(String.CASE_INSENSITIVE_ORDER)
                .forEach { (category, categoryDishes) ->
                    item(span = { GridItemSpan(maxLineSpan) }) { Text(category, style = MaterialTheme.typography.titleLarge) }
                    gridItems(categoryDishes.sortedBy { it.name.lowercase() }, key = { it.id }) { dish ->
                        DishCard(
                            dish,
                            onOpen = { viewModel.openDetail(dish) },
                            onEdit = { viewModel.edit(dish) },
                            onDelete = { dishToDelete = dish },
                        )
                    }
                }
        }
    }
    if (state.editorOpen) DishEditorSimple(state.draft, viewModel)
    state.detail?.let { detail ->
        DishDetailDialog(
            detail = detail,
            onDismiss = viewModel::closeDetail,
            onEdit = { viewModel.edit(detail.dish); viewModel.closeDetail() },
            onDelete = { dishToDelete = detail.dish; viewModel.closeDetail() },
        )
    }
    dishToDelete?.let { dish ->
        AlertDialog(
            onDismissRequest = { dishToDelete = null },
            title = { Text("Eliminar plato") },
            text = { Text("El plato se eliminara del catalogo.") },
            confirmButton = { TextButton(onClick = { viewModel.delete(dish); dishToDelete = null }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { dishToDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun DishCard(
    dish: Dish,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val imageModifier = Modifier.fillMaxWidth().size(90.dp)
            val image = dish.imageBase64?.let(::decodeImage)
            if (image != null) {
                Image(image.asImageBitmap(), "Imagen de ${dish.name}", imageModifier, contentScale = ContentScale.Crop)
            } else {
                androidx.compose.foundation.layout.Box(
                    modifier = imageModifier,
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Restaurant, contentDescription = "Sin imagen", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(dish.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Editar", tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun DishDetailDialog(
    detail: DishDetail,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(detail.dish.name) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                detail.dish.imageBase64?.let { value ->
                    item {
                        decodeImage(value)?.let { bitmap ->
                            Image(bitmap.asImageBitmap(), detail.dish.name, Modifier.fillMaxWidth(), contentScale = ContentScale.Crop)
                        }
                    }
                }
                if (detail.dish.category.isNotBlank()) item { Text("Categoría: ${detail.dish.category}") }
                if (detail.dish.recipe.isNotBlank()) item { Text(detail.dish.recipe) }
                if (detail.dish.recipeSteps.isNotBlank()) {
                    item { Text("Pasos", style = MaterialTheme.typography.titleSmall) }
                    item { Text(detail.dish.recipeSteps) }
                }
                if (detail.dish.preparationMinutes != null || detail.dish.servings != null) {
                    item {
                        Text(
                            listOfNotNull(
                                detail.dish.preparationMinutes?.let { "${it} min" },
                                detail.dish.servings?.let { "${it} raciones" },
                            ).joinToString("  | "),
                        )
                    }
                }
                if (detail.dish.notes.isNotBlank()) item { Text(detail.dish.notes) }
                if (detail.ingredients.isNotEmpty()) {
                    item { Text("Ingredientes", style = MaterialTheme.typography.titleSmall) }
                    items(detail.ingredients) { ingredient -> Text("- ${ingredientLabel(ingredient)}") }
                }
                detail.variants.forEach { variant ->
                    item { Text("Variante: ${variant.name}", style = MaterialTheme.typography.titleSmall) }
                    if (variant.recipe.isNotBlank()) item { Text(variant.recipe) }
                    variant.ingredients.forEach { ingredient ->
                        item { Text("- ${ingredientLabel(ingredient)}") }
                    }
                }
                item { Text("Asignado ${detail.usageStats.assignmentCount} veces", style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = onEdit) { Text("Editar") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Eliminar") }
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        },
    )
}

private fun ingredientLabel(ingredient: DraftIngredient): String =
    listOf(ingredient.name, ingredient.quantity, ingredient.unit).filter { it.isNotBlank() }.joinToString(" ")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DishEditorSimple(draft: DishDraft, viewModel: DishesViewModel) {
    val context = LocalContext.current
    var sourceDialogOpen by rememberSaveable { mutableStateOf(false) }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cropDialogOpen by rememberSaveable { mutableStateOf(false) }
    fun prepareImage(input: java.io.InputStream) {
        decodeOriented(input)?.let {
            pendingBitmap = it
            cropDialogOpen = true
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.openInputStream(uri)?.use(::prepareImage)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        if (saved && uri != null) context.contentResolver.openInputStream(uri)?.use(::prepareImage)
        uri?.path?.let { File(it).delete() }
        cameraUri = null
    }
    fun openCamera() {
        val file = File.createTempFile("dish_", ".jpg", context.cacheDir)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraUri = uri
        camera.launch(uri)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openCamera()
    }
    AlertDialog(
        onDismissRequest = viewModel::closeEditor,
        title = { Text(if (draft.dishId == null) "Nuevo plato" else "Editar plato") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = viewModel::updateName,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nombre del plato") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = draft.category,
                    onValueChange = viewModel::updateCategory,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Categoría") },
                    singleLine = true,
                )
                Text("Foto (opcional)", style = MaterialTheme.typography.titleMedium)
                draft.imageBase64?.let { value ->
                    decodeImage(value)?.let { bitmap ->
                        Image(bitmap.asImageBitmap(), "Vista previa", Modifier.fillMaxWidth().size(160.dp), contentScale = ContentScale.Crop)
                    }
                    TextButton(onClick = { viewModel.updateImage(null) }) { Text("Quitar foto") }
                }
                Button(onClick = { sourceDialogOpen = true }) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Text("Elegir foto")
                }
                Text("Receta (opcional)", style = MaterialTheme.typography.titleMedium)
                Text("Ingredientes", style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    var ingredientName by rememberSaveable { mutableStateOf("") }
                    var ingredientQuantity by rememberSaveable { mutableStateOf("") }
                    var ingredientUnit by rememberSaveable { mutableStateOf("") }
                    OutlinedTextField(ingredientName, { ingredientName = it }, Modifier.fillMaxWidth(), label = { Text("Ingrediente") }, singleLine = true)
                    OutlinedTextField(ingredientQuantity, { ingredientQuantity = it }, Modifier.fillMaxWidth(), label = { Text("Cantidad") }, singleLine = true)
                    Text("Unidad", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("g", "ml", "porción", "gotas").forEach { unit ->
                            FilterChip(
                                selected = ingredientUnit == unit,
                                onClick = { ingredientUnit = unit },
                                label = { Text(unit) },
                            )
                        }
                    }
                    Button(onClick = {
                        viewModel.addIngredient(ingredientName, ingredientQuantity, ingredientUnit)
                        ingredientName = ""
                        ingredientQuantity = ""
                        ingredientUnit = ""
                    }, enabled = ingredientName.isNotBlank()) { Text("Añadir ingrediente") }
                }
                draft.ingredients.forEachIndexed { index, ingredient ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ingredientLabel(ingredient), Modifier.weight(1f))
                        IconButton(onClick = { viewModel.removeIngredient(index) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar ingrediente")
                        }
                    }
                }
                Text("Pasos de preparacion (opcional)", style = MaterialTheme.typography.titleMedium)
                var stepText by rememberSaveable(draft.dishId) { mutableStateOf("") }
                draft.steps.forEachIndexed { index, step ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Paso ${index + 1}: $step", Modifier.weight(1f))
                        IconButton(onClick = { viewModel.removeStep(index) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar paso")
                        }
                    }
                }
                OutlinedTextField(
                    value = stepText,
                    onValueChange = { stepText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nuevo paso") },
                )
                Button(
                    onClick = { viewModel.addStep(stepText); stepText = "" },
                    enabled = stepText.isNotBlank(),
                ) { Text("Agregar paso") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tiempo aproximado:")
                    OutlinedTextField(
                        value = draft.preparationMinutes,
                        onValueChange = { value -> viewModel.updatePreparationMinutes(value.filter { it.isDigit() }) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Minutos") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Personas: ${draft.servings.toIntOrNull()?.coerceAtLeast(1) ?: 2}", Modifier.weight(1f))
                    IconButton(onClick = {
                        val people = (draft.servings.toIntOrNull() ?: 2).coerceAtLeast(1) - 1
                        viewModel.updateServings(people.coerceAtLeast(1).toString())
                    }) {
                        Icon(Icons.Filled.Remove, contentDescription = "Quitar persona")
                    }
                    IconButton(onClick = {
                        val people = (draft.servings.toIntOrNull() ?: 2).coerceAtLeast(1) + 1
                        viewModel.updateServings(people.toString())
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "Añadir persona")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = viewModel::save, enabled = draft.name.isNotBlank() && draft.category.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = viewModel::closeEditor) { Text("Cancelar") } },
    )
    if (sourceDialogOpen) {
        AlertDialog(
            onDismissRequest = { sourceDialogOpen = false },
            title = { Text("Añadir foto") },
            text = { Text("Elige el origen de la imagen") },
            confirmButton = {
                TextButton(onClick = { sourceDialogOpen = false; gallery.launch("image/*") }) { Text("Galería") }
            },
            dismissButton = {
                TextButton(onClick = {
                    sourceDialogOpen = false
                    if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera()
                    else permission.launch(Manifest.permission.CAMERA)
                }) { Text("Cámara") }
            },
        )
    }
    if (cropDialogOpen) {
        pendingBitmap?.let { bitmap ->
            CropImageDialog(
                bitmap = bitmap,
                onDismiss = {
                    cropDialogOpen = false
                    pendingBitmap = null
                },
                onApply = { cropped ->
                    viewModel.updateImage(cropped.toBase64())
                    cropDialogOpen = false
                    pendingBitmap = null
                },
            )
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
                }, enabled = variantName.isNotBlank()) { Text("Añadir variante") }
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
                TextButton(onClick = { attachmentOptions = false; gallery.launch("image/*") }) { Text("Galería") }
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
                }) { Text("Cámara") }
            },
        )
    }
}

@Composable
private fun CropImageDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit,
    onApply: (Bitmap) -> Unit,
) {
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajustar foto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Mueve la imagen y usa dos dedos para cambiar el zoom.")
                Canvas(
                    modifier = Modifier
                        .size(280.dp)
                        .clipToBounds()
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(bitmap) {
                            detectTransformGestures { _, panChange, zoomChange, _ ->
                                val nextZoom = (zoom * zoomChange).coerceIn(1f, 5f)
                                val viewSize = minOf(canvasSize.width, canvasSize.height).toFloat()
                                val baseScale = if (viewSize > 0f) {
                                    maxOf(viewSize / bitmap.width, viewSize / bitmap.height)
                                } else {
                                    1f
                                }
                                val imageScale = baseScale * nextZoom
                                val maxPanX = ((bitmap.width * imageScale - viewSize) / 2f).coerceAtLeast(0f)
                                val maxPanY = ((bitmap.height * imageScale - viewSize) / 2f).coerceAtLeast(0f)
                                pan = Offset(
                                    (pan.x + panChange.x).coerceIn(-maxPanX, maxPanX),
                                    (pan.y + panChange.y).coerceIn(-maxPanY, maxPanY),
                                )
                                zoom = nextZoom
                            }
                        },
                ) {
                    val region = cropRegion(bitmap, IntSize(size.width.toInt(), size.height.toInt()), zoom, pan)
                    drawImage(
                        image = bitmap.asImageBitmap(),
                        srcOffset = IntOffset(region.left, region.top),
                        srcSize = IntSize(region.width, region.height),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    )
                    drawRect(
                        color = Color.White,
                        style = Stroke(width = 4.dp.toPx()),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(cropBitmap(bitmap, canvasSize, zoom, pan)) },
                enabled = canvasSize != IntSize.Zero,
            ) { Text("Aplicar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun cropBitmap(bitmap: Bitmap, canvasSize: IntSize, zoom: Float, pan: Offset): Bitmap {
    val region = cropRegion(bitmap, canvasSize, zoom, pan)
    return Bitmap.createBitmap(bitmap, region.left, region.top, region.width, region.height)
}

private data class CropRegion(val left: Int, val top: Int, val width: Int, val height: Int)

private fun cropRegion(bitmap: Bitmap, canvasSize: IntSize, zoom: Float, pan: Offset): CropRegion {
    val viewSize = minOf(canvasSize.width, canvasSize.height).toFloat().coerceAtLeast(1f)
    val baseScale = maxOf(viewSize / bitmap.width, viewSize / bitmap.height)
    val imageScale = baseScale * zoom
    val sourceSize = (viewSize / imageScale).toInt().coerceIn(1, minOf(bitmap.width, bitmap.height))
    val centerX = bitmap.width / 2f - pan.x / imageScale
    val centerY = bitmap.height / 2f - pan.y / imageScale
    val left = (centerX - sourceSize / 2f).toInt().coerceIn(0, bitmap.width - sourceSize)
    val top = (centerY - sourceSize / 2f).toInt().coerceIn(0, bitmap.height - sourceSize)
    return CropRegion(left, top, sourceSize, sourceSize)
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
