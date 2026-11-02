package com.londkirs.monopoly.engine

import org.junit.Assert.*
import org.junit.Test

internal object TestFixtures {
    fun board(): BoardData {
        val squares = (0..39).map { id ->
            when (id) {
                1 -> SquareDefinition(1, "Mediterranean", SquareType.PROPERTY, 60, "brown", listOf(2, 10, 30, 90, 160, 250), 50)
                3 -> SquareDefinition(3, "Baltic", SquareType.PROPERTY, 60, "brown", listOf(4, 20, 60, 180, 320, 450), 50)
                5, 15, 25, 35 -> SquareDefinition(id, "Railroad $id", SquareType.RAILROAD, 200)
                12, 28 -> SquareDefinition(id, "Utility $id", SquareType.UTILITY, 150)
                4, 38 -> SquareDefinition(id, "Tax", SquareType.TAX, 100)
                10 -> SquareDefinition(id, "Jail", SquareType.JAIL)
                20 -> SquareDefinition(id, "Parking", SquareType.FREE_PARKING)
                30 -> SquareDefinition(id, "Go Jail", SquareType.GO_TO_JAIL)
                else -> SquareDefinition(id, "Square $id", SquareType.GO)
            }
        }
        return BoardData(squares, emptyList(), emptyList())
    }
    fun players() = listOf(Player("a", "Alice", 0xFFFF0000, cash = 1500), Player("b", "Bob", 0xFF0000FF, cash = 1500))
    fun snapshot(properties: List<PropertyState>, phase: GamePhase = GamePhase.ROLL, pending: Int? = null, currentPlayerIndex: Int = 0): GameSnapshot {
        val board = board()
        val players = players()
        val state = GameState(players = players, properties = properties, phase = phase, pendingSquareId = pending, currentPlayerIndex = currentPlayerIndex)
        return GameSnapshot(board, GameSettings(), state)
    }
}

class RentCalculatorTest {
    @Test fun monopolyDoublesUndevelopedRent() {
        val board = TestFixtures.board()
        val props = listOf(PropertyState(1, "a"), PropertyState(3, "a"))
        val snapshot = TestFixtures.snapshot(props, currentPlayerIndex = 1)
        assertEquals(4, RentCalculator.calculate(board, snapshot.state, 1, 7))
    }

    @Test fun railroadRentScalesByOwnedRailroads() {
        val board = TestFixtures.board()
        val props = listOf(PropertyState(5, "a"), PropertyState(15, "a"), PropertyState(25, "a"))
        val snapshot = TestFixtures.snapshot(props, currentPlayerIndex = 1)
        assertEquals(100, RentCalculator.calculate(board, snapshot.state, 5, 7))
    }

    @Test fun utilityRentUsesFourOrTenTimesDice() {
        val board = TestFixtures.board()
        val one = TestFixtures.snapshot(listOf(PropertyState(12, "a")), currentPlayerIndex = 1)
        val two = TestFixtures.snapshot(listOf(PropertyState(12, "a"), PropertyState(28, "a")), currentPlayerIndex = 1)
        assertEquals(28, RentCalculator.calculate(board, one.state, 12, 7))
        assertEquals(70, RentCalculator.calculate(board, two.state, 12, 7))
    }
}

class BuildingManagerTest {
    @Test fun buildingMustBeEven() {
        val board = TestFixtures.board()
        val snapshot = TestFixtures.snapshot(listOf(PropertyState(1, "a"), PropertyState(3, "a", houses = 1)))
        val engine = MonopolyEngine(board)
        assertTrue(engine.build(snapshot, 1).state.properties.first { it.squareId == 1 }.houses == 1)
        assertTrue(engine.build(snapshot, 3).events.any { it.contains("שווה") })
    }

