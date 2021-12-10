/*
 * Copyright 2021 Anthony Marland
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.amarland.iconvector.demo.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.singleWindowApplication
import com.amarland.iconvector.desktop.iconVGResource
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

@OptIn(ExperimentalUnsignedTypes::class)
fun main() {
    val ivgResources = FileSystem.RESOURCES.list(".".toPath())
        .filter { path -> path.name.endsWith(".ivg") }
        .map(Path::name)

    singleWindowApplication(title = "IconVector Demo") {
        LazyColumn(
            contentPadding = PaddingValues(all = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(ivgResources) { path ->
                Image(
                    imageVector = ImageVector.iconVGResource(path),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp)
                )
            }
        }
    }
}
