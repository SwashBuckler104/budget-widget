package io.github.swashbuckler104.budgetwidget.core.data.category

import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import io.github.swashbuckler104.budgetwidget.core.model.sms.FlowHint

/**
 * One seed category (SCOPE.md "Seed categories"). [key] is stable across renames. A subcategory's
 * [since] is the [SeedCategories.VERSION] that added it to a group older installs already have.
 */
internal data class SeedCategory(
    val key: String,
    val name: String,
    val emoji: String,
    val children: List<SeedCategory> = emptyList(),
    val since: Int = 1,
)

/** [since] is the [SeedCategories.VERSION] that introduced the group; older installs get it on upgrade. */
internal data class SeedGroup(
    val category: SeedCategory,
    val colorArgb: Int,
    val kind: CategoryKind,
    val since: Int = 1,
)

/**
 * The editable starter set, chosen by the owner (docs/DECISIONS.md ADR-0018, ADR-0026). To add a
 * group or a subcategory, bump [VERSION] and give it `since = VERSION`; `CategorySeeder` adds only
 * seeds newer than what an install already has, adopting same-named categories the user made. Investments are a TRANSFER kind: money moved to your own
 * investments is not spending (docs/DECISIONS.md ADR-0018). Emoji are limited to Emoji 5.0 so
 * they render on Android 8 (minSdk) in widgets and notifications, which can't use EmojiCompat.
 */
internal object SeedCategories {
    const val VERSION = 4

    private fun c(key: String, name: String, emoji: String, vararg children: SeedCategory) =
        SeedCategory(key, name, emoji, children.toList())

