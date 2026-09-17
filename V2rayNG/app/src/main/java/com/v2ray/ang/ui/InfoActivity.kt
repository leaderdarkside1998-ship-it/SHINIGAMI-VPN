package com.v2ray.ang.ui

import android.os.Bundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.SettingsMenuItem
import com.v2ray.ang.util.Utils

class InfoActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        InfoScreen(onBackClick = { finish() })
    }
}

@Composable
fun InfoScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_info),
                onBackClick = onBackClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.img_shinigami_profile),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(top = 24.dp, bottom = 8.dp)
                    .size(120.dp)
                    .clip(CircleShape)
            )
            Text(
                text = stringResource(R.string.app_name),
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_telegram_24dp),
                title = stringResource(R.string.title_developer_id),
                subtitle = "@IDSHINIGAMI",
                onClick = { Utils.openUri(context, "https://t.me/IDSHINIGAMI") },
                modifier = Modifier.fillMaxWidth()
            )
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_telegram_24dp),
                title = stringResource(R.string.title_tg_channel),
                subtitle = "@IDSHINIGAMI_STORE",
                onClick = { Utils.openUri(context, "https://t.me/IDSHINIGAMI_STORE") },
                modifier = Modifier.fillMaxWidth()
            )

            NavigationBarsSpacer()
        }
    }
}
