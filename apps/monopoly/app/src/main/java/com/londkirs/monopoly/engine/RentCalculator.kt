package com.londkirs.monopoly.engine

object RentCalculator {
    fun calculate(board: BoardData, state: GameState, squareId: Int, diceTotal: Int): Int {
        val square = board.squares[squareId]
        val property = state.properties.firstOrNull { it.squareId == squareId } ?: return 0
        if (property.ownerId == null || property.mortgaged || property.ownerId == state.currentPlayer.id) return 0
        val ownerProperties = state.properties.filter { it.ownerId == property.ownerId }
        return when (square.type) {
            SquareType.PROPERTY -> {
                val groupIds = board.squares.filter { it.group == square.group }.map { it.id }.toSet()
                val ownsGroup = groupIds.isNotEmpty() && groupIds.all { id -> state.properties.any { it.squareId == id && it.ownerId == property.ownerId } }
                val groupUnmortgaged = groupIds.none { id -> state.properties.any { it.squareId == id && it.mortgaged } }
                if (property.houses > 0) square.rents.getOrElse(property.houses) { 0 }
                else if (property.hotel) square.rents.getOrElse(5) { 0 }
                else square.rents.firstOrNull().orZero() * if (ownsGroup && groupUnmortgaged) 2 else 1
            }
            SquareType.RAILROAD -> {
                val count = ownerProperties.count { board.squares[it.squareId].type == SquareType.RAILROAD && !it.mortgaged }
                listOf(0, 25, 50, 100, 200).getOrElse(count) { 200 }
            }
            SquareType.UTILITY -> {
                val count = ownerProperties.count { board.squares[it.squareId].type == SquareType.UTILITY && !it.mortgaged }
                diceTotal * if (count >= 2) 10 else 4
            }
            else -> 0
        }
    }
    private fun Int?.orZero() = this ?: 0
}
