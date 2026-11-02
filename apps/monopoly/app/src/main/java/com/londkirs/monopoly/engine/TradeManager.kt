package com.londkirs.monopoly.engine

import kotlinx.serialization.Serializable

@Serializable data class TradeOffer(
    val fromPlayerId: String, val toPlayerId: String,
    val cashFrom: Int = 0, val cashTo: Int = 0,
    val propertiesFrom: List<Int> = emptyList(), val propertiesTo: List<Int> = emptyList(),
    val jailCardsFrom: Int = 0, val jailCardsTo: Int = 0
)

object TradeManager {
    fun validate(snapshot: GameSnapshot, offer: TradeOffer): String? {
        val s = snapshot.state
        val from = s.players.firstOrNull { it.id == offer.fromPlayerId } ?: return "שחקן מציע לא נמצא."
        val to = s.players.firstOrNull { it.id == offer.toPlayerId } ?: return "שחקן מקבל לא נמצא."
        if (from.bankrupt || to.bankrupt || from.id == to.id) return "עסקה בין שחקנים פעילים בלבד."
        if (offer.cashFrom < 0 || offer.cashTo < 0 || from.cash < offer.cashFrom || to.cash < offer.cashTo) return "אין מספיק כסף לביצוע העסקה."
        if (offer.jailCardsFrom < 0 || offer.jailCardsTo < 0 || from.jailFreeCards < offer.jailCardsFrom || to.jailFreeCards < offer.jailCardsTo) return "אין מספיק כרטיסי יציאה מהכלא."
        val fromOwned = s.properties.filter { it.ownerId == from.id }.map { it.squareId }.toSet()
        val toOwned = s.properties.filter { it.ownerId == to.id }.map { it.squareId }.toSet()
        if (!fromOwned.containsAll(offer.propertiesFrom) || !toOwned.containsAll(offer.propertiesTo)) return "אחד הנכסים אינו בבעלות המציע המתאים."
        val touched = offer.propertiesFrom + offer.propertiesTo
        if (touched.any { id -> s.properties.any { it.squareId == id && (it.houses > 0 || it.hotel) } }) return "יש למכור את כל המבנים לפני מסחר בנכס."
        return null
    }

    fun accept(snapshot: GameSnapshot, offer: TradeOffer): EngineResult {
        validate(snapshot, offer)?.let { return EngineResult(snapshot.state, listOf(it)) }
        val s = snapshot.state
        val from = s.players.first { it.id == offer.fromPlayerId }
        val to = s.players.first { it.id == offer.toPlayerId }
        val fromCards = from.jailFreeCardSources + List((from.jailFreeCards - from.jailFreeCardSources.size).coerceAtLeast(0)) { "chance" }
        val toCards = to.jailFreeCardSources + List((to.jailFreeCards - to.jailFreeCardSources.size).coerceAtLeast(0)) { "chance" }
        val movedFrom = fromCards.take(offer.jailCardsFrom)
        val movedTo = toCards.take(offer.jailCardsTo)
        val players = s.players.map { p ->
            when (p.id) {
                offer.fromPlayerId -> p.copy(
                    cash = p.cash - offer.cashFrom + offer.cashTo,
                    jailFreeCards = p.jailFreeCards - offer.jailCardsFrom + offer.jailCardsTo,
                    jailFreeCardSources = fromCards.drop(offer.jailCardsFrom) + movedTo
                )
                offer.toPlayerId -> p.copy(
                    cash = p.cash - offer.cashTo + offer.cashFrom,
                    jailFreeCards = p.jailFreeCards - offer.jailCardsTo + offer.jailCardsFrom,
                    jailFreeCardSources = toCards.drop(offer.jailCardsTo) + movedFrom
                )
                else -> p
            }
        }
        val properties = s.properties.map { p ->
            when (p.squareId) {
                in offer.propertiesFrom -> p.copy(ownerId = offer.toPlayerId)
                in offer.propertiesTo -> p.copy(ownerId = offer.fromPlayerId)
                else -> p
            }
        }
        return EngineResult(s.copy(players = players, properties = properties), listOf("העסקה בין השחקנים אושרה."))
    }
}
