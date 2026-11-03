package dk.sdu.expenseshare.data.balances

import dk.sdu.expenseshare.data.UserScoped

/**
 * Holds the [BalancesRepository] of the signed-in user, so queued writes keep being retried
 * while the user moves between screens. Unsent writes stay saved after sign-out.
 */
typealias BalancesRepositories = UserScoped<BalancesRepository>
