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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.majyd.casa.data.model.Dish
import com.majyd.casa.data.model.DishMealType
import com.majyd.casa.data.model.DishCollection
import com.majyd.casa.ui.components.RecipeCard
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@Composable
private fun EditorSectionLabel(
    text: String,
    large: Boolean = false,
    filled: Boolean = true,
) {
    val hasBackground = large || filled
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (hasBackground) {
                    Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.92f), RoundedCornerShape(10.dp))
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 10.dp, vertical = if (large) 8.dp else 5.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text,
            color = if (hasBackground) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DishesScreen(
    onBack: () -> Unit,
    collection: DishCollection = DishCollection.MENU,
    viewModel: DishesViewModel = hiltViewModel(),
) {
    LaunchedEffect(collection) { viewModel.setCollection(collection) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var dishToDelete by remember { mutableStateOf<Dish?>(null) }
    var categoriesOpen by remember { mutableStateOf(false) }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    val visibleDishes = state.dishes.filter { dish ->
        selectedCategory == null || dish.category.split(',').any { it.trim().equals(selectedCategory, ignoreCase = true) }
    }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Volver") } },
                title = { Text(if (collection == DishCollection.RECIPE_BOOK) "Recetario" else "Catalogo de platos") },
                actions = {
                    androidx.compose.material3.TextButton(onClick = viewModel::openNew) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(if (collection == DishCollection.RECIPE_BOOK) "Nueva receta" else "Nuevo plato")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(com.majyd.casa.R.drawable.kitchen_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
            if (collection == DishCollection.RECIPE_BOOK) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Button(
                        onClick = { categoriesOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(selectedCategory?.let { "Categoría: $it" } ?: "Categorías") }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::updateQuery,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Buscar en tus platos") },
                    placeholder = { Text("Nombre del plato") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    shape = RoundedCornerShape(18.dp),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFFFF9EE),
                        unfocusedContainerColor = Color(0xFFFFF9EE),
                        focusedBorderColor = Color(0xFF557A5D),
                        unfocusedBorderColor = Color(0xFFC99C76),
                    ),
                    singleLine = true,
                )
            }
            if (visibleDishes.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    if (state.dishes.isEmpty()) "Todavía no hay platos guardados." else "No hay platos en esta categoría.",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
            visibleDishes.flatMap { dish ->
                dish.category.split(',').map { it.trim() }.filter { it.isNotBlank() }.ifEmpty { listOf("Sin categoria") }.map { category -> category to dish }
            }.groupBy({ it.first }, { it.second })
                .toSortedMap(String.CASE_INSENSITIVE_ORDER)
                .forEach { (category, categoryDishes) ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(category, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
                    }
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
    }
    if (state.editorOpen) DishEditorSimple(state.draft, state.categories, collection, viewModel)
    state.detail?.let { detail ->
        DishDetailScreen(
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
    if (categoriesOpen && collection == DishCollection.RECIPE_BOOK) {
        RecipeCategoryDialog(
            categories = state.categories,
            onSave = viewModel::saveCategory,
            onRename = viewModel::renameCategory,
            onDelete = viewModel::deleteCategory,
            onSelect = { selectedCategory = it; categoriesOpen = false },
            onDismiss = { categoriesOpen = false },
        )
    }
}

@Composable
private fun RecipeCategoryDialog(
    categories: List<com.majyd.casa.data.model.RecipeCategory>,
    onSave: (String) -> Unit,
    onRename: (com.majyd.casa.data.model.RecipeCategory, String) -> Unit,
    onDelete: (com.majyd.casa.data.model.RecipeCategory) -> Unit,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<com.majyd.casa.data.model.RecipeCategory?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categorias del recetario") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Mostrar todas")
                }
                categories.forEach { category ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            category.name,
                            Modifier.weight(1f).clickable { onSelect(category.name) },
                        )
                        TextButton(onClick = { editing = category; name = category.name }) { Text("Editar") }
                        IconButton(onClick = { onDelete(category) }) { Icon(Icons.Filled.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error) }
                    }
                }
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(if (editing == null) "Nueva categoria" else "Renombrar categoria") }, singleLine = true)
                Button(onClick = { if (editing == null) onSave(name) else editing?.let { onRename(it, name) }; editing = null; name = "" }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text(if (editing == null) "Añadir" else "Guardar")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun DishCard(
    dish: Dish,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    RecipeCard(
        dish = dish,
        onOpen = onOpen,
        onEdit = onEdit,
        onDelete = onDelete,
    )
}

@Composable
private fun DishDetailScreen(
    detail: DishDetail,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val image = detail.dish.imageBase64?.let(::decodeImage)
    val steps = detail.dish.recipeSteps.lines().filter { it.isNotBlank() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp))) {
            Image(
                painter = painterResource(com.majyd.casa.R.drawable.kitchen_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)).padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(detail.dish.name, color = MaterialTheme.colorScheme.onPrimary, textAlign = TextAlign.Center, style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFFFF9EE).copy(alpha = 0.96f)).border(BorderStroke(1.dp, Color(0xFFC99C76)), RoundedCornerShape(16.dp))) {
                            if (image != null) {
                                Image(image.asImageBitmap(), detail.dish.name, Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                            } else {
                                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Restaurant, "Sin imagen", tint = Color(0xFF24352F), modifier = Modifier.size(48.dp))
                                }
                            }
                        }
                    }
                    item { DishDetailSection("Ingredientes") { if (detail.ingredients.isEmpty()) Text("Sin ingredientes") else detail.ingredients.forEach { Text("- ${ingredientLabel(it)}") } } }
                    item { DishDetailSection("Pasos") { if (steps.isEmpty()) Text("Sin pasos indicados") else steps.forEachIndexed { index, step -> Text("${index + 1}. $step") } } }
                    if (detail.variants.isNotEmpty()) item { DishDetailSection("Variantes") { detail.variants.forEach { variant -> Text(variant.name); variant.recipe.takeIf { it.isNotBlank() }?.let { Text(it) }; variant.notes.takeIf { it.isNotBlank() }?.let { Text(it) } } } }
                    item { DishDetailSection("Tiempo") { Text(detail.dish.preparationMinutes?.let { "$it min" } ?: "No indicado") } }
                    item { DishDetailSection("Raciones") { Text(detail.dish.servings?.let { "$it raciones" } ?: "No indicadas") } }
                }
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), RoundedCornerShape(14.dp)).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onEdit, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Editar") }
                    Button(onClick = onDelete, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Eliminar") }
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) { Text("Cerrar") }
                }
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
    val image = detail.dish.imageBase64?.let(::decodeImage)
    val steps = detail.dish.recipeSteps.lines().filter { it.isNotBlank() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        title = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = detail.dish.name,
                    color = MaterialTheme.colorScheme.onPrimary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                Image(
                    painter = painterResource(com.majyd.casa.R.drawable.kitchen_background),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                LazyColumn(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color(0xFFFFF9EE)),
                        ) {
                            if (image != null) {
                                Image(
                                    bitmap = image.asImageBitmap(),
                                    contentDescription = detail.dish.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(190.dp)
                                        .clip(RoundedCornerShape(16.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                androidx.compose.foundation.layout.Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .clip(RoundedCornerShape(16.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.Restaurant,
                                        contentDescription = "Sin imagen",
                                        tint = Color(0xFF24352F),
                                        modifier = Modifier.size(42.dp),
                                    )
                                }
                            }
                        }
                    }
                    if (detail.ingredients.isNotEmpty()) {
                        item {
                            DishDetailSection("Ingredientes") {
                                detail.ingredients.forEach { ingredient ->
                                    Text("- ${ingredientLabel(ingredient)}")
                                }
                            }
                        }
                    } else {
                        item { DishDetailSection("Ingredientes") { Text("Sin ingredientes") } }
                    }
                    item {
                        DishDetailSection("Pasos") {
                            if (steps.isEmpty()) {
                                Text("Sin pasos indicados")
                            } else {
                                steps.forEachIndexed { index, step -> Text("${index + 1}. $step") }
                            }
                        }
                    }
                    if (detail.variants.isNotEmpty()) {
                        item {
                            DishDetailSection("Variantes") {
                                detail.variants.forEach { variant ->
                                    Text(variant.name)
                                    variant.recipe.takeIf { it.isNotBlank() }?.let { Text(it) }
                                    variant.notes.takeIf { it.isNotBlank() }?.let { Text(it) }
                                }
                            }
                        }
                    }
                    item { DishDetailSection("Tiempo") { Text(detail.dish.preparationMinutes?.let { "$it min" } ?: "No indicado") } }
                    item { DishDetailSection("Raciones") { Text(detail.dish.servings?.let { "$it raciones" } ?: "No indicadas") } }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onEdit,
                colors = ButtonDefaults.textButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = RoundedCornerShape(10.dp),
            ) { Text("Editar") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Eliminar") }
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Cerrar") }
            }
        },
    )
}

