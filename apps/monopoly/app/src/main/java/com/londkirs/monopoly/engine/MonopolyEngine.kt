package com.londkirs.monopoly.engine

import kotlin.random.Random

/** Pure Kotlin rules engine. It has no Android or Compose dependencies. */
class MonopolyEngine(private val board: BoardData, private val random: Random = Random.Default) {
    fun newGame(players: List<Player>, settings: GameSettings, now: Long = System.currentTimeMillis()): GameSnapshot {
        require(players.size in 2..6) { "A game needs 2–6 players" }
        require(players.map { it.id }.distinct().size == players.size) { "Player IDs must be unique" }
        val normalized = players.map { it.copy(cash = settings.startingCash, position = 0, bankrupt = false, inJail = false, failedJailRolls = 0) }
        val properties = board.squares.filter { it.type in setOf(SquareType.PROPERTY, SquareType.RAILROAD, SquareType.UTILITY) }.map { PropertyState(it.id) }
        return GameSnapshot(board, settings, GameState(
            players = normalized, properties = properties,
            chanceDeck = board.chanceCards.shuffled(random), chestDeck = board.communityChestCards.shuffled(random),
            startedAtEpochMillis = now, log = listOf("המשחק התחיל. ${normalized.first().name} מתחיל.")
        ))
    }

    fun roll(snapshot: GameSnapshot, forcedDice: List<Int>? = null): EngineResult {
        val s = snapshot.state
        if (s.phase != GamePhase.ROLL || s.currentPlayer.bankrupt) return result(s, "אי אפשר לזרוק קוביות עכשיו.")
        val outstandingDebt = s.currentPlayer.debts.entries.firstOrNull()
        if (outstandingDebt != null) {
            val debtor = s.currentPlayer
            val creditor = s.players.firstOrNull { it.id == outstandingDebt.key && !it.bankrupt }
            if (debtor.cash >= outstandingDebt.value) {
                var paid = changeCash(s, debtor.id, -outstandingDebt.value)
                if (creditor != null) paid = changeCash(paid, creditor.id, outstandingDebt.value)
                paid = updatePlayer(paid, debtor.id) { it.copy(debts = it.debts - outstandingDebt.key) }
                return result(paid, debtor.name + " שילם חוב של " + outstandingDebt.value + " ל-" + (creditor?.name ?: "הבנק") + ".")
            }
            return result(s.copy(phase = GamePhase.PAY_DEBT, pendingDebt = outstandingDebt.value, debtCreditorId = outstandingDebt.key),
                debtor.name + " חייב " + outstandingDebt.value + " לפני שיוכל לזרוק קוביות.")
        }
        val dice = forcedDice ?: listOf(random.nextInt(1, 7), random.nextInt(1, 7))
        require(dice.size == 2 && dice.all { it in 1..6 })
        val total = dice.sum()
        val isDouble = dice[0] == dice[1]
        var state = s.copy(dice = dice, turnRolled = true)
        val player = state.currentPlayer
        val events = mutableListOf("${player.name} זרק ${dice[0]} ו-${dice[1]} (סה״כ $total).")

        if (player.inJail) {
            if (isDouble) {
                state = updatePlayer(state, player.id) { it.copy(inJail = false, failedJailRolls = 0) }
                events += "${player.name} יצא מהכלא בזכות דאבל."
                state = move(state, player.id, total, events, snapshot.settings)
            } else if (player.failedJailRolls >= 2) {
                state = changeCash(state, player.id, -50)
                events += "${player.name} שילם 50 ויצא מהכלא לאחר שלושה ניסיונות."
                state = updatePlayer(state, player.id) { it.copy(inJail = false, failedJailRolls = 0) }
                state = move(state, player.id, total, events, snapshot.settings)
            } else {
                state = updatePlayer(state, player.id) { it.copy(failedJailRolls = it.failedJailRolls + 1) }
                events += "הניסיון לצאת מהכלא נכשל."
                state = finishTurn(state, false)
            }
            return EngineResult(appendLog(state, events), events)
        }

        val doubles = if (isDouble) state.consecutiveDoubles + 1 else 0
        state = state.copy(consecutiveDoubles = doubles)
        if (doubles >= 3) {
            state = sendToJail(state, player.id)
            events += "${player.name} זרק שלושה דאבלים ברצף ונשלח לכלא."
            state = finishTurn(state, false)
        } else {
            state = move(state, player.id, total, events, snapshot.settings)
            if (state.phase == GamePhase.ROLL && isDouble) events += "דאבל! ${player.name} מקבל תור נוסף."
        }
        return EngineResult(appendLog(state, events), events)
    }

