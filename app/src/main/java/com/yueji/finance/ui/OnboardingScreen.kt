package com.yueji.finance.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yueji.finance.feature.OnboardingUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onChoose: (Boolean) -> Unit,
    onFiscalMonth: (Int) -> Unit,
    onGoals: (Boolean) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit,
) {
    Scaffold(topBar = {
        TopAppBar(title = { Text("开始使用月迹") }, navigationIcon = {
            if (state.step > 0) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            LinearProgressIndicator(progress = { (state.step + 1) / 4f }, Modifier.fillMaxWidth())
            AnimatedContent(state.step, label = "onboarding") { step ->
                when (step) {
                    0 -> WelcomeStep(onChoose)
                    1 -> FiscalStep(state.fiscalYearStartMonth, onFiscalMonth, onNext)
                    2 -> DataStep(state.importHistory, onNext)
                    else -> GoalStep(state, onGoals, onFinish)
                }
            }
        }
    }
}

@Composable private fun WelcomeStep(onChoose: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.NightsStay, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp)); Text("从这个月开始，看清每一笔钱去了哪里。", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp)); Text("数据默认只保存在你的手机中。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(40.dp)); Button(onClick = { onChoose(true) }, Modifier.fillMaxWidth()) { Text("使用已有历史数据") }
        Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { onChoose(false) }, Modifier.fillMaxWidth()) { Text("创建空白账本") }
    }
}

@Composable private fun FiscalStep(value: Int, onValue: (Int) -> Unit, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(top = 32.dp)) {
        Text("选择统计周期", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("之后仍可在“我的”中修改。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        listOf(1 to "自然年（1 月至 12 月）", 9 to "财务年（9 月至次年 8 月）").forEach { (month, title) ->
            ListItem(headlineContent = { Text(title) }, leadingContent = { RadioButton(value == month, { onValue(month) }) }, modifier = Modifier.fillMaxWidth())
        }
        Text("自定义起始月：$value 月", style = MaterialTheme.typography.titleMedium)
        Slider(value.toFloat(), { onValue(it.toInt()) }, valueRange = 1f..12f, steps = 10)
        Spacer(Modifier.weight(1f)); Button(onNext, Modifier.fillMaxWidth().padding(bottom = 24.dp)) { Text("继续") }
    }
}

@Composable private fun DataStep(importHistory: Boolean, onNext: () -> Unit) {
    val rows = if (importHistory) listOf("10 个账户与子账户", "4 个月份余额快照", "FY2024—FY2026 年度汇总", "允许农业银行历史负余额") else listOf("1 个现金账户", "30 个预置收支分类", "不创建任何交易")
    Column(Modifier.fillMaxSize().padding(top = 32.dp)) {
        Text(if (importHistory) "确认历史数据" else "创建空白账本", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp)); Card { Column(Modifier.padding(16.dp)) {
            rows.forEach { Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Text(it) } }
        } }
        if (importHistory) { Spacer(Modifier.height(16.dp)); Text("年度汇总只作为年度数据保存，不会被均摊成虚假的月度流水。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.weight(1f)); Button(onNext, Modifier.fillMaxWidth().padding(bottom = 24.dp)) { Text("数据无误，继续") }
    }
}

@Composable private fun GoalStep(state: OnboardingUiState, onGoals: (Boolean) -> Unit, onFinish: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(top = 32.dp)) {
        Text("设置首批目标", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (state.importHistory) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Switch(state.saveRecommendedGoals, onGoals); Spacer(Modifier.width(12.dp)); Text("保存推荐目标")
            }
            LazyColumn(Modifier.weight(1f)) { items(listOf("月消费 5,000 元", "年消费 60,000 元", "月结余 6,000 元", "年结余 72,000 元", "年储蓄率 60%", "资产 300,000 元")) { Text(it, Modifier.padding(vertical = 10.dp)) } }
        } else { Text("目标可稍后在目标页面添加。", Modifier.weight(1f).padding(top = 16.dp)) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onFinish, enabled = !state.working, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            if (state.working) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("进入首页")
        }
    }
}
