package com.cartrip.analyzer.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlacesTest {

    @Test fun parsesTopPlaceName() {
        val json = """{"places":[{"displayName":{"text":"IKEA North York","languageCode":"en"}}]}"""
        assertEquals("IKEA North York", Places.topPlaceName(json))
    }

    @Test fun emptyOrMissingPlacesIsNull() {
        assertNull(Places.topPlaceName("""{"places":[]}"""))
        assertNull(Places.topPlaceName("""{}"""))
        assertNull(Places.topPlaceName("""{"places":[{"displayName":{"text":""}}]}"""))
    }

    @Test fun malformedJsonIsNullNotCrash() {
        assertNull(Places.topPlaceName("not json"))
        assertNull(Places.topPlaceName(""))
    }

    @Test fun cellKeyQuantizesByCell() {
        // Two points ~30 m apart land in the same ~110 m cell; a point ~1 km away does not.
        val a = Places.cellKey(43.7582, -79.4039)
        val near = Places.cellKey(43.7584, -79.4040)
        val far = Places.cellKey(43.7682, -79.4039)
        assertEquals(a, near)
        assertNotEquals(a, far)
    }
}