    fun buy(snapshot: GameSnapshot): EngineResult {
        val s = snapshot.state
        val squareId = s.pendingSquareId ?: return result(s, "אין נכס שממתין לרכישה.")
        if (s.phase != GamePhase.BUY_OR_AUCTION) return result(s, "שלב הרכישה אינו פעיל.")
        val square = board.squares[squareId]
        val player = s.currentPlayer
        if (player.cash < square.price) return result(s, "אין מספיק כסף לרכישת הנכס.")
        val state = changeCash(s, player.id, -square.price).copy(
            properties = s.properties.map { if (it.squareId == squareId) it.copy(ownerId = player.id) else it },
            pendingSquareId = null
        )
        val events = listOf("${player.name} רכש את ${square.name} תמורת ${square.price}.")
        return finishAfterLanding(state, events)
    }

    fun declinePurchase(snapshot: GameSnapshot): EngineResult {
        val s = snapshot.state
        val id = s.pendingSquareId ?: return result(s, "אין נכס למכירה.")
        if (s.phase != GamePhase.BUY_OR_AUCTION) return result(s, "שלב הרכישה אינו פעיל.")
        if (snapshot.settings.houseRules.noAuctions) {
            return finishAfterLanding(s.copy(pendingSquareId = null), listOf("${board.squares[id].name} נשאר פנוי (מכרזים כבויים)."))
        }
        val auction = AuctionState(id, s.activePlayers.map { it.id })
        val next = s.copy(phase = GamePhase.AUCTION, auction = auction)
        return EngineResult(appendLog(next, listOf("מתחיל מכרז על ${board.squares[id].name}.")))
    }

    fun auctionBid(snapshot: GameSnapshot, amount: Int): EngineResult {
        val s = snapshot.state
        val a = s.auction ?: return result(s, "אין מכרז פעיל.")
        val bidder = a.eligiblePlayerIds.getOrNull(a.activePlayerIndex) ?: return result(s, "אין מציע פעיל.")
        val player = s.players.first { it.id == bidder }
        if (bidder in a.passedPlayerIds || amount <= a.highBid || amount > player.cash) return result(s, "הצעה לא תקינה.")
        return advanceAuction(s.copy(auction = a.copy(highBid = amount, highBidderId = bidder)), listOf("${player.name} מציע $amount."))
    }

    fun auctionPass(snapshot: GameSnapshot): EngineResult {
        val s = snapshot.state
        val a = s.auction ?: return result(s, "אין מכרז פעיל.")
        val id = a.eligiblePlayerIds.getOrNull(a.activePlayerIndex) ?: return result(s, "אין שחקן פעיל במכרז.")
        val name = s.players.first { it.id == id }.name
        return advanceAuction(s.copy(auction = a.copy(passedPlayerIds = a.passedPlayerIds + id)), listOf("$name פרש מהמכרז."))
    }

