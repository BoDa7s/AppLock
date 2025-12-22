package com.example.adamapplock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fun goNext() {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        setContent {
            // Ensure at least ONE frame is drawn so your full background is visible
            LaunchedEffect(Unit) {
                delay(3000L) // 3 seconds
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                finish()
            }

            val cfg = LocalConfiguration.current
            val logoSize = (cfg.screenWidthDp * 0.30f).dp  // 30% of screen width

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0B1220)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(R.drawable.splash_logo),
                        contentDescription = null,
                        modifier = Modifier.size(logoSize)
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Lock your apps. Stay in control.",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
