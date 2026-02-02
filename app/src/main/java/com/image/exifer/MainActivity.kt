package com.image.exifer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.image.exifer.ui.theme.ExiferTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExiferTheme {
                ExifScreen(onBack = {
                    // Handle back button press, e.g., finish the activity or navigate
                    finish()
                })
            }
        }
    }
}
