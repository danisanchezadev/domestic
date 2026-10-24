package com.majyd.casa.ui.weeklymenu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.majyd.casa.data.model.MealSlot
import com.majyd.casa.data.model.Dish
import com.majyd.casa.data.model.WeeklyMenuItem

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WeeklyMenuScreen(
    onBack: () -> Unit,
    onOpenDishes: () -> Unit,
    viewModel: WeeklyMenuViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectorOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                title = { Text("Menú") },
                actions = {
                    TextButton(onClick = onOpenDishes) {
                        Icon(Icons.Filled.Restaurant, contentDescription = null)
                        Text("Catalogo de platos")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                WeeklyMenuGrid(
                    items = state.activeItems,
                    dishes = state.dishes,
                    onSlotClick = { day, slot, item ->
                        if (item == null) {
                            viewModel.startNewForSlot(day, slot)
                            selectorOpen = true
                        } else {
                            viewModel.openRecipe(item)
                        }
                    },
                )
            }
        }
    }
    state.ingredientPicker?.let { picker ->
        AlertDialog(
            onDismissRequest = viewModel::cancelIngredientSelection,
            title = { Text("Ingredientes para la compra") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Selecciona los ingredientes que quieres añadir.")
                    picker.ingredients.forEach { ingredient ->
                        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = ingredient.id in picker.selectedIds,
                                onCheckedChange = { viewModel.toggleIngredient(ingredient.id) },
                            )
                            Text(ingredient.name)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::confirmIngredientSelection) { Text("Continuar") } },
            dismissButton = { TextButton(onClick = viewModel::cancelIngredientSelection) { Text("Cancelar") } },
        )
    }
    state.recipeDetail?.let { recipe ->
        RecipeDetailDialog(
            detail = recipe,
            onDismiss = viewModel::closeRecipe,
            onDelete = viewModel::deleteRecipeAssignment,
        )
    }
    if (selectorOpen) {
        DishSelectorDialog(
            dayOfWeek = state.draft.dayOfWeek,
            dishes = state.dishes,
            draft = state.draft,
            variants = state.variants,
            onDismiss = { selectorOpen = false },
            onDishSelected = viewModel::selectDish,
            onVariantSelected = viewModel::selectVariant,
            onContinue = { selectorOpen = false; viewModel.saveItem() },
        )
    }
}

