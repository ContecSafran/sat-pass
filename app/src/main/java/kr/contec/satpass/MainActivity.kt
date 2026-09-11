package kr.contec.satpass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kr.contec.satpass.ui.SatPassApp
import kr.contec.satpass.ui.theme.SatPassTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 갤럭시 S25 Edge 처럼 화면이 길고 펀치홀이 있는 기기를 고려해 edge-to-edge 로 그리고,
        // 인셋은 Scaffold 가 각 화면에 contentPadding 으로 내려 준다.
        enableEdgeToEdge()
        setContent {
            SatPassTheme {
                SatPassApp()
            }
        }
    }
}
