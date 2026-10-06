package com.parktimedetector

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.parktimedetector.notification.NotificationHelper
import com.parktimedetector.service.AccessibilityNode
import com.parktimedetector.service.QuickRenewManager
import com.parktimedetector.service.QuickRenewState
import com.parktimedetector.service.RenewAppType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeAccessibilityNode(
    override var text: CharSequence? = null,
    override var contentDescription: CharSequence? = null,
    override var className: CharSequence? = null,
    override var viewIdResourceName: String? = null,
    override var isClickable: Boolean = false,
    override var isEditable: Boolean = false,
    val children: MutableList<FakeAccessibilityNode> = mutableListOf(),
    override var parent: AccessibilityNode? = null
) : AccessibilityNode {

    val actionsPerformed = mutableListOf<Int>()
    val actionArguments = mutableListOf<Bundle?>()

    override val childCount: Int get() = children.size

    override fun getChild(index: Int): AccessibilityNode? = children.getOrNull(index)

    fun addChild(child: FakeAccessibilityNode): FakeAccessibilityNode {
        children.add(child)
        child.parent = this
        return child
    }

    override fun performAction(action: Int, arguments: Bundle?): Boolean {
        actionsPerformed.add(action)
        actionArguments.add(arguments)
        return true
    }
}

class QuickRenewManagerTest {

    @Before
    fun setup() {
        QuickRenewManager.disarm()
    }

    @Test
    fun testExtractZoneDigitsExplicitZone() {
        val digits = QuickRenewManager.extractZoneDigits("Zone 4022")
        assertEquals("4022", digits)
    }

    @Test
    fun testExtractZoneDigitsMyParkingLot58() {
        // In Calgary Parking Authority, Lot 58 maps to Zone 9058
        val digits = QuickRenewManager.extractZoneDigits("Lot 58 - 935 - 4 Av SW")
        assertEquals("9058", digits)
    }

    @Test
    fun testExtractZoneDigitsNumericString() {
        val digits = QuickRenewManager.extractZoneDigits("9058")
        assertEquals("9058", digits)
    }

    @Test
    fun testExtractZoneDigitsFromLocationAddressFallback() {
        val digits = QuickRenewManager.extractZoneDigits(null, "Lot 58 - 935 - 4 Av SW")
        assertEquals("9058", digits)
    }

    @Test
    fun testExtractZoneDigitsLongZoneNumber() {
        val digits = QuickRenewManager.extractZoneDigits("Zone 1205")
        assertEquals("1205", digits)
    }

    @Test
    fun testExtractZoneDigitsFallback() {
        val digits = QuickRenewManager.extractZoneDigits("")
        assertEquals("4022", digits)
    }

    @Test
    fun testDisarmResetsState() {
        assertFalse(QuickRenewManager.isArmed())
        assertEquals(QuickRenewState.IDLE, QuickRenewManager.state.value)
        assertNull(QuickRenewManager.targetPackage)
    }

    @Test
    fun testParkedInFlow_locatesAndClicksExtendButton() {
        QuickRenewManager.arm(
            packageName = NotificationHelper.PARKEDIN_PACKAGE,
            zoneOrLot = "Zone 4022",
            appType = RenewAppType.PARKEDIN
        )

        assertTrue(QuickRenewManager.isArmed())
        assertEquals(QuickRenewState.ARMED_AWAITING_APP, QuickRenewManager.state.value)

        val rootNode = FakeAccessibilityNode(className = "android.widget.FrameLayout")
        val cardNode = rootNode.addChild(FakeAccessibilityNode(className = "android.view.ViewGroup"))
        val extendButton = cardNode.addChild(
            FakeAccessibilityNode(
                text = "EXTEND SESSION",
                className = "android.widget.Button",
                isClickable = true
            )
        )

        val handled = QuickRenewManager.handleNode(
            rootNode = rootNode,
            eventPackage = NotificationHelper.PARKEDIN_PACKAGE,
            pauseBeforePayment = true
        )

        assertTrue("ParkedIn extend button should be handled", handled)
        assertTrue("Extend button should have received ACTION_CLICK", extendButton.actionsPerformed.contains(AccessibilityNodeInfo.ACTION_CLICK))
        assertEquals(QuickRenewState.PAUSED_FOR_USER_CONFIRMATION, QuickRenewManager.state.value)
        assertFalse("QuickRenewManager should disarm after handling extend", QuickRenewManager.isArmed())
    }

