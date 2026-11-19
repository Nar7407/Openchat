package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.StudyPack
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissConcrete
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissSignalRedTint
import com.example.ui.theme.SwissSubtleFill
import com.example.ui.theme.SwissWhite

@Composable
fun StudyQuizDialog(
    studyPack: StudyPack,
    onDismiss: () -> Unit
) {
    var activeTab by remember { mutableIntStateOf(if (studyPack.flashcards.isNotEmpty()) 0 else 1) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = SwissCanvas,
            shape = RectangleShape,
            border = BorderStroke(1.dp, SwissInk),
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissInk)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "STUDY STUDIO // FLASHCARDS & QUIZ",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissSignalRed
                        )
                        Text(
                            text = studyPack.title.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = SwissWhite
                        )
                    }
                    Box(
                        modifier = Modifier
                            .border(1.dp, SwissWhite, RectangleShape)
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("close_study_dialog")
                    ) {
                        Text("[ CLOSE ]", style = MaterialTheme.typography.labelSmall, color = SwissWhite)
                    }
                }

                // Segmented Swiss Tab Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissPaper)
                        .border(1.dp, SwissInk, RectangleShape)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (activeTab == 0) SwissSignalRed else SwissPaper)
                            .clickable { activeTab = 0 }
                            .padding(vertical = 12.dp)
                            .testTag("study_tab_flashcards"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "FLASHCARDS (${studyPack.flashcards.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (activeTab == 0) SwissWhite else SwissInk
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (activeTab == 1) SwissSignalRed else SwissPaper)
                            .clickable { activeTab = 1 }
                            .padding(vertical = 12.dp)
                            .testTag("study_tab_quiz"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "QUIZ (${studyPack.quiz.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (activeTab == 1) SwissWhite else SwissInk
                        )
                    }
                }

                if (activeTab == 0) {
                    FlashcardsPane(studyPack = studyPack, modifier = Modifier.weight(1f))
                } else {
                    QuizPane(studyPack = studyPack, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FlashcardsPane(
    studyPack: StudyPack,
    modifier: Modifier = Modifier
) {
    val cards = studyPack.flashcards
    if (cards.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("No flashcards generated.", style = MaterialTheme.typography.bodyMedium, color = SwissMuted)
        }
        return
    }
    var index by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    val mastered = remember { mutableStateMapOf<Int, Boolean>() }
    var dragAccum by remember { mutableFloatStateOf(0f) }

    val card = cards[index.coerceIn(0, cards.lastIndex)]
    val isMastered = mastered[index] == true

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CARD ${index + 1} / ${cards.size}",
                style = MaterialTheme.typography.labelLarge,
                color = SwissInk
            )
            Text(
                text = "MASTERED: ${mastered.values.count { it }} / ${cards.size}",
                style = MaterialTheme.typography.labelSmall,
                color = SwissSignalRed
            )
        }

        LinearProgressIndicator(
            progress = { (index + 1).toFloat() / cards.size.toFloat() },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = SwissSignalRed,
            trackColor = SwissConcrete
        )

        // Tap-to-flip & Swipe-to-navigate Swiss Card
        Surface(
            color = if (flipped) SwissInk else SwissPaper,
            shape = RectangleShape,
            border = BorderStroke(2.dp, if (isMastered) SwissSignalRed else SwissInk),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(index, cards.size) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragAccum < -90f && index < cards.lastIndex) {
                                index++
                                flipped = false
                            } else if (dragAccum > 90f && index > 0) {
                                index--
                                flipped = false
                            }
                            dragAccum = 0f
                        },
                        onHorizontalDrag = { _, delta -> dragAccum += delta }
                    )
                }
                .clickable { flipped = !flipped }
                .testTag("flashcard_surface")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (flipped) "02 // ANSWER (BACK)" else "01 // PROMPT (FRONT)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (flipped) SwissSignalRed else SwissMuted
                    )
                    if (isMastered) {
                        Text(
                            text = "[ MASTERED ]",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissSignalRed
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (flipped) card.back else card.front,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (flipped) SwissWhite else SwissInk
                    )
                }

                Text(
                    text = "TAP CARD TO FLIP · SWIPE LEFT/RIGHT FOR NEXT",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (flipped) SwissCanvas else SwissMuted
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(SwissPaper)
                    .border(1.dp, SwissInk, RectangleShape)
                    .clickable(enabled = index > 0) {
                        index = (index - 1).coerceAtLeast(0)
                        flipped = false
                    }
                    .padding(vertical = 12.dp)
                    .testTag("flashcard_prev"),
                contentAlignment = Alignment.Center
            ) {
                Text("< PREV", style = MaterialTheme.typography.labelLarge, color = if (index > 0) SwissInk else SwissMuted)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (isMastered) SwissSignalRed else SwissInk)
                    .clickable { mastered[index] = !isMastered }
                    .padding(vertical = 12.dp)
                    .testTag("flashcard_master"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isMastered) "MASTERED ✓" else "MARK MASTERED",
                    style = MaterialTheme.typography.labelLarge,
                    color = SwissWhite
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(SwissPaper)
                    .border(1.dp, SwissInk, RectangleShape)
                    .clickable(enabled = index < cards.lastIndex) {
                        index = (index + 1).coerceAtMost(cards.lastIndex)
                        flipped = false
                    }
                    .padding(vertical = 12.dp)
                    .testTag("flashcard_next"),
                contentAlignment = Alignment.Center
            ) {
                Text("NEXT >", style = MaterialTheme.typography.labelLarge, color = if (index < cards.lastIndex) SwissInk else SwissMuted)
            }
        }
    }
}

