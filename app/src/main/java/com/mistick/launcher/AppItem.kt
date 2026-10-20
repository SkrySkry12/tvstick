package com.mistick.launcher

import android.content.Intent
import android.graphics.drawable.Drawable

/**
 * A single launchable entry shown on the home screen.
 *
 * @param label       user visible name
 * @param packageName owning package (empty for internal shortcuts)
 * @param icon        app icon
 * @param banner      optional 16:9 TV banner (preferred over icon when present)
 * @param launchIntent intent started when the card is clicked
 * @param isSystem    true for pre-installed/system apps
 */
data class AppItem(
    val label: String,
    val packageName: String,
    val icon: Drawable?,
    val banner: Drawable?,
    val launchIntent: Intent,
    val isSystem: Boolean = false,
    val isTvApp: Boolean = false,
)
