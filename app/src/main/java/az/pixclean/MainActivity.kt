package az.pixclean

import android.content.IntentSender
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import az.pixclean.core.ConsentBroker
import az.pixclean.ui.PixCleanRoot
import az.pixclean.ui.theme.PixCleanTheme

class MainActivity : ComponentActivity() {

    private val broker = ConsentBroker()

    private val consentLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        broker.deliver(result.resultCode == RESULT_OK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        broker.launch = { sender: IntentSender ->
            runCatching { consentLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
                .onFailure { broker.deliver(false) }
        }
        setContent {
            PixCleanTheme {
                PixCleanRoot(broker = broker)
            }
        }
    }

    override fun onDestroy() {
        broker.launch = null
        super.onDestroy()
    }
}
