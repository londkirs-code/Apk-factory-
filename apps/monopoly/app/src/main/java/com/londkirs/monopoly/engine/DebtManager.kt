package com.londkirs.monopoly.engine

object DebtManager {
    fun settle(snapshot: GameSnapshot, playerId: String, amount: Int, creditorId: String? = null): EngineResult {
        require(amount >= 0)
        val s = snapshot.state
        val debtor = s.players.first { it.id == playerId }
        if (debtor.cash < amount) return EngineResult(
            s.copy(phase = GamePhase.PAY_DEBT, pendingDebt = amount, debtCreditorId = creditorId),
            listOf("נדרש גיוס של ${amount - debtor.cash} נוספים.")
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
            listOf("${debtor.name} שילם חוב של $amount.")
        )
    }

    fun declareBankruptcy(snapshot: GameSnapshot, playerId: String, creditorId: String?): EngineResult {
        val s = snapshot.state
        val debtor = s.players.first { it.id == playerId }
        val properties = s.properties.map { p ->
            if (p.ownerId == playerId) p.copy(ownerId = creditorId, houses = 0, hotel = false) else p
        }
        val players = s.players.map { p -> if (p.id == playerId) p.copy(cash = 0, bankrupt = true) else p }
        val next = s.copy(players = players, properties = properties, pendingDebt = 0, debtCreditorId = null)
        val activeIndices = players.indices.filter { !players[it].bankrupt }
        val finished = when {
            activeIndices.size == 1 -> next.copy(phase = GamePhase.GAME_OVER, winnerId = players[activeIndices.first()].id)
            activeIndices.isEmpty() -> next.copy(phase = GamePhase.GAME_OVER, winnerId = null)
            else -> {
                val nextIndex = (1..players.size).map { (s.currentPlayerIndex + it) % players.size }.first { !players[it].bankrupt }
                next.copy(currentPlayerIndex = nextIndex, phase = GamePhase.ROLL, turnRolled = false, consecutiveDoubles = 0)
            }
        }
        val recipient = creditorId?.let { id -> players.firstOrNull { it.id == id }?.name } ?: "הבנק"
        return EngineResult(finished, listOf("${debtor.name} פשט רגל. נכסיו הועברו אל $recipient."))
    }
}
