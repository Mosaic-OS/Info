package app.grapheneos.info.ui.donate

import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Wallpapers
import androidx.compose.ui.unit.dp
import app.grapheneos.info.R
import app.grapheneos.info.ui.reusablecomposables.ScreenLazyColumn
import app.grapheneos.info.ui.reusablecomposables.ScreenNavCardItem

@Composable
fun DonateStartScreen(
    modifier: Modifier = Modifier,
    onNavigateToCryptocurrenciesScreen: () -> Unit = {},
    onNavigateToBuyMeACoffeeScreen: () -> Unit = {},
    additionalContentPadding: PaddingValues = PaddingValues(0.dp)
) {
    ScreenLazyColumn(
        modifier = modifier
            .fillMaxSize(),
        additionalContentPadding = additionalContentPadding
    ) {
        item {
            Text(stringResource(R.string.donate_start_info_part_1))
        }
        item {
            Text(stringResource(R.string.donate_start_info_part_2))
        }
        item {
            Text(
                stringResource(R.string.donate_the_way_you_want),
                modifier = Modifier.padding(top = 16.dp)
                .semantics { heading() },
                style = MaterialTheme.typography.titleLarge
            )
        }
        item {
            Text(stringResource(R.string.donate_start_info_part_3))
        }
        item {
            ScreenNavCardItem(
                painter = painterResource(id = R.drawable.crypto),
                title = stringResource(id = R.string.cryptocurrencies)
            ) {
                onNavigateToCryptocurrenciesScreen()
            }
        }
        item {
            ScreenNavCardItem(
                painter = rememberVectorPainter(Icons.Filled.LocalCafe),
                title = stringResource(id = R.string.buy_me_a_coffee)
            ) {
                onNavigateToBuyMeACoffeeScreen()
            }
        }
    }
}

@Preview(
    showBackground = true,
    wallpaper = Wallpapers.RED_DOMINATED_EXAMPLE,
    uiMode = Configuration.UI_MODE_NIGHT_UNDEFINED
)
@Composable
private fun DonateScreenPreview() {
    MaterialTheme {
        DonateStartScreen()
    }
}