    val groups: List<SeedGroup> = listOf(
        SeedGroup(
            c(
                "food_drinks",
                "Food & Drinks",
                "🍽️",
                c("groceries", "Groceries", "🛒"),
                c("restaurants", "Restaurant, Fast-food", "🍔"),
                c("drinks_alcohol", "Drinks, Alcohol", "🍺"),
            ),
            0xFFA99BFF.toInt(),
            CategoryKind.EXPENSE,
        ),
        SeedGroup(
            c(
                "shopping", "Shopping", "🛍️",
                c("clothes", "Clothes, Shoes & Jewelry", "👗"),
                c("drugstore", "Drug-store, Chemist", "💊"),
                c("electronics", "Electronics, Accessories", "💻"),
                c("gifts", "Gifts, Joy", "🎁"),
                c("health_beauty", "Health & Beauty", "💄"),
                c("home_garden", "Home, Garden", "🏡"),
                c("kids", "Kids", "👶"),
                c("pets", "Pets, Animals", "🐶"),
                c("stationery_tools", "Stationery, Tools", "🔧"),
            ),
            0xFFFFF176.toInt(),
            CategoryKind.EXPENSE,
        ),
        SeedGroup(
            c(
                "housing",
                "Housing",
                "🏠",
                c("utilities", "Electricity, Utilities", "💡"),
                c("maintenance", "Maintenance, Repairs", "🔨"),
                c("rent", "Rent", "🔑"),
            ),
            0xFFCFD8DC.toInt(),
            CategoryKind.EXPENSE,
        ),
        SeedGroup(
            c(
                "transportation",
                "Transportation",
                "🚌",
                c("trips", "Trips", "🗺️"),
                c("flight", "Flight", "✈️"),
                c("public_transport", "Public transport", "🚆"),
            ),
            0xFF5CD3FF.toInt(),
            CategoryKind.EXPENSE,
        ),
        SeedGroup(
            c(
                "vehicle",
                "Vehicle",
                "🚗",
                c("fuel", "Fuel", "⛽"),
                c("vehicle_insurance", "Vehicle insurance", "🛡️"),
                c("vehicle_maintenance", "Vehicle maintenance", "🛠️"),
                c("vehicle_accessories", "Vehicle accessories", "🔩"),
            ),
            0xFFFFAB40.toInt(),
            CategoryKind.EXPENSE,
        ),
        SeedGroup(
            c(
                "life_entertainment", "Life & Entertainment", "🎬",
                c("gym_fitness", "Gym, Fitness", "🏋️"),
                c("books_audio", "Books, Audio, Subscription", "📚"),
                c("sports", "Sports", "⚽"),
                c("education", "Education, Development", "🎓"),
                c("tv_streaming", "TV, Streaming", "📺"),
                c("ai_subscription", "AI Subscription", "🤖"),
                c("wellness_beauty", "Wellness, Beauty", "💆"),
            ),
            0xFFE07CFF.toInt(),
            CategoryKind.EXPENSE,
        ),
        SeedGroup(
            c(
                "communication_pc",
                "Communication, PC",
                "📱",
                c("internet", "Internet", "🌐"),
                c("phone", "Phone, Cell phone", "📞"),
                c("software_apps", "Software, Apps, Games", "🎮"),
            ),
            0xFF7AA7FF.toInt(),
            CategoryKind.EXPENSE,
        ),
        SeedGroup(
            c(
                "financial_expenses",
                "Financial Expenses",
                "🏦",
                c("fines", "Fines", "⚠️"),
                c("insurances", "Insurances", "📋"),
                c("loans_interest", "Loans, Interest", "💳"),
                c("taxes", "Taxes", "🏛️"),
                // Outgoing leg of a transfer between your own accounts; the incoming leg goes
                // under Income, so the two cancel out (owner's choice, ADR-0018).
                c("self_transfer_out", "Self Transfer", "↔️"),
            ),
            0xFF4DFFF0.toInt(),
            CategoryKind.EXPENSE,
        ),
        SeedGroup(
            c(
                "family",
                "Family",
                "👪",
                c("family_transfer_lend", "Transfer, Lend", "💸"),
                c("family_groceries", "D-Mart, Kirana, Groceries", "🛒"),
                c("family_other", "Other", "🤝"),
            ),
            0xFFFF6FB5.toInt(),
            CategoryKind.EXPENSE,
            since = 2,
        ),
        SeedGroup(
            c(
                "investments",
                "Investments",
                "📈",
                c("financial_investments", "Financial investments (SIP, bonds, etc.)", "💹"),
                c("realty", "Realty", "🏘️"),
                c("savings_ef", "Savings (emergency fund)", "💰"),
            ),
            0xFFD4FF4A.toInt(),
            CategoryKind.TRANSFER,
        ),
        // Money moved between your own pots: never spending or income (ADR-0042). Transactions the
        // parser recognises as one of these are sorted here automatically.
        SeedGroup(
            c(
                "transfers",
                "Transfers",
                "🔁",
                c(TransferSeeds.OWN_TRANSFER, "Between my accounts", "↔️"),
                c(TransferSeeds.LOAN_EMI, "Loan EMI", "🏦"),
                c(TransferSeeds.CARD_BILL, "Credit card bill", "💳"),
                c(TransferSeeds.LOAN_RECEIVED, "Loan received", "📥"),
                // The balance an account had when tracking started (ADR-0059): money already there,
                // so never income.
                SeedCategory(TransferSeeds.OPENING_BALANCE, "Opening balance", "🏁", since = 4),
            ),
            0xFF8C92AC.toInt(),
            CategoryKind.TRANSFER,
            since = 3,
        ),
        SeedGroup(
            c(
                "income",
                "Income",
                "💵",
                c("salary", "Salary", "💼"),
                c("freelancing", "Freelancing", "✍️"),
                c("other_income", "Other", "➕"),
                c("self_transfer", "Self Transfer", "↔️"),
            ),
            0xFF7CFF6B.toInt(),
            CategoryKind.INCOME,
        ),
        // A single leaf for payments the user doesn't want to sort one by one, e.g. a whole
        // imported history (ADR-0058). Money coming in goes to Income > Other instead.
        SeedGroup(
            c(OtherSeeds.OTHER, "Other", "📦"),
            0xFFB0B0B0.toInt(),
            CategoryKind.EXPENSE,
            since = 4,
        ),
    )
}

/** Seed keys of the catch-all leaves that bulk filing uses (ADR-0058), one per direction. */
internal object OtherSeeds {
    const val OTHER = "other"
    const val OTHER_INCOME = "other_income"
}

/** Seed keys of the Transfers group, and which [FlowHint] lands in which (ADR-0042). */
internal object TransferSeeds {
    const val OWN_TRANSFER = "own_transfer"
    const val LOAN_EMI = "loan_emi"
    const val CARD_BILL = "card_bill"
    const val LOAN_RECEIVED = "loan_received"
    const val OPENING_BALANCE = "opening_balance"

    fun keyFor(hint: FlowHint): String = when (hint) {
        FlowHint.OWN_TRANSFER -> OWN_TRANSFER
        FlowHint.LOAN_REPAYMENT -> LOAN_EMI
        FlowHint.CARD_BILL_PAYMENT -> CARD_BILL
        FlowHint.LOAN_DISBURSAL -> LOAN_RECEIVED
    }

    fun flowFor(seedKey: String): FlowHint? = FlowHint.entries.firstOrNull { keyFor(it) == seedKey }
}
