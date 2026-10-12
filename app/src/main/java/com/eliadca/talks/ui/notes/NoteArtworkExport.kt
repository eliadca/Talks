package com.eliadca.talks.ui.notes

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.text.*
import android.text.style.*
import com.eliadca.talks.core.ink.*
import com.eliadca.talks.core.notes.NoteFormatting
import java.io.OutputStream
import kotlin.math.ceil

/** Uses the same ink renderer as the editor and Android's rich-text layout. */
internal object NoteArtworkExport {
    internal fun layout(text:String,format:NoteFormatting,width:Float,size:Float,color:Int,scale:Float):StaticLayout {
        val rich=SpannableString(text)
        fun span(value:Any,a:Int,b:Int) {val start=a.coerceIn(0,text.length);val end=b.coerceIn(start,text.length);if(end>start)rich.setSpan(value,start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)}
        format.spans.forEach {r->val s=r.style
            if(s.bold || s.italic)span(StyleSpan(if(s.bold && s.italic)Typeface.BOLD_ITALIC else if(s.bold)Typeface.BOLD else Typeface.ITALIC),r.start,r.end)
            if(s.underline)span(UnderlineSpan(),r.start,r.end)
            if(s.strike)span(StrikethroughSpan(),r.start,r.end)
            span(AbsoluteSizeSpan((s.size*scale).toInt().coerceAtLeast(1)),r.start,r.end)
            s.color?.let {span(ForegroundColorSpan(it),r.start,r.end)}
            s.highlight?.let {span(BackgroundColorSpan(it),r.start,r.end)}
        }
        format.paragraphs.forEach {r->span(AlignmentSpan.Standard(when(r.alignment){1->Layout.Alignment.ALIGN_CENTER;2->Layout.Alignment.ALIGN_OPPOSITE;else->Layout.Alignment.ALIGN_NORMAL}),r.start,r.end)}
        val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {textSize=size*scale;this.color=color}
        return StaticLayout.Builder.obtain(rich,0,rich.length,paint,width.toInt().coerceAtLeast(1)).setIncludePad(false).setLineSpacing(0f,1.39f).build()
    }
    fun write(context:Context,ink:InkDocument,body:String,format:String,output:OutputStream) {
        if(format=="svg") {output.write(NoteSvgExport.write(ink,body).toByteArray(Charsets.UTF_8));return}
        val frame=ink.bodyFrame ?: NoteTextFrame(width=ink.pageWidth-80f)
        val main=layout(body,ink.formatting,frame.width,46f,Color.rgb(35,43,60),frame.scale)
        val boxes=ink.textBoxes.filterNot {it.id in ink.hiddenIds}.associate {it.id to layout(it.text,it.formatting,it.width,it.size,it.color,it.size/46f)}
        val height=maxOf(ink.height,if(InkDocument.MAIN_TEXT_ID in ink.hiddenIds)0f else frame.y+main.height+40,ink.textBoxes.maxOfOrNull {it.y+(boxes[it.id]?.height ?: 0)+40} ?: 0f)
        require(ink.pageWidth.isFinite() && height.isFinite() && ink.pageWidth in 1f..10000f && height in 1f..50000f) {"La hoja es demasiado grande para exportar."}
        val page=InkPageView(context).apply {document=ink;tool=InkTool.MOVE;drawTextBoxes=false;layout(0,0,ceil(ink.pageWidth.toDouble()).toInt(),ceil(height.toDouble()).toInt())}
        fun draw(canvas:Canvas) {
            canvas.drawColor(ink.paperColor);page.draw(canvas)
            if(InkDocument.MAIN_TEXT_ID !in ink.hiddenIds) {canvas.save();canvas.translate(frame.x,frame.y);main.draw(canvas);canvas.restore()}
            ink.textBoxes.filterNot {it.id in ink.hiddenIds}.forEach {box->canvas.save();canvas.translate(box.x,box.y);boxes.getValue(box.id).draw(canvas);canvas.restore()}
        }
        if(format=="png") {
            val scale=minOf(1f,2048f/ink.pageWidth,kotlin.math.sqrt(8000000f/(ink.pageWidth*height)))
            val bitmap=Bitmap.createBitmap((ink.pageWidth*scale).toInt().coerceAtLeast(1),(height*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
            try {draw(Canvas(bitmap).apply {scale(scale,scale)});check(bitmap.compress(Bitmap.CompressFormat.PNG,100,output)) {"No se pudo crear la imagen."}}finally {bitmap.recycle()}
        } else {
            val pdf=PdfDocument()
            try {
                val scale=563f/ink.pageWidth;val worldPage=800f/scale;val count=ceil(height/worldPage).toInt()
                require(count<=100) {"La nota requiere demasiadas páginas."}
                repeat(count) {index->val p=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,index+1).create())
                    p.canvas.save();p.canvas.translate(16f,20f);p.canvas.clipRect(0f,0f,563f,800f);p.canvas.scale(scale,scale);p.canvas.translate(0f,-index*worldPage);draw(p.canvas);p.canvas.restore();pdf.finishPage(p)}
                pdf.writeTo(output)
            } finally {pdf.close()}
        }
    }
}
