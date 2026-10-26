package com.hypermarker

import com.hypermarker.viewer.CrossLayout
import com.hypermarker.viewer.ReaderAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossLayoutTest {
    @Test fun defaultsFollowTheRequestedOrderPerMode() {
        assertEquals(listOf(ReaderAction.AUTOSCROLL, ReaderAction.ZOOM_FIT, ReaderAction.SETTINGS, ReaderAction.RELOAD), CrossLayout.parse("VIEW", null))
        assertEquals(CrossLayout.defaults.getValue("VIEW"), CrossLayout.parse("VIEW", "AUTOSCROLL,ZOOM_FIT,ZOOM_RESET,RELOAD"))
        assertTrue(ReaderAction.ZOOM_RESET !in CrossLayout.pool("VIEW"))
        assertTrue(ReaderAction.ZOOM_RESET in CrossLayout.rest("VIEW", CrossLayout.defaults.getValue("VIEW")))
        assertEquals(listOf(ReaderAction.PIN, ReaderAction.EDIT_TEXT, ReaderAction.DELETE_PINS, ReaderAction.RESTORE_TEXT), CrossLayout.parse("EDIT", "basura"))
        assertEquals(listOf(ReaderAction.COLOR, ReaderAction.WIDTH, ReaderAction.TOOL, ReaderAction.STABILIZER), CrossLayout.parse("DRAW", "COLOR,COLOR,TOOL,WIDTH"))
        assertTrue(CrossLayout.MODES.all { mode -> CrossLayout.defaults.getValue(mode).all { mode in it.modes } })
    }
    @Test fun assigningSwapsOrReplacesAndTheRestStaysInTheMenu() {
        val view = CrossLayout.defaults.getValue("VIEW")
        val replaced = CrossLayout.assign(view, 3, ReaderAction.BACK)
        assertEquals(listOf(ReaderAction.AUTOSCROLL, ReaderAction.ZOOM_FIT, ReaderAction.SETTINGS, ReaderAction.BACK), replaced)
        assertTrue(ReaderAction.RELOAD in CrossLayout.rest("VIEW", replaced))
        assertEquals(listOf(ReaderAction.SETTINGS, ReaderAction.ZOOM_FIT, ReaderAction.AUTOSCROLL, ReaderAction.BACK), CrossLayout.assign(replaced, 0, ReaderAction.SETTINGS))
        val stored = CrossLayout.format(replaced)
        assertEquals(replaced, CrossLayout.parse("VIEW", stored))
        assertEquals(CrossLayout.defaults.getValue("EDIT"), CrossLayout.parse("EDIT", stored))
    }
}