    fun build(snapshot: GameSnapshot, squareId: Int): EngineResult {
        val s = snapshot.state
        val property = s.properties.firstOrNull { it.squareId == squareId } ?: return result(s, "המשבצת אינה נכס.")
        val square = board.squares[squareId]
        val owner = s.currentPlayer
        val group = board.squares.filter { it.group == square.group }
        if (square.type != SquareType.PROPERTY || property.ownerId != owner.id || group.isEmpty()) return result(s, "רק בעל נכס יכול לבנות.")
        val groupProps = group.map { sq -> s.properties.first { it.squareId == sq.id } }
        if (groupProps.any { it.ownerId != owner.id || it.mortgaged }) return result(s, "נדרש מונופול ללא נכסים ממושכנים.")
        if (property.hotel) return result(s, "כבר נבנה מלון בנכס זה.")
        val minLevel = groupProps.minOf { if (it.hotel) 5 else it.houses }
        if (property.houses > minLevel) return result(s, "יש לבנות באופן שווה בין הנכסים בקבוצה.")
        if (owner.cash < square.houseCost) return result(s, "אין מספיק כסף לבנייה.")
        val buildingHotel = property.houses == 4
        if (buildingHotel && s.hotelsInBank < 1) return result(s, "אין מלונות זמינים בבנק.")
        if (!buildingHotel && s.housesInBank < 1) return result(s, "אין בתים זמינים בבנק.")
        val updated = s.properties.map { if (it.squareId == squareId) {
            if (buildingHotel) it.copy(houses = 0, hotel = true) else it.copy(houses = it.houses + 1)
        } else it }
        val bank = if (buildingHotel) s.copy(housesInBank = s.housesInBank + 4, hotelsInBank = s.hotelsInBank - 1)
                   else s.copy(housesInBank = s.housesInBank - 1)
        val events = listOf("${owner.name} בנה ${if (buildingHotel) "מלון" else "בית"} ב-${square.name} תמורת ${square.houseCost}.")
        val next = changeCash(bank.copy(properties = updated), owner.id, -square.houseCost)
        return EngineResult(appendLog(next, events), events)
    }

    fun sellBuilding(snapshot: GameSnapshot, squareId: Int): EngineResult {
        val s = snapshot.state
        val p = s.properties.firstOrNull { it.squareId == squareId } ?: return result(s, "נכס לא נמצא.")
        val sq = board.squares[squareId]
        if (p.ownerId != s.currentPlayer.id || (p.houses == 0 && !p.hotel)) return result(s, "אין מבנה למכירה.")
        val groupProps = s.properties.filter { board.squares[it.squareId].group == sq.group && it.squareId != squareId }
        val levelAfterSale = if (p.hotel) 4 else p.houses - 1
        if (groupProps.any { (if (it.hotel) 5 else it.houses) > levelAfterSale + 1 }) return result(s, "יש למכור באופן שווה בין הנכסים.")
        if (p.hotel && s.housesInBank < 4) return result(s, "אין בבנק ארבעה בתים להחלפת המלון.")
        val refund = sq.houseCost / 2
        val updated = s.properties.map { if (it.squareId == squareId) {
            if (it.hotel) it.copy(hotel = false, houses = 4) else it.copy(houses = it.houses - 1)
        } else it }
        val bank = if (p.hotel) s.copy(housesInBank = s.housesInBank - 4, hotelsInBank = s.hotelsInBank + 1)
                   else s.copy(housesInBank = s.housesInBank + 1)
        val events = listOf("${s.currentPlayer.name} מכר מבנה ב-${sq.name} וקיבל $refund.")
        val next = changeCash(bank.copy(properties = updated), s.currentPlayer.id, refund)
        return EngineResult(appendLog(next, events), events)
    }

    fun mortgage(snapshot: GameSnapshot, squareId: Int): EngineResult {
        val s = snapshot.state
        val p = s.properties.firstOrNull { it.squareId == squareId } ?: return result(s, "נכס לא נמצא.")
        val sq = board.squares[squareId]
        val groupIds = board.squares.filter { it.group != null && it.group == sq.group }.map { it.id }
        if (p.ownerId != s.currentPlayer.id || p.mortgaged) return result(s, "אי אפשר למשכן את הנכס.")
        if (groupIds.any { id -> s.properties.any { it.squareId == id && (it.houses > 0 || it.hotel) } }) return result(s, "יש למכור תחילה את כל המבנים בקבוצה.")
        val amount = sq.price / 2
        val updated = s.copy(properties = s.properties.map { if (it.squareId == squareId) it.copy(mortgaged = true) else it })
        val events = listOf("${s.currentPlayer.name} משכן את ${sq.name} ומקבל $amount.")
        return EngineResult(appendLog(changeCash(updated, s.currentPlayer.id, amount), events), events)
    }