    @Test
    fun testMyParkingFlow_endToEndSearchToStartScreen() {
        QuickRenewManager.arm(
            packageName = NotificationHelper.MYPARKING_PACKAGE,
            zoneOrLot = "Lot 58 - 935 - 4 Av SW",
            appType = RenewAppType.MYPARKING
        )

        assertEquals("9058", QuickRenewManager.zoneDigits)
        assertEquals(QuickRenewState.ARMED_AWAITING_APP, QuickRenewManager.state.value)

        // Step 1: Unexpanded search container is shown on the map screen
        val mapRoot = FakeAccessibilityNode(className = "android.widget.FrameLayout")
        val searchContainer = mapRoot.addChild(
            FakeAccessibilityNode(
                contentDescription = "Search zone or address",
                className = "android.view.View",
                isClickable = true
            )
        )

        val step1Handled = QuickRenewManager.handleNode(
            rootNode = mapRoot,
            eventPackage = NotificationHelper.MYPARKING_PACKAGE,
            pauseBeforePayment = true
        )

        assertTrue("Search container click should be handled", step1Handled)
        assertTrue(searchContainer.actionsPerformed.contains(AccessibilityNodeInfo.ACTION_CLICK))
        assertEquals(QuickRenewState.MYPARKING_FINDING_SEARCH, QuickRenewManager.state.value)

        // Step 2: Search view opens with an editable search field
        QuickRenewManager.resetDebounceForTesting()
        val searchViewRoot = FakeAccessibilityNode(className = "android.widget.FrameLayout")
        val searchEditText = searchViewRoot.addChild(
            FakeAccessibilityNode(
                className = "android.widget.EditText",
                isEditable = true
            )
        )

        val step2Handled = QuickRenewManager.handleNode(
            rootNode = searchViewRoot,
            eventPackage = NotificationHelper.MYPARKING_PACKAGE,
            pauseBeforePayment = true
        )

        assertTrue("EditText injection should be handled", step2Handled)
        assertTrue(searchEditText.actionsPerformed.contains(AccessibilityNodeInfo.ACTION_SET_TEXT))
        assertEquals(QuickRenewState.MYPARKING_SELECTING_RESULT, QuickRenewManager.state.value)

        // Step 3: Search suggestions dropdown appears with matching lot result
        QuickRenewManager.resetDebounceForTesting()
        val suggestionsRoot = FakeAccessibilityNode(className = "android.widget.FrameLayout")
        val resultItem = suggestionsRoot.addChild(
            FakeAccessibilityNode(
                text = "Lot 58 - 935 - 4 Av SW (Zone 9058)",
                className = "android.widget.TextView",
                isClickable = true
            )
        )

        val step3Handled = QuickRenewManager.handleNode(
            rootNode = suggestionsRoot,
            eventPackage = NotificationHelper.MYPARKING_PACKAGE,
            pauseBeforePayment = true
        )

        assertTrue("Search result selection should be handled", step3Handled)
        assertTrue(resultItem.actionsPerformed.contains(AccessibilityNodeInfo.ACTION_CLICK))
        assertEquals(QuickRenewState.MYPARKING_AWAITING_START_SCREEN, QuickRenewManager.state.value)

        // Step 4: Start Parking Session screen appears
        QuickRenewManager.resetDebounceForTesting()
        val startScreenRoot = FakeAccessibilityNode(className = "android.widget.FrameLayout")
        val startButton = startScreenRoot.addChild(
            FakeAccessibilityNode(
                text = "START PARKING SESSION",
                className = "android.widget.Button",
                isClickable = true
            )
        )

        val step4Handled = QuickRenewManager.handleNode(
            rootNode = startScreenRoot,
            eventPackage = NotificationHelper.MYPARKING_PACKAGE,
            pauseBeforePayment = true // Pause for user review
        )

        assertTrue("Start screen should be handled", step4Handled)
        assertFalse(startButton.actionsPerformed.contains(AccessibilityNodeInfo.ACTION_CLICK))
        assertEquals(QuickRenewState.PAUSED_FOR_USER_CONFIRMATION, QuickRenewManager.state.value)
        assertFalse("QuickRenewManager should disarm after reaching start screen with pause", QuickRenewManager.isArmed())
    }

    @Test
    fun testPackageFiltering_ignoresUnrelatedApps() {
        QuickRenewManager.arm(
            packageName = NotificationHelper.PARKEDIN_PACKAGE,
            zoneOrLot = "Zone 4022",
            appType = RenewAppType.PARKEDIN
        )

        val rootNode = FakeAccessibilityNode(text = "EXTEND SESSION", isClickable = true)
        val handled = QuickRenewManager.handleNode(
            rootNode = rootNode,
            eventPackage = "com.google.android.youtube",
            pauseBeforePayment = true
        )

        assertFalse("Events from non-target packages must be ignored", handled)
        assertTrue("QuickRenewManager should remain armed", QuickRenewManager.isArmed())
    }

    @Test
    fun testMockPackageSupport_allowsOwnAppEventsWhenMockArmed() {
        QuickRenewManager.arm(
            packageName = "com.parktimedetector.mock",
            zoneOrLot = "Zone 4022",
            appType = RenewAppType.PARKEDIN
        )

        assertTrue(QuickRenewManager.isTargetMock())
        assertTrue("Own package should match mock target", QuickRenewManager.matchesTargetPackage("com.parktimedetector"))

        val rootNode = FakeAccessibilityNode(className = "android.widget.FrameLayout")
        val button = rootNode.addChild(FakeAccessibilityNode(text = "EXTEND SESSION", isClickable = true))

        val handled = QuickRenewManager.handleNode(
            rootNode = rootNode,
            eventPackage = "com.parktimedetector",
            pauseBeforePayment = true
        )

        assertTrue("Mock events from app package should be processed", handled)
        assertTrue(button.actionsPerformed.contains(AccessibilityNodeInfo.ACTION_CLICK))
    }

    @Test
    fun testDebouncePreventsRapidMultipleFires() {
        QuickRenewManager.arm(
            packageName = NotificationHelper.PARKEDIN_PACKAGE,
            zoneOrLot = "Zone 4022",
            appType = RenewAppType.PARKEDIN
        )

        val rootNode = FakeAccessibilityNode(className = "android.widget.FrameLayout")
        rootNode.addChild(FakeAccessibilityNode(text = "EXTEND SESSION", isClickable = true))

        val firstResult = QuickRenewManager.handleNode(
            rootNode = rootNode,
            eventPackage = NotificationHelper.PARKEDIN_PACKAGE,
            pauseBeforePayment = true
        )
        assertTrue(firstResult)

        val secondResult = QuickRenewManager.handleNode(
            rootNode = rootNode,
            eventPackage = NotificationHelper.PARKEDIN_PACKAGE,
            pauseBeforePayment = true
        )
        assertFalse("Subsequent event within 400ms debounce must be dropped", secondResult)
    }
}
