package com.camshield.app.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.camshield.app.R
import androidx.compose.material3.Text
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        )
        alpha.animateTo(1f, animationSpec = tween(1000))
        delay(2500)
        alpha.animateTo(0f, animationSpec = tween(500))
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentHeight()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFe0f2fe), Color(0xFFf0fdf4))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha.value)
        ) {
            Image(
                painter = painterResource(id = R.drawable.camshieldlogo),
                contentDescription = "CamSHIELD Logo",
                modifier = Modifier
                    .size(width = 250.dp, height = 80.dp)
                    .scale(scale.value),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your Campus Safety Companion",
                fontSize = 16.sp,
                color = Color.Black.copy(alpha = 0.8f)
            )
        }
    }
}