    fun redeemMortgage(snapshot: GameSnapshot, squareId: Int): EngineResult {
        val s = snapshot.state
        val p = s.properties.firstOrNull { it.squareId == squareId } ?: return result(s, "נכס לא נמצא.")
        val sq = board.squares[squareId]
        val cost = (sq.price * 0.55).toInt()
        if (p.ownerId != s.currentPlayer.id || !p.mortgaged || s.currentPlayer.cash < cost) return result(s, "אין אפשרות לפדות את המשכון.")
        val updated = s.copy(properties = s.properties.map { if (it.squareId == squareId) it.copy(mortgaged = false) else it })
        val events = listOf("${s.currentPlayer.name} פדה את המשכון ב-${sq.name} תמורת $cost.")
        return EngineResult(appendLog(changeCash(updated, s.currentPlayer.id, -cost), events), events)
    }

    fun useJailCard(snapshot: GameSnapshot): EngineResult {
        val p = snapshot.state.currentPlayer
        if (!p.inJail || p.jailFreeCards <= 0) return result(snapshot.state, "אין כרטיס יציאה זמין.")
        val source = p.jailFreeCardSources.firstOrNull() ?: "chance"
        val card = (if (source == "chest") board.communityChestCards else board.chanceCards).firstOrNull { it.kind == "jailCard" }
        var state = updatePlayer(snapshot.state, p.id) {
            it.copy(inJail = false, failedJailRolls = 0, jailFreeCards = it.jailFreeCards - 1,
                jailFreeCardSources = it.jailFreeCardSources.drop(1))
        }
        if (card != null) state = if (source == "chest") state.copy(chestDeck = state.chestDeck + card)
            else state.copy(chanceDeck = state.chanceDeck + card)
        return EngineResult(appendLog(state.copy(phase = GamePhase.ROLL), listOf("${p.name} השתמש בכרטיס יציאה מהכלא.")))
    }

    fun payToLeaveJail(snapshot: GameSnapshot): EngineResult {
        val s = snapshot.state
        val p = s.currentPlayer
        if (!p.inJail || p.cash < 50) return result(s, "אי אפשר לשלם כדי לצאת מהכלא.")
        val state = changeCash(updatePlayer(s, p.id) { it.copy(inJail = false, failedJailRolls = 0) }, p.id, -50)
        val events = listOf("${p.name} שילם 50 ויצא מהכלא.")
        return EngineResult(appendLog(state.copy(phase = GamePhase.ROLL), events), events)
    }

    fun endTurn(snapshot: GameSnapshot): EngineResult {
        val s = snapshot.state
        if (s.phase != GamePhase.ROLL || !s.turnRolled) return result(s, "התור עדיין לא הושלם.")
        val minutes = snapshot.settings.houseRules.timedGameMinutes
        if (minutes > 0 && System.currentTimeMillis() - s.startedAtEpochMillis >= minutes * 60_000L) {
            val winner = s.activePlayers.maxByOrNull { player ->
                val owned = s.properties.filter { it.ownerId == player.id }.sumOf { property ->
                    val square = board.squares[property.squareId]
                    (if (property.mortgaged) square.price / 2 else square.price) +
                        property.houses * square.houseCost + if (property.hotel) square.houseCost * 5 else 0
                }
                player.cash + owned
            }
            val ended = s.copy(phase = GamePhase.GAME_OVER, winnerId = winner?.id)
            val event = "הזמן הסתיים. המנצח לפי שווי כולל: " + (winner?.name ?: "אין") + "."
            return EngineResult(appendLog(ended, listOf(event)), listOf(event))
        }
        return EngineResult(finishTurn(s, false), emptyList())
    }

    private fun move(state0: GameState, playerId: String, steps: Int, events: MutableList<String>, settings: GameSettings, rentMultiplier: Int = 1, utilityMultiplier: Int? = null): GameState {
        var state = state0
        val player = state.players.first { it.id == playerId }
        val raw = player.position + steps
        val destination = raw % 40
        if (raw >= 40) {
            val bonus = if (destination == 0 && settings.houseRules.doubleGoLanding) 400 else 200
            state = changeCash(state, playerId, bonus)
            events += "${player.name} עבר דרך GO וקיבל $bonus."
        } else if (destination == 0 && settings.houseRules.doubleGoLanding) {
            state = changeCash(state, playerId, 400)
            events += "${player.name} נחת בדיוק על GO וקיבל 400."
        } else if (destination == 0) {
            state = changeCash(state, playerId, 200)
            events += "${player.name} נחת על GO וקיבל 200."
        }
        state = updatePlayer(state, playerId) { it.copy(position = destination) }
        events += "${player.name} התקדם ל-${board.squares[destination].name}."
        return resolveLanding(state, playerId, destination, events, settings, rentMultiplier, utilityMultiplier)
    }

