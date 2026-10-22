package com.hypermarker

import com.hypermarker.viewer.CrossLayout
import com.hypermarker.viewer.ReaderAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossLayoutTest {
    @Test fun defaultsFollowTheRequestedOrderPerMode() {
        assertEquals(listOf(ReaderAction.AUTOSCROLL, ReaderAction.ZOOM_FIT, ReaderAction.ZOOM_RESET, ReaderAction.RELOAD), CrossLayout.parse("VIEW", null))
        assertEquals(listOf(ReaderAction.PIN, ReaderAction.EDIT_TEXT, ReaderAction.DELETE_PINS, ReaderAction.RESTORE_TEXT), CrossLayout.parse("EDIT", "basura"))
        assertEquals(listOf(ReaderAction.COLOR, ReaderAction.WIDTH, ReaderAction.TOOL, ReaderAction.STABILIZER), CrossLayout.parse("DRAW", "COLOR,COLOR,TOOL,WIDTH"))
        assertTrue(CrossLayout.MODES.all { mode -> CrossLayout.defaults.getValue(mode).all { mode in it.modes } })
    }
    @Test fun assigningSwapsOrReplacesAndTheRestStaysInTheMenu() {
        val view = CrossLayout.defaults.getValue("VIEW")
        val replaced = CrossLayout.assign(view, 3, ReaderAction.BACK)
        assertEquals(listOf(ReaderAction.AUTOSCROLL, ReaderAction.ZOOM_FIT, ReaderAction.ZOOM_RESET, ReaderAction.BACK), replaced)
        assertTrue(ReaderAction.RELOAD in CrossLayout.rest("VIEW", replaced))
        assertEquals(listOf(ReaderAction.ZOOM_RESET, ReaderAction.ZOOM_FIT, ReaderAction.AUTOSCROLL, ReaderAction.BACK), CrossLayout.assign(replaced, 0, ReaderAction.ZOOM_RESET))
        val stored = CrossLayout.format(replaced)
        assertEquals(replaced, CrossLayout.parse("VIEW", stored))
        assertEquals(CrossLayout.defaults.getValue("EDIT"), CrossLayout.parse("EDIT", stored))
    }
}
