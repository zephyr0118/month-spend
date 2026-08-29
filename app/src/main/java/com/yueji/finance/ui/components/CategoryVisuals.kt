package com.yueji.finance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yueji.finance.core.model.TransactionType

fun categoryIcon(iconKey: String?, name: String? = null): ImageVector = when (iconKey) {
    "restaurant" -> Icons.Default.Restaurant
    "directions_car" -> Icons.Default.DirectionsCar
    "home" -> Icons.Default.Home
    "flight" -> Icons.Default.Flight
    "medical_services" -> Icons.Default.MedicalServices
    "school" -> Icons.Default.School
    "movie" -> Icons.Default.Movie
    "payments" -> Icons.Default.Payments
    "swap_horiz" -> Icons.Default.SwapHoriz
    "balance_adjustment" -> Icons.Default.Tune
    "refund" -> Icons.Default.Replay
    else -> when {
        name?.contains("购物") == true || name?.contains("日用") == true -> Icons.Default.ShoppingBag
        name?.contains("通讯") == true || name?.contains("网络") == true -> Icons.Default.PhoneAndroid
        name?.contains("水电") == true -> Icons.Default.Bolt
        name?.contains("服饰") == true || name?.contains("美妆") == true -> Icons.Default.Checkroom
        name?.contains("宠物") == true -> Icons.Default.Pets
        name?.contains("家庭") == true -> Icons.Default.FamilyRestroom
        name?.contains("保险") == true -> Icons.Default.Security
        name?.contains("工资") == true -> Icons.Default.AccountBalanceWallet
        name?.contains("奖金") == true -> Icons.Default.EmojiEvents
        else -> Icons.Default.Widgets
    }
}

fun transactionIconKey(type: TransactionType, categoryIconKey: String?): String? = when (type) {
    TransactionType.TRANSFER -> "swap_horiz"
    TransactionType.BALANCE_ADJUSTMENT -> "balance_adjustment"
    TransactionType.REFUND -> categoryIconKey ?: "refund"
    else -> categoryIconKey
}

@Composable
fun CategoryIconBadge(
    iconKey: String?,
    name: String?,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    selected: Boolean = false,
) {
    val background = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
    val foreground = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
    Box(modifier.size(size).background(background, CircleShape), contentAlignment = Alignment.Center) {
        Icon(categoryIcon(iconKey, name), contentDescription = null, tint = foreground, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun transactionAmountColor(isIncome: Boolean): Color =
    if (isIncome) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