    @Test fun mortgagePaysHalfPriceAndRequiresNoBuildings() {
        val board = TestFixtures.board()
        val blocked = TestFixtures.snapshot(listOf(PropertyState(1, "a", houses = 1), PropertyState(3, "a")))
        assertTrue(MonopolyEngine(board).mortgage(blocked, 1).events.any { it.contains("למכור") })
        val clear = TestFixtures.snapshot(listOf(PropertyState(1, "a"), PropertyState(3, "a")))
        val result = MonopolyEngine(board).mortgage(clear, 1)
        assertEquals(1530, result.state.currentPlayer.cash)
        assertTrue(result.state.properties.first { it.squareId == 1 }.mortgaged)
    }
}

class JailManagerTest {
    @Test fun jailExitOptionsAreCalculated() {
        val jailed = Player("a", "A", 1, cash = 50, inJail = true, failedJailRolls = 2, jailFreeCards = 1)
        assertTrue(JailManager.canPay(jailed))
        assertTrue(JailManager.canUseCard(jailed))
        assertEquals(1, JailManager.attemptsRemaining(jailed))
    }

    @Test fun thirdFailedAttemptForcesPaymentAndMovement() {
        val board = TestFixtures.board()
        val players = TestFixtures.players().map { if (it.id == "a") it.copy(position = 10, inJail = true, failedJailRolls = 2) else it }
        val state = GameState(players = players, properties = emptyList())
        val result = MonopolyEngine(board).roll(GameSnapshot(board, GameSettings(), state), listOf(1, 2)).state
        val alice = result.players.first { it.id == "a" }
        assertFalse(alice.inJail)
        assertEquals(1450, alice.cash)
        assertEquals(13, alice.position)
    }

    @Test fun threeConsecutiveDoublesSendPlayerToJail() {
        val board = TestFixtures.board()
        val engine = MonopolyEngine(board)
        var snapshot = GameSnapshot(board, GameSettings(), GameState(players = TestFixtures.players(), properties = emptyList()))
        snapshot = snapshot.copy(state = engine.roll(snapshot, listOf(1, 1)).state)
        snapshot = snapshot.copy(state = engine.roll(snapshot, listOf(1, 1)).state)
        snapshot = snapshot.copy(state = engine.roll(snapshot, listOf(1, 1)).state)
        val alice = snapshot.state.players.first { it.id == "a" }
        assertTrue(alice.inJail)
        assertEquals(10, alice.position)
        assertEquals(1, snapshot.state.currentPlayerIndex)
    }
}

class DebtManagerTest {
    @Test fun bankruptcyTransfersAssetsToCreditorAndMarksPlayer() {
        val snapshot = TestFixtures.snapshot(listOf(PropertyState(1, "a"), PropertyState(3, "a", mortgaged = true)))
        val result = DebtManager.declareBankruptcy(snapshot, "a", "b")
        assertTrue(result.state.players.first { it.id == "a" }.bankrupt)
        assertTrue(result.state.properties.all { it.ownerId == "b" })
        assertEquals("b", result.state.properties.first { it.squareId == 3 }.ownerId)
    }

    @Test fun bankruptcyToBankStartsAuctionsForEveryAsset() {
        val snapshot = TestFixtures.snapshot(listOf(PropertyState(1, "a"), PropertyState(3, "a")))
        val result = DebtManager.declareBankruptcy(snapshot, "a", null).state
        assertEquals(GamePhase.AUCTION, result.phase)
        assertEquals(1, result.auction?.squareId)
        assertEquals(listOf(3), result.bankruptcyAuctionQueue)
        assertTrue(result.bankruptcyAuctionActive)
    }
}

class AuctionManagerTest {
    @Test fun highestBidderWinsAfterOtherPlayerPasses() {
        val board = TestFixtures.board()
        val snapshot = TestFixtures.snapshot(listOf(PropertyState(1)), GamePhase.BUY_OR_AUCTION, pending = 1)
        val engine = MonopolyEngine(board)
        val auction = engine.declinePurchase(snapshot).state
        val bid = engine.auctionBid(GameSnapshot(board, snapshot.settings, auction), 70).state
        val result = engine.auctionPass(GameSnapshot(board, snapshot.settings, bid)).state
        assertEquals("a", result.properties.first { it.squareId == 1 }.ownerId)
        assertEquals(70, result.players.first { it.id == "a" }.cash.let { 1500 - it })
    }
}
