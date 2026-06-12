package com.leejlredstar.redefinencm.kmp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // Koin is started in RedefineNCMApp.onCreate(); App() resolves ViewModels via koinInject().
            App()
        }
    }
}
