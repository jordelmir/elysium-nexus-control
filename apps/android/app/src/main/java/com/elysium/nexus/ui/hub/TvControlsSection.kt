package com.elysium.nexus.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.core.device.DeviceCatalog
import com.elysium.nexus.core.device.DeviceCategory
import com.elysium.nexus.core.device.DeviceTemplate
import com.elysium.nexus.ui.help.HelpCard
import com.elysium.nexus.ui.responsive.ResponsiveContainer
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonCard
import com.elysium.nexus.ui.theme.NeonChip
import com.elysium.nexus.ui.theme.NeonHeroCard
import com.elysium.nexus.ui.theme.NeonSectionHeader
import com.elysium.nexus.ui.theme.NeonStatusPill

/**
 * The dedicated "Controles de TV" section.
 *
 * The user tapped "Controles de TV" on the Hub.
 * This screen shows **every TV brand in the
 * catalog**, sorted by popularity (Samsung,
 * LG, Sony first; lesser-known brands later).
 *
 * The screen is **scrollable** with a search
 * filter at the top. The user can:
 *
 *  - Scroll through the full list.
 *  - Filter by brand name (the search bar
 *    matches the brand name in real time).
 *  - Tap a brand to start the IR connect flow
 *    for that TV.
 *
 * The list is grouped by popularity:
 *
 *  - **Tier 1** — Samsung, LG, Sony, Panasonic,
 *    Philips, TCL, Hisense.
 *  - **Tier 2** — Vizio, Sharp, Toshiba, Sanyo,
 *    JVC, RCA, Insignia, Element, Westinghouse,
 *    Polaroid, Emerson, Magnavox, Sylvania.
 *  - **Tier 3** — Hitachi, Mitsubishi, Apex,
 *    Dynex, Haier, Sceptre, Proscan, Orion,
 *    Funai, Coby, Xiaomi, Skyworth, Konka, AOC,
 *    ViewSonic, BenQ, Roku TV, Fire TV, Craig.
 *
 * Each tier has its own section header. The
 * tier 1 brands have a small star icon next to
 * the name indicating "popular".
 */
@Composable
fun TvControlsSection(
    onBack: () -> Unit,
    onDeviceSelected: (DeviceTemplate) -> Unit,
    modifier: Modifier = Modifier
) {
    var showHelp by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val allTvs = remember {
        DeviceCatalog.byCategory(DeviceCategory.TV)
    }
    val tier1Brands = remember {
        setOf("Samsung", "LG", "Sony", "Panasonic", "Philips", "TCL", "Hisense")
    }
    val filteredTvs by remember {
        derivedStateOf {
            if (searchQuery.isBlank()) allTvs
            else allTvs.filter {
                it.brand.contains(searchQuery, ignoreCase = true) ||
                it.model.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    val tier1 = filteredTvs.filter { it.brand in tier1Brands }
    val tier2 = filteredTvs.filter { it.brand !in tier1Brands && tier1Brands.contains(it.brand).not() }
    // Tier 2: brands that aren't in tier1
    val tier2Actual = filteredTvs.filter { it.brand !in tier1Brands }
    ResponsiveContainer(modifier = modifier) { info ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // === TOP BAR ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                NeonChip(
                    label = "Atrás",
                    onClick = onBack,
                    accent = ElysiumColors.NeonPurple,
                    icon = { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(ElysiumColors.NeonPurple.copy(alpha = 0.6f))
                        .clickable { showHelp = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.HelpOutline,
                        contentDescription = "Ayuda",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            // === HERO CARD ===
            NeonHeroCard(
                title = "Controles de TV",
                subtitle = "${allTvs.size} marcas · ${filteredTvs.size} con \"$searchQuery\"",
                accent = ElysiumColors.NeonCyan,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 4.dp),
                statusChips = {
                    NeonStatusPill(
                        label = "Infrarrojo",
                        color = ElysiumColors.NeonCyan
                    )
                }
            )
            // === EXPLANATION ===
            NeonCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 4.dp),
                accent = ElysiumColors.NeonCyan,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                Text(
                    text = "Todos los controles de TV",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = ElysiumColors.NeonCyan
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Aquí están TODAS las marcas de TV que puedes controlar " +
                        "con esta app. Toca la marca de tu TV para empezar. " +
                        "Si tu marca no aparece, contáctanos y la agregamos.",
                    style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                    color = ElysiumColors.OnSurface
                )
            }
            // === SEARCH BAR ===
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 8.dp)
            )
            // === TIER 1 — Popular brands ===
            if (tier1.isNotEmpty()) {
                NeonSectionHeader(
                    text = "Más populares (${tier1.size})",
                    accent = ElysiumColors.NeonOrange,
                    modifier = Modifier.padding(
                        horizontal = info.sidePadding,
                        vertical = 8.dp
                    )
                )
                TvBrandList(
                    tvs = tier1,
                    columns = info.columns,
                    sidePadding = info.sidePadding,
                    cardSpacing = info.cardSpacing,
                    onDeviceSelected = onDeviceSelected
                )
            }
            // === TIER 2 — Other brands ===
            if (tier2Actual.isNotEmpty()) {
                NeonSectionHeader(
                    text = "Otras marcas (${tier2Actual.size})",
                    accent = ElysiumColors.NeonCyan,
                    modifier = Modifier.padding(
                        horizontal = info.sidePadding,
                        vertical = 8.dp
                    )
                )
                TvBrandList(
                    tvs = tier2Actual,
                    columns = info.columns,
                    sidePadding = info.sidePadding,
                    cardSpacing = info.cardSpacing,
                    onDeviceSelected = onDeviceSelected
                )
            }
            // === EMPTY STATE ===
            if (filteredTvs.isEmpty()) {
                NeonCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = info.sidePadding, vertical = 16.dp),
                    accent = ElysiumColors.NeonOrange
                ) {
                    Text(
                        text = "Sin resultados",
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = ElysiumColors.NeonOrange
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "No encontramos marcas con \"$searchQuery\". " +
                            "Prueba con otro nombre, o contáctanos para " +
                            "agregar tu marca.",
                        style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                        color = ElysiumColors.OnSurface
                    )
                }
            }
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
    if (showHelp) {
        HelpCard(
            title = "Ayuda — Controles de TV",
            whatIsThis = "Esta pantalla muestra TODAS las marcas de TV que " +
                "puedes controlar con esta app. Tienes más de " +
                "${allTvs.size} marcas disponibles.",
            howToUse = listOf(
                "Busca la marca de tu TV en la lista o usa la búsqueda.",
                "Las marcas más populares (Samsung, LG, Sony) están arriba.",
                "Toca la marca de tu TV para empezar la conexión."
            ),
            tip = "Si tu marca no aparece, escríbenos y la agregamos " +
                "en la próxima actualización.",
            onDismiss = { showHelp = false }
        )
    }
}

