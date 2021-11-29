package com.amarland.iconvector.demo.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.amarland.iconvector.android.iconVGResource
import com.amarland.iconvector.demo.R

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Scaffold(
                topBar = { TopAppBar(title = { Text("IconVector Demo") }) }
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(all = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val ivgResourceIds =
                        R.raw::class.java.fields
                            .filter { field -> field.name.startsWith("ivg_") }
                            .map { field -> field.getInt(null) }
                    items(ivgResourceIds) { id ->
                        Image(
                            imageVector = ImageVector.iconVGResource(id),
                            contentDescription = null,
                            modifier = Modifier.size(96.dp)
                        )
                    }
                }
            }
        }
    }
}
