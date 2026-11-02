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
        val group = snapshot.board.squares.filter { it.group == square.group }
        return group.all { item -> snapshot.state.properties.any { it.squareId == item.id && it.ownerId == player.id && !it.mortgaged } }
    }

    fun acceptTrade(valueReceived: Int, valueGiven: Int, level: AiLevel): Boolean {
        val margin = when (level) { AiLevel.EASY -> 1.15; AiLevel.MEDIUM -> 1.0; AiLevel.HARD -> 0.9 }
        return valueReceived >= valueGiven * margin
    }
}
