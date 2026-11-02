package com.londkirs.monopoly.engine

object DebtManager {
    fun settle(snapshot: GameSnapshot, playerId: String, amount: Int, creditorId: String? = null): EngineResult {
        require(amount >= 0)
        val s = snapshot.state
        val debtor = s.players.first { it.id == playerId }
        if (debtor.cash < amount) return EngineResult(
            s.copy(phase = GamePhase.PAY_DEBT, pendingDebt = amount, debtCreditorId = creditorId),
            listOf("נדרש גיוס של " + (amount - debtor.cash) + " נוספים.")
        )
        val players = s.players.map { p ->
            when (p.id) {
                playerId -> p.copy(cash = p.cash - amount)
                creditorId -> p.copy(cash = p.cash + amount)
                else -> p
            }
        }
        return EngineResult(
            s.copy(players = players, pendingDebt = 0, debtCreditorId = null, phase = GamePhase.ROLL),
            listOf(debtor.name + " שילם חוב של " + amount + ".")
        )
    }

    fun declareBankruptcy(snapshot: GameSnapshot, playerId: String, creditorId: String?, noAuctions: Boolean = false): EngineResult {
        val s = snapshot.state
        val debtor = s.players.first { it.id == playerId }
        val owned = s.properties.filter { it.ownerId == playerId }
        val housesReturned = owned.sumOf { it.houses + if (it.hotel) 4 else 0 }
        val hotelsReturned = owned.count { it.hotel }
        val interestDue = if (creditorId == null) 0 else owned.filter { it.mortgaged }.sumOf { (snapshot.board.squares[it.squareId].price * 0.05).toInt() }
        val creditorCash = creditorId?.let { id -> s.players.first { it.id == id }.cash } ?: 0
        val creditorCanPayInterest = creditorCash + debtor.cash >= interestDue
        val properties = s.properties.map { p ->
            if (p.ownerId == playerId) p.copy(ownerId = creditorId, houses = 0, hotel = false) else p
        }
        val players = s.players.map { p ->
            when (p.id) {
                playerId -> p.copy(cash = 0, bankrupt = true)
                creditorId -> p.copy(cash = p.cash + debtor.cash - if (creditorCanPayInterest) interestDue else 0)
                else -> p
            }
        }
        val next = s.copy(
            players = players, properties = properties, pendingDebt = 0, debtCreditorId = null,
            housesInBank = (s.housesInBank + housesReturned).coerceAtMost(32),
            hotelsInBank = (s.hotelsInBank + hotelsReturned).coerceAtMost(12)
        )
        val activeIndices = players.indices.filter { !players[it].bankrupt }
        val nextIndex = activeIndices.firstOrNull()?.let { first ->
            (1..players.size).map { (s.currentPlayerIndex + it) % players.size }.firstOrNull { !players[it].bankrupt } ?: first
        } ?: 0
        val finished = when {
            activeIndices.size == 1 -> next.copy(phase = GamePhase.GAME_OVER, winnerId = players[activeIndices.first()].id)
            activeIndices.isEmpty() -> next.copy(phase = GamePhase.GAME_OVER, winnerId = null)
            creditorId == null && owned.isNotEmpty() && !noAuctions -> {
                val eligible = activeIndices.map { players[it].id }
                next.copy(
                    currentPlayerIndex = nextIndex, phase = GamePhase.AUCTION,
                    auction = AuctionState(owned.first().squareId, eligible),
                    bankruptcyAuctionQueue = owned.drop(1).map { it.squareId }, bankruptcyAuctionActive = true,
                    turnRolled = false, consecutiveDoubles = 0
                )
            }
            else -> next.copy(currentPlayerIndex = nextIndex, phase = GamePhase.ROLL, turnRolled = false, consecutiveDoubles = 0)
        }
        val creditorIndex = creditorId?.let { id -> players.indexOfFirst { it.id == id } } ?: -1
        val finalState = if (creditorId != null && interestDue > 0 && !creditorCanPayInterest && creditorIndex >= 0) {
            finished.copy(currentPlayerIndex = creditorIndex, phase = GamePhase.PAY_DEBT, pendingDebt = interestDue, debtCreditorId = null, turnRolled = false, consecutiveDoubles = 0)
        } else finished
        val recipient = creditorId?.let { id -> players.firstOrNull { it.id == id }?.name } ?: "הבנק"
        val interestMessage = if (creditorId != null && interestDue > 0) " ריבית המשכון: " + interestDue + "." else ""
        return EngineResult(finalState, listOf(debtor.name + " פשט רגל. נכסיו הועברו אל " + recipient + "." + interestMessage))
    }
}