    private fun resolveLanding(s0: GameState, playerId: String, squareId: Int, events: MutableList<String>, settings: GameSettings, rentMultiplier: Int = 1, utilityMultiplier: Int? = null): GameState {
        var s = s0
        val square = board.squares[squareId]
        val player = s.players.first { it.id == playerId }
        when (square.type) {
            SquareType.GO -> Unit
            SquareType.JAIL -> events += "${player.name} רק מבקר בכלא."
            SquareType.FREE_PARKING -> if (settings.houseRules.freeParkingPot) {
                val pot = s.freeParkingPot
                s = changeCash(s, playerId, pot).copy(freeParkingPot = 0)
                events += "${player.name} אסף $pot מחניית חינם."
            }
            SquareType.GO_TO_JAIL -> { s = sendToJail(s, playerId); events += "${player.name} נשלח לכלא." }
            SquareType.TAX -> {
                val tax = if (squareId == 4) 200 else 100
                if (player.cash >= tax) {
                    s = changeCash(s, playerId, -tax)
                    events += "${player.name} שילם מס $tax."
                    if (settings.houseRules.freeParkingPot) s = s.copy(freeParkingPot = s.freeParkingPot + tax)
                } else {
                    s = s.copy(phase = GamePhase.PAY_DEBT, pendingDebt = tax, debtCreditorId = null, pendingDebtToParkingPot = settings.houseRules.freeParkingPot)
                    events += "${player.name} אינו יכול לשלם מס $tax ונדרש לגייס ${tax - player.cash}."
                }
            }
            SquareType.CHEST, SquareType.CHANCE -> s = drawCard(s, playerId, square.type == SquareType.CHANCE, events, settings)
            SquareType.PROPERTY, SquareType.RAILROAD, SquareType.UTILITY -> {
                val p = s.properties.first { it.squareId == squareId }
                if (p.ownerId == null) {
                    s = s.copy(phase = GamePhase.BUY_OR_AUCTION, pendingSquareId = squareId)
                    events += "${square.name} פנוי לרכישה במחיר ${square.price}."
                } else if (p.ownerId != playerId && !p.mortgaged) {
                    val rent = if (square.type == SquareType.UTILITY && utilityMultiplier != null) s.dice.sum() * utilityMultiplier else RentCalculator.calculate(board, s, squareId, s.dice.sum()) * rentMultiplier
                    if (player.cash >= rent) {
                        s = changeCash(s, playerId, -rent)
                        s = changeCash(s, p.ownerId, rent)
                        events += "${player.name} שילם שכירות $rent ל-${s.players.first { it.id == p.ownerId }.name}."
                    } else {
                        s = s.copy(phase = GamePhase.PAY_DEBT, pendingDebt = rent, debtCreditorId = p.ownerId, pendingDebtToParkingPot = false)
                        events += "${player.name} אינו יכול לשלם שכירות $rent ונדרש לגייס ${rent - player.cash}."
                    }
                }
            }
        }
        return s
    }

