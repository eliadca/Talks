package com.eliadca.talks.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Expands the existing left menu; narrow screens keep a visible strip of their content. */
@Composable
fun SidebarLayout(expanded: Boolean, onClose: () -> Unit, showRail: Boolean, menu: @Composable () -> Unit,
    rail: @Composable () -> Unit, modifier: Modifier = Modifier, content: @Composable (Modifier) -> Unit) {
    BackHandler(enabled = expanded, onBack = onClose)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val menuWidth = minOf(280.dp, maxWidth * .72f)
        Row(Modifier.fillMaxSize()) {
            if (showRail) {
                Surface(Modifier.animateContentSize().width(if (expanded) menuWidth else 88.dp).fillMaxHeight().testTag("left-menu"),
                    shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = if (expanded) 4.dp else 0.dp) {
                    if (expanded) menu() else rail()
                }
            }
            content(Modifier.weight(1f).fillMaxHeight())
        }
        if (!showRail && expanded) {
            Surface(Modifier.align(Alignment.CenterStart).width(menuWidth).fillMaxHeight().testTag("left-menu"),
                shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = 12.dp) { menu() }
        }
    }
}
