package com.medicineboxassistant

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MedicineFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun reset() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("medicine-box", Context.MODE_PRIVATE).edit().clear().commit()
        compose.activityRule.scenario.recreate()
    }

    private fun confirmDemo() {
        compose.onNodeWithText("Найти по названию").performScrollTo().performClick()
        compose.onNodeWithText("Название лекарства").performTextInput("Учебная упаковка")
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
        compose.onNodeWithText("Сохранить в аптечку").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Другое лекарство").performScrollTo().performClick()
        compose.onNodeWithText("Моя аптечка").performClick()
        compose.onNodeWithText("Открыть упаковку").performScrollTo().performClick()
        compose.onNodeWithText("Отправить вопрос").assertDoesNotExist()
        compose.onNodeWithText("Всё совпадает, продолжить").assertExists()
    }
}