@Composable
private fun QuizPane(
    studyPack: StudyPack,
    modifier: Modifier = Modifier
) {
    val questions = studyPack.quiz
    if (questions.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("No quiz questions generated.", style = MaterialTheme.typography.bodyMedium, color = SwissMuted)
        }
        return
    }
    val selectedAnswers = remember { mutableStateMapOf<Int, Int>() }
    val correctCount = questions.indices.count { qIdx -> selectedAnswers[qIdx] == questions[qIdx].correctIndex }
    val answeredCount = selectedAnswers.size

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Score HUD
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SwissPaper)
                .border(1.dp, SwissInk, RectangleShape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SCORE // $correctCount / ${questions.size} CORRECT ($answeredCount ANSWERED)",
                style = MaterialTheme.typography.labelLarge,
                color = SwissInk
            )
            if (answeredCount > 0) {
                Text(
                    text = "[ RESET ]",
                    style = MaterialTheme.typography.labelSmall,
                    color = SwissSignalRed,
                    modifier = Modifier.clickable { selectedAnswers.clear() }
                )
            }
        }

        questions.forEachIndexed { qIdx, q ->
            val picked = selectedAnswers[qIdx]
            Surface(
                color = SwissPaper,
                shape = RectangleShape,
                border = BorderStroke(1.dp, SwissInk),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Q${qIdx + 1} //",
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissSignalRed
                    )
                    Text(
                        text = q.question,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SwissInk
                    )
                    Spacer(Modifier.height(2.dp))
                    q.options.forEachIndexed { optIdx, optText ->
                        val isPicked = picked == optIdx
                        val isCorrectOption = optIdx == q.correctIndex
                        val bg = when {
                            picked == null -> SwissSubtleFill
                            isCorrectOption -> SwissInk
                            isPicked && !isCorrectOption -> SwissSignalRed
                            else -> SwissPaper
                        }
                        val fg = when {
                            picked != null && (isCorrectOption || isPicked) -> SwissWhite
                            else -> SwissInk
                        }
                        val letter = ('A' + optIdx).toString()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(bg)
                                .border(1.dp, SwissInk, RectangleShape)
                                .clickable(enabled = picked == null) {
                                    selectedAnswers[qIdx] = optIdx
                                }
                                .padding(horizontal = 10.dp, vertical = 10.dp)
                                .testTag("quiz_q${qIdx}_opt$optIdx"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "[$letter]",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (picked == null) SwissSignalRed else fg
                            )
                            Text(
                                text = optText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = fg,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    if (picked != null && q.explanation.isNotBlank()) {
                        val right = picked == q.correctIndex
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (right) SwissSubtleFill else SwissSignalRedTint)
                                .border(1.dp, if (right) SwissInk else SwissSignalRed, RectangleShape)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = (if (right) "CORRECT // " else "EXPLANATION // ") + q.explanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = SwissInk
                            )
                        }
                    }
                }
            }
        }
    }
}
