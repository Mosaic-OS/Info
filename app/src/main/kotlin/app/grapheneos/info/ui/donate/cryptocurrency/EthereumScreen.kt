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
fun EthereumScreen(
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
            Text(stringResource(R.string.etherium_info))
        }
        item {
            AddressInfoItem(
                title = stringResource(R.string.ethereum),
                qrCodePainterResourceId = R.drawable.donate_ethereum_qr_code,
                qrCodeContentDescription = stringResource(R.string.ethereum_qr_code_description),
                addressUrl = "ethereum:0x44efF65cAAd5c3395B112e9A8524aA3a2171b28f",
                address = "0x44efF65cAAd5c3395B112e9A8524aA3a2171b28f",
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
private fun EthereumScreenPreview() {
    MaterialTheme {
        EthereumScreen()
    }
}