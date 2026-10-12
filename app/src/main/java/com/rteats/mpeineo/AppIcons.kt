package com.rteats.mpeineo

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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
    @DrawableRes const val Schedule = R.drawable.ms_date_range
    @DrawableRes const val Bars = R.drawable.ms_account_balance
    @DrawableRes const val Mail = R.drawable.ms_mail
    @DrawableRes const val Settings = R.drawable.ms_settings
    @DrawableRes const val Back = R.drawable.ms_arrow_back
    @DrawableRes const val Forward = R.drawable.ms_arrow_forward
    @DrawableRes const val OpenPortal = R.drawable.ms_open_in_new
    @DrawableRes const val Confirm = R.drawable.ms_check
    @DrawableRes const val ExpandMore = R.drawable.ms_keyboard_arrow_down
    @DrawableRes const val ExpandLess = R.drawable.ms_keyboard_arrow_up
    @DrawableRes const val Favorite = R.drawable.ms_star
    @DrawableRes const val Overflow = R.drawable.ms_more_vert
    @DrawableRes const val Edit = R.drawable.ms_edit
    @DrawableRes const val Search = R.drawable.ms_search
    @DrawableRes const val Refresh = R.drawable.ms_refresh
    @DrawableRes const val Location = R.drawable.ms_location_on
    @DrawableRes const val Person = R.drawable.ms_person
    @DrawableRes const val GroupList = R.drawable.ms_list
    @DrawableRes const val Attachment = R.drawable.ms_attach_file
    @DrawableRes const val Download = R.drawable.ms_download
    @DrawableRes const val Logout = R.drawable.ms_logout
    @DrawableRes const val About = R.drawable.ms_info
    @DrawableRes const val Storage = R.drawable.ms_storage
    @DrawableRes const val CheckUpdates = R.drawable.ms_sync
    @DrawableRes const val ClearCache = R.drawable.ms_cleaning_services
    @DrawableRes const val ClearLog = R.drawable.ms_delete_sweep
    @DrawableRes const val Copy = R.drawable.ms_content_copy
    @DrawableRes const val Diagnostics = R.drawable.ms_bug_report
    @DrawableRes const val InstallUpdate = R.drawable.ms_install_mobile
    @DrawableRes const val Notifications = R.drawable.ms_notifications
    @DrawableRes const val Updates = R.drawable.ms_system_update
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
