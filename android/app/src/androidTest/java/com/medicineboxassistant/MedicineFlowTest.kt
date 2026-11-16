package com.medicineboxassistant

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

class MedicineFlowTest {
    @get:Rule(order = 0) val cleanPreferences = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            context.getSharedPreferences("medicine-box", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    private fun confirmDemo() {
        compose.onNodeWithText("Найти по названию").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Название лекарства").performTextInput("Учебная упаковка")
        compose.onNodeWithText("Найти лекарство").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Всё совпадает, продолжить").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Отправить вопрос").assertDoesNotExist()
        compose.onNodeWithText("Всё совпадает, продолжить").performScrollTo().performClick()
    }

    @Test fun confirmationGatesChatAndDemoNeverPretendsToBeMedicalAnswer() {
        confirmDemo()
        compose.onNodeWithText("Как хранить?").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Недостаточно сведений в инструкции").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Это учебный пример интерфейса. Медицинские ответы доступны в режиме API по настоящей инструкции.").assertExists()
    }

    @Test fun cabinetSurvivesActivityRecreationAndRequiresConfirmationAgain() {
        confirmDemo()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Сохранить в аптечку"))
        compose.onNodeWithText("Сохранить в аптечку").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Другое лекарство").performScrollTo().performClick()
        compose.onNodeWithText("Моя аптечка").performClick()
        compose.onNodeWithText("Открыть упаковку").performScrollTo().performClick()
        compose.onNodeWithText("Отправить вопрос").assertDoesNotExist()
        compose.onNodeWithText("Всё совпадает, продолжить").assertExists()
    }
}
