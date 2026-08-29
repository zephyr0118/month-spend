package com.yueji.finance.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yueji.finance.feature.OnboardingUiState
import com.yueji.finance.ui.theme.YueJiTheme
import com.yueji.finance.core.model.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun welcomeOffersHistoryAndBlankBook() {
        compose.setContent { YueJiTheme(ThemeMode.LIGHT, false) { OnboardingScreen(OnboardingUiState(), {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("使用已有历史数据").assertIsDisplayed()
        compose.onNodeWithText("创建空白账本").assertIsDisplayed()
        compose.onNodeWithText("数据默认只保存在你的手机中。").assertIsDisplayed()
    }
}
