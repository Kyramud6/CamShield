package com.camshield.app.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        BottomNavItem("Main", FeatherIcons.Home),         // Nicer rounded home icon
        BottomNavItem("Report", FeatherIcons.FileText),     // Better report icon
        BottomNavItem("Emergency", FeatherIcons.PhoneCall), // Emergency icon
        BottomNavItem("Profile", FeatherIcons.User)     // Rounded person icon
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .selectableGroup(),
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
    ) {
        // Clean icon button without background
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(38.dp)
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(25.dp)
            )
        }

        // Label text
        Text(
            text = item.title,
            color = if (isSelected)
                MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(top = 1.dp)
        )
    }
}