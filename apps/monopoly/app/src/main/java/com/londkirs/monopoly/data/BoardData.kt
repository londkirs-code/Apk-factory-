package com.londkirs.monopoly.data

import android.content.Context
import com.londkirs.monopoly.engine.BoardData
import com.londkirs.monopoly.engine.CardDefinition
import com.londkirs.monopoly.engine.SquareDefinition
import com.londkirs.monopoly.engine.SquareType
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable private data class RawBoard(val squares: List<RawSquare>, val chanceCards: List<CardDefinition>, val communityChestCards: List<CardDefinition>)
@Serializable private data class RawSquare(val id: Int, val name: String, val type: String, val price: Int = 0, val group: String? = null, val rents: List<Int> = emptyList(), val houseCost: Int = 0)

object BoardDataLoader {
    private val json = Json { ignoreUnknownKeys = true }
    fun load(context: Context): BoardData {
        val raw = context.assets.open("monopoly_data.json").bufferedReader().use { json.decodeFromString<RawBoard>(it.readText()) }
        require(raw.squares.size == 40 && raw.squares.map { it.id } == (0..39).toList()) { "Board must contain exactly 40 ordered squares" }
        require(raw.chanceCards.size == 16 && raw.communityChestCards.size == 16) { "Each deck must contain 16 cards" }
        return BoardData(
            squares = raw.squares.map { SquareDefinition(it.id, it.name, when (it.type) {
                "corner" -> when (it.id) { 0 -> SquareType.GO; 10 -> SquareType.JAIL; 20 -> SquareType.FREE_PARKING; else -> SquareType.GO_TO_JAIL }
                "property" -> SquareType.PROPERTY
                "chest" -> SquareType.CHEST
                "chance" -> SquareType.CHANCE
                "tax" -> SquareType.TAX
                "railroad" -> SquareType.RAILROAD
                "utility" -> SquareType.UTILITY
                else -> error("Unknown square type: ${it.type}")
            }, it.price, it.group, it.rents, it.houseCost) },
            chanceCards = raw.chanceCards, communityChestCards = raw.communityChestCards
        )
    }
}
