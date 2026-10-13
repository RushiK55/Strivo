package com.example.strivo.feature.profile

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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Female
import androidx.compose.material.icons.rounded.Male
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.strivo.core.ui.components.AccentButton
import com.example.strivo.core.ui.components.DecimalWheelPicker
import com.example.strivo.core.ui.components.RestDayPicker
import com.example.strivo.core.ui.components.WheelPicker
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppShapes
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.feature.auth.AuthViewModel
import kotlinx.coroutines.launch
import java.time.DayOfWeek

private const val StepCount = 5

@Composable
fun OnboardingScreen(authViewModel: AuthViewModel, profileViewModel: ProfileViewModel) {
    val pagerState = rememberPagerState(pageCount = { StepCount })
    val scope = rememberCoroutineScope()

    var gender by rememberSaveable { mutableStateOf("Male") }
    var age by rememberSaveable { mutableIntStateOf(25) }
    var height by rememberSaveable { mutableDoubleStateOf(170.0) }
    var weight by rememberSaveable { mutableDoubleStateOf(70.0) }
    // Kept as text so it survives a rotation; Sunday is the usual day off.
    var restDaysText by rememberSaveable { mutableStateOf(DayOfWeek.SUNDAY.name) }
    val restDays = restDaysText.split(",").mapNotNull { name -> runCatching { DayOfWeek.valueOf(name) }.getOrNull() }.toSet()

    val currentIndex = pagerState.currentPage

    fun goTo(page: Int) {
        scope.launch { pagerState.animateScrollToPage(page, animationSpec = tween(500)) }
    }

    fun saveAndFinish() {
        scope.launch {
            profileViewModel.saveRestDays(restDays)
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
        // Back on the left, then a segmented bar showing how far through setup the user is.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 24.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { goTo(currentIndex - 1) }, enabled = currentIndex > 0) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = if (currentIndex > 0) AppColors.TextPrimary else Color.Transparent,
                )
            }
            ProgressSegments(currentIndex, Modifier.weight(1f))
        }
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier.weight(1f),
        ) { page ->
            when (page) {
                0 -> StepContainer("What's your gender?", "Used to estimate the calories you burn.") {
                    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        GenderButton("Male", Icons.Rounded.Male, gender == "Male") { gender = "Male" }
                        Spacer(Modifier.width(24.dp))
                        GenderButton("Female", Icons.Rounded.Female, gender == "Female") { gender = "Female" }
                    }
                }

                1 -> StepContainer("How old are you?", "Used for your daily calorie needs.") {
                    WheelPicker(
                        label = "years",
                        minValue = 10,
                        maxValue = 100,
                        initialValue = age,
                        onChanged = { age = it },
                    )
                }

                2 -> StepContainer("What's your height?", "In centimetres. Used for your BMI.") {
                    DecimalWheelPicker(
                        label = "cm",
                        minValue = 100,
                        maxValue = 250,
                        value = height,
                        onValueChange = { height = it },
                        dotSize = 40.sp,
                    )
                }

                3 -> StepContainer("What's your weight?", "In kilograms. You can update it every week.") {
                    DecimalWheelPicker(
                        label = "kg",
                        minValue = 30,
                        maxValue = 250,
                        value = weight,
                        onValueChange = { weight = it },
                        dotSize = 40.sp,
                    )
                }

                else -> StepContainer(
                    "Which days do you rest?",
                    "A rest day without a workout won't break your streak. Pick any days, or none.",
                ) {
                    RestDayPicker(
                        selected = restDays,
                        onChange = { days -> restDaysText = days.joinToString(",") { it.name } },
                    )
                }
            }
        }

        AccentButton(
            text = if (currentIndex == StepCount - 1) "Finish" else "Continue",
            onClick = { if (currentIndex < StepCount - 1) goTo(currentIndex + 1) else saveAndFinish() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
            height = 54.dp,
        )
    }
}

@Composable
private fun ProgressSegments(currentIndex: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(StepCount) { index ->
            val color by animateColorAsState(if (index <= currentIndex) AppColors.Accent else AppColors.SurfaceHigh, tween(300), label = "step-color")
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
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
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(28.dp))
        Text(text = title, style = AppType.LargeTitle, color = AppColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(text = subtitle, style = AppType.Body, color = AppColors.TextSecondary)
        // The input sits in the middle of the space left under the title.
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun GenderButton(value: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(if (selected) AppColors.Accent else AppColors.Surface, tween(300), label = "gender-bg")
    Column(
        modifier = Modifier
            .size(width = 120.dp, height = 140.dp)
            .clip(AppShapes.Card)
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
            style = AppType.BodyStrong,
        )
    }
}
