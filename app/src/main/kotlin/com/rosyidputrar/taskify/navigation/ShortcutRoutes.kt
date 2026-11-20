package com.rosyidputrar.taskify.navigation

import androidx.navigation.NavHostController
import com.rosyidputrar.taskify.core.domain.home.QuickView
import com.rosyidputrar.taskify.core.domain.home.ShortcutRef
import com.rosyidputrar.taskify.core.domain.home.ShortcutType

/**
 * Rute untuk satu pintasan Hari ini. Isi modul membuka layarnya langsung (catatan, snippet, chat,
 * …); tampilan cepat membuka modul di tab/filter yang tepat. Murni → mudah diuji.
 */
fun shortcutRoute(ref: ShortcutRef, today: String): Any? = when (ref.type) {
    ShortcutType.Task -> TasksRoute(taskId = ref.target)
    ShortcutType.Journal -> JournalEntryRoute(id = ref.target)
    ShortcutType.Note -> NoteEditRoute(id = ref.target)
    ShortcutType.Transaction -> TransactionRoute(id = ref.target)
    ShortcutType.Snippet -> SnippetRoute(ref.target)
    ShortcutType.Project -> ProjectRoute(ref.target)
    ShortcutType.Config -> ConfigFileRoute(ref.target)
    ShortcutType.AiChat -> AiChatRoute(ref.target)
    ShortcutType.TaskList -> TasksRoute(view = "list:${ref.target}")
    ShortcutType.Wallet -> FinanceRoute(tab = "Transactions", walletId = ref.target)
    ShortcutType.Tag -> TagRoute(ref.target)
    ShortcutType.View -> ref.view?.let { quickViewRoute(it, today) }
}

fun quickViewRoute(v: QuickView, today: String): Any = when (v) {
    QuickView.TasksToday -> TasksRoute(view = "Today")
    QuickView.TasksOverdue -> TasksRoute(view = "Overdue")
    QuickView.TasksUpcoming -> TasksRoute(view = "Upcoming")
    QuickView.JournalToday -> JournalEntryRoute(date = today)
    QuickView.JournalInsight -> JournalRoute(tab = "Insight")
    QuickView.FinanceTransactions -> FinanceRoute(tab = "Transactions")
    QuickView.FinanceBudget -> FinanceRoute(tab = "Budget")
    QuickView.FinanceDebt -> FinanceRoute(tab = "Debt")
    QuickView.NewTransaction -> TransactionRoute(date = today)
    QuickView.NotificationDrafts -> NotificationsRoute(drafts = true)
    QuickView.SandiCodes -> SandiRoute(tab = "Codes")
    QuickView.SandiGenerator -> SandiRoute(tab = "Generator")
    QuickView.VaultConfig -> VaultRoute(tab = "Config")
    QuickView.VaultProjects -> VaultRoute(tab = "Projects")
    QuickView.CalendarToday -> CalendarRoute(today)
    QuickView.FocusStart -> FocusRoute()
    QuickView.AiNewChat -> AiChatRoute()
    QuickView.Review -> ReviewRoute
    QuickView.Music -> MusicRoute()
    QuickView.Wellbeing -> WellbeingRoute
    QuickView.Files -> FilesRoute
}

/** Satu salinan di atas tumpukan, seperti `openEntity` (ketuk ganda tidak menumpuk layar sama). */
fun NavHostController.openShortcut(ref: ShortcutRef, today: String) {
    val route = shortcutRoute(ref, today) ?: return
    navigate(route) { launchSingleTop = true }
}
