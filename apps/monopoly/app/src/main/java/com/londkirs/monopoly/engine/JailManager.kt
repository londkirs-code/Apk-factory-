package com.londkirs.monopoly.engine

object JailManager {
    fun canPay(player: Player) = player.inJail && player.cash >= 50
    fun canUseCard(player: Player) = player.inJail && player.jailFreeCards > 0
    fun attemptsRemaining(player: Player) = if (!player.inJail) 0 else (3 - player.failedJailRolls).coerceAtLeast(0)
}
