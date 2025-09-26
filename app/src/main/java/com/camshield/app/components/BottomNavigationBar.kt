package com.camshield.app.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.remember
import com.camshield.app.models.*
import compose.icons.FeatherIcons
import compose.icons.feathericons.FileText
import compose.icons.feathericons.Home
import compose.icons.feathericons.PhoneCall
import compose.icons.feathericons.User

@Composable
fun ModernBottomNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val items: List<BottomNavItem> = listOf(
        BottomNavItem("Main", FeatherIcons.Home),
        BottomNavItem("Report", FeatherIcons.FileText),
        BottomNavItem("Emergency", FeatherIcons.PhoneCall),
        BottomNavItem("Profile", FeatherIcons.User)
    )

    // Get system navigation bar height for minimal spacing adjustment
    val view = LocalView.current
    val bottomInset = remember {
        ViewCompat.getRootWindowInsets(view)?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
    }

    // Only add minimal padding if there's system navigation (gesture navigation has bottomInset = 0)
    val extraPadding = if (bottomInset > 100) 8.dp else 0.dp // Only add padding for 3-button nav

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp + extraPadding), // Much smaller adaptive height
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        // Main navigation content
        Row(
            modifier = Modifier
                .fillMaxSize()
                .selectableGroup()
                .padding(bottom = extraPadding), // Add minimal bottom padding only when needed
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                SecurityNavItem(
                    item = item,
                    isSelected = selectedTab == index,
                    onClick = { onTabSelected(index) }
                )
            }
        }
    }
}

@Composable
private fun RowScope.SecurityNavItem(
    item: BottomNavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(vertical = 8.dp) // Add some vertical padding for better touch targets
    ) {
        // Icon with improved touch target
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(44.dp) // Larger touch target for better accessibility
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }

        // Label text with improved spacing
        Text(
            text = item.title,
            color = if (isSelected)
                MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}