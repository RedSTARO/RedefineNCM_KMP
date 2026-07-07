package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun AutoHideMiniPlayerController(
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(true) }
    var revealRequest by remember { mutableIntStateOf(0) }

    fun reveal() {
        revealRequest += 1
    }

    LaunchedEffect(revealRequest) {
        visible = true
        delay(3_600)
        visible = false
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(112.dp)
                .pointerInput(Unit) {
                    detectTapGestures { reveal() }
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        if (dragAmount < -10f) {
                            change.consume()
                            reveal()
                        }
                    }
                },
        )

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
            enter = slideInVertically(
                animationSpec = tween(260, easing = FastOutSlowInEasing),
                initialOffsetY = { it / 2 },
            ) + fadeIn(animationSpec = tween(160, easing = LinearOutSlowInEasing)) +
                scaleIn(
                    initialScale = 0.92f,
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                ),
            exit = slideOutVertically(
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                targetOffsetY = { it / 2 },
            ) + fadeOut(animationSpec = tween(140, easing = LinearOutSlowInEasing)) +
                scaleOut(
                    targetScale = 0.92f,
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                ),
        ) {
            MiniNowPlayingBar(
                onExpand = {
                    reveal()
                    onExpand()
                },
            )
        }

        AnimatedVisibility(
            visible = !visible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 18.dp),
            enter = fadeIn(animationSpec = tween(180, easing = LinearOutSlowInEasing)) +
                scaleIn(
                    initialScale = 0.94f,
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                ),
            exit = fadeOut(animationSpec = tween(120, easing = LinearOutSlowInEasing)) +
                scaleOut(
                    targetScale = 0.94f,
                    animationSpec = tween(140, easing = FastOutSlowInEasing),
                ),
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.14f),
                contentColor = Color.White.copy(alpha = 0.84f),
            ) {
                Text(
                    text = "轻点底部或上滑呼出播放器",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}
