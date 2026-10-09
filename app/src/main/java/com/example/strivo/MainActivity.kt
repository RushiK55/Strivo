package com.example.strivo

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.strivo.ui.StrivoRoot
import com.example.strivo.ui.theme.StrivoTheme

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        // Back on screen: upload anything saved while away and pick up changes made on another phone.
        (application as StrivoApp).syncNow()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            StrivoTheme {
                StrivoRoot()
            }
        }
    }
}
