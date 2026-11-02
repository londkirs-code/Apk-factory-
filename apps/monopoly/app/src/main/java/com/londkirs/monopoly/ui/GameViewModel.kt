package com.londkirs.monopoly.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.londkirs.monopoly.ai.MonopolyAI
import com.londkirs.monopoly.data.BoardDataLoader
import com.londkirs.monopoly.engine.*
import com.londkirs.monopoly.storage.GameStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

data class MonopolyUiState(val board: BoardData? = null, val snapshot: GameSnapshot? = null, val loading: Boolean = true, val error: String? = null)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = GameStorage(application)
    private val mutable = MutableStateFlow(MonopolyUiState())
    val uiState: StateFlow<MonopolyUiState> = mutable
    private var engine: MonopolyEngine? = null

    init {
        runCatching {
            val board = BoardDataLoader.load(application)
            engine = MonopolyEngine(board)
            val saved = storage.load()?.takeIf { it.board.squares.size == 40 }
            mutable.value = MonopolyUiState(board, saved, loading = false)
            if (saved != null) runAiIfNeeded()
        }.onFailure { mutable.value = MonopolyUiState(loading = false, error = it.message ?: "שגיאה בטעינת המשחק") }
    }

    fun startGame(names: List<String>, humanFlags: List<Boolean>, startingCash: Int, rules: HouseRules) {
        val board = mutable.value.board ?: return
        val colors = listOf(0xFFE85D4A, 0xFF2878D0, 0xFF25A56A, 0xFF9B59B6, 0xFFF1B82D, 0xFF4D5966)
        val players = names.take(6).mapIndexed { index, name ->
            Player(id = "p$index", name = name.ifBlank { "שחקן \${index + 1}" }, color = colors[index],
                isHuman = humanFlags.getOrElse(index) { true }, aiLevel = AiLevel.MEDIUM)
        }
        val settings = GameSettings(startingCash = startingCash.coerceAtLeast(500), houseRules = rules)
        val snapshot = engine!!.newGame(players, settings)
        storage.save(snapshot)
        mutable.value = mutable.value.copy(snapshot = snapshot)
        runAiIfNeeded()
    }

    fun roll() = dispatch { engine!!.roll(it) }
    fun buy() = dispatch { engine!!.buy(it) }
    fun decline() = dispatch { engine!!.declinePurchase(it) }
    fun bid(amount: Int) = dispatch { engine!!.auctionBid(it, amount) }
    fun passAuction() = dispatch { engine!!.auctionPass(it) }
    fun build(squareId: Int) = dispatch { engine!!.build(it, squareId) }
    fun sellBuilding(squareId: Int) = dispatch { engine!!.sellBuilding(it, squareId) }
    fun mortgage(squareId: Int) = dispatch { engine!!.mortgage(it, squareId) }
    fun redeem(squareId: Int) = dispatch { engine!!.redeemMortgage(it, squareId) }
    fun payJail() = dispatch { engine!!.payToLeaveJail(it) }
    fun useJailCard() = dispatch { engine!!.useJailCard(it) }
    fun endTurn() = dispatch { engine!!.endTurn(it) }

    fun newGame() {
        storage.clear()
        mutable.value = mutable.value.copy(snapshot = null)
    }

    private fun dispatch(action: (GameSnapshot) -> EngineResult) {
        val current = mutable.value.snapshot ?: return
        val result = runCatching { action(current) }.getOrElse {
            mutable.value = mutable.value.copy(error = it.message ?: "הפעולה נכשלה")
            return
        }
        val next = current.copy(state = result.state)
        storage.save(next)
        mutable.value = mutable.value.copy(snapshot = next, error = null)
        runAiIfNeeded()
    }

    private fun runAiIfNeeded() {
        viewModelScope.launch {
            repeat(40) {
                val snapshot = mutable.value.snapshot ?: return@launch
                val state = snapshot.state
                val player = state.currentPlayer
                if (player.isHuman || state.phase == GamePhase.GAME_OVER || state.phase == GamePhase.PAY_DEBT || state.phase == GamePhase.JAIL_DECISION) return@launch
                delay(Random.nextLong(500, 1501))
                val current = mutable.value.snapshot ?: return@launch
                val result = when (current.state.phase) {
                    GamePhase.ROLL -> engine!!.roll(current)
                    GamePhase.BUY_OR_AUCTION -> {
                        val id = current.state.pendingSquareId ?: return@launch
                        if (MonopolyAI.shouldBuy(current, id)) engine!!.buy(current) else engine!!.declinePurchase(current)
                    }
                    GamePhase.AUCTION -> {
                        val auction = current.state.auction ?: return@launch
                        val activeId = auction.eligiblePlayerIds.getOrNull(auction.activePlayerIndex)
                        val activePlayer = current.state.players.firstOrNull { it.id == activeId } ?: return@launch
                        if (activePlayer.isHuman) return@launch
                        val bid = MonopolyAI.auctionBid(current)
                        if (bid != null) engine!!.auctionBid(current, bid) else engine!!.auctionPass(current)
                    }
                    else -> return@launch
                }
                val next = current.copy(state = result.state.copy(log = result.state.log + "🤖 ${player.name}: ${result.events.lastOrNull().orEmpty()}".take(180)))
                storage.save(next)
                mutable.value = mutable.value.copy(snapshot = next)
            }
        }
    }
}
