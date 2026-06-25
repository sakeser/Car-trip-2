package com.cartrip.analyzer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A road-"bump" glyph (a hump over a baseline) as a Compose [ImageVector], so the speed-timeline and
 * event list show the SAME bump symbol the map marker draws (`TripMap.drawBump`). The declared fill
 * is overridden by the `Icon(tint = ...)` at the call site, so it renders yellow in the list and
 * white-on-yellow on the timeline, matching the map.
 */
val BumpGlyph: ImageVector = ImageVector.Builder(
    name = "BumpGlyph",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    // The hump (a filled dome sitting on the baseline).
    path(fill = SolidColor(Color.Black)) {
        moveTo(4.5f, 15.5f)
        curveTo(4.5f, 5f, 19.5f, 5f, 19.5f, 15.5f)
        close()
    }
    // The road baseline under it.
    path(fill = SolidColor(Color.Black)) {
        moveTo(2.5f, 15.5f)
        lineTo(21.5f, 15.5f)
        lineTo(21.5f, 18f)
        lineTo(2.5f, 18f)
        close()
    }
}.build()
