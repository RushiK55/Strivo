package com.example.strivo.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Female
import androidx.compose.material.icons.rounded.Male
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.strivo.ui.components.AccentButton
import com.example.strivo.ui.components.DecimalWheelPicker
import com.example.strivo.ui.components.WheelPicker
import com.example.strivo.ui.theme.AppColors
import com.example.strivo.viewmodel.AuthViewModel
import com.example.strivo.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch

private const val StepCount = 4

@Composable
fun OnboardingScreen(authViewModel: AuthViewModel, profileViewModel: ProfileViewModel) {
    val pagerState = rememberPagerState(pageCount = { StepCount })
    val scope = rememberCoroutineScope()

    var gender by rememberSaveable { mutableStateOf("Male") }
    var age by rememberSaveable { mutableIntStateOf(25) }
    var height by rememberSaveable { mutableDoubleStateOf(170.0) }
    var weight by rememberSaveable { mutableDoubleStateOf(70.0) }

    val currentIndex = pagerState.currentPage

    fun goTo(page: Int) {
        scope.launch { pagerState.animateScrollToPage(page, animationSpec = tween(500)) }
    }

    fun saveAndFinish() {
        scope.launch {
            profileViewModel.saveProfile(gender = gender, age = age, height = height, weight = weight)
            authViewModel.refreshProfileStatus()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .systemBarsPadding(),
    ) {
        Spacer(Modifier.height(40.dp))
        ProgressDots(currentIndex)
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier.weight(1f),
        ) { page ->
            when (page) {
                0 -> StepContainer("What's your gender?", "Help us customize your experience") {
                    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        GenderButton("Male", Icons.Rounded.Male, gender == "Male") { gender = "Male" }
                        Spacer(Modifier.width(24.dp))
                        GenderButton("Female", Icons.Rounded.Female, gender == "Female") { gender = "Female" }
                    }
                }

                1 -> StepContainer("How old are you?", "Your age helps us calculate metrics") {
                    WheelPicker(
                        label = "YEARS",
                        minValue = 10,
                        maxValue = 100,
                        initialValue = age,
                        onChanged = { age = it },
                    )
                }

                2 -> StepContainer("What's your height?", "Measure in centimeters") {
                    DecimalWheelPicker(
                        label = "CM",
                        minValue = 100,
                        maxValue = 250,
                        value = height,
                        onValueChange = { height = it },
                        dotSize = 40.sp,
                    )
                }

                else -> StepContainer("What's your weight?", "Measure in kilograms") {
                    DecimalWheelPicker(
                        label = "KG",
                        minValue = 30,
                        maxValue = 250,
                        value = weight,
                        onValueChange = { weight = it },
                        dotSize = 40.sp,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (currentIndex > 0) {
                TextButton(onClick = { goTo(currentIndex - 1) }) {
                    Text(
                        "BACK",
                        color = AppColors.TextSecondary,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                    )
                }
            } else {
                Spacer(Modifier)
            }
            AccentButton(
                text = if (currentIndex == StepCount - 1) "FINISH" else "NEXT",
                onClick = { if (currentIndex < StepCount - 1) goTo(currentIndex + 1) else saveAndFinish() },
                modifier = Modifier.width(150.dp),
                height = 55.dp,
                shape = RoundedCornerShape(20.dp),
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun ProgressDots(currentIndex: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(StepCount) { index ->
            val active = currentIndex == index
            val width by animateDpAsState(if (active) 30.dp else 8.dp, tween(300), label = "dot-width")
            val color by animateColorAsState(if (active) AppColors.Accent else AppColors.Field, tween(300), label = "dot-color")
            Box(
                Modifier
                    .padding(horizontal = 5.dp)
                    .height(8.dp)
                    .width(width)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun StepContainer(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            textAlign = TextAlign.Center,
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            color = AppColors.TextPrimary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = subtitle,
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            color = AppColors.TextSecondary,
        )
        Spacer(Modifier.height(60.dp))
        content()
    }
}

@Composable
private fun GenderButton(value: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(if (selected) AppColors.Accent else AppColors.Surface, tween(300), label = "gender-bg")
    Column(
        modifier = Modifier
            .size(width = 120.dp, height = 140.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(background)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = value,
            tint = if (selected) Color.Black else AppColors.Accent,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = value,
            color = if (selected) Color.Black else AppColors.TextPrimary,
            fontWeight = FontWeight.Black,
            fontSize = 16.sp,
        )
    }
}
