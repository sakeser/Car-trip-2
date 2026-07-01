package com.cartrip.uinext

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

/**
 * A small round map-marker bitmap: a white ring around a coloured centre. Compact and readable — replaces the
 * oversized default Google teardrop pins so the :ui-next maps feel as refined as the legacy app. Used by the
 * trip-detail map hero for the start / end / scrubber markers (the Map hub renders event pins with an equivalent
 * small dot). Anchor the marker at (0.5, 0.5) so the dot centres on the point.
 */
internal fun mapDotIcon(argb: Int, sizePx: Int = 30): BitmapDescriptor {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val c = sizePx / 2f
    val ring = sizePx * 0.14f
    paint.color = AndroidColor.WHITE
    canvas.drawCircle(c, c, c, paint)
    paint.color = argb
    canvas.drawCircle(c, c, c - ring, paint)
    return BitmapDescriptorFactory.fromBitmap(bmp)
}
