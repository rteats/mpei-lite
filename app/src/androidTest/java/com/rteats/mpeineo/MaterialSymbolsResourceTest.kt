package com.rteats.mpeineo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Resource IDs can be non-final in Android projects; using const val for R.drawable
 * IDs can inline 0 into UI bytecode and crash painterResource on app startup.
 */
@RunWith(AndroidJUnit4::class)
class MaterialSymbolsResourceTest {
    @Test
    fun everyCatalogEntryResolvesToPackagedDrawable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val icons = listOf(
            "Schedule" to AppIcons.Schedule,
            "Bars" to AppIcons.Bars,
            "Mail" to AppIcons.Mail,
            "Settings" to AppIcons.Settings,
            "Back" to AppIcons.Back,
            "Forward" to AppIcons.Forward,
            "OpenPortal" to AppIcons.OpenPortal,
            "Confirm" to AppIcons.Confirm,
            "ExpandMore" to AppIcons.ExpandMore,
            "ExpandLess" to AppIcons.ExpandLess,
            "Favorite" to AppIcons.Favorite,
            "Overflow" to AppIcons.Overflow,
            "Edit" to AppIcons.Edit,
            "Search" to AppIcons.Search,
            "Refresh" to AppIcons.Refresh,
            "Location" to AppIcons.Location,
            "Person" to AppIcons.Person,
            "GroupList" to AppIcons.GroupList,
            "Attachment" to AppIcons.Attachment,
            "Download" to AppIcons.Download,
            "Logout" to AppIcons.Logout,
            "About" to AppIcons.About,
            "CheckUpdates" to AppIcons.CheckUpdates,
            "ClearLog" to AppIcons.ClearLog,
            "Copy" to AppIcons.Copy,
            "Diagnostics" to AppIcons.Diagnostics,
            "InstallUpdate" to AppIcons.InstallUpdate,
            "Notifications" to AppIcons.Notifications,
            "Updates" to AppIcons.Updates,
        )
        for ((name, id) in icons) {
            assertTrue("$name resolved to resource ID 0", id != 0)
            assertEquals("$name is not a drawable", "drawable", context.resources.getResourceTypeName(id))
            assertNotNull("$name could not load", context.getDrawable(id))
        }
    }
}
