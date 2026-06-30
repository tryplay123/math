package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.media.AudioManager
import com.example.R
import com.example.util.GameUtils
import com.example.util.SpeechHelper
import com.example.util.TTSHelper
import com.example.util.SuccessHeartsAnimation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(levelId: Int, viewModel: MainViewModel, onHomeClick: () -> Unit) {
    val profile by viewModel.profile.collectAsState()
    val islandCoins by viewModel.islandCoins.collectAsState()
    
    if (profile == null) return
    val lang = profile!!.language
    val name = profile!!.name
    val operation = profile!!.currentOperation
    
    val context = LocalContext.current
    val ttsHelper = remember { TTSHelper(context) }
    val scope = rememberCoroutineScope()
    var boxLayoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var interactiveCoinTrigger by remember { mutableIntStateOf(0) }
    var shouldNavigateHomeOnAnimEnd by remember { mutableStateOf(false) }
    
    var askedQuestions by remember { 
        mutableStateOf(profile!!.askedQuestionsHistory.split(",").filter { it.isNotEmpty() }.toSet()) 
    }
    var currentLevelId by remember { mutableStateOf(levelId) }
    var questionCountInLevel by remember { mutableStateOf(1) }
    val maxQuestionsPerLevel = 3
    
    var question by remember { mutableStateOf(GameUtils.generateQuestion(operation, currentLevelId, askedQuestions)) }
    var userAnswer by remember { mutableStateOf("") }
    var showCorrect by remember { mutableStateOf(false) }
    var showIncorrect by remember { mutableStateOf(false) }
    var isKeyboardVisible by remember { mutableStateOf(false) }
    var coinAnimationsTrigger by remember { mutableStateOf(0) }
    var chestPosition by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    // Coins are now persisted in the profile, no need to reset on each launch
    // viewModel.resetIslandCoins()

    fun handleSuccess() {
        showCorrect = true
        showIncorrect = false
        coinAnimationsTrigger++
        interactiveCoinTrigger++
        shouldNavigateHomeOnAnimEnd = false
        viewModel.addCoins(10)
        askedQuestions = askedQuestions + question.first
        viewModel.addQuestionToHistory(question.first)
        
        scope.launch {
            try {
                // Use applicationContext to ensure player survives screen transitions
                val mp = MediaPlayer.create(context.applicationContext, R.raw.clapping)
                mp?.apply {
                    setVolume(1.0f, 1.0f)
                    start()
                    setOnCompletionListener { it.release() }
                }
            } catch (e: Exception) {
                // Fallback to ToneGenerator if MediaPlayer fails or resource missing
                try {
                    val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                    repeat(6) {
                        tg.startTone(ToneGenerator.TONE_PROP_ACK, 100)
                        delay(120)
                    }
                } catch (e2: Exception) {}
            }
        }

        val nameWithSukun = if (lang == "ar") "$name" + "ْ" else name
        val msg = if (lang == "ar") "أَحْسَنْتَ يَا $nameWithSukun" else "Well done, $name"
        ttsHelper.speak(msg, lang, rate = 0.8f) 
        
        val isLastLevelInIsland = currentLevelId % 4 == 0
        val isLastQuestionInLevel = questionCountInLevel >= maxQuestionsPerLevel
        
        if (!(isLastLevelInIsland && isLastQuestionInLevel)) {
            scope.launch {
                delay(3000) 
                showCorrect = false
                userAnswer = ""
                
                val nextSet = askedQuestions + question.first
                askedQuestions = nextSet

                if (isLastQuestionInLevel) {
                    // Move to next level within the same island
                    viewModel.completeLevel()
                    currentLevelId++
                    questionCountInLevel = 1
                    question = GameUtils.generateQuestion(operation, currentLevelId, nextSet)
                } else {
                    // Next question in same level
                    questionCountInLevel++
                    question = GameUtils.generateQuestion(operation, currentLevelId, nextSet)
                }
            }
        } else {
            // Last question of last level - ensure it's added to asked
            askedQuestions = askedQuestions + question.first
        }
    }

    fun handleFailure() {
        showIncorrect = true
        showCorrect = false
        val nameWithSukun = if (lang == "ar") "$name" + "ْ" else name
        val msg = if (lang == "ar") "حَاوِلْ مَرَّةً أُخْرَى يَا $nameWithSukun" else "Try again, $name"
        ttsHelper.speak(msg, lang, rate = 0.7f) 
        scope.launch {
            delay(1000)
            showIncorrect = false
            userAnswer = ""
        }
    }
    
    val speechHelper = remember {
        SpeechHelper(
            context = context,
            onResult = { result ->
                userAnswer = result.filter { it.isDigit() }
                if (userAnswer == question.second.toString()) {
                    handleSuccess()
                } else {
                    handleFailure()
                }
            },
            onError = {
                // Optional: handle speech error
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            ttsHelper.shutdown()
            speechHelper.destroy()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFE3F2FD))) {
        // Main Frame - Raised 0.3cm (~11dp) from bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
                .padding(top = 40.dp, bottom = 51.dp) // Matched Home screen dimensions
                .border(8.dp, Color(0xFF03A9F4), RoundedCornerShape(24.dp))
                .background(Color.White, RoundedCornerShape(24.dp))
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Home Button and Island Chest
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 15.dp, start = 15.dp, end = 10.dp), // Reduced end padding to bring home button closer to frame
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.offset(y = 4.dp) // Move chest down 0.1cm (4dp)
                ) {
                    // Chest without square frame border
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .padding(4.dp) // 0.1cm spacing
                            .onGloballyPositioned { coordinates ->
                                chestPosition = coordinates.positionInWindow()
                                boxLayoutCoordinates = coordinates
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_treasure_glass_open),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(4.dp)) // Moved closer (12 -> 4)
                    
                    // Coins in a circle with gold border - Shifted down by 0.2cm (8dp)
                    Box(
                        modifier = Modifier
                            .offset(y = 8.dp)
                            .size(60.dp)
                            .background(Color.White, CircleShape)
                            .border(3.dp, Color(0xFFFFD700), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.animation.AnimatedContent(
                            targetState = islandCoins,
                            transitionSpec = {
                                androidx.compose.animation.slideInVertically { height -> height } + androidx.compose.animation.fadeIn() togetherWith
                                androidx.compose.animation.slideOutVertically { height -> -height } + androidx.compose.animation.fadeOut()
                            }, label = ""
                        ) { targetCoins ->
                            Text("$targetCoins", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFA000))
                        }
                    }
                }

                IconButton(
                    onClick = onHomeClick,
                    modifier = Modifier
                        .offset(y = 8.dp) // Move home button down 0.2cm (8dp)
                        .size(50.dp)
                        .background(Color(0xFF03A9F4), CircleShape)
                ) {
                    Icon(Icons.Default.Home, contentDescription = "Home", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }

            Spacer(modifier = Modifier.height(40.dp)) // Lowered the hut and question box

            // Current Hut Image
            val hutImages = listOf(
                R.drawable.img_hut_1,
                R.drawable.img_hut_2,
                R.drawable.img_hut_3,
                R.drawable.img_hut_4,
                R.drawable.img_hut_5
            )
            val currentHutRes = hutImages[(currentLevelId - 1) % 4]
            Image(
                painter = painterResource(id = currentHutRes),
                contentDescription = "Current Hut",
                modifier = Modifier.size(140.dp) // Slightly larger
            )

            Spacer(modifier = Modifier.height(30.dp)) 

            // Adaptive Question Box with Rainbow Border
            Box(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(horizontal = 4.dp) 
                    .height(200.dp) 
                    .background(Color.White, RoundedCornerShape(32.dp))
                    .border(
                        width = 8.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF4CAF50), // Green
                                Color(0xFF2196F3), // Blue
                                Color(0xFFFF9800), // Orange
                                Color(0xFFF44336), // Red
                                Color(0xFF9C27B0)  // Purple
                            )
                        ),
                        shape = RoundedCornerShape(32.dp)
                    )
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val answerLength = question.second.toString().length
                    val fontSizeValue = when {
                        question.first.length > 8 || answerLength > 2 -> 38.sp
                        question.first.length > 6 -> 48.sp
                        else -> 60.sp
                    }
                    
                    Text(
                        text = "${question.first} =",
                        fontSize = fontSizeValue, 
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(if (answerLength > 2) 12.dp else 24.dp))
                    
                    // Dynamic width answer box
                    Box(
                        modifier = Modifier
                            .widthIn(min = 100.dp, max = 220.dp)
                            .width(if (answerLength > 2) (45 * answerLength).dp else 100.dp)
                            .height(100.dp)
                            .background(Color.White, androidx.compose.ui.graphics.RectangleShape) 
                            .border(6.dp, Color(0xFF4CAF50), androidx.compose.ui.graphics.RectangleShape)
                            .clickable { isKeyboardVisible = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (userAnswer.isEmpty()) "?" else userAnswer,
                            fontSize = fontSizeValue,
                            fontWeight = FontWeight.Bold,
                            color = if (userAnswer.isEmpty()) Color.LightGray else Color(0xFF4CAF50),
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp)) 

            // Keypad integrated directly - only if visible
            if (isKeyboardVisible) {
                com.example.ui.components.NumericKeypad(
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    onKeyClick = { digit ->
                        if (userAnswer.length < 5) {
                            userAnswer += digit
                            if (userAnswer == question.second.toString()) {
                                scope.launch {
                                    delay(300)
                                    handleSuccess()
                                    // viewModel.completeLevel() - removed from here because it's handled in handleSuccess for 5th question
                                    if (currentLevelId % 4 == 0 && questionCountInLevel >= maxQuestionsPerLevel) {
                                        // Final question of final level reached
                                        // viewModel.completeIsland(currentLevelId / 4) - will be called on button click
                                    }
                                }
                            } else if (userAnswer.length >= question.second.toString().length && userAnswer != question.second.toString()) {
                                scope.launch {
                                    delay(300)
                                    handleFailure()
                                }
                            }
                        }
                    },
                    onDeleteClick = {
                        if (userAnswer.isNotEmpty()) userAnswer = userAnswer.dropLast(1)
                    }
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        if (showCorrect) {
            LaunchedEffect(Unit) {
        try {
            val mediaPlayer = android.media.MediaPlayer.create(context, com.example.R.raw.clapping)
            mediaPlayer.start()
            mediaPlayer.setOnCompletionListener { it.release() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(32.dp)
                        .background(Color.White, RoundedCornerShape(24.dp))
                        .border(8.dp, Color(0xFFFFD700), RoundedCornerShape(24.dp))
                        .padding(32.dp)
                ) {
                    Text(
                        text = if (lang == "ar") "إجابة صحيحة!" else "Correct Answer!",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.img_heart_emoji),
                            contentDescription = null,
                            modifier = Modifier.size(60.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "+10",
                            fontSize = 40.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFFA000)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (lang == "ar") "أَحْسَنْتَ يَا $name" else "Well done, $name",
                        fontSize = 22.sp,
                        color = Color.Gray
                    )

                    // Button for manual transition
                    val isLastLevelInIsland = currentLevelId % 4 == 0
                    val isLastQuestionInLevel = questionCountInLevel >= maxQuestionsPerLevel
                    
                    if (isLastLevelInIsland && isLastQuestionInLevel) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                viewModel.completeLevelAndIsland((currentLevelId - 1) / 4)
                                onHomeClick()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Text(
                                if (lang == "ar") "الجزيرة التالية" else "Next Island",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
                SuccessHeartsAnimation()
            }
        }

        if (showIncorrect) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                // Text removed as per request - only audio plays
            }
        }
    }
}
@Composable
fun InteractiveCoinAnimation(
    boxCoordinates: LayoutCoordinates?,
    trigger: Int,
    onAnimationEnd: () -> Unit
) {
    if (trigger == 0 || boxCoordinates == null) return

    val targetX = boxCoordinates.positionInRoot().x
    val targetY = boxCoordinates.positionInRoot().y

    val animatables = remember(trigger) { List(8) { Animatable(0f) } }
    val scope = rememberCoroutineScope()

    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        animatables.forEachIndexed { index, animatable ->
            launch {
                try {
                    animatable.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = 1000,
                            delayMillis = index * 100,
                            easing = FastOutSlowInEasing
                        )
                    )
                } finally {
                    if (index == animatables.lastIndex) {
                        onAnimationEnd()
                    }
                }
            }
        }
    }

    animatables.forEachIndexed { index, animatable ->
        val progress = animatable.value
        if (progress > 0f && progress < 1f) {
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            val density = androidx.compose.ui.platform.LocalDensity.current
            val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
            val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
            
            // Random blast direction for each coin
            val angle = remember(trigger, index) { (index.toFloat() / 8f) * 2f * Math.PI.toFloat() }
            val blastRadius = remember(trigger, index) { Random.nextInt(100, 200).toFloat() }
            
            val startX = screenWidthPx / 2
            val startY = screenHeightPx / 2
            
            // Explosion phase (first 20% of progress)
            val burstProgress = (progress / 0.2f).coerceAtMost(1f)
            val burstX = (Math.cos(angle.toDouble()).toFloat() * blastRadius) * burstProgress
            val burstY = (Math.sin(angle.toDouble()).toFloat() * blastRadius) * burstProgress
            
            // Travel phase (remaining 80%)
            val travelProgress = ((progress - 0.2f) / 0.8f).coerceAtLeast(0f)
            
            val currentStartX = startX + burstX
            val currentStartY = startY + burstY
            
            val currentX = currentStartX + (targetX + 40 - currentStartX) * travelProgress
            
            // Add a high arc to the travel phase
            val arcHeight = -500f
            val currentY = currentStartY + (targetY + 40 - currentStartY) * travelProgress + 
                           (4 * arcHeight * travelProgress * (1 - travelProgress))

            Box(
                modifier = Modifier
                    .offset { IntOffset(currentX.roundToInt(), currentY.roundToInt()) }
                    .size(45.dp)
                    .graphicsLayer(
                        rotationZ = progress * 1080f,
                        scaleX = if (progress < 0.2f) progress / 0.2f else 1f,
                        scaleY = if (progress < 0.2f) progress / 0.2f else 1f,
                        alpha = if (progress > 0.9f) 1f - (progress - 0.9f) * 10f else 1f
                    )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.gold_coin_3d_no_bg_1782735140174),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            }
        }
    }
}

