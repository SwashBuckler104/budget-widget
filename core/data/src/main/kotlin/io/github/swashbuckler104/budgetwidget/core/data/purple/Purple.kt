package io.github.swashbuckler104.budgetwidget.core.data.purple

import io.github.swashbuckler104.budgetwidget.core.data.sort.MerchantScope
import io.github.swashbuckler104.budgetwidget.core.data.sort.SortTransactionUseCase
import io.github.swashbuckler104.budgetwidget.core.data.update.UpdateReminder
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.database.dao.InboxSignals
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * What Purple, the in-app helper (ADR-0063), can say. Tour steps come first, in this order;
 * [id] is what [SettingsStore.purpleSeen] remembers once the user answers.
 */
enum class PurpleTip(val id: String) {
    TOUR_TRANSACTIONS("tour_transactions"),
    TOUR_ANALYSIS("tour_analysis"),
    TOUR_CATEGORIES("tour_categories"),
    TOUR_SETTINGS("tour_settings"),

    /** Notice: this version is old enough to look for a newer one (ADR-0060). */
    UPDATE("update"),

    /** Notice: new bank SMS the parser couldn't read; offers "Help improve the parser". */
    UNREAD_SMS("unread_sms"),

    /** Tip: two alerts in the inbox look like one payment. */
    DUPLICATE("duplicate"),

    /** Tip: "Sort all" walks the inbox one by one. */
    SORT_ALL("sort_all"),

    /** Offer: always sort a merchant the user keeps sorting the same way (ADR-0062). */
    ALWAYS("always"),
    ;

    val isTour: Boolean get() = this in TOUR

    /** A notice is shown even with tips turned off. */
    val isNotice: Boolean get() = this == UPDATE || this == UNREAD_SMS

    companion object {
        val TOUR = listOf(TOUR_TRANSACTIONS, TOUR_ANALYSIS, TOUR_CATEGORIES, TOUR_SETTINGS)
    }
}

/**
 * One thing Purple says now. [unreadSms]: how many new messages it couldn't read (UNREAD_SMS),
 * the newest being [newestUnreadId]; [always]: the merchant it offers to always sort (ALWAYS);
 * [tourStep] counts from 1.
 */
data class PurpleNote(
    val tip: PurpleTip,
    val unreadSms: Int = 0,
    val newestUnreadId: Long = 0,
    val always: AlwaysOffer? = null,
) {
    val tourStep: Int get() = PurpleTip.TOUR.indexOf(tip) + 1
}

/** "Always sort [merchant] into [category]?" (ADR-0062): the user sorted it there 3+ times. */
data class AlwaysOffer(val merchantKey: String, val merchant: String, val categoryId: Long, val category: String)

/** Everything Purple decides from, read together so a note never pairs with stale state. */
internal data class PurpleInputs(
    val seen: Set<String>,
    val tipsOn: Boolean,
    val updateDue: Boolean,
    val unreadIds: List<Long>,
    val unreadMark: Long,
    val inbox: InboxSignals,
    val alwaysOffers: List<AlwaysOffer>,
)

/**
 * Picks Purple's one note at a time and remembers the answers (ADR-0063). Every note shows once
 * (an unread-SMS notice again for newer messages, the update notice again after its snooze), so
 * it never nags; tips can be turned off in Settings.
 */
class Purple @Inject constructor(
    private val db: BudgetDatabase,
    private val settings: SettingsStore,
    private val update: UpdateReminder,
    private val sort: SortTransactionUseCase,
) {
    /** [installedAtMillis]: when this version was installed, for the update notice. */
    fun note(installedAtMillis: Long): Flow<PurpleNote?> = combine(
        combine(settings.purpleSeen, settings.purpleTips, update.due(installedAtMillis)) { seen, tips, due ->
            Triple(seen, tips, due)
        },
        db.unparsedSmsDao().observeUnreadIds(),
        settings.purpleUnparsedMark,
        db.transactionDao().observeInboxSignals(),
        db.merchantRuleDao().observeAlwaysCandidates().map { list ->
            list.map { AlwaysOffer(it.merchantKey, it.merchant, it.categoryId, it.category) }
        },
    ) { (seen, tips, due), unread, mark, inbox, candidates ->
        pick(PurpleInputs(seen, tips, due, unread, mark, inbox, candidates))
    }.distinctUntilChanged()

    /**
     * The user answered [note] (any button, or closed it). [accepted]: the main button; for
     * ALWAYS that makes the rule "always" and sorts the merchant's earlier payments.
     */
    suspend fun answer(note: PurpleNote, accepted: Boolean) {
        when (note.tip) {
            PurpleTip.UPDATE -> update.snooze()

            PurpleTip.UNREAD_SMS -> settings.setPurpleUnparsedMark(note.newestUnreadId)

            PurpleTip.ALWAYS -> {
                val candidate = note.always ?: return
                if (accepted) sort.forMerchant(candidate.merchantKey, candidate.categoryId, MerchantScope.BOTH)
                val declined = if (accepted) emptyList() else listOf(DECLINED_ALWAYS + candidate.merchantKey)
                settings.markPurpleSeen(listOf(alwaysId(candidate.merchantKey)) + declined)
            }

            else -> settings.markPurpleSeen(listOf(note.tip.id))
        }
    }

    /** "Skip" during the tour: the remaining steps are not shown. */
    suspend fun skipTour() = settings.markPurpleSeen(PurpleTip.TOUR.map { it.id })

    companion object {
        /** After declining this many "always" offers, Purple stops offering. */
        const val MAX_DECLINED_ALWAYS = 2
        internal const val DECLINED_ALWAYS = "always_declined:"

        internal fun alwaysId(merchantKey: String) = "always:$merchantKey"

        /**
         * The tour first, then notices (update, unread SMS), then tips in order of use: a
         * possible duplicate, "Sort all", an "always" offer.
         */
        internal fun pick(input: PurpleInputs): PurpleNote? {
            val seen = input.seen
            if (input.tipsOn) {
                PurpleTip.TOUR.firstOrNull { it.id !in seen }?.let { return PurpleNote(it) }
            }
            if (input.updateDue) return PurpleNote(PurpleTip.UPDATE)
            val unread = input.unreadIds.filter { it > input.unreadMark }
            if (unread.isNotEmpty()) return PurpleNote(PurpleTip.UNREAD_SMS, unread.size, unread.max())
            if (!input.tipsOn) return null
            if (input.inbox.duplicates > 0 && PurpleTip.DUPLICATE.id !in seen) return PurpleNote(PurpleTip.DUPLICATE)
            if (input.inbox.pending >= SORT_ALL_AFTER && PurpleTip.SORT_ALL.id !in seen) {
                return PurpleNote(PurpleTip.SORT_ALL)
            }
            if (seen.count { it.startsWith(DECLINED_ALWAYS) } >= MAX_DECLINED_ALWAYS) return null
            return input.alwaysOffers.firstOrNull { alwaysId(it.merchantKey) !in seen }
                ?.let { PurpleNote(PurpleTip.ALWAYS, always = it) }
        }

        /** "Sort all" is worth a tip once a few payments wait. */
        const val SORT_ALL_AFTER = 3
    }
}
