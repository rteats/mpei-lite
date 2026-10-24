package com.rteats.mpeineo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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

/** Structured help remains accessible without filling the task-first BARS UI. */
internal data class BarsInfoSection(val heading: String, val description: String)

/**
 * Extended explanations belong in a dismissible ModalBottomSheet rather than
 * a cramped alert dialog. Confirmations (for example, renaming a favorite)
 * keep AlertDialog, because they require one focused choice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BarsInfoButton(
    title: String,
    sections: List<BarsInfoSection>,
    modifier: Modifier = Modifier,
    tag: String = "bars-info",
) {
    var sheetVisible by rememberSaveable { mutableStateOf(false) }
    IconButton(
        onClick = { sheetVisible = true },
        modifier = modifier.testTag(tag),
    ) {
        AppIcon(AppIcons.About, contentDescription = title)
    }
    if (sheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { sheetVisible = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            modifier = Modifier.testTag(tag + "-sheet"),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold)
                sections.forEach { section ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (section.heading.isNotBlank()) {
                            Text(section.heading,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold)
                        }
                        Text(section.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = { sheetVisible = false }) {
                    Text("Понятно")
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
