// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.content.Context
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private data class Detail(val en:String,val pt:String,val value:String)
private fun readTechnical(context:Context,item:MediaItem):List<Detail>{
    val result=mutableListOf<Detail>()
    fun add(en:String,pt:String,value:String?){if(!value.isNullOrBlank())result.add(Detail(en,pt,value))}
    if(item.video){
        runCatching{
            val retriever=MediaMetadataRetriever()
            try{
                retriever.setDataSource(context,item.uri)
                add("Bitrate","Taxa de bits",retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let{(it/1000).toString()+" kb/s"})
                add("Frame rate","Taxa de quadros",retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE))
                add("Rotation","Rotação",retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION))
                add("Audio","Áudio",retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
            }finally{retriever.release()}
        }
    }else runCatching{
        checkNotNull(context.contentResolver.openFileDescriptor(item.uri,"r")).use{fd->
            val exif=ExifInterface(fd.fileDescriptor)
            val tags=listOf(
                Triple("Manufacturer","Fabricante","Make"),Triple("Camera","Câmera","Model"),
                Triple("Lens","Lente","LensModel"),Triple("Captured","Captura","DateTimeOriginal"),
                Triple("ISO","ISO","PhotographicSensitivity"),Triple("Shutter speed","Obturador","ExposureTime"),
                Triple("Aperture","Abertura","FNumber"),Triple("Focal length","Distância focal","FocalLength"),
                Triple("35mm equivalent","Equivalente 35 mm","FocalLengthIn35mmFilm"),
                Triple("Exposure compensation","Compensação de exposição","ExposureBiasValue"),
                Triple("Flash","Flash","Flash"),Triple("White balance","Balanço de branco","WhiteBalance"),
                Triple("Orientation","Orientação","Orientation"),Triple("Software","Software","Software"),
                Triple("Color space","Espaço de cor","ColorSpace"),Triple("Artist","Autor","Artist"),
                Triple("Copyright","Direitos autorais","Copyright"),Triple("Description","Descrição","ImageDescription"),
                Triple("Resolution X","Resolução X","XResolution"),Triple("Resolution Y","Resolução Y","YResolution"))
            for((en,pt,tag) in tags)add(en,pt,exif.getAttribute(tag))
            val gps=FloatArray(2)
            if(exif.getLatLong(gps))add("Coordinates","Coordenadas","%.6f, %.6f".format(java.util.Locale.ROOT,gps[0],gps[1]))
            val altitude=exif.getAltitude(Double.NaN);if(altitude.isFinite())add("Altitude","Altitude","%.1f m".format(altitude))
        }
    }
    return result
}
@Composable internal fun GalleryDetailsDialog(items:List<MediaItem>,onClose:()->Unit){
    val context=LocalContext.current
    val clipboard=LocalClipboardManager.current
    val item=items.singleOrNull()
    val technical by produceState<List<Detail>?>(null,item?.uri){value=if(item==null)emptyList()else withContext(Dispatchers.IO){readTechnical(context,item)}}
    val base=remember(items){
        val date=DateFormat.getDateTimeInstance()
        fun at(seconds:Long)=if(seconds>0)date.format(Date(seconds*1000))else "—"
        if(item==null)listOf(Detail("Items","Itens",items.size.toString()),Detail("Images","Imagens",items.count{!it.video}.toString()),Detail("Videos","Vídeos",items.count{it.video}.toString()),Detail("Total size","Tamanho total",Formatter.formatFileSize(context,items.sumOf{it.bytes})))
        else buildList{
            add(Detail("File name","Nome",item.name));add(Detail("Size","Tamanho",Formatter.formatFileSize(context,item.bytes)+" ("+item.bytes+" B)"))
            add(Detail("Format","Formato",item.mime));add(Detail("Dimensions","Dimensões","${item.width} × ${item.height} px"))
            if(item.width>0&&item.height>0){
                add(Detail("Megapixels","Megapixels","%.2f MP".format(item.width.toLong()*item.height/1000000.0)))
                var a=item.width;var b=item.height;while(b!=0){val tmp=a%b;a=b;b=tmp};add(Detail("Aspect ratio","Proporção","${item.width/a}:${item.height/a}"))
            }
            add(Detail("Date","Data",at(item.date)));add(Detail("Modified","Modificado",at(item.modified)))
            add(Detail("Album","Álbum",item.folder));if(item.relative.isNotBlank())add(Detail("Path","Caminho",item.relative+item.name))
            if(item.volume.isNotBlank())add(Detail("Volume","Volume",item.volume))
            if(item.video)add(Detail("Duration","Duração",(item.duration/1000).toString()+" s"))
            add(Detail("Content URI","URI",item.uri.toString()))
        }
    }
    val pt=androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language=="pt"
    fun label(d:Detail)=if(pt)d.pt else d.en
    AlertDialog(onDismissRequest=onClose,title={Text(stringResource(R.string.gallery_details))},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(photoText("File information","Informações do arquivo"),style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)
            base.forEach{d->Column{Text(label(d),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(d.value,style=MaterialTheme.typography.bodyMedium)}}
            if(item!=null){
                HorizontalDivider()
                Text(if(item.video)photoText("Video metadata","Metadados do vídeo")else "EXIF",style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)
                if(technical==null)LinearProgressIndicator(Modifier.fillMaxWidth())
                else if(technical.orEmpty().isEmpty())Text(photoText("No additional metadata available","Nenhum metadado adicional disponível"))
                else technical.orEmpty().forEach{d->Column{Text(label(d),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(d.value,style=MaterialTheme.typography.bodyMedium)}}
            }
        }
    },dismissButton={TextButton(onClick={clipboard.setText(AnnotatedString((base+technical.orEmpty()).joinToString("\n"){label(it)+": "+it.value}))}){Text(photoText("Copy details","Copiar informações"))}},confirmButton={TextButton(onClick=onClose){Text(tr("Close"))}})
}
