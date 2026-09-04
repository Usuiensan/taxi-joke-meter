package jp.usuiensan.taxijokemeter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import jp.usuiensan.taxijokemeter.ui.shell.AppShell
import jp.usuiensan.taxijokemeter.ui.theme.TaxiJokeMeterTheme
import android.view.WindowManager
import com.google.android.libraries.places.api.Places
import jp.usuiensan.taxijokemeter.BuildConfig

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.GOOGLE_MAPS_API_KEY.isNotBlank() && !Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(this, BuildConfig.GOOGLE_MAPS_API_KEY)
        }
        setContent { TaxiJokeMeterTheme { AppShell() } }
    }
}
