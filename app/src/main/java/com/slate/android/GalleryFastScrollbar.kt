// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.Channel
import kotlin.math.roundToInt

// Full-width date headers occupy one grid row, independent of the photo density.
internal fun galleryRowAnchors(groupSizes:List<Int>,columns:Int,headers:Boolean):IntArray{
    require(columns>0)
    val rows=ArrayList<Int>()
    var index=0
    for(count in groupSizes){
        if(headers)rows.add(index++)
        for(offset in 0 until count step columns)rows.add(index+offset)
        index+=count
    }
    return rows.toIntArray()
}
internal fun galleryRowIndex(anchors:IntArray,item:Int):Int{
    if(anchors.isEmpty())return 0
    val found=anchors.binarySearch(item)
    return (if(found>=0)found else -found-2).coerceIn(0,anchors.lastIndex)
}
internal fun gallerySeekItem(anchors:IntArray,fraction:Float):Int{
    if(anchors.isEmpty())return 0
    val row=(fraction.coerceIn(0f,1f)*anchors.lastIndex).roundToInt()
    return anchors[row]
}

@Composable
internal fun GalleryFastScrollbar(state:LazyGridState,anchors:IntArray,modifier:Modifier=Modifier,enabled:Boolean=true){
    val scrollable by remember(state){derivedStateOf{state.canScrollBackward||state.canScrollForward}}
    if(!scrollable||anchors.size<2)return
    val progress by remember(state,anchors){derivedStateOf{
        when{
            !state.canScrollBackward->0f
            !state.canScrollForward->1f
            else->{
                val row=galleryRowIndex(anchors,state.firstVisibleItemIndex)
                val item=state.layoutInfo.visibleItemsInfo.firstOrNull{it.index==state.firstVisibleItemIndex}
                val part=if(item!=null&&item.size.height>0)(state.firstVisibleItemScrollOffset.toFloat()/item.size.height).coerceIn(0f,1f)else 0f
                ((row+part)/anchors.lastIndex).coerceIn(0f,1f)
            }
        }
    }}
    var dragging by remember{mutableStateOf(false)}
    var draggedProgress by remember{mutableFloatStateOf(0f)}
    val requests=remember(state,anchors){Channel<Int>(Channel.CONFLATED)}
    LaunchedEffect(state,requests){for(index in requests)state.scrollToItem(index)}
    DisposableEffect(requests){onDispose{requests.close()}}
    val trackColor=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.14f)
    val thumbColor=MaterialTheme.colorScheme.primary
    val label=stringResource(R.string.gallery_fast_scroll)
    Canvas(modifier.width(24.dp).semantics{
        contentDescription=label
        progressBarRangeInfo=ProgressBarRangeInfo(if(dragging)draggedProgress else progress,0f..1f)
        if(enabled)setProgress{value->requests.trySend(gallerySeekItem(anchors,value));true}
    }.pointerInput(state,anchors,enabled){
        if(!enabled)return@pointerInput
        awaitEachGesture{
            val down=awaitFirstDown(requireUnconsumed=false)
            val thumb=44.dp.toPx().coerceAtMost(size.height.toFloat())
            val travel=(size.height-thumb).coerceAtLeast(1f)
            val top=progress*travel
            val grab=if(down.position.y in top..(top+thumb))down.position.y-top else thumb/2f
            fun seek(y:Float){
                draggedProgress=((y-grab)/travel).coerceIn(0f,1f)
                requests.trySend(gallerySeekItem(anchors,draggedProgress))
            }
            down.consume()
            dragging=true
            try{
                seek(down.position.y)
                do{
                    val event=awaitPointerEvent()
                    val change=event.changes.firstOrNull{it.id==down.id}?:break
                    if(!change.pressed){change.consume();break}
                    seek(change.position.y)
                    change.consume()
                }while(true)
            }finally{dragging=false}
        }
    }){
        val thumb=44.dp.toPx().coerceAtMost(size.height)
        val trackWidth=3.dp.toPx()
        val thumbWidth=(if(dragging)8.dp else 5.dp).toPx()
        val center=size.width-9.dp.toPx()
        val y=(size.height-thumb)*(if(dragging)draggedProgress else progress)
        drawRoundRect(trackColor,topLeft=Offset(center-trackWidth/2,0f),size=Size(trackWidth,size.height),cornerRadius=CornerRadius(trackWidth/2))
        drawRoundRect(thumbColor.copy(alpha=if(dragging)1f else .78f),topLeft=Offset(center-thumbWidth/2,y),size=Size(thumbWidth,thumb),cornerRadius=CornerRadius(thumbWidth/2))
    }
}
