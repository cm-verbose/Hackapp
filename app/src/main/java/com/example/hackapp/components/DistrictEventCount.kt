package com.example.hackapp.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hackapp.R

@Composable
fun DistrictEventCount(
    result : Int = 0,
    badgeColor : Color = colorResource(R.color.black),
    badgeSize : Dp = 32.dp
) {
    Box(
        modifier = Modifier
            .size(badgeSize)
            .clip(CircleShape)
            .background(badgeColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = result.toString(),
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFEDEDED)
@Composable
private fun DistrictEventCountPreview() {
    //How to call
    DistrictEventCount(
        result = 8,
        badgeColor = Color(0xFFE0042B),
        badgeSize = 64.dp
    )
}