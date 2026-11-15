package com.rosyidputrar.taskify.feature.finance.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.designsystem.theme.namedColor

/** Nama ikon di DB (sama dengan webapp) → ikon Material. */
private val ICONS: Map<String, ImageVector> = mapOf(
    "utensils" to Icons.Outlined.Restaurant,
    "coffee" to Icons.Outlined.LocalCafe,
    "shopping-cart" to Icons.Outlined.ShoppingCart,
    "car" to Icons.Outlined.DirectionsCar,
    "fuel" to Icons.Outlined.LocalGasStation,
    "plane" to Icons.Outlined.Flight,
    "receipt" to Icons.Outlined.Receipt,
    "zap" to Icons.Outlined.Bolt,
    "droplets" to Icons.Outlined.WaterDrop,
    "wifi" to Icons.Outlined.Wifi,
    "smartphone" to Icons.Outlined.Smartphone,
    "house" to Icons.Outlined.Home,
    "shopping-bag" to Icons.Outlined.ShoppingBag,
    "shirt" to Icons.Outlined.Checkroom,
    "gamepad-2" to Icons.Outlined.SportsEsports,
    "film" to Icons.Outlined.Movie,
    "heart-pulse" to Icons.Outlined.MonitorHeart,
    "dumbbell" to Icons.Outlined.FitnessCenter,
    "graduation-cap" to Icons.Outlined.School,
    "book-open" to Icons.AutoMirrored.Outlined.MenuBook,
    "baby" to Icons.Outlined.ChildCare,
    "paw-print" to Icons.Outlined.Pets,
    "wrench" to Icons.Outlined.Build,
    "repeat" to Icons.Outlined.Repeat,
    "gift" to Icons.Outlined.CardGiftcard,
    "wallet" to Icons.Outlined.AccountBalanceWallet,
    "laptop" to Icons.Outlined.Laptop,
    "briefcase" to Icons.Outlined.Work,
    "trending-up" to Icons.AutoMirrored.Outlined.TrendingUp,
    "piggy-bank" to Icons.Outlined.Savings,
    "hand-coins" to Icons.Outlined.Handshake,
    "banknote" to Icons.Outlined.Payments,
    "landmark" to Icons.Outlined.AccountBalance,
    "circle-ellipsis" to Icons.Outlined.MoreHoriz,
)

fun financeIcon(name: String?, fallback: String = "circle-ellipsis"): ImageVector = ICONS[name] ?: ICONS.getValue(fallback)

val TransferIcon: ImageVector = Icons.Outlined.SwapHoriz

/** Lencana bulat kategori/dompet: latar warna bernama samar + ikon berwarna. */
@Composable
fun CategoryBadge(icon: String?, color: String?, modifier: Modifier = Modifier, size: Dp = 40.dp, transfer: Boolean = false) {
    val tint = if (transfer) TaskifyTheme.modules.accent(com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule.Finance) else namedColor(color)
    Box(modifier.size(size).background(tint.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(if (transfer) TransferIcon else financeIcon(icon), contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}
