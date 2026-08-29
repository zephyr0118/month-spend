package com.yueji.finance.data

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.yueji.finance.core.database.LegacyAnnualSummaryEntity
import com.yueji.finance.core.model.Money
import com.yueji.finance.core.model.MonthlyDashboard
import com.yueji.finance.core.model.Periods
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportService @Inject constructor() {
    fun monthly(output: OutputStream, dashboard: MonthlyDashboard) {
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val canvas = page.canvas; canvas.drawColor(android.graphics.Color.WHITE)
            val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(0, 77, 86); textSize = 28f; typeface = Typeface.DEFAULT_BOLD }
            val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.DKGRAY; textSize = 17f; typeface = Typeface.DEFAULT_BOLD }
            val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(50, 50, 50); textSize = 14f }
            canvas.drawText("月迹 · ${Periods.label(dashboard.month)}月报", 44f, 64f, title)
            var y = 112f
            listOf("收入" to dashboard.incomeMinor, "消费" to dashboard.effectiveExpenseMinor, "结余" to dashboard.savingsMinor).forEach { (label, value) ->
                canvas.drawText(label, 44f, y, heading); canvas.drawText(Money(value).format(), 170f, y, body); y += 34f
            }
            canvas.drawText("储蓄率", 44f, y, heading); canvas.drawText(dashboard.savingsRate?.let { "${it / 100f}%" } ?: "不可计算", 170f, y, body); y += 48f
            canvas.drawText("预算结果", 44f, y, heading); y += 28f
            canvas.drawText(dashboard.budgetMinor?.let { "预算 ${Money(it).format()}，剩余 ${Money(dashboard.budgetRemainingMinor ?: 0).format()}" } ?: "本月未设置消费预算", 44f, y, body); y += 48f
            canvas.drawText("消费前三分类", 44f, y, heading); y += 28f
            dashboard.categories.take(3).forEachIndexed { index, item -> canvas.drawText("${index + 1}. ${item.name}  ${Money(item.amountMinor).format()}", 54f, y, body); y += 26f }
            y += 18f; canvas.drawText("目标完成情况", 44f, y, heading); y += 28f
            dashboard.goals.take(6).forEach { goal ->
                val percent = if (goal.targetMinor > 0) goal.currentMinor * 100 / goal.targetMinor else 0
                canvas.drawText("${goal.name}：$percent%", 54f, y, body); y += 24f
            }
            canvas.drawText("报告由本机离线生成。默认不包含账户明细余额。", 44f, 800f, Paint(body).apply { textSize = 11f; color = android.graphics.Color.GRAY })
            pdf.finishPage(page); pdf.writeTo(output)
        } finally { pdf.close(); output.close() }
    }

    fun annual(output: OutputStream, summaries: List<LegacyAnnualSummaryEntity>) {
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create()); val canvas = page.canvas; canvas.drawColor(android.graphics.Color.WHITE)
            val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(0, 77, 86); textSize = 28f; typeface = Typeface.DEFAULT_BOLD }
            val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.DKGRAY; textSize = 15f }
            canvas.drawText("月迹 · 年度财务回顾", 44f, 64f, title); var y = 116f
            summaries.forEach { item ->
                canvas.drawText(item.label, 44f, y, Paint(body).apply { typeface = Typeface.DEFAULT_BOLD; textSize = 20f }); y += 32f
                canvas.drawText("收入 ${Money(item.incomeMinor).format()}  消费 ${Money(item.expenseMinor).format()}  结余 ${Money(item.savingsMinor).format()}", 54f, y, body); y += 44f
            }
            canvas.drawText("年度汇总与逐笔月度流水分开保存，本报告不均摊或虚构历史交易。", 44f, 800f, Paint(body).apply { textSize = 11f; color = android.graphics.Color.GRAY })
            pdf.finishPage(page); pdf.writeTo(output)
        } finally { pdf.close(); output.close() }
    }
}