    private fun drawCard(s0: GameState, playerId: String, chance: Boolean, events: MutableList<String>, settings: GameSettings): GameState {
        var s = s0
        val deck = if (chance) s.chanceDeck else s.chestDeck
        if (deck.isEmpty()) return s
        val card = deck.first()
        if (chance) s = s.copy(chanceDeck = deck.drop(1), pendingCard = card) else s = s.copy(chestDeck = deck.drop(1), pendingCard = card)
        events += "קלף: ${card.title}."
        when (card.kind) {
            "move" -> {
                val current = s.players.first { it.id == playerId }.position
                val target = card.target ?: 0
                val steps = (target - current + 40) % 40
                s = move(s, playerId, steps, events, settings)
            }
            "back" -> {
                val current = s.players.first { it.id == playerId }.position
                val target = (current - card.amount + 40) % 40
                s = updatePlayer(s, playerId) { it.copy(position = target) }
                s = resolveLanding(s, playerId, target, events, settings)
            }
            "nearestRailroad", "nearestUtility" -> {
                val current = s.players.first { it.id == playerId }.position
                val wanted = if (card.kind == "nearestRailroad") SquareType.RAILROAD else SquareType.UTILITY
                val target = (1..40).map { (current + it) % 40 }.first { board.squares[it].type == wanted }
                val steps = (target - current + 40) % 40
                if (card.kind == "nearestRailroad") s = move(s, playerId, steps, events, settings, rentMultiplier = 2)
                else s = move(s, playerId, steps, events, settings, utilityMultiplier = 10)
            }
            "collect" -> s = changeCash(s, playerId, card.amount)
            "pay" -> s = payCardAmount(s, playerId, card.amount, events)
            "jail" -> s = sendToJail(s, playerId)
            "jailCard" -> s = updatePlayer(s, playerId) { it.copy(jailFreeCards = it.jailFreeCards + 1, jailFreeCardSources = it.jailFreeCardSources + (if (chance) "chance" else "chest")) }
            "payEach" -> {
                val recipients = s.players.filter { it.id != playerId && !it.bankrupt }
                val total = card.amount * recipients.size
                val payer = s.players.first { it.id == playerId }
                if (payer.cash >= total) {
                    s = changeCash(s, playerId, -total)
                    recipients.forEach { other -> s = changeCash(s, other.id, card.amount) }
                } else {
                    s = s.copy(phase = GamePhase.PAY_DEBT, pendingDebt = total, debtCreditorId = null,
                        pendingDebtToParkingPot = false, pendingPayments = recipients.map { DebtPayment(it.id, card.amount) })
                    events += payer.name + " חייב לשלם " + total + " לשאר השחקנים."
                }
            }
            "collectEach" -> s.players.filter { it.id != playerId && !it.bankrupt }.forEach { other ->
                if (other.cash >= card.amount) {
                    s = changeCash(s, playerId, card.amount)
                    s = changeCash(s, other.id, -card.amount)
                } else {
                    s = updatePlayer(s, other.id) { debtor ->
                        debtor.copy(debts = debtor.debts + (playerId to ((debtor.debts[playerId] ?: 0) + card.amount)))
                    }
                    events += other.name + " חייב " + card.amount + " ל-" + s.players.first { it.id == playerId }.name + " וישלם בתורו."
                }
            }
            "repairs" -> {
                val owned = s.properties.filter { it.ownerId == playerId }
                val cost = owned.sumOf { it.houses * card.house + if (it.hotel) card.hotel else 0 }
                s = payCardAmount(s, playerId, cost, events)
                events += "עלות התיקונים: $cost."
            }
        }
        if (card.kind != "jailCard") {
            if (chance) s = s.copy(chanceDeck = s.chanceDeck + card) else s = s.copy(chestDeck = s.chestDeck + card)
        }
        return s
    }

    private fun payCardAmount(s: GameState, playerId: String, amount: Int, events: MutableList<String>): GameState {
        if (amount <= 0) return s
        val player = s.players.first { it.id == playerId }
        return if (player.cash >= amount) {
            changeCash(s, playerId, -amount)
        } else {
            events += player.name + " אינו יכול לשלם " + amount + " ונדרש לגייס " + (amount - player.cash) + "."
            s.copy(phase = GamePhase.PAY_DEBT, pendingDebt = amount, debtCreditorId = null, pendingDebtToParkingPot = false)
        }
    }

    private fun sendToJail(s: GameState, playerId: String) =
        updatePlayer(s, playerId) { it.copy(position = 10, inJail = true, failedJailRolls = 0) }.copy(consecutiveDoubles = 0)

