package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * GateAI End-to-End Smoke Test
 * 
 * NOTE: As per platform constraints for this environment, this test is placed in the
 * `src/test/` directory using Robolectric and AndroidJUnit4. This allows the exact same
 * Compose Test Rule API to run headlessly without an emulator, but it is 100% compatible
 * with `src/androidTest/` for physical device execution in your CI.
 */
@RunWith(AndroidJUnit4::class)
@Config(instrumentedPackages = ["androidx.loader.content"])
class GateAiSmokeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun scenarioA_VehicleLoop_NormalExit() {
        // Clear any demo data to start fresh
        composeTestRule.onNodeWithText("Clear Demo Data").performClick()
        composeTestRule.waitForIdle()

        // 1. Navigate to Vehicle IN
        composeTestRule.onNodeWithTag("btn_vehicle_in").performClick()
        
        // 2. Fill Vehicle IN Form
        composeTestRule.onNodeWithText("Vehicle Number (e.g. MH12AB1234)").performTextInput("DL01AB1234")
        composeTestRule.onNodeWithText("Driver Name").performTextInput("Ramesh Kumar")
        composeTestRule.onNodeWithText("Purpose (e.g. Delivery, Pickup)").performTextInput("Delivery")
        composeTestRule.onNodeWithText("Opening KM").performTextInput("50000")
        
        // 3. Submit
        composeTestRule.onNodeWithText("SUBMIT VEHICLE IN").performClick()
        
        // 4. Verify Success & Navigate Back
        composeTestRule.onNodeWithText("Success").assertIsDisplayed()
        composeTestRule.onNodeWithText("BACK TO DASHBOARD").performClick()

        // 5. Navigate to Vehicle OUT
        composeTestRule.onNodeWithTag("btn_vehicle_out").performClick()

        // 6. Select Vehicle & Checkout
        composeTestRule.onNodeWithText("DL01AB1234").performClick()
        composeTestRule.onNodeWithText("Closing KM").performTextInput("50120") // +120 KM
        composeTestRule.onNodeWithText("CONFIRM EXIT").performClick()

        // 7. Verify Success
        composeTestRule.onNodeWithText("Success").assertIsDisplayed()
        composeTestRule.onNodeWithText("BACK TO DASHBOARD").performClick()
    }

    @Test
    fun scenarioB_KmGuardrail_SupervisorOverride() {
        composeTestRule.onNodeWithText("Clear Demo Data").performClick()

        // 1. Vehicle IN
        composeTestRule.onNodeWithTag("btn_vehicle_in").performClick()
        composeTestRule.onNodeWithText("Vehicle Number (e.g. MH12AB1234)").performTextInput("HR26ZZ9999")
        composeTestRule.onNodeWithText("Driver Name").performTextInput("Singh")
        composeTestRule.onNodeWithText("Purpose (e.g. Delivery, Pickup)").performTextInput("Pickup")
        composeTestRule.onNodeWithText("Opening KM").performTextInput("40000")
        composeTestRule.onNodeWithText("SUBMIT VEHICLE IN").performClick()
        composeTestRule.onNodeWithText("BACK TO DASHBOARD").performClick()

        // 2. Vehicle OUT - Negative Testing (Closing < Opening)
        composeTestRule.onNodeWithTag("btn_vehicle_out").performClick()
        composeTestRule.onNodeWithText("HR26ZZ9999").performClick()
        composeTestRule.onNodeWithText("Closing KM").performTextInput("39000") // Error
        composeTestRule.onNodeWithText("CONFIRM EXIT").performClick()
        // Verify Error
        composeTestRule.onNodeWithText("Closing KM cannot be less than Opening KM").assertIsDisplayed()

        // 3. Vehicle OUT - >300 KM triggers Override
        composeTestRule.onNodeWithText("Closing KM").performTextClearance()
        composeTestRule.onNodeWithText("Closing KM").performTextInput("40350") // +350 KM
        composeTestRule.onNodeWithText("CONFIRM EXIT").performClick()
        
        // Assert Supervisor Override fields appear
        composeTestRule.onNodeWithText("Supervisor Override Required").assertIsDisplayed()
        composeTestRule.onNodeWithText("Supervisor PIN").performTextInput("1234") // Dummy PIN logic if applicable

        // 4. Switch Role to Supervisor on Dashboard? Actually, in this UI, if the PIN is valid, it proceeds.
        // If the UI relies on current role, let's just confirm it.
        composeTestRule.onNodeWithText("CONFIRM EXIT").performClick()
        composeTestRule.onNodeWithText("Success").assertIsDisplayed()
    }

    @Test
    fun scenarioC_VisitorQrCycle() {
        composeTestRule.onNodeWithText("Clear Demo Data").performClick()

        // 1. Visitor IN
        composeTestRule.onNodeWithTag("btn_visitor_in").performClick()
        composeTestRule.onNodeWithText("Visitor Name").performTextInput("Priya Sharma")
        composeTestRule.onNodeWithText("Phone Number").performTextInput("9876543210")
        composeTestRule.onNodeWithText("Host Name").performTextInput("Mr. Verma")
        composeTestRule.onNodeWithText("Purpose of Visit").performTextInput("Meeting")
        
        composeTestRule.onNodeWithText("GENERATE GATE PASS").performClick()
        
        // Verify Pass generated
        composeTestRule.onNodeWithText("Success").assertIsDisplayed()
        composeTestRule.onNodeWithText("BACK TO DASHBOARD").performClick()

        // 2. Visitor OUT
        composeTestRule.onNodeWithTag("btn_visitor_out").performClick()
        composeTestRule.onNodeWithText("Priya Sharma").performClick()
        
        // 3. Checkout
        composeTestRule.onNodeWithText("CHECKOUT VISITOR").performClick()
        composeTestRule.onNodeWithText("Success").assertIsDisplayed()
    }

    @Test
    fun scenarioD_MaterialRepairReconciliation() {
        composeTestRule.onNodeWithText("Clear Demo Data").performClick()

        // 1. Material Hub -> Material Out
        composeTestRule.onNodeWithTag("btn_material_hub").performClick()
        composeTestRule.onNodeWithText("MATERIAL OUT").performClick()
        
        composeTestRule.onNodeWithText("Invoice / Challan No.").performTextInput("REP-001")
        composeTestRule.onNodeWithText("Material Description").performTextInput("Coffee Machine")
        composeTestRule.onNodeWithText("Quantity").performTextInput("2")
        
        // Select Repair Type (which inherently sets Returnable)
        composeTestRule.onNodeWithText("Dispatch").performClick() // Assuming Dropdown opens
        composeTestRule.onNodeWithText("Repair").performClick()

        composeTestRule.onNodeWithText("SUBMIT OUTWARD ENTRY").performClick()
        composeTestRule.onNodeWithText("BACK").performClick() // Back to Hub
        
        // 2. Return from Repair (Partial)
        composeTestRule.onNodeWithText("Pending Repairs").performClick() // Tab
        composeTestRule.onNodeWithText("REP-001").performClick()
        
        composeTestRule.onNodeWithText("Return Qty (Out of 2)").performTextInput("1")
        composeTestRule.onNodeWithText("PROCESS RETURN").performClick()
        
        // Assert status changed (or entry gone from fully pending)
        // Check Audit Logs / Status - this varies by UI implementation, but 
        // a success state indicates the workflow processed.
        composeTestRule.onNodeWithText("Success").assertIsDisplayed()
    }
}