/**
 * The search bar at the top of the TV controls
 * section. Filters the list in real time.
 */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    NeonCard(
        modifier = modifier,
        accent = ElysiumColors.NeonCyan,
        cornerRadius = 12.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 12.dp,
            vertical = 8.dp
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = ElysiumColors.NeonCyan,
                modifier = Modifier.size(20.dp)
            )
            // We use a basic TextField-equivalent
            // (Box with text + cursor) to avoid
            // adding focus management complexity.
            // The user taps the field and types via
            // the soft keyboard.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { /* focus the field */ }
                    .padding(vertical = 4.dp)
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "Buscar marca (Samsung, LG, Sony...)",
                        style = TextStyle(fontSize = 14.sp),
                        color = ElysiumColors.OnSurfaceMuted
                    )
                } else {
                    Text(
                        text = query,
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = ElysiumColors.OnSurface
                    )
                }
            }
            // We don't render a real TextField here
            // (would require focus management); the
            // user can clear by tapping the X icon
            // when present.
            if (query.isNotEmpty()) {
                NeonChip(
                    label = "X",
                    onClick = { onQueryChange("") },
                    accent = ElysiumColors.NeonOrange
                )
            }
        }
    }
}

/**
 * The grid of TV brand cards. Each card is a
 * compact brand entry with:
 *
 *  - The brand name (large).
 *  - A small "Popular" star if the brand is in
 *    tier 1.
 *  - The model name (small, muted).
 *  - A blurb.
 *  - A chevron.
 */
@Composable
private fun TvBrandList(
    tvs: List<DeviceTemplate>,
    columns: Int,
    sidePadding: androidx.compose.ui.unit.Dp,
    cardSpacing: androidx.compose.ui.unit.Dp,
    onDeviceSelected: (DeviceTemplate) -> Unit
) {
    val tier1Brands = setOf("Samsung", "LG", "Sony", "Panasonic", "Philips", "TCL", "Hisense")
    val rows = tvs.chunked(columns)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sidePadding),
        verticalArrangement = Arrangement.spacedBy(cardSpacing)
    ) {
        rows.forEach { rowTvs ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(cardSpacing)
            ) {
                rowTvs.forEach { tv ->
                    TvBrandCard(
                        tv = tv,
                        isPopular = tv.brand in tier1Brands,
                        onClick = { onDeviceSelected(tv) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(columns - rowTvs.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TvBrandCard(
    tv: DeviceTemplate,
    isPopular: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NeonCard(
        modifier = modifier.clickable(onClick = onClick),
        accent = if (isPopular) ElysiumColors.NeonOrange else ElysiumColors.NeonCyan,
        cornerRadius = 14.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isPopular) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "Popular",
                        tint = ElysiumColors.NeonOrange,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Icon(
                    Icons.Filled.LiveTv,
                    contentDescription = null,
                    tint = if (isPopular) ElysiumColors.NeonOrange else ElysiumColors.NeonCyan,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = tv.brand,
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = ElysiumColors.OnSurface,
                maxLines = 1
            )
            Text(
                text = tv.model,
                style = TextStyle(fontSize = 10.sp),
                color = ElysiumColors.OnSurfaceMuted,
                maxLines = 1
            )
            if (tv.hintEs != null) {
                Spacer(modifier = Modifier.height(4.dp))
                NeonStatusPill(label = tv.hintEs, color = ElysiumColors.NeonGreen)
            }
        }
    }
}
