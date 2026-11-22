package com.example.hackapp.scaffold.screens.places

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.hackapp.data.local.PlacesDatabase
import com.example.hackapp.data.model.Place
import com.example.hackapp.data.model.VenueType
import com.example.hackapp.data.repository.RoomPlaceRepository
import kotlinx.serialization.Serializable
import java.text.Normalizer

@Serializable
object PlacesDestination

/** Browse every ACTIVE place in places.db. Debug/inspection screen: search by name or borough, filter by venue type. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesScreen() {
    val context = LocalContext.current
    val places by produceState<List<Place>?>(initialValue = null) {
        val db = PlacesDatabase.get(context)
        value = RoomPlaceRepository(db.placeDao(), db.referenceDao()).allPlaces()
    }
    var query by remember { mutableStateOf("") }
    var venue by remember { mutableStateOf<VenueType?>(null) }

    val all = places
    if (all == null) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
        }
        return
    }
    val q = query.fold()
    val shown = remember(all, q, venue) {
        all.filter { p ->
            (venue == null || p.venueType == venue) &&
                (q.isEmpty() || p.name.fold().contains(q) || (p.borough ?: "").fold().contains(q) || p.address.fold().contains(q))
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("Search name, address or borough") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = venue == null, onClick = { venue = null }, label = { Text("All") }) }
            items(VenueType.entries) { t ->
                FilterChip(selected = venue == t, onClick = { venue = if (venue == t) null else t }, label = { Text(t.name.lowercase().replace('_', ' ')) })
            }
        }
        Text(
            "${shown.size} of ${all.size} places",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shown, key = { it.id }) { PlaceCard(it, Modifier.animateItem()) }
        }
    }
}

@Composable
private fun PlaceCard(p: Place, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(p.price?.label() ?: "no price", style = MaterialTheme.typography.labelLarge)
            }
            Text(p.address, style = MaterialTheme.typography.bodySmall)
            val meta = listOfNotNull(
                p.venueType.name.lowercase().replace('_', ' '),
                p.borough,
                p.cuisines.takeIf { it.isNotEmpty() }?.joinToString(", "),
                p.rating?.let { "★ $it (${p.ratingCount ?: 0})" },
                if (p.isChain) "chain" else null,
            )
            Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            p.description.en?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2) }
        }
    }
}

private fun String.fold(): String =
    Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD).replace(Regex("""\p{Mn}+"""), "")
