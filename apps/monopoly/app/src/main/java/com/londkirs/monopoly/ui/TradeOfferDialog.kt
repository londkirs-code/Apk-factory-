package com.londkirs.monopoly.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.londkirs.monopoly.engine.GameSnapshot
import com.londkirs.monopoly.engine.TradeOffer

@Composable
fun TradeOfferDialog(snapshot: GameSnapshot, onDismiss: () -> Unit, onSubmit: (TradeOffer) -> Unit) {
    val state = snapshot.state
    val current = state.currentPlayer
    val targets = state.players.filter { it.id != current.id && !it.bankrupt }
    var targetId by remember { mutableStateOf(targets.firstOrNull()?.id.orEmpty()) }
    var cashFrom by remember { mutableStateOf("0") }
    var cashTo by remember { mutableStateOf("0") }
    var cardsFrom by remember { mutableStateOf("0") }
    var cardsTo by remember { mutableStateOf("0") }
    val selectedFrom = remember { mutableStateListOf<Int>() }
    val selectedTo = remember { mutableStateListOf<Int>() }
    val target = targets.firstOrNull { it.id == targetId }
    val fromProperties = state.properties.filter { it.ownerId == current.id && it.houses == 0 && !it.hotel }
    val toProperties = state.properties.filter { it.ownerId == targetId && it.houses == 0 && !it.hotel }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("הצעת מסחר") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("למי להציע?")
                Row {
                    targets.forEach { player ->
                        FilterChip(selected = targetId == player.id, onClick = { targetId = player.id; selectedTo.clear() }, label = { Text(player.name) })
                    }
                }
                Text("מה אתה נותן", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(cashFrom, { cashFrom = it.filter(Char::isDigit) }, label = { Text("כסף") }, singleLine = true)
                OutlinedTextField(cardsFrom, { cardsFrom = it.filter(Char::isDigit) }, label = { Text("כרטיסי יציאה מהכלא") }, singleLine = true)
                Text("נכסים שלך")
                fromProperties.forEach { property ->
                    val name = snapshot.board.squares[property.squareId].name
                    FilterChip(selected = property.squareId in selectedFrom, onClick = {
                        if (property.squareId in selectedFrom) selectedFrom.remove(property.squareId) else selectedFrom.add(property.squareId)
                    }, label = { Text(name) })
                }
                Divider()
                Text("מה אתה מבקש", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(cashTo, { cashTo = it.filter(Char::isDigit) }, label = { Text("כסף מהשחקן השני") }, singleLine = true)
                OutlinedTextField(cardsTo, { cardsTo = it.filter(Char::isDigit) }, label = { Text("כרטיסי יציאה מהכלא") }, singleLine = true)
                Text("נכסים של " + (target?.name ?: "השחקן השני"))
                toProperties.forEach { property ->
                    val name = snapshot.board.squares[property.squareId].name
                    FilterChip(selected = property.squareId in selectedTo, onClick = {
                        if (property.squareId in selectedTo) selectedTo.remove(property.squareId) else selectedTo.add(property.squareId)
                    }, label = { Text(name) })
                }
                Text("העסקה תועבר לאישור השחקן השני. נכסים עם בתים או מלונות אינם זמינים למסחר.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (targetId.isNotBlank()) onSubmit(TradeOffer(
                    fromPlayerId = current.id, toPlayerId = targetId,
                    cashFrom = cashFrom.toIntOrNull() ?: 0, cashTo = cashTo.toIntOrNull() ?: 0,
                    propertiesFrom = selectedFrom.toList(), propertiesTo = selectedTo.toList(),
                    jailCardsFrom = cardsFrom.toIntOrNull() ?: 0, jailCardsTo = cardsTo.toIntOrNull() ?: 0
                ))
            }, enabled = target != null) { Text("שלח הצעה") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}
