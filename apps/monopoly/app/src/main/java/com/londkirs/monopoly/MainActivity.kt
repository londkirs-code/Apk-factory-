package com.londkirs.monopoly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as columnItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.londkirs.monopoly.engine.*
import com.londkirs.monopoly.ui.GameViewModel

class MainActivity : ComponentActivity() {
    private val vm by viewModels<GameViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF7652A8), background = Color(0xFFF5F2F8))) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val ui by vm.uiState.collectAsStateWithLifecycle()
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        when {
                            ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                            ui.board == null -> Text(ui.error ?: "לא ניתן לטעון את הלוח", Modifier.padding(24.dp))
                            ui.snapshot == null -> SetupScreen(vm::startGame)
                            else -> GameScreen(ui.snapshot!!, vm::roll, vm::buy, vm::decline, vm::bid, vm::passAuction,
                                vm::build, vm::sellBuilding, vm::mortgage, vm::redeem, vm::payJail, vm::useJailCard, vm::newGame, vm::endTurn, vm::settleDebt, vm::declareBankruptcy)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun SetupScreen(start: (List<String>, List<Boolean>, Int, HouseRules) -> Unit) {
    var count by remember { mutableIntStateOf(2) }
    var cash by remember { mutableIntStateOf(1500) }
    var parking by remember { mutableStateOf(false) }
    var doubleGo by remember { mutableStateOf(false) }
    var noAuction by remember { mutableStateOf(false) }
    val names = remember { mutableStateListOf("שחקן 1", "שחקן 2", "שחקן 3", "שחקן 4", "שחקן 5", "שחקן 6") }
    val ai = remember { mutableStateListOf(false, true, true, true, true, true) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("מונופול", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color(0xFF513477))
        Text("משחק אופליין · 2–6 שחקנים")
        Card {
            Column(Modifier.padding(14.dp)) {
                Text("מספר שחקנים", fontWeight = FontWeight.Bold)
                Row { (2..6).forEach { n -> FilterChip(selected = count == n, onClick = { count = n }, label = { Text(n.toString()) }) } }
                Text("כסף התחלתי: " + cash.toString())
                Slider(value = cash.toFloat(), onValueChange = { cash = it.toInt() }, valueRange = 500f..3000f, steps = 9)
            }
        }
        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("שחקנים", fontWeight = FontWeight.Bold)
                repeat(count) { i ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(names[i], { names[i] = it }, Modifier.weight(1f), label = { Text("שם שחקן " + (i + 1)) }, singleLine = true)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (ai[i]) "מחשב" else "אנושי", fontSize = 10.sp)
                            Switch(ai[i], { ai[i] = it })
                        }
                    }
                }
            }
        }
        Card {
            Column(Modifier.padding(14.dp)) {
                Text("כללי בית", fontWeight = FontWeight.Bold)
                Rule("קופת חניה חופשית", parking) { parking = it }
                Rule("400 במקום 200 בנחיתה מדויקת על GO", doubleGo) { doubleGo = it }
                Rule("ללא מכרזים", noAuction) { noAuction = it }
            }
        }
        Button(onClick = { start(names.take(count).toList(), ai.take(count).map { !it }, cash, HouseRules(parking, doubleGo, noAuction)) }, Modifier.fillMaxWidth().height(52.dp)) {
            Text("התחלת משחק", fontSize = 18.sp)
        }
    }
}

@Composable private fun Rule(label: String, value: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(value, change)
    }
}

