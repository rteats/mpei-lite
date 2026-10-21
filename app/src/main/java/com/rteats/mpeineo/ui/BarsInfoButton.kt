package com.rteats.mpeineo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons

/** Structured text stays available without occupying the task-first BARS screens. */
internal data class BarsInfoSection(val heading: String, val description: String)

/** Same accessible help affordance and dismissible, scrollable Material 3 dialog everywhere. */
@Composable
internal fun BarsInfoButton(
    title: String,
    sections: List<BarsInfoSection>,
    modifier: Modifier = Modifier,
    tag: String = "bars-info",
) {
    var dialogVisible by rememberSaveable { mutableStateOf(false) }
    IconButton(
        onClick = { dialogVisible = true },
        modifier = modifier.testTag(tag),
    ) {
        AppIcon(AppIcons.About, contentDescription = title)
    }
    if (dialogVisible) {
        AlertDialog(
            onDismissRequest = { dialogVisible = false },
            title = { Text(title) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    sections.forEach { section ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (section.heading.isNotBlank()) {
                                Text(
                                    section.heading,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Text(
                                section.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogVisible = false }) { Text("Понятно") }
            },
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.testTag(tag + "-dialog"),
        )
    }
}
