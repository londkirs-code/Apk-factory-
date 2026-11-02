package com.londkirs.monopoly.engine

import kotlinx.serialization.Serializable

@Serializable enum class SquareType { GO, PROPERTY, CHEST, CHANCE, TAX, RAILROAD, UTILITY, JAIL, FREE_PARKING, GO_TO_JAIL }
@Serializable enum class AiLevel { EASY, MEDIUM, HARD }
@Serializable enum class GamePhase { SETUP, ROLL, BUY_OR_AUCTION, PAY_DEBT, JAIL_DECISION, AUCTION, GAME_OVER }
@Serializable enum class AuctionStatus { ACTIVE, COMPLETE }

@Serializable data class SquareDefinition(
    val id: Int, val name: String, val type: SquareType, val price: Int = 0,
    val group: String? = null, val rents: List<Int> = emptyList(), val houseCost: Int = 0
)

@Serializable data class CardDefinition(
    val title: String, val kind: String, val target: Int? = null, val amount: Int = 0,
    val house: Int = 0, val hotel: Int = 0
)

@Serializable data class BoardData(
    val squares: List<SquareDefinition>,
    val chanceCards: List<CardDefinition>,
    val communityChestCards: List<CardDefinition>
)

@Serializable data class Player(
    val id: String, val name: String, val color: Long, val cash: Int = 1500,
    val position: Int = 0, val isHuman: Boolean = true, val aiLevel: AiLevel = AiLevel.MEDIUM,
    val inJail: Boolean = false, val failedJailRolls: Int = 0, val jailFreeCards: Int = 0,
    val bankrupt: Boolean = false
)

@Serializable data class PropertyState(
    val squareId: Int, val ownerId: String? = null, val houses: Int = 0,
    val hotel: Boolean = false, val mortgaged: Boolean = false
)

@Serializable data class HouseRules(
    val freeParkingPot: Boolean = false, val doubleGoLanding: Boolean = false,
    val noAuctions: Boolean = false, val timedGameMinutes: Int = 0
)

@Serializable data class GameSettings(
    val startingCash: Int = 1500, val houseRules: HouseRules = HouseRules(),
    val soundEnabled: Boolean = false, val vibrationEnabled: Boolean = false
)

@Serializable data class AuctionState(
    val squareId: Int, val eligiblePlayerIds: List<String>, val activePlayerIndex: Int = 0,
    val highBid: Int = 0, val highBidderId: String? = null,
    val passedPlayerIds: List<String> = emptyList(), val status: AuctionStatus = AuctionStatus.ACTIVE
)

@Serializable data class GameState(
    val players: List<Player>, val properties: List<PropertyState>,
    val currentPlayerIndex: Int = 0, val phase: GamePhase = GamePhase.ROLL,
    val dice: List<Int> = listOf(1, 1), val consecutiveDoubles: Int = 0,
    val pendingRent: Int = 0, val pendingDebt: Int = 0, val debtCreditorId: String? = null,
    val pendingSquareId: Int? = null, val auction: AuctionState? = null,
    val chanceDeck: List<CardDefinition> = emptyList(), val chestDeck: List<CardDefinition> = emptyList(),
    val freeParkingPot: Int = 0, val startedAtEpochMillis: Long = 0L,
    val log: List<String> = emptyList(), val winnerId: String? = null,
    val turnRolled: Boolean = false
) {
    val currentPlayer: Player get() = players[currentPlayerIndex.coerceIn(players.indices)]
    val activePlayers: List<Player> get() = players.filterNot { it.bankrupt }
}

@Serializable data class GameSnapshot(val board: BoardData, val settings: GameSettings, val state: GameState)

data class EngineResult(val state: GameState, val events: List<String> = emptyList())