@Composable private fun GameScreen(
    snap: GameSnapshot, roll: () -> Unit, buy: () -> Unit, decline: () -> Unit,
    bid: (Int) -> Unit, pass: () -> Unit, build: (Int) -> Unit, sell: (Int) -> Unit,
    mortgage: (Int) -> Unit, redeem: (Int) -> Unit, payJail: () -> Unit,
    jailCard: () -> Unit, newGame: () -> Unit, endTurn: () -> Unit, settleDebt: () -> Unit, bankrupt: () -> Unit
) {
    val s = snap.state
    var selected by remember { mutableStateOf<Int?>(null) }
    var assets by remember { mutableStateOf(false) }
    var bidValue by remember(s.auction?.highBid) { mutableIntStateOf((s.auction?.highBid ?: 0) + 10) }
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("מונופול", Modifier.weight(1f), fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color(0xFF513477))
            TextButton(onClick = newGame) { Text("משחק חדש") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            s.players.forEach { p ->
                Card(colors = CardDefaults.cardColors(containerColor = if (p.id == s.currentPlayer.id) Color(0xFFEDE3FA) else Color.White)) {
                    Column(Modifier.width(100.dp).padding(8.dp)) {
                        Text(p.name, fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 12.sp)
                        Text("כסף: " + p.cash.toString(), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(s.properties.count { it.ownerId == p.id }.toString() + " נכסים", fontSize = 10.sp)
                        if (p.inJail) Text("בכלא", color = Color.Red)
                        if (p.bankrupt) Text("פשט רגל", color = Color.Red)
                    }
                }
            }
        }
        LazyVerticalGrid(columns = GridCells.Fixed(5), modifier = Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            items(snap.board.squares) { sq ->
                val prop = s.properties.firstOrNull { it.squareId == sq.id }
                val owner = s.players.firstOrNull { it.id == prop?.ownerId }
                Column(Modifier.height(67.dp).background(Color.White, RoundedCornerShape(6.dp)).clickable { selected = sq.id }.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.fillMaxWidth().height(5.dp).background(colorFor(sq.group)))
                    Text(sq.id.toString(), fontSize = 9.sp, color = Color.Gray)
                    Text(sq.name, fontSize = 9.sp, maxLines = 2, lineHeight = 10.sp, fontWeight = FontWeight.SemiBold)
                    if (owner != null) Text(owner.name.take(7), fontSize = 8.sp, color = Color(owner.color))
                    if (prop?.mortgaged == true) Text("ממושכן", fontSize = 8.sp, color = Color.Red)
                    else if (prop?.hotel == true) Text("מלון", fontSize = 8.sp, color = Color.Red)
                    else if ((prop?.houses ?: 0) > 0) Text(prop!!.houses.toString() + " בתים", fontSize = 8.sp, color = Color.Green)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Card(Modifier.weight(1f)) {
                Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("קוביות", color = Color.Gray)
                    Text(s.dice.joinToString("  ⚄  "), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(s.currentPlayer.name, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (s.phase) {
                    GamePhase.ROLL -> if (s.turnRolled && s.consecutiveDoubles == 0) Button(endTurn, Modifier.fillMaxWidth()) { Text("סיום תור") } else Button(roll, Modifier.fillMaxWidth(), enabled = s.currentPlayer.isHuman) { Text("הטלת קוביות") }
                    GamePhase.BUY_OR_AUCTION -> {
                        Button(buy, Modifier.fillMaxWidth()) { Text("קנייה") }
                        OutlinedButton(decline, Modifier.fillMaxWidth()) { Text("ויתור / מכרז") }
                    }
                    GamePhase.AUCTION -> {
                        OutlinedTextField(bidValue.toString(), { bidValue = it.toIntOrNull() ?: bidValue }, label = { Text("הצעה") })
                        Button({ bid(bidValue) }, Modifier.fillMaxWidth()) { Text("הצע") }
                        OutlinedButton(pass, Modifier.fillMaxWidth()) { Text("פרוש") }
                    }
                    GamePhase.PAY_DEBT -> {
                        Text("חוב לתשלום: " + s.pendingDebt.toString(), color = Color.Red, fontWeight = FontWeight.Bold)
                        Button(settleDebt, Modifier.fillMaxWidth(), enabled = s.currentPlayer.cash >= s.pendingDebt) { Text("שלם חוב") }
                        OutlinedButton(bankrupt, Modifier.fillMaxWidth()) { Text("הכרז פשיטת רגל") }
                    }
                    GamePhase.GAME_OVER -> Text("המשחק הסתיים")
                    else -> Text("נדרשת פעולה נוספת")
                }
                if (s.currentPlayer.inJail) {
                    TextButton(payJail, enabled = s.currentPlayer.cash >= 50) { Text("שלם 50 כדי לצאת") }
                    TextButton(jailCard, enabled = s.currentPlayer.jailFreeCards > 0) { Text("השתמש בכרטיס יציאה") }
                }
                OutlinedButton({ assets = true }, Modifier.fillMaxWidth()) { Text("ניהול נכסים") }
            }
        }
        Text("יומן אירועים", fontWeight = FontWeight.Bold)
        LazyColumn(Modifier.weight(0.8f).fillMaxWidth().background(Color.White, RoundedCornerShape(10.dp)).padding(8.dp)) {
            columnItems(s.log.takeLast(30).reversed()) { Text("• " + it, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp)) }
        }
    }
    selected?.let { id ->
        val sq = snap.board.squares[id]
        val p = s.properties.firstOrNull { it.squareId == id }
        val owner = s.players.firstOrNull { it.id == p?.ownerId }
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(sq.name) }, text = {
            Column {
                Text("משבצת " + id + " · " + sq.type)
                if (sq.price > 0) Text("מחיר: " + sq.price)
                if (sq.rents.isNotEmpty()) Text("שכירות: " + sq.rents.joinToString(" / "))
                if (sq.houseCost > 0) Text("מחיר בית: " + sq.houseCost)
                Text("בעלים: " + (owner?.name ?: "הבנק"))
                if (p != null && p.ownerId == s.currentPlayer.id) {
                    Row {
                        TextButton({ build(id); selected = null }) { Text("בנה") }
                        TextButton({ sell(id); selected = null }, enabled = p.houses > 0 || p.hotel) { Text("מכור מבנה") }
                    }
                    Row {
                        TextButton({ mortgage(id); selected = null }, enabled = !p.mortgaged) { Text("משכן") }
                        TextButton({ redeem(id); selected = null }, enabled = p.mortgaged) { Text("פדה") }
                    }
                }
            }
        }, confirmButton = { TextButton({ selected = null }) { Text("סגור") } })
    }
    if (assets) {
        val mine = s.properties.filter { it.ownerId == s.currentPlayer.id }.sortedBy { it.squareId }
        AlertDialog(onDismissRequest = { assets = false }, title = { Text("הנכסים שלי") },
            text = { LazyColumn(Modifier.heightIn(max = 420.dp)) {
                columnItems(mine) { p ->
                    val sq = snap.board.squares[p.squareId]
                    Text(sq.name + " · " + if (p.hotel) "מלון" else p.houses.toString() + " בתים")
                    Row {
                        TextButton({ build(p.squareId) }, enabled = !p.mortgaged && sq.type == SquareType.PROPERTY) { Text("בנה") }
                        TextButton({ sell(p.squareId) }, enabled = p.houses > 0 || p.hotel) { Text("מכור") }
                        TextButton({ mortgage(p.squareId) }, enabled = !p.mortgaged && p.houses == 0 && !p.hotel) { Text("משכן") }
                        TextButton({ redeem(p.squareId) }, enabled = p.mortgaged) { Text("פדה") }
                    }
                    Divider()
                }
            } }, confirmButton = { TextButton({ assets = false }) { Text("סיום") } })
    }
}

private fun colorFor(group: String?): Color = when (group) {
    "brown" -> Color(0xFF9B7654); "lightBlue" -> Color(0xFF83CBE8); "pink" -> Color(0xFFE99AB8)
    "orange" -> Color(0xFFEE9A42); "red" -> Color(0xFFD94C4C); "yellow" -> Color(0xFFF2D34F)
    "green" -> Color(0xFF5DAA72); "darkBlue" -> Color(0xFF5366B1); else -> Color(0xFFD9D5DE)
}
