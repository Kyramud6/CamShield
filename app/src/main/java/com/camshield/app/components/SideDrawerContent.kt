package com.camshield.app.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.camshield.app.models.DrawerMenuItem
import com.camshield.app.R
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Walking
import compose.icons.fontawesomeicons.solid.MapMarkerAlt
import compose.icons.fontawesomeicons.solid.PhoneAlt
import compose.icons.fontawesomeicons.solid.Lightbulb
import compose.icons.fontawesomeicons.solid.Bell
import compose.icons.fontawesomeicons.solid.Cog


@Composable
fun SideDrawerContent(
    onCloseDrawer: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(320.dp),
        drawerContainerColor = Color(0xFFFAFAFA),
        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp) // smaller height since logo + button are in one row
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF2E3440),
                                Color(0xFF3B4252),
                                Color(0xFF434C5E)
                            )
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Logo on the left
                    Image(
                        painter = painterResource(id = R.drawable.camshieldlogo),
                        contentDescription = "CamShield Logo",
                        modifier = Modifier.size(160.dp) // adjust size
                    )

                    // Close button on the right
                    IconButton(
                        onClick = onCloseDrawer,
                        modifier = Modifier
                            .background(
                                Color.White.copy(alpha = 0.1f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }



            Spacer(modifier = Modifier.height(16.dp))

            // Enhanced Menu Items with better styling
            val menuItems = listOf(
                DrawerMenuItem("Walk With Me", FontAwesomeIcons.Solid.Walking, Color(0xFF4CAF50)),
                DrawerMenuItem("Safe Locations", FontAwesomeIcons.Solid.MapMarkerAlt, Color(0xFF2196F3)),
                DrawerMenuItem("Safety Tips", FontAwesomeIcons.Solid.Lightbulb, Color(0xFFFF9800)),
            )


            menuItems.forEach { item ->
                NavigationDrawerItem(
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    item.color.copy(alpha = 0.12f),
                                    RoundedCornerShape(12.dp)
                                )
                                .shadow(2.dp, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = item.color,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    },
                    label = {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            fontSize = 16.sp,
                            color = Color(0xFF2E3440)
                        )
                    },
                    selected = false,
                    onClick = { onCloseDrawer() },
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        selectedContainerColor = Color(0xFF2196F3).copy(alpha = 0.1f)
                    )
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Footer section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Stay Safe, Stay Connected",
                    color = Color(0xFF6B7280),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Light
                )
            }
        }
    }
}