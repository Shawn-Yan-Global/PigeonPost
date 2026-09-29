package com.octopus.pigeon.post.ui.screen

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.octopus.pigeon.post.R
import com.octopus.pigeon.post.data.repository.DebugUnlockState
import com.octopus.pigeon.post.permission.PermissionManager
import com.octopus.pigeon.post.ui.activity.ServiceStatusActivity
import com.octopus.pigeon.post.ui.theme.Gray10
import com.octopus.pigeon.post.ui.theme.ThemeColor
import com.octopus.pigeon.post.ui.viewmodel.MainViewModel
import com.octopus.ui.spacing.AppSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onDebugClick: () -> Unit = {},
    onLogManagementClick: () -> Unit = {},
    onForwardRecordsClick: () -> Unit = {},
    onLanguageSettingsClick: () -> Unit = {},
    permissionRefreshTrigger: Int = 0,
) {
    val context = LocalContext.current
    var hasPermissions by remember { mutableStateOf(PermissionManager.hasAllPermissions(context)) }
    val serviceEnabled by viewModel.serviceEnabled.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Monitor permission changes and refresh automatically when trigger updates
    LaunchedEffect(permissionRefreshTrigger) {
        hasPermissions = PermissionManager.hasAllPermissions(context)
    }

    // Auto-refresh permissions status when app resumes
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    hasPermissions = PermissionManager.hasAllPermissions(context)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Periodically refresh permission status every 10 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(10.seconds)
            hasPermissions = PermissionManager.hasAllPermissions(context)
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }

    // Drawer state to control open/close status
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Lives out here rather than inside the drawer sheet: the prompt is
    // shown on top of the whole drawer, once it is dismissed.
    var showDebugPassword by remember { mutableStateOf(false) }

    // Once the password has been given in this run, the menu stops asking.
    val debugUnlocked by DebugUnlockState.isUnlocked.collectAsState()

    val requestDebugAccess: () -> Unit = {
        if (debugUnlocked) {
            scope.launch { drawerState.close() }
            onDebugClick()
        } else {
            showDebugPassword = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            // Drawer content sheet styled for ModalNavigationDrawer
            ModalDrawerSheet {
                Column(modifier = Modifier.fillMaxSize()) {
                    // The entries scroll on their own so the version footer stays
                    // pinned to the bottom of the sheet rather than trailing the
                    // last button.
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(Gray10)
                                    .padding(AppSpacing.large),
                        ) {
                            // Example image at the top
                            Image(
                                painter = painterResource(id = R.drawable.pigeon_post),
                                contentDescription = "App Logo",
                                modifier =
                                    Modifier
                                        .size(AppSpacing.heroIconSize)
                                        .padding(AppSpacing.small),
                                contentScale = ContentScale.Fit,
                                colorFilter = ColorFilter.tint(ThemeColor),
                            )
                            Spacer(modifier = Modifier.width(AppSpacing.small))
                            Column {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = stringResource(R.string.pigeon_sms_listener),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.DarkGray,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(AppSpacing.small))

                        // Prominent Service Status entry in Drawer
                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AppSpacing.large, vertical = AppSpacing.small)
                                    .clickable {
                                        scope.launch { drawerState.close() }
                                        context.startActivity(Intent(context, ServiceStatusActivity::class.java))
                                    },
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        if (serviceEnabled && hasPermissions) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.errorContainer
                                        },
                                ),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(AppSpacing.large),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        imageVector = if (serviceEnabled && hasPermissions) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (serviceEnabled && hasPermissions) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(AppSpacing.xxLarge),
                                    )
                                    Spacer(modifier = Modifier.width(AppSpacing.medium))
                                    Column {
                                        Text(
                                            text = stringResource(R.string.drawer_service_status_entry),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            text =
                                                if (serviceEnabled && hasPermissions) {
                                                    stringResource(R.string.status_service_running_normal)
                                                } else {
                                                    stringResource(R.string.banner_permission_issue_prompt)
                                                },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(AppSpacing.extraLarge),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(AppSpacing.small))

                        // Forward Records first (most important)
                        Button(
                            onClick = {
                                scope.launch { drawerState.close() }
                                onForwardRecordsClick()
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AppSpacing.large),
                        ) {
                            Text(stringResource(R.string.go_to_forward_records))
                        }

                        Spacer(modifier = Modifier.height(AppSpacing.small))

                        Button(
                            onClick = {
                                scope.launch { drawerState.close() }
                                onLogManagementClick()
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AppSpacing.large),
                        ) {
                            Text(stringResource(R.string.go_to_log_management))
                        }

                        Spacer(modifier = Modifier.height(AppSpacing.small))

                        Button(
                            onClick = {
                                scope.launch { drawerState.close() }
                                onLanguageSettingsClick()
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AppSpacing.large),
                        ) {
                            Text(stringResource(R.string.go_to_language_settings))
                        }

                        Spacer(modifier = Modifier.height(AppSpacing.small))

                        // Debug tools, folded away by default. The password gate on
                        // the debug screen is the real control; this only keeps the
                        // entry from advertising itself in a drawer that ordinary
                        // users open regularly.
                        var showDebugEntry by remember { mutableStateOf(false) }

                        TextButton(
                            onClick = { showDebugEntry = !showDebugEntry },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AppSpacing.large),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.drawer_advanced_options),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Icon(
                                    imageVector =
                                        if (showDebugEntry) {
                                            Icons.Default.ExpandLess
                                        } else {
                                            Icons.Default.ExpandMore
                                        },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (showDebugEntry) {
                            // Visible is not the same as open. This entry point
                            // goes through the same password as the version
                            // footer, otherwise hiding one of them is pointless.
                            Button(
                                onClick = { requestDebugAccess() },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = AppSpacing.large),
                            ) {
                                Text(stringResource(R.string.go_to_debug_screen))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(AppSpacing.small))

                    // Pinned footer, outside the scrolling entries. Long pressing
                    // it three times opens the password prompt for the debug menu.
                    VersionDebugTrigger(
                        onUnlock = {
                            scope.launch { drawerState.close() }
                            onDebugClick()
                        },
                        unlocked = debugUnlocked,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                    )

                    Spacer(modifier = Modifier.height(AppSpacing.medium))
                }
            }
        },
    ) {
        if (showDebugPassword) {
            DebugPasswordPrompt(
                onUnlocked = {
                    showDebugPassword = false
                    scope.launch { drawerState.close() }
                    onDebugClick()
                },
                onDismiss = { showDebugPassword = false },
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.app_title),
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    // Navigation icon to open the drawer
                    navigationIcon = {
                        IconButton(onClick = {
                            // Open drawer on menu icon click
                            scope.launch { drawerState.open() }
                        }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = stringResource(R.string.menu),
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                )
            },
        ) { paddingValues ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
            ) {
                // Service status banner prompt (lightweight bar to navigate to ServiceStatusActivity)
                if (!serviceEnabled || !hasPermissions) {
                    Surface(
                        color = if (!hasPermissions) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    context.startActivity(Intent(context, ServiceStatusActivity::class.java))
                                },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = AppSpacing.large, vertical = AppSpacing.smallPlus),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = if (!hasPermissions) Icons.Default.Warning else Icons.Default.Info,
                                    contentDescription = null,
                                    tint = if (!hasPermissions) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(AppSpacing.iconSizeSmall),
                                )
                                Spacer(modifier = Modifier.width(AppSpacing.small))
                                Text(
                                    text =
                                        if (!hasPermissions) {
                                            stringResource(R.string.banner_permission_issue_prompt)
                                        } else {
                                            stringResource(R.string.banner_service_stopped_prompt)
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = if (!hasPermissions) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = stringResource(R.string.banner_open_drawer),
                                tint = if (!hasPermissions) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(AppSpacing.iconSizeSmall),
                            )
                        }
                    }
                }

                // Tabs for different app sections
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.template_settings)) },
                        icon = { Icon(Icons.Default.AccountBox, contentDescription = null) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(R.string.email_config)) },
                        icon = { Icon(Icons.Default.Email, contentDescription = null) },
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text(stringResource(R.string.active_time)) },
                        icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                    )
                }

                // Display the content corresponding to the selected tab
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                ) {
                    when (selectedTab) {
                        0 ->
                            TemplateScreen(
                                viewModel = viewModel,
                                onOpenServiceStatus = {
                                    context.startActivity(Intent(context, ServiceStatusActivity::class.java))
                                },
                            )
                        1 -> EmailConfigScreen(viewModel = viewModel)
                        2 -> ActiveTimeScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