    private fun finishAfterLanding(s: GameState, events: List<String>): EngineResult {
        val next = if (s.phase == GamePhase.PAY_DEBT || s.phase == GamePhase.AUCTION) s
        else if (s.consecutiveDoubles > 0 && !s.currentPlayer.inJail) s.copy(phase = GamePhase.ROLL, turnRolled = false)
        else finishTurn(s, false)
        return EngineResult(appendLog(next, events), events)
    }

    private fun finishTurn(s: GameState, extra: Boolean): GameState {
        if (extra && !s.currentPlayer.bankrupt) return s.copy(phase = GamePhase.ROLL, turnRolled = false)
        val active = s.players.indices.filter { !s.players[it].bankrupt }
        if (active.size <= 1) return s.copy(phase = GamePhase.GAME_OVER, winnerId = active.firstOrNull()?.let { s.players[it].id })
        val next = (1..s.players.size).map { (s.currentPlayerIndex + it) % s.players.size }.first { !s.players[it].bankrupt }
        return s.copy(currentPlayerIndex = next, phase = GamePhase.ROLL, consecutiveDoubles = 0, turnRolled = false, pendingSquareId = null, pendingDebt = 0, debtCreditorId = null, aiTradeAttemptedPlayerId = null)
    }

    private fun advanceAuction(s0: GameState, events: List<String>): EngineResult {
        val a = s0.auction ?: return result(s0, "אין מכרז.")
        val remaining = a.eligiblePlayerIds.filterNot { it in a.passedPlayerIds }
        if (remaining.isEmpty()) {
            val cleared = s0.copy(auction = null, pendingSquareId = null, phase = GamePhase.ROLL)
            val ev = events + "המכרז הסתיים ללא הצעות זוכות."
            return completeAuction(cleared, ev)
        }
        if (remaining.size == 1 && a.highBidderId == remaining.first()) {
            val winner = s0.players.first { it.id == a.highBidderId }
            val square = board.squares[a.squareId]
            val state = changeCash(s0, winner.id, -a.highBid).copy(
                properties = s0.properties.map { if (it.squareId == a.squareId) it.copy(ownerId = winner.id) else it },
                auction = null, pendingSquareId = null, phase = GamePhase.ROLL
            )
            val ev = events + (winner.name + " זכה ב-" + square.name + " במכרז תמורת " + a.highBid + ".")
            return completeAuction(state, ev)
        }
        val nextIndex = (1..a.eligiblePlayerIds.size).map { (a.activePlayerIndex + it) % a.eligiblePlayerIds.size }
            .firstOrNull { a.eligiblePlayerIds[it] in remaining } ?: a.activePlayerIndex
        return EngineResult(appendLog(s0.copy(auction = a.copy(activePlayerIndex = nextIndex)), events))
    }

    private fun completeAuction(state: GameState, events: List<String>): EngineResult {
        if (state.bankruptcyAuctionQueue.isNotEmpty()) {
            val nextSquare = state.bankruptcyAuctionQueue.first()
            val remaining = state.bankruptcyAuctionQueue.drop(1)
            val eligible = state.activePlayers.map { it.id }
            val next = state.copy(
                phase = GamePhase.AUCTION,
                auction = AuctionState(nextSquare, eligible),
                bankruptcyAuctionQueue = remaining
            )
            return EngineResult(appendLog(next, events), events)
        }
        val cleared = state.copy(auction = null, bankruptcyAuctionQueue = emptyList())
        val next = if (state.bankruptcyAuctionActive) {
            cleared.copy(phase = GamePhase.ROLL, bankruptcyAuctionActive = false, turnRolled = false, consecutiveDoubles = 0)
        } else finishTurn(cleared, false)
        return EngineResult(appendLog(next, events), events)
    }

    private fun updatePlayer(s: GameState, id: String, transform: (Player) -> Player) =
        s.copy(players = s.players.map { if (it.id == id) transform(it) else it })
    private fun changeCash(s: GameState, id: String, amount: Int) = updatePlayer(s, id) { it.copy(cash = it.cash + amount) }
    private fun appendLog(s: GameState, events: List<String>) = s.copy(log = (s.log + events).takeLast(250))
    private fun result(s: GameState, message: String) = EngineResult(appendLog(s, listOf(message)), listOf(message))
}
