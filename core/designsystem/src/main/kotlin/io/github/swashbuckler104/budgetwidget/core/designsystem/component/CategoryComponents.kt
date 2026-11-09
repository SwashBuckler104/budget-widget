package io.github.swashbuckler104.budgetwidget.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.swashbuckler104.budgetwidget.core.model.Category
import io.github.swashbuckler104.budgetwidget.core.model.CategoryGroup

/** The category's emoji on a soft disc of its color. Decorative: the name always sits next to it. */
@Composable
fun CategoryAvatar(emoji: String, colorArgb: Int, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(colorArgb).copy(alpha = 0.22f))
            .clearAndSetSemantics { },
    ) {
        Text(
            emoji,
            style = if (size >=
                40.dp
            ) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyMedium
            },
        )
    }
}

/** A one-tap category choice: emoji plus name. At least 48 dp tall through the chip's touch target. */
@Composable
fun CategoryChip(category: Category, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(category.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Text(category.emoji, modifier = Modifier.clearAndSetSemantics { }) },
        modifier = modifier,
    )
}

/**
 * Every pickable leaf, grouped under its top-level category (SCOPE.md F4 "full category grid with
 * subcategories"). Groups with nothing to pick are left out.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(
    groups: List<CategoryGroup>,
    onPick: (Category) -> Unit,
    modifier: Modifier = Modifier,
    selectedId: Long? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        groups.filter { it.pickableLeaves.isNotEmpty() }.forEach { group ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CategoryAvatar(group.category.emoji, group.category.colorArgb, size = 24.dp)
                    Text(
                        group.category.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.pickableLeaves.forEach { leaf ->
                        CategoryChip(leaf, onClick = { onPick(leaf) }, selected = leaf.id == selectedId)
                    }
                }
            }
        }
    }
}
