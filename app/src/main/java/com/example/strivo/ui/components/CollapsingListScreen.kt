package com.example.strivo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.strivo.ui.theme.AppColors

private val ToolbarHeight = 56.dp

/**
 * A list screen whose tall header scrolls away while a slim pinned bar (back button + title) fades in,
 * like a collapsing app bar.
 */
@Composable
fun CollapsingListScreen(
    title: String,
    expandedHeight: Dp,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    backButtonBackground: Color = AppColors.Surface,
    overlay: @Composable BoxScope.() -> Unit = {},
    header: @Composable BoxScope.() -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val density = LocalDensity.current
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerHeight = expandedHeight + statusBarHeight
    val collapseRangePx = with(density) { (headerHeight - ToolbarHeight - statusBarHeight).toPx() }

    val collapse by remember(collapseRangePx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / collapseRangePx).coerceIn(0f, 1f)
        }
    }

    Box(modifier.fillMaxSize().background(AppColors.Background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        ) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(headerHeight)
                        .background(AppColors.Background)
                ) {
                    Box(Modifier.fillMaxSize().padding(top = statusBarHeight)) { header() }
                    Text(
                        text = title,
                        color = AppColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 24.dp, end = 24.dp, bottom = 14.dp)
                            .alpha(1f - collapse),
                    )
                }
            }
            content()
        }

        // Pinned bar
        Box(
            Modifier
                .fillMaxWidth()
                .height(ToolbarHeight + statusBarHeight)
                .background(AppColors.Background.copy(alpha = collapse))
                .padding(top = statusBarHeight),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                color = AppColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                maxLines = 1,
                modifier = Modifier
                    .padding(horizontal = 64.dp)
                    .alpha(collapse),
            )
            Box(Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) {
                CircleBackButton(onBack, background = backButtonBackground)
            }
        }

        overlay()
    }
}
