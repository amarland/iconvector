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

package com.amarland.iconvector.demo.android

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.TextAppearanceSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.annotation.AttrRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.amarland.iconvector.androidcompose.iconVGResource
import com.amarland.iconvector.androidlegacy.getIconVGDrawable

private typealias IconVGAsset = Pair<Int, List<Int>>

class MainActivity : ComponentActivity(R.layout.activity_main) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        findViewById<TextView>(R.id.caption_drawables)
            .setCaptionText("As \"regular\" Drawables:", spanStart = 13, spanEnd = 21)

        findViewById<TextView>(R.id.caption_image_vectors)
            .setCaptionText("As Jetpack Compose ImageVectors:", spanStart = 19, spanEnd = 30)

        val ivgAssets: List<IconVGAsset> =
            R.raw::class.java.fields
                .filter { field -> field.name.startsWith("ivg_") }
                .map { field ->
                    val palette = if ("action_info" in field.name)
                        listOf(resolveThemeAttribute(R.attr.colorPrimary))
                    else emptyList()
                    return@map field.getInt(null) to palette
                }

        val root = findViewById<View>(R.id.root)
        root.post {
            val itemSize = resources.getDimension(R.dimen.image_view_size)
            val columnCount = (root.width / itemSize).toInt().coerceAtLeast(1)

            setUpRecyclerView(columnCount, ivgAssets)
            setUpComposeView(columnCount, ivgAssets, itemSize)
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun setUpRecyclerView(columnCount: Int, ivgAssets: List<IconVGAsset>) {
        findViewById<RecyclerView>(R.id.recycler_view).apply {
            setHasFixedSize(true)
            layoutManager = GridLayoutManager(context, columnCount)
            adapter = Adapter.also { adapter -> adapter.submitList(ivgAssets) }
        }
    }

    @OptIn(
        ExperimentalFoundationApi::class,
        ExperimentalUnsignedTypes::class
    )
    private fun setUpComposeView(
        columnCount: Int,
        ivgAssets: List<IconVGAsset>,
        itemSize: Float
    ) {
        val itemMargin = resources.getDimension(R.dimen.image_view_margin)
        findViewById<ComposeView>(R.id.compose_view).setContent {
            LazyVerticalGrid(
                cells = GridCells.Fixed(columnCount),
                contentPadding = PaddingValues(all = itemMargin.dp),
                verticalArrangement = Arrangement.spacedBy(itemMargin.dp),
                horizontalArrangement = Arrangement.spacedBy(itemMargin.dp)
            ) {
                items(ivgAssets) { (resourceId, palette) ->
                    Image(
                        imageVector = ImageVector.iconVGResource(
                            resourceId,
                            palette.map(::Color)
                        ),
                        contentDescription = null,
                        modifier = Modifier.requiredSize(
                            (itemSize / resources.displayMetrics.density).dp
                        )
                    )
                }
            }
        }
    }

    private fun TextView.setCaptionText(caption: String, spanStart: Int, spanEnd: Int) {
        setText(
            SpannableString(caption).apply {
                // val secondaryColor = resolveThemeAttribute(R.attr.colorSecondary)
                // val onSecondaryColor = resolveThemeAttribute(R.attr.colorOnSecondary)
                val flags = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                // setSpan(BackgroundColorSpan(secondaryColor), spanStart, spanEnd, flags)
                // setSpan(ForegroundColorSpan(onSecondaryColor), spanStart, spanEnd, flags)
                setSpan(
                    TextAppearanceSpan(
                        this@MainActivity,
                        R.style.TextAppearance_MaterialComponents_Body2_Monospace
                    ), spanStart, spanEnd, flags
                )
            },
            TextView.BufferType.SPANNABLE
        )
    }

    private fun resolveThemeAttribute(@AttrRes id: Int) =
        TypedValue().also { typedValue -> theme.resolveAttribute(id, typedValue, true) }.data

    @ExperimentalUnsignedTypes
    private object Adapter : ListAdapter<IconVGAsset, RecyclerView.ViewHolder>(DiffItemCallback) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            object : RecyclerView.ViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_image_view, parent, false)
            ) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            with(holder.itemView as ImageView) {
                val (resourceId, palette) = getItem(position)
                setImageDrawable(
                    resources.getIconVGDrawable(
                        resourceId,
                        R.dimen.image_view_size,
                        palette.toIntArray()
                    )
                )
            }
        }

        private object DiffItemCallback : DiffUtil.ItemCallback<IconVGAsset>() {

            override fun areItemsTheSame(old: IconVGAsset, new: IconVGAsset) =
                old.first == new.first

            override fun areContentsTheSame(old: IconVGAsset, new: IconVGAsset) = old == new
        }
    }
}
