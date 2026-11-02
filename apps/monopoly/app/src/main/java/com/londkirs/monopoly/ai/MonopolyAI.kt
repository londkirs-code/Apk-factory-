package com.londkirs.monopoly.ai

import com.londkirs.monopoly.engine.*

object MonopolyAI {
    fun shouldBuy(snapshot: GameSnapshot, squareId: Int): Boolean {
        val s = snapshot.state
        val player = s.currentPlayer
        val square = snapshot.board.squares[squareId]
        val reserve = when (player.aiLevel) { AiLevel.EASY -> 450; AiLevel.MEDIUM -> 250; AiLevel.HARD -> 100 }
        val groupOwned = snapshot.board.squares.filter { it.group != null && it.group == square.group }
            .count { sq -> s.properties.any { it.squareId == sq.id && it.ownerId == player.id } }
        val bonus = when (player.aiLevel) {
            AiLevel.EASY -> 0
            AiLevel.MEDIUM -> groupOwned * 45
            AiLevel.HARD -> if (square.group == "orange" || square.group == "red") 180 else if (square.type == SquareType.RAILROAD) 120 else groupOwned * 90
        }
        return player.cash - square.price >= reserve - bonus
    }

    fun auctionBid(snapshot: GameSnapshot): Int? {
        val auction = snapshot.state.auction ?: return null
        val playerId = auction.eligiblePlayerIds.getOrNull(auction.activePlayerIndex) ?: return null
        val player = snapshot.state.players.firstOrNull { it.id == playerId } ?: return null
        val square = snapshot.board.squares[auction.squareId]
        val factor = when (player.aiLevel) { AiLevel.EASY -> 0.65; AiLevel.MEDIUM -> 1.0; AiLevel.HARD -> 1.45 }
        val strategic = if (player.aiLevel == AiLevel.HARD && (square.group == "orange" || square.group == "red")) 1.35 else 1.0
        val cap = (square.price * factor * strategic).toInt().coerceAtMost(player.cash - 100)
        val next = auction.highBid + 10
        return if (next <= cap) next else null
    }

    fun shouldBuild(snapshot: GameSnapshot, squareId: Int): Boolean {
        val player = snapshot.state.currentPlayer
        val square = snapshot.board.squares[squareId]
        if (square.group == null || player.cash < square.houseCost + 250) return false
        val property = snapshot.state.properties.firstOrNull { it.squareId == squareId } ?: return false
        if (property.hotel || (property.houses == 4 && snapshot.state.hotelsInBank == 0) ||
            (property.houses < 4 && snapshot.state.housesInBank == 0)) return false
        val group = snapshot.board.squares.filter { it.group == square.group }
        val groupProperties = group.map { item -> snapshot.state.properties.firstOrNull { it.squareId == item.id } ?: return false }
        if (groupProperties.any { it.ownerId != player.id || it.mortgaged }) return false
        val minimum = groupProperties.minOf { if (it.hotel) 5 else it.houses }
        return property.houses <= minimum
    }

    fun proposeMonopolyCompletionTrade(snapshot: GameSnapshot, playerId: String): TradeOffer? {
        val player = snapshot.state.players.firstOrNull { it.id == playerId && !it.isHuman && !it.bankrupt } ?: return null
        if (player.aiLevel != AiLevel.HARD || snapshot.state.aiTradeAttemptedPlayerId == playerId) return null
        val groups = snapshot.board.squares.filter { it.type == SquareType.PROPERTY && it.group != null }.groupBy { it.group }
        for ((_, group) in groups) {
            val missing = group.filter { square -> snapshot.state.properties.firstOrNull { it.squareId == square.id }?.ownerId != playerId }
            val ownedCount = group.size - missing.size
            if (ownedCount != group.size - 1 || missing.size != 1) continue
            val targetSquare = missing.first()
            val targetId = snapshot.state.properties.firstOrNull { it.squareId == targetSquare.id }?.ownerId ?: continue
            val target = snapshot.state.players.firstOrNull { it.id == targetId && it.isHuman && !it.bankrupt } ?: continue
            val offer = (targetSquare.price * 1.2).toInt()
            if (player.cash < offer + 100) continue
            return TradeOffer(fromPlayerId = player.id, toPlayerId = target.id, cashFrom = offer, propertiesTo = listOf(targetSquare.id))
        }
        return null
    }

    fun acceptTrade(valueReceived: Int, valueGiven: Int, level: AiLevel): Boolean {
        val margin = when (level) { AiLevel.EASY -> 1.15; AiLevel.MEDIUM -> 1.0; AiLevel.HARD -> 0.9 }
        return valueReceived >= valueGiven * margin
    }
}
