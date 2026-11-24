// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.device

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation
import java.util.Locale

@Composable
internal fun LiveSensors(){
    val context=LocalContext.current
    val manager=remember(context){context.getSystemService(SensorManager::class.java)}
    val available=remember(manager){listOf(Sensor.TYPE_ACCELEROMETER,Sensor.TYPE_GYROSCOPE,Sensor.TYPE_MAGNETIC_FIELD,Sensor.TYPE_LIGHT,Sensor.TYPE_PROXIMITY,Sensor.TYPE_PRESSURE).mapNotNull{manager?.getDefaultSensor(it)}}
    var active by remember{mutableStateOf(false)}
    val readings=remember{mutableStateMapOf<Int,List<Float>>()}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(active,manager,lifecycle){
        if(!active || manager==null)return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){
            val last=HashMap<Int,Long>()
            val listener=object:SensorEventListener{
                override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int){}
                override fun onSensorChanged(event:SensorEvent){
                    val type=event.sensor.type
                    if(event.timestamp-(last[type] ?: 0)<200_000_000L)return
                    last[type]=event.timestamp
                    readings[type]=event.values.take(3)
                }
            }
            try {
                available.forEach{manager.registerListener(listener,it,SensorManager.SENSOR_DELAY_UI)}
                awaitCancellation()
            }finally{manager.unregisterListener(listener)}
        }
    }
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){Text("Live sensors",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);Switch(active,{active=it})}
            if(active){
                if(available.isEmpty())Text("No supported sensors")
                available.forEach{sensor->
                    Text(sensor.stringType.substringAfterLast('.').replace('_',' '),style=MaterialTheme.typography.labelMedium)
                    val unit=when(sensor.type){Sensor.TYPE_ACCELEROMETER->"m/s²";Sensor.TYPE_GYROSCOPE->"rad/s";Sensor.TYPE_MAGNETIC_FIELD->"µT";Sensor.TYPE_LIGHT->"lx";Sensor.TYPE_PROXIMITY->"cm";Sensor.TYPE_PRESSURE->"hPa";else->""}
                    Text(readings[sensor.type]?.joinToString(" · "){String.format(Locale.getDefault(),"%.2f",it)}?.let{"$it $unit"} ?: "—",style=MaterialTheme.typography.bodyMedium)
                    HorizontalDivider()
                }
            }
        }
    }
}
@Composable
internal fun DeviceTests(onBack:()->Unit){
    var mode by remember{mutableIntStateOf(0)}
    var color by remember{mutableIntStateOf(0)}
    val colors=listOf(Color.Red,Color.Green,Color.Blue,Color.White,Color.Black)
    val points=remember{mutableStateListOf<Offset>()}
    BackHandler{if(mode!=0)mode=0 else onBack()}
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick={if(mode!=0)mode=0 else onBack()}){Icon(Icons.Default.ArrowBack,"Back")}
            Text(listOf("Device tests","Display test","Touch test","Sensor test")[mode],Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            if(mode==2)TextButton(onClick={points.clear()}){Text("Clear")}
        }
        when(mode){
            0->Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                listOf("Display colors","Touch response","Live sensors").forEachIndexed{i,label->
                    Card(onClick={mode=i+1},modifier=Modifier.fillMaxWidth()){
                        ListItem(headlineContent={Text(label)})
                    }
                }
            }
            1->Box(Modifier.fillMaxSize().background(colors[color]).pointerInput(Unit){detectTapGestures{color=(color+1)%colors.size}}){
                Text("Tap to change color",Modifier.align(Alignment.BottomCenter).padding(12.dp),color=if(color==4)Color.White else Color.Black)
            }
            2->{
                val ink=MaterialTheme.colorScheme.primary
                Text("Drag across the screen",Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall)
                Canvas(Modifier.fillMaxSize().pointerInput(Unit){
                    detectDragGestures(onDragStart={if(points.size>=4000)points.clear();points.add(it)}){change,_->
                        change.consume()
                        if(points.size>=4000)points.clear()
                        points.add(change.position)
                    }
                }.pointerInput(Unit){detectTapGestures{if(points.size>=4000)points.clear();points.add(it)}}){
                    for(point in points)drawCircle(ink,8.dp.toPx(),point)
                }
            }
            3->Column(Modifier.padding(14.dp)){LiveSensors()}
        }
    }
}