@Composable
private fun DishDetailSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFFF9EE).copy(alpha = 0.96f), RoundedCornerShape(14.dp))
                .border(BorderStroke(1.dp, Color(0xFFC99C76)), RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = Color(0xFF24352F))
            content()
        }
    }
}

private fun ingredientLabel(ingredient: DraftIngredient): String =
    listOf(ingredient.name, ingredient.quantity, ingredient.unit).filter { it.isNotBlank() }.joinToString(" ")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DishEditorSimple(
    draft: DishDraft,
    categories: List<com.majyd.casa.data.model.RecipeCategory>,
    collection: DishCollection,
    viewModel: DishesViewModel,
) {
    val context = LocalContext.current
    var sourceDialogOpen by rememberSaveable { mutableStateOf(false) }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cropDialogOpen by rememberSaveable { mutableStateOf(false) }
    var categoryMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var variantText by rememberSaveable(draft.dishId) { mutableStateOf("") }
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
    Dialog(
        onDismissRequest = viewModel::closeEditor,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EditorSectionLabel(if (draft.dishId == null) "Nuevo plato" else "Editar plato", large = true)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp)),
                ) {
                    Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .background(Color(0xFFFFF9EE), RoundedCornerShape(14.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = viewModel::updateName,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nombre del plato") },
                    singleLine = true,
                )
                if (collection == DishCollection.RECIPE_BOOK) {
                    ExposedDropdownMenuBox(
                        expanded = categoryMenuExpanded,
                        onExpandedChange = { categoryMenuExpanded = !categoryMenuExpanded },
                    ) {
                        val selectedCategoryNames = categories
                            .filter { it.id in draft.categoryIds }
                            .joinToString(", ") { it.name }
                        OutlinedTextField(
                            value = selectedCategoryNames,
                            onValueChange = {},
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            label = { Text("Categorías") },
                            placeholder = { Text("Selecciona una o varias") },
                            readOnly = true,
                            singleLine = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                        )
                        ExposedDropdownMenu(
                            expanded = categoryMenuExpanded,
                            onDismissRequest = { categoryMenuExpanded = false },
                        ) {
                            categories.forEach { category ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(if (category.id in draft.categoryIds) "[x] ${category.name}" else category.name) },
                                    onClick = {
                                        viewModel.toggleCategory(category.id)
                                        categoryMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                if (collection == DishCollection.MENU) OutlinedTextField(
                    value = draft.category,
                    onValueChange = viewModel::updateCategory,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Categoría") },
                    singleLine = true,
                )
                EditorSectionLabel("Foto (opcional)", filled = false)
                draft.imageBase64?.let { value ->
                    decodeImage(value)?.let { bitmap ->
                        Image(bitmap.asImageBitmap(), "Vista previa", Modifier.fillMaxWidth().size(160.dp), contentScale = ContentScale.Crop)
                    }
                    TextButton(onClick = { viewModel.updateImage(null) }) { Text("Quitar foto") }
                }
                Button(
                    onClick = { sourceDialogOpen = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Text("Elegir foto")
                }
                EditorSectionLabel("Receta (opcional)", filled = false)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = draft.preparationRequired,
                        onCheckedChange = viewModel::updatePreparationRequired,
                    )
                    Text("Preparación previa")
                }
                EditorSectionLabel("Ingredientes", filled = false)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    var ingredientName by rememberSaveable { mutableStateOf("") }
                    var ingredientQuantity by rememberSaveable { mutableStateOf("") }
                    var ingredientUnit by rememberSaveable { mutableStateOf("") }
                    fun normalizeQuantity(value: String, unit: String): String =
                        if (unit == "g" || unit == "ml") value.filter(Char::isDigit) else value
                    OutlinedTextField(ingredientName, { ingredientName = it }, Modifier.fillMaxWidth(), label = { Text("Ingrediente") }, singleLine = true)
                    OutlinedTextField(
                        value = ingredientQuantity,
                        onValueChange = { ingredientQuantity = normalizeQuantity(it, ingredientUnit) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Cantidad") },
                        singleLine = true,
                    )
                    Text("Unidad", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf("g", "ml", "porción", "gotas").forEach { unit ->
                            FilterChip(
                                selected = ingredientUnit == unit,
                                onClick = {
                                    ingredientUnit = unit
                                    ingredientQuantity = normalizeQuantity(ingredientQuantity, unit)
                                },
                                modifier = Modifier.weight(1f),
                                label = { Text(unit) },
                            )
                        }
                    }
                    Button(
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                        ),
                        onClick = {
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
                EditorSectionLabel("Pasos de preparacion (opcional)", filled = false)
                var editingStepIndex by rememberSaveable(draft.dishId) { mutableStateOf<Int?>(null) }
                var editingStepText by rememberSaveable(draft.dishId) { mutableStateOf("") }
                var stepText by rememberSaveable(draft.dishId) { mutableStateOf("") }
                draft.steps.forEachIndexed { index, step ->
                    if (editingStepIndex == index) {
                        OutlinedTextField(
                            value = editingStepText,
                            onValueChange = { editingStepText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Paso ${index + 1}") },
                            minLines = 2,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    viewModel.updateStep(index, editingStepText)
                                    editingStepIndex = null
                                },
                            ) { Text("Guardar") }
                            TextButton(onClick = { editingStepIndex = null }) { Text("Cancelar") }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Paso ${index + 1}: $step", Modifier.weight(1f))
                            IconButton(onClick = {
                                editingStepIndex = index
                                editingStepText = step
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Editar paso")
                            }
                            IconButton(onClick = { viewModel.removeStep(index) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Eliminar paso")
                            }
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                    ),
                ) { Text("Agregar paso") }
                EditorSectionLabel("Variantes (opcional)")
                OutlinedTextField(
                    value = variantText,
                    onValueChange = { variantText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Describe la variante") },
                    minLines = 2,
                )
                Button(
                    onClick = {
                        viewModel.addVariant(variantText, "", "")
                        variantText = ""
                    },
                    enabled = variantText.isNotBlank(),
                ) { Text("Agregar variante") }
                draft.variants.forEachIndexed { index, variant ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(variant.name, Modifier.weight(1f))
                        IconButton(onClick = { viewModel.removeVariant(index) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar variante")
                        }
                    }
                }
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
                }
                val hasRequiredCategory = if (collection == DishCollection.RECIPE_BOOK) {
                    draft.categoryIds.isNotEmpty()
                } else {
                    draft.category.isNotBlank()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = viewModel::closeEditor,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                    ) { Text("Cancelar") }
                    Button(
                        onClick = viewModel::save,
                        modifier = Modifier.weight(1f),
                        enabled = draft.name.isNotBlank() && hasRequiredCategory,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) { Text("Guardar") }
                }
            }
        }
    }
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
