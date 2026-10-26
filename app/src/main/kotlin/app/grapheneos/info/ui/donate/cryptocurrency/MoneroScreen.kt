package app.grapheneos.info.ui.donate.cryptocurrency

import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Wallpapers
import androidx.compose.ui.unit.dp
import app.grapheneos.info.R
import app.grapheneos.info.ui.reusablecomposables.ScreenLazyColumn

@Composable
fun MoneroScreen(
    modifier: Modifier = Modifier,
    showSnackbarError: (String) -> Unit = {},
    additionalContentPadding: PaddingValues = PaddingValues(0.dp)
) {
    ScreenLazyColumn(
        modifier = modifier
            .fillMaxSize(),
        additionalContentPadding = additionalContentPadding
    ) {
        item {
            Text(stringResource(R.string.monero_info))
        }
        item {
            AddressInfoItem(
                title = "Monero",
                qrCodePainterResourceId = R.drawable.donate_monero_qr_code,
                qrCodeContentDescription = stringResource(R.string.monero_qr_code_description),
                addressUrl = "monero:87J2LuewKtRh78VqDdn3DoUS7rr5hqpMv1Fukf4qgR7a6DoxA61JC2Ad5pg8zyc2wVB6SU9a1TMmDB35EYg7D6HV92JPSTB?recipient_name=MosaicOS&tx_description=Donation%20to%20MosaicOS",
                address = "87J2LuewKtRh78VqDdn3DoUS7rr5hqpMv1Fukf4qgR7a6DoxA61JC2Ad5pg8zyc2wVB6SU9a1TMmDB35EYg7D6HV92JPSTB",
                showSnackbarError = showSnackbarError
            )
        }
    }
}

@Preview(
    showBackground = true,
    wallpaper = Wallpapers.RED_DOMINATED_EXAMPLE,
    uiMode = Configuration.UI_MODE_NIGHT_UNDEFINED
)
@Composable
private fun MoneroScreenPreview() {
    MaterialTheme {
        MoneroScreen()
    }
}