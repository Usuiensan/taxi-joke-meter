package jp.usuiensan.taxijokemeter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import jp.usuiensan.taxijokemeter.ui.shell.AppShell
import jp.usuiensan.taxijokemeter.ui.theme.TaxiJokeMeterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TaxiJokeMeterTheme { AppShell() } }
    }
}
