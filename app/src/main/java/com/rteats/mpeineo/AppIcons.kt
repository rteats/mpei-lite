package com.rteats.mpeineo

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection

/**
 * Central registry for official Google Material Symbols Outlined.
 * Every resource has FILL=0, wght=400, GRAD=0, opsz=24.
 * Change only the resource mapping or associated ms_*.xml to replace an icon.
 *
 * Sources: https://github.com/google/material-design-icons/tree/master/symbols/web
 * License: Apache License 2.0.
 */
object AppIcons {
    @get:DrawableRes val Schedule: Int get() = R.drawable.ms_date_range
    @get:DrawableRes val Bars: Int get() = R.drawable.ms_account_balance
    @get:DrawableRes val Mail: Int get() = R.drawable.ms_mail
    @get:DrawableRes val Settings: Int get() = R.drawable.ms_settings
    @get:DrawableRes val Home: Int get() = R.drawable.ms_home
    @get:DrawableRes val Back: Int get() = R.drawable.ms_arrow_back
    @get:DrawableRes val Forward: Int get() = R.drawable.ms_arrow_forward
    @get:DrawableRes val OpenPortal: Int get() = R.drawable.ms_open_in_new
    @get:DrawableRes val Confirm: Int get() = R.drawable.ms_check
    @get:DrawableRes val ExpandMore: Int get() = R.drawable.ms_keyboard_arrow_down
    @get:DrawableRes val ExpandLess: Int get() = R.drawable.ms_keyboard_arrow_up
    @get:DrawableRes val Favorite: Int get() = R.drawable.ms_star
    @get:DrawableRes val Overflow: Int get() = R.drawable.ms_more_vert
    @get:DrawableRes val Edit: Int get() = R.drawable.ms_edit
    @get:DrawableRes val Search: Int get() = R.drawable.ms_search
    @get:DrawableRes val Refresh: Int get() = R.drawable.ms_refresh
    @get:DrawableRes val Location: Int get() = R.drawable.ms_location_on
    @get:DrawableRes val Person: Int get() = R.drawable.ms_person
    @get:DrawableRes val GroupList: Int get() = R.drawable.ms_list
    @get:DrawableRes val Attachment: Int get() = R.drawable.ms_attach_file
    @get:DrawableRes val Download: Int get() = R.drawable.ms_download
    @get:DrawableRes val Logout: Int get() = R.drawable.ms_logout
    @get:DrawableRes val About: Int get() = R.drawable.ms_info
    @get:DrawableRes val CheckUpdates: Int get() = R.drawable.ms_sync
    @get:DrawableRes val ClearLog: Int get() = R.drawable.ms_delete_sweep
    @get:DrawableRes val Copy: Int get() = R.drawable.ms_content_copy
    @get:DrawableRes val Diagnostics: Int get() = R.drawable.ms_bug_report
    @get:DrawableRes val InstallUpdate: Int get() = R.drawable.ms_install_mobile
    @get:DrawableRes val Notifications: Int get() = R.drawable.ms_notifications
    @get:DrawableRes val Updates: Int get() = R.drawable.ms_system_update
}

/**
 * Compose Icon wrapper: preserves Material You tinting, normal sizing and
 * accessibility semantics without a font or additional icon dependency.
 * Directional arrows retain the RTL behavior of their former AutoMirrored icons.
 */
@Composable
fun AppIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val mirrorInRtl = LocalLayoutDirection.current == LayoutDirection.Rtl &&
        (icon == AppIcons.Back || icon == AppIcons.Forward)
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = if (mirrorInRtl) modifier.graphicsLayer { scaleX = -1f } else modifier,
        tint = tint,
    )
}
