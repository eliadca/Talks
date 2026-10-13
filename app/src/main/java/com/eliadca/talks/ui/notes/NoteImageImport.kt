package com.eliadca.talks.ui.notes

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.util.Base64
import com.eliadca.talks.core.ink.NoteImage
import java.io.ByteArrayOutputStream

internal object NoteImageImport {
    fun read(context:Context,uri:Uri):NoteImage {
        val bytes=context.contentResolver.openInputStream(uri)?.use {input->
            val out=ByteArrayOutputStream();val buffer=ByteArray(16384);var read:Int
            while(input.read(buffer).also {read=it}>0) {require(out.size()+read<=48*1024*1024) {"La imagen supera 48 MB."};out.write(buffer,0,read)};out.toByteArray()
        } ?: error("No se pudo abrir la imagen.")
        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        require(bounds.outWidth>0 && bounds.outHeight>0) {"El archivo no es una imagen compatible."}
        val decoded=if(Build.VERSION.SDK_INT>=28) ImageDecoder.decodeBitmap(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))) {decoder,info,_->
            decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
            val scale=minOf(1f,1600f/maxOf(info.size.width,info.size.height));decoder.setTargetSize((info.size.width*scale).toInt().coerceAtLeast(1),(info.size.height*scale).toInt().coerceAtLeast(1))
        } else BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply {inSampleSize=maxOf(1,maxOf(bounds.outWidth,bounds.outHeight)/1600)}) ?: error("No se pudo decodificar la imagen.")
        val longest=maxOf(decoded.width,decoded.height)
        val bitmap=if(longest>1600)Bitmap.createScaledBitmap(decoded,(decoded.width*1600f/longest).toInt().coerceAtLeast(1),(decoded.height*1600f/longest).toInt().coerceAtLeast(1),true).also {if(it!==decoded)decoded.recycle()} else decoded
        try {
            val output=ByteArrayOutputStream();check(bitmap.compress(Bitmap.CompressFormat.PNG,100,output));require(output.size()<=8*1024*1024) {"La imagen es demasiado compleja. Reduce su tamaño."}
            return NoteImage(0L,Base64.encodeToString(output.toByteArray(),Base64.NO_WRAP),height=500f*bitmap.height/bitmap.width)
        } finally {bitmap.recycle()}
    }
}