@Composable
private fun WeeklyMenuGrid(
    items: List<WeeklyMenuItem>,
    dishes: List<Dish>,
    onSlotClick: (Int, MealSlot, WeeklyMenuItem?) -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Menú semanal",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            dayLabels().forEachIndexed { index, label ->
                val day = index + 1
                val dayItems = items.filter { it.dayOfWeek == day }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MenuSlotCard(
                                title = "Comida",
                                item = dayItems.firstOrNull { it.mealSlot == MealSlot.LUNCH },
                                dishes = dishes,
                                modifier = Modifier.weight(1f),
                                onClick = { onSlotClick(day, MealSlot.LUNCH, dayItems.firstOrNull { it.mealSlot == MealSlot.LUNCH }) },
                            )
                            MenuSlotCard(
                                title = "Cena",
                                item = dayItems.firstOrNull { it.mealSlot == MealSlot.DINNER },
                                dishes = dishes,
                                modifier = Modifier.weight(1f),
                                onClick = { onSlotClick(day, MealSlot.DINNER, dayItems.firstOrNull { it.mealSlot == MealSlot.DINNER }) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuSlotCard(
    title: String,
    item: WeeklyMenuItem?,
    dishes: List<Dish>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(modifier = modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            item?.let { MenuDishPreview(it, dishes) }
                ?: Text("Sin asignar", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MenuDishPreview(item: WeeklyMenuItem, dishes: List<Dish>) {
    val dish = dishes.firstOrNull { it.id == item.dishId }
    val bitmap = dish?.imageBase64?.let(::decodeDishImage)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (bitmap != null) {
            Card(
                modifier = Modifier.fillMaxWidth().size(72.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            ) {
                Image(
                    bitmap.asImageBitmap(),
                    contentDescription = dish?.name,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().size(72.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Restaurant, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Text(dish?.name ?: item.title, style = MaterialTheme.typography.bodySmall, maxLines = 2)
    }
}

@Composable
private fun DayDetailDialog(
    dayOfWeek: Int,
    items: List<WeeklyMenuItem>,
    dishes: List<Dish>,
    onDismiss: () -> Unit,
    onSelect: (MealSlot, WeeklyMenuItem?) -> Unit,
    onRecipe: (WeeklyMenuItem) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dayName(dayOfWeek)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(MealSlot.LUNCH to "Comida", MealSlot.DINNER to "Cena").forEach { (slot, label) ->
                    val item = items.firstOrNull { it.dayOfWeek == dayOfWeek && it.mealSlot == slot }
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (item == null) onSelect(slot, null) else onRecipe(item)
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(label, fontWeight = FontWeight.SemiBold)
                                if (item == null) Text("Sin asignar")
                                else MenuDishPreview(item, dishes)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun RecipeDetailDialog(
    detail: RecipeDetailState,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    val bitmap = detail.dish.imageBase64?.let(::decodeDishImage)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(detail.dish.name, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            }
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap.asImageBitmap(),
                                contentDescription = detail.dish.name,
                                modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(120.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Restaurant, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                item {
                    RecipeSection("Ingredientes") {
                        if (detail.ingredients.isEmpty()) {
                            Text("Sin ingredientes")
                        } else {
                            detail.ingredients.forEach { ingredient ->
                                Text(
                                    listOfNotNull(
                                        ingredient.name,
                                        ingredient.quantity?.toString(),
                                        ingredient.unit.takeIf { it.isNotBlank() },
                                    ).joinToString(" "),
                                )
                            }
                        }
                    }
                }
                val steps = detail.dish.recipeSteps.lines().filter { it.isNotBlank() }
                item {
                    RecipeSection("Pasos") {
                        if (steps.isEmpty()) {
                            Text("Sin pasos indicados")
                        } else {
                            steps.forEachIndexed { index, step -> Text("${index + 1}. $step") }
                        }
                    }
                }
                item {
                    RecipeSection("Tiempo estimado") {
                        Text(detail.dish.preparationMinutes?.let { "$it minutos" } ?: "No indicado")
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("Volver") }
                Button(onClick = onDelete) { Text("Eliminar plato") }
            }
        },
    )
}

@Composable
private fun RecipeSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DishSelectorDialog(
    dayOfWeek: Int,
    dishes: List<Dish>,
    draft: WeeklyMenuDraft,
    variants: List<com.majyd.casa.data.model.DishVariant>,
    onDismiss: () -> Unit,
    onDishSelected: (Dish?) -> Unit,
    onVariantSelected: (com.majyd.casa.data.model.DishVariant?) -> Unit,
    onContinue: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = dishes.filter { dish ->
        dish.name.contains(query.trim(), ignoreCase = true)
    }.sortedBy { it.name.lowercase() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Elegir plato para ${dayName(dayOfWeek)}") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Buscar plato") }, singleLine = true)
                }
                if (filtered.isEmpty()) {
                    item { Text(if (dishes.isEmpty()) "No hay platos en el catalogo" else "No hay resultados para la busqueda") }
                } else {
                    items(filtered, key = { it.id }) { dish ->
                        DishSelectorCard(dish, selected = draft.dishId == dish.id, onClick = { onDishSelected(dish) })
                    }
                }
                if (variants.isNotEmpty()) {
                    item { Text("Variante", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(draft.variantId == null, { onVariantSelected(null) }, label = { Text("Base") })
                            variants.forEach { variant ->
                                FilterChip(draft.variantId == variant.id, { onVariantSelected(variant) }, label = { Text(variant.name) })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onContinue, enabled = draft.dishId != null || draft.title.isNotBlank()) { Text("Continuar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun DishSelectorCard(dish: Dish, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            dish.imageBase64?.let { encoded ->
                decodeDishImage(encoded)?.let { bitmap ->
                    Card(
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                    ) {
                        Image(
                            bitmap.asImageBitmap(),
                            contentDescription = dish.name,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(dish.name, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun decodeDishImage(value: String) = runCatching {
    val bytes = Base64.decode(value, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()

@Composable
private fun WeeklyMenuDraftCard(
    draft: WeeklyMenuDraft,
    dishes: List<com.majyd.casa.data.model.Dish>,
    variants: List<com.majyd.casa.data.model.DishVariant>,
    onDayChange: (Int) -> Unit,
    onMealSlotChange: (MealSlot) -> Unit,
    onTitleChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onDishChange: (com.majyd.casa.data.model.Dish?) -> Unit,
    onVariantChange: (com.majyd.casa.data.model.DishVariant?) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Nuevo plato", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Plato del catalogo", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = draft.dishId == null,
                    onClick = { onDishChange(null) },
                    label = { Text("Texto libre") },
                )
                dishes.forEach { dish ->
                    FilterChip(
                        selected = draft.dishId == dish.id,
                        onClick = { onDishChange(dish) },
                        label = { Text(dish.name) },
                    )
                }
            }
            if (variants.isNotEmpty()) {
                Text("Variante", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(draft.variantId == null, { onVariantChange(null) }, label = { Text("Base") })
                    variants.forEach { variant ->
                        FilterChip(draft.variantId == variant.id, { onVariantChange(variant) }, label = { Text(variant.name) })
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                dayLabels().forEachIndexed { index, label ->
                    FilterChip(
                        selected = draft.dayOfWeek == index + 1,
                        onClick = { onDayChange(index + 1) },
                        label = { Text(label) },
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MealSlot.entries.forEach { slot ->
                    FilterChip(
                        selected = draft.mealSlot == slot,
                        onClick = { onMealSlotChange(slot) },
                        label = { Text(slot.label) },
                    )
                }
            }
            OutlinedTextField(
                value = draft.title,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Plato") },
            )
            OutlinedTextField(
                value = draft.notes,
                onValueChange = onNotesChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notas") },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onSave) {
                    Text("Guardar")
                }
                Button(onClick = onClear) {
                    Text("Limpiar")
                }
            }
        }
    }
}

private fun dayLabels(): List<String> = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

private fun dayName(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "Lunes"
    2 -> "Martes"
    3 -> "Miercoles"
    4 -> "Jueves"
    5 -> "Viernes"
    6 -> "Sabado"
    7 -> "Domingo"
    else -> "Dia"
}
