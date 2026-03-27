package com.flx_apps.distraquit.ui.screens.feature

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.distraquit.MainActivity
import com.flx_apps.distraquit.R
import com.flx_apps.distraquit.feature_types.SupportsScheduleFeature
import com.flx_apps.distraquit.ui.screens.nav_host.NavViewModel
import com.flx_apps.distraquit.ui.screens.nav_host.NavigationRoutes
import com.flx_apps.distraquit.ui.widgets.SimpleListTile

/**
 * A tile that opens the schedule screen
 */
@Composable
fun OpenScheduleTile(
    featureViewModel: FeatureViewModel = viewModel(),
    navViewModel: NavViewModel = viewModel(viewModelStoreOwner = LocalContext.current as MainActivity)
) {
    val rulesCount = (featureViewModel.feature as SupportsScheduleFeature).scheduleRules.size
    SimpleListTile(titleText = stringResource(id = R.string.feature_settings_schedule),
        subtitleText = if (rulesCount == 0) stringResource(id = R.string.feature_settings_schedule_hint_activeAllTheTime)
        else stringResource(
            id = R.string.feature_settings_schedule_hint, rulesCount
        ),
        trailing = {
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = "Manage Schedule",
                modifier = Modifier.size(24.dp)
            )
        },
        leadingIcon = Icons.Default.EditCalendar,
        onClick = {
            navViewModel.openRoute(NavigationRoutes.FeatureSchedule(featureId = featureViewModel.feature.id))
        })
}