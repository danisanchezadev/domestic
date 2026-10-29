package com.majyd.casa.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.majyd.casa.data.model.Dish

private val RecipeCardCream = Color(0xFFFFF9EE)
private val RecipeCardBorder = Color(0xFFC99C76)
private val RecipeCardText = Color(0xFF24352F)
private val RecipeCardSeparator = Color(0xFFD5B99C)

@Composable
fun RecipeCard(
    dish: Dish,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onOpen: () -> Unit = {},
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var actionsOpen by remember { mutableStateOf(false) }
    val imageHeight = if (compact) 104.dp else 140.dp
    val image = dish.imageBase64?.let(::decodeRecipeImage)
    val hasActions = onEdit != null || onDelete != null

    androidx.compose.foundation.layout.Box(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(dish.id) {
                    detectTapGestures(
                        onTap = { onOpen() },
                        onLongPress = { if (hasActions) actionsOpen = true },
                    )
                },
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(2.dp, RecipeCardBorder),
            colors = CardDefaults.cardColors(containerColor = RecipeCardCream),
        ) {
            Column(
                modifier = Modifier.padding(if (compact) 6.dp else 8.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp),
            ) {
                if (image != null) {
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = dish.name,
                        modifier = Modifier.fillMaxWidth().height(imageHeight).clip(RoundedCornerShape(13.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxWidth().height(imageHeight).clip(RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Restaurant, contentDescription = "Sin imagen", tint = RecipeCardText)
                    }
                }
                Text(
                    text = dish.name,
                    modifier = Modifier.fillMaxWidth().height(if (compact) 42.dp else 50.dp),
                    color = RecipeCardText,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        RecipeCardInfo(Icons.Filled.Timer, dish.preparationMinutes?.let { "$it min" } ?: "—", compact)
                    }
                    androidx.compose.foundation.Canvas(Modifier.width(1.dp).height(28.dp)) {
                        drawLine(
                            RecipeCardSeparator,
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(0f, size.height),
                            strokeWidth = 2f,
                        )
                    }
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        RecipeCardInfo(Icons.Filled.People, dish.servings?.let { "$it raciones" } ?: "—", compact)
                    }
                }
            }
        }
        if (hasActions) {
            DropdownMenu(expanded = actionsOpen, onDismissRequest = { actionsOpen = false }) {
                onEdit?.let { edit ->
                    DropdownMenuItem(
                        text = { Text("Editar") },
                        leadingIcon = { Icon(Icons.Filled.Edit, "Editar") },
                        onClick = { actionsOpen = false; edit() },
                    )
                }
                onDelete?.let { delete ->
                    DropdownMenuItem(
                        text = { Text("Eliminar") },
                        leadingIcon = { Icon(Icons.Filled.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error) },
                        onClick = { actionsOpen = false; delete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecipeCardInfo(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, compact: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .wrapContentWidth()
            .padding(horizontal = if (compact) 1.dp else 3.dp),
    ) {
        Icon(icon, contentDescription = null, tint = RecipeCardText, modifier = Modifier.size(if (compact) 16.dp else 20.dp))
        Text(text, color = RecipeCardText, style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

private fun decodeRecipeImage(value: String) = runCatching {
    val bytes = Base64.decode(value, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()
