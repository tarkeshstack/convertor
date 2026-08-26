package com.tarkeshstack.speakeasy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tarkeshstack.speakeasy.ui.theme.IndiaChakraBlue
import com.tarkeshstack.speakeasy.ui.theme.IndiaGreen
import com.tarkeshstack.speakeasy.ui.theme.IndiaSaffron
import com.tarkeshstack.speakeasy.ui.theme.LanguageMaroon
import com.tarkeshstack.speakeasy.ui.theme.LanguageTeal

private data class LanguageBadge(val glyph: String, val color: Color)

private val LANGUAGE_BADGES = listOf(
    LanguageBadge("A", IndiaGreen),
    LanguageBadge("अ", IndiaSaffron),
    LanguageBadge("அ", LanguageMaroon),
    LanguageBadge("Ñ", LanguageTeal),
    LanguageBadge("É", IndiaChakraBlue),
)

/** Row of rounded, colored badges — one script sample per supported language — used as
 *  the shared hero-header element on the Interpret and History screens. */
@Composable
fun LanguageBadgeRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LANGUAGE_BADGES.forEach { badge ->
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .shadow(3.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(badge.color),
                contentAlignment = Alignment.Center,
            ) {
                Text(badge.glyph, style = MaterialTheme.typography.titleLarge, color = Color.White)
            }
        }
    }
}
