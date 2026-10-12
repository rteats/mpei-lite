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
    @DrawableRes val Schedule: Int get() = R.drawable.ms_date_range
    @DrawableRes val Bars: Int get() = R.drawable.ms_account_balance
    @DrawableRes val Mail: Int get() = R.drawable.ms_mail
    @DrawableRes val Settings: Int get() = R.drawable.ms_settings
    @DrawableRes val Back: Int get() = R.drawable.ms_arrow_back
    @DrawableRes val Forward: Int get() = R.drawable.ms_arrow_forward
    @DrawableRes val OpenPortal: Int get() = R.drawable.ms_open_in_new
    @DrawableRes val Confirm: Int get() = R.drawable.ms_check
    @DrawableRes val ExpandMore: Int get() = R.drawable.ms_keyboard_arrow_down
    @DrawableRes val ExpandLess: Int get() = R.drawable.ms_keyboard_arrow_up
    @DrawableRes val Favorite: Int get() = R.drawable.ms_star
    @DrawableRes val Overflow: Int get() = R.drawable.ms_more_vert
    @DrawableRes val Edit: Int get() = R.drawable.ms_edit
    @DrawableRes val Search: Int get() = R.drawable.ms_search
    @DrawableRes val Refresh: Int get() = R.drawable.ms_refresh
    @DrawableRes val Location: Int get() = R.drawable.ms_location_on
    @DrawableRes val Person: Int get() = R.drawable.ms_person
    @DrawableRes val GroupList: Int get() = R.drawable.ms_list
    @DrawableRes val Attachment: Int get() = R.drawable.ms_attach_file
    @DrawableRes val Download: Int get() = R.drawable.ms_download
    @DrawableRes val Logout: Int get() = R.drawable.ms_logout
    @DrawableRes val About: Int get() = R.drawable.ms_info
    @DrawableRes val CheckUpdates: Int get() = R.drawable.ms_sync
    @DrawableRes val ClearLog: Int get() = R.drawable.ms_delete_sweep
    @DrawableRes val Copy: Int get() = R.drawable.ms_content_copy
    @DrawableRes val Diagnostics: Int get() = R.drawable.ms_bug_report
    @DrawableRes val InstallUpdate: Int get() = R.drawable.ms_install_mobile
    @DrawableRes val Notifications: Int get() = R.drawable.ms_notifications
    @DrawableRes val Updates: Int get() = R.drawable.ms_system_update
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
