package com.londkirs.monopoly.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.londkirs.monopoly.engine.GameSnapshot

@Composable
fun ColumnScope.BoardGrid(snapshot: GameSnapshot, onSelect: (Int) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    val coordinates = remember {
        buildMap<Int, Pair<Int, Int>> {
            put(0, 10 to 10)
            for (i in 1..9) put(i, 10 to (10 - i))
            put(10, 10 to 0)
            for (i in 11..19) put(i, (20 - i) to 0)
            put(20, 0 to 0)
            for (i in 21..29) put(i, 0 to (i - 20))
            put(30, 0 to 10)
            for (i in 31..39) put(i, (i - 30) to 10)
        }
    }
    val squares = snapshot.board.squares.associateBy { it.id }
    val properties = snapshot.state.properties.associateBy { it.squareId }
    val playersAt = snapshot.state.players.filterNot { it.bankrupt }.groupBy { it.position }
    Box(Modifier.fillMaxWidth().weight(1.1f).background(Color(0xFFE7E1EC), RoundedCornerShape(10.dp))
        .horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())
        .pointerInput(Unit) { detectTransformGestures { _, _, zoom, _ -> scale = (scale * zoom).coerceIn(0.72f, 1.8f) } }
        .padding(3.dp)) {
        Column(Modifier.width(360.dp).graphicsLayer(scaleX = scale, scaleY = scale)) {
            for (row in 0..10) {
                Row(Modifier.fillMaxWidth()) {
                    for (col in 0..10) {
                        val id = coordinates.entries.firstOrNull { it.value == (row to col) }?.key
                        if (id == null) {
                            Box(Modifier.weight(1f).height(38.dp).padding(1.dp).background(Color(0xFFF7F4FA), RoundedCornerShape(3.dp)),
                                contentAlignment = Alignment.Center) {
                                if (row == 5 && col == 5) Text("MONOPOLY", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color(0xFF7652A8))
                            }
                        } else {
                            val square = squares[id]!!
                            val property = properties[id]
                            val owner = snapshot.state.players.firstOrNull { it.id == property?.ownerId }
                            Column(Modifier.weight(1f).height(38.dp).padding(1.dp).background(Color.White, RoundedCornerShape(3.dp))
                                .border(0.5.dp, Color(0xFFD6D0DB), RoundedCornerShape(3.dp)).clickable { onSelect(id) },
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Box(Modifier.fillMaxWidth().height(4.dp).background(groupColor(square.group)))
                                Text(id.toString(), fontSize = 7.sp, color = Color.Gray)
                                Text(square.name.take(8), fontSize = 6.sp, lineHeight = 7.sp, maxLines = 2, fontWeight = FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.Center) {
                                    if (owner != null) Box(Modifier.size(5.dp).background(Color(owner.color), RoundedCornerShape(50)))
                                    if (property?.hotel == true) Text("H", fontSize = 6.sp, color = Color.Red)
                                    else if ((property?.houses ?: 0) > 0) Text(property!!.houses.toString(), fontSize = 6.sp, color = Color(0xFF278044))
                                    if (property?.mortgaged == true) Text("M", fontSize = 6.sp, color = Color.Red)
                                    AnimatedVisibility(visible = playersAt[id].orEmpty().isNotEmpty()) {
                                        Row { playersAt[id]?.forEach { Text("●", fontSize = 6.sp, color = Color(it.color)) } }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun groupColor(group: String?): Color = when (group) {
    "brown" -> Color(0xFF9B7654)
    "lightBlue" -> Color(0xFF83CBE8)
    "pink" -> Color(0xFFE99AB8)
    "orange" -> Color(0xFFEE9A42)
    "red" -> Color(0xFFD94C4C)
    "yellow" -> Color(0xFFF2D34F)
    "green" -> Color(0xFF5DAA72)
    "darkBlue" -> Color(0xFF5366B1)
    else -> Color(0xFFD9D5DE)
}
