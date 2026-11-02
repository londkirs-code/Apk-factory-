package com.londkirs.monopoly.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.londkirs.monopoly.ai.MonopolyAI
import com.londkirs.monopoly.data.BoardDataLoader
import com.londkirs.monopoly.engine.*
import com.londkirs.monopoly.storage.GameStorage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

data class MonopolyUiState(val board: BoardData? = null, val snapshot: GameSnapshot? = null, val loading: Boolean = true, val error: String? = null)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = GameStorage(application)
    private val feedback = GameFeedback(application)
    private val mutable = MutableStateFlow(MonopolyUiState())
    val uiState: StateFlow<MonopolyUiState> = mutable
    private var engine: MonopolyEngine? = null
    private var aiJob: Job? = null

    init {
        runCatching {
            val board = BoardDataLoader.load(application)
            engine = MonopolyEngine(board)
            val saved = storage.load()?.takeIf { it.board.squares.size == 40 }
            mutable.value = MonopolyUiState(board, saved, loading = false)
            if (saved != null) runAiIfNeeded()
        }.onFailure { mutable.value = MonopolyUiState(loading = false, error = it.message ?: "שגיאה בטעינת המשחק") }
    }

    fun startGame(names: List<String>, humanFlags: List<Boolean>, startingCash: Int, rules: HouseRules, aiLevel: AiLevel, soundEnabled: Boolean, vibrationEnabled: Boolean, colors: List<Long>) {
        val board = mutable.value.board ?: return
        val palette = listOf(0xFFE85D4A, 0xFF2878D0, 0xFF25A56A, 0xFF9B59B6, 0xFFF1B82D, 0xFF4D5966)
        val players = names.take(6).mapIndexed { index, name ->
            Player(id = "p$index", name = name.ifBlank { "שחקן ${index + 1}" }, color = colors.getOrElse(index) { palette[index % palette.size] },
                isHuman = humanFlags.getOrElse(index) { true }, aiLevel = aiLevel)
        }
        val settings = GameSettings(startingCash = startingCash.coerceAtLeast(500), houseRules = rules, soundEnabled = soundEnabled, vibrationEnabled = vibrationEnabled)
        val snapshot = engine!!.newGame(players, settings)
        storage.save(snapshot)
        mutable.value = mutable.value.copy(snapshot = snapshot)
        runAiIfNeeded()
    }

    fun roll() {
        mutable.value.snapshot?.settings?.let { feedback.dice(it.soundEnabled, it.vibrationEnabled) }
        dispatch { engine!!.roll(it) }
    }
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
    fun settleDebt() = dispatch { DebtManager.settle(it, it.state.currentPlayer.id, it.state.pendingDebt, it.state.debtCreditorId) }
    fun declareBankruptcy() = dispatch { DebtManager.declareBankruptcy(it, it.state.currentPlayer.id, it.state.debtCreditorId, it.settings.houseRules.noAuctions) }

    fun proposeTrade(offer: TradeOffer) {
        val current = mutable.value.snapshot ?: return
        val problem = TradeManager.validate(current, offer)
        if (problem != null) {
            val next = current.copy(state = current.state.copy(log = current.state.log + problem))
            storage.save(next)
            mutable.value = mutable.value.copy(snapshot = next)
            return
        }
        val target = current.state.players.first { it.id == offer.toPlayerId }
        val source = current.state.players.first { it.id == offer.fromPlayerId }
        val attempted = if (!source.isHuman) source.id else current.state.aiTradeAttemptedPlayerId
        val next = current.copy(state = current.state.copy(pendingTrade = offer, aiTradeAttemptedPlayerId = attempted, log = current.state.log + "הצעת מסחר נשלחה ל-" + target.name + "."))
        storage.save(next)
        mutable.value = mutable.value.copy(snapshot = next)
        if (!target.isHuman) viewModelScope.launch {
            delay(Random.nextLong(500, 1501))
            val latest = mutable.value.snapshot ?: return@launch
            val pending = latest.state.pendingTrade ?: return@launch
            val received = pending.cashFrom + pending.propertiesFrom.sumOf { latest.board.squares[it].price } + pending.jailCardsFrom * 50
            val given = pending.cashTo + pending.propertiesTo.sumOf { latest.board.squares[it].price } + pending.jailCardsTo * 50
            if (MonopolyAI.acceptTrade(received, given, target.aiLevel)) acceptTrade() else rejectTrade()
        }
    }

    fun acceptTrade() {
        val current = mutable.value.snapshot ?: return
        val offer = current.state.pendingTrade ?: return
        val result = TradeManager.accept(current, offer)
        val next = current.copy(state = result.state.copy(pendingTrade = null, log = (result.state.log + result.events).takeLast(250)))
        storage.save(next)
        mutable.value = mutable.value.copy(snapshot = next)
        runAiIfNeeded()
    }

    fun dismissCard() {
        val current = mutable.value.snapshot ?: return
        val next = current.copy(state = current.state.copy(pendingCard = null))
        storage.save(next)
        mutable.value = mutable.value.copy(snapshot = next)
        runAiIfNeeded()
    }

    fun rejectTrade() {
        val current = mutable.value.snapshot ?: return
        val offer = current.state.pendingTrade ?: return
        val recipient = current.state.players.firstOrNull { it.id == offer.toPlayerId }?.name ?: "השחקן"
        val next = current.copy(state = current.state.copy(pendingTrade = null, log = (current.state.log + recipient + " דחה את הצעת המסחר.").takeLast(250)))
        storage.save(next)
        mutable.value = mutable.value.copy(snapshot = next)
        runAiIfNeeded()
    }

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
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            repeat(40) {
                val snapshot = mutable.value.snapshot ?: return@launch
                val state = snapshot.state
                if (state.pendingTrade != null || state.pendingCard != null) return@launch
                val player = if (state.phase == GamePhase.AUCTION) {
                    val activeId = state.auction?.eligiblePlayerIds?.getOrNull(state.auction.activePlayerIndex)
                    state.players.firstOrNull { it.id == activeId } ?: return@launch
                } else state.currentPlayer
                if (player.isHuman || state.phase == GamePhase.GAME_OVER || state.phase == GamePhase.JAIL_DECISION) return@launch
                delay(Random.nextLong(500, 1501))
                val current = mutable.value.snapshot ?: return@launch
                val result = when (current.state.phase) {
                    GamePhase.ROLL -> {
                        if (current.state.turnRolled && current.state.consecutiveDoubles == 0) {
                            val buildId = current.state.properties.firstOrNull { it.ownerId == player.id && MonopolyAI.shouldBuild(current, it.squareId) }?.squareId
                            if (buildId != null) engine!!.build(current, buildId) else {
                                val offer = MonopolyAI.proposeMonopolyCompletionTrade(current, player.id)
                                if (offer != null) {
                                    proposeTrade(offer)
                                    return@launch
                                }
                                engine!!.endTurn(current)
                            }
                        } else engine!!.roll(current)
                    }
                    GamePhase.BUY_OR_AUCTION -> {
                        val id = current.state.pendingSquareId ?: return@launch
                        if (MonopolyAI.shouldBuy(current, id)) engine!!.buy(current) else engine!!.declinePurchase(current)
                    }
                    GamePhase.PAY_DEBT -> {
                        val debtor = current.state.currentPlayer
                        if (debtor.cash >= current.state.pendingDebt) {
                            DebtManager.settle(current, debtor.id, current.state.pendingDebt, current.state.debtCreditorId)
                        } else {
                            val building = current.state.properties.firstOrNull { it.ownerId == debtor.id && (it.houses > 0 || it.hotel) }
                            val mortgageable = current.state.properties.firstOrNull { property ->
                                property.ownerId == debtor.id && !property.mortgaged &&
                                    current.board.squares.filter { it.group == current.board.squares[property.squareId].group }
                                        .none { groupSquare -> current.state.properties.any { it.squareId == groupSquare.id && (it.houses > 0 || it.hotel) } }
                            }
                            val saleResult = building?.let { engine!!.sellBuilding(current, it.squareId) }
                            when {
                                saleResult != null && saleResult.state.currentPlayer.cash > debtor.cash -> saleResult
                                mortgageable != null -> engine!!.mortgage(current, mortgageable.squareId)
                                else -> DebtManager.declareBankruptcy(current, debtor.id, current.state.debtCreditorId, current.settings.houseRules.noAuctions)
                            }
                        }
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
