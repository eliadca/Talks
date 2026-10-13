package com.eliadca.talks.ui.notes

import com.eliadca.talks.core.ink.*
import com.eliadca.talks.core.notes.NoteFormatting

/** Portable SVG: editable vector paths and text, embedded images and no network dependencies. */
internal object NoteSvgExport {
    private fun xml(text:String)=text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
    private fun color(value:Int)="#%06x".format(value and 0xffffff)
    fun write(ink:InkDocument,body:String,context:android.content.Context?=null):String = buildString {
        val frame=ink.bodyFrame ?: NoteTextFrame(width=ink.pageWidth-80f)
        val layout=NoteArtworkExport.layout(body,ink.formatting,frame.width,46f,0xff232b3c.toInt(),frame.scale,context)
        val height=maxOf(ink.height,frame.y+layout.height+40)
        append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${ink.pageWidth}\" height=\"$height\" viewBox=\"0 0 ${ink.pageWidth} $height\">")
        append("<rect width=\"100%\" height=\"100%\" fill=\"${color(ink.paperColor)}\"/>")
        append("<defs>")
        if(context!=null) {
            val fonts=(ink.formatting.spans+ink.textBoxes.flatMap {it.formatting.spans}).map {it.style.font}.distinct()
            fonts.forEach {font->NoteFonts.bytes(context,font)?.let {bytes->
                append("<style>@font-face{font-family:'${NoteFonts.svgFamily(font)}';src:url(data:font/ttf;base64,${android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP)})}</style>")
            }}
        }
        for(s in ink.strokes)if(s.gradientColor!=null && s.fillColor!=null)append("<linearGradient id=\"g${s.id}\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"1\"><stop offset=\"0\" stop-color=\"${color(s.fillColor!!)}\"/><stop offset=\"1\" stop-color=\"${color(s.gradientColor!!)}\"/></linearGradient>")
        append("</defs>")
        fun picture(image:NoteImage) {
            if(image.id in ink.hiddenIds)return
            append("<g opacity=\"${image.opacity}\" transform=\"rotate(${image.rotation} ${image.x+image.width/2} ${image.y+image.height/2})\"><svg x=\"${image.x}\" y=\"${image.y}\" width=\"${image.width}\" height=\"${image.height}\" viewBox=\"${image.cropLeft} ${image.cropTop} ${image.cropRight-image.cropLeft} ${image.cropBottom-image.cropTop}\" preserveAspectRatio=\"none\"><image width=\"1\" height=\"1\" preserveAspectRatio=\"none\" href=\"data:image/png;base64,${image.data}\"/></svg></g>")
        }
        fun line(x1:Float,y1:Float,x2:Float,y2:Float) {append("<path d=\"M$x1 $y1 L$x2 $y2\" stroke=\"#a5b5c9\" stroke-width=\"1.5\" fill=\"none\"/>")}
        val grid=ink.gridSpacing.coerceIn(16f,160f)
        if(ink.gridVisible) {var x=grid;while(x<ink.pageWidth){line(x,0f,x,height);x+=grid};var y=grid;while(y<height){line(0f,y,ink.pageWidth,y);y+=grid}}
        if(ink.dotted) {var x=grid;while(x<ink.pageWidth){var y=grid;while(y<height){append("<circle cx=\"$x\" cy=\"$y\" r=\"1.6\" fill=\"#a5b5c9\"/>");y+=grid};x+=grid}}
        fun rules(l:Float,t:Float,r:Float,b:Float) {var y=t+ink.spacing.coerceIn(40f,100f);while(y<=b){line(l,y,r,y);y+=ink.spacing.coerceIn(40f,100f)}}
        if(ink.wholePageRuled)rules(32f,32f,ink.pageWidth-32,height) else ink.areas.forEach {rules(it.left,it.top,it.right,it.bottom)}
        ink.images.filterNot {it.foreground}.forEach(::picture)
        for(s in ink.strokes)if(s.id !in ink.hiddenIds) {
            if(s.points.size==1 && s.contours.isEmpty()) {
                val p=s.points.single();append("<circle cx=\"${p.x}\" cy=\"${p.y}\" r=\"${s.width/2}\" fill=\"${color(s.color)}\" opacity=\"${s.opacity}\"/>");continue
            }
            val path=buildString {(listOf(s.points)+s.contours).forEach {p->if(p.isNotEmpty()) {
                append("M${p[0].x} ${p[0].y} ")
                if(s.bezier && p.size>=4)for(i in 1..p.size-3 step 3)append("C${p[i].x} ${p[i].y} ${p[i+1].x} ${p[i+1].y} ${p[i+2].x} ${p[i+2].y} ")
                else p.drop(1).forEach {append("L${it.x} ${it.y} ")}
                if(p.first()==p.last())append("Z ")
            }}}
            val fill=if(s.fillColor==null)"none" else if(s.gradientColor!=null)"url(#g${s.id})" else color(s.fillColor!!)
            append("<path d=\"$path\" fill=\"$fill\" fill-rule=\"evenodd\" stroke=\"${color(s.color)}\" stroke-width=\"${s.width}\" opacity=\"${s.opacity * if(s.kind==PenKind.HIGHLIGHTER).3f else 1f}\" stroke-linecap=\"round\" stroke-linejoin=\"round\"")
            if(s.dashed)append(" stroke-dasharray=\"${s.width*4} ${s.width*3}\"")
            append("/>")
        }
        ink.images.filter {it.foreground}.forEach(::picture)
        fun text(value:String,format:NoteFormatting,x:Float,y:Float,width:Float,size:Float,baseColor:Int,scale:Float) {
            val lines=NoteArtworkExport.layout(value,format,width,size,baseColor,scale,context)
            for(i in 0 until lines.lineCount) {
                val start=lines.getLineStart(i);val end=lines.getLineEnd(i)
                append("<text xml:space=\"preserve\" x=\"${x+lines.getLineLeft(i)}\" y=\"${y+lines.getLineBaseline(i)}\" font-family=\"sans-serif\" font-size=\"${size*scale}\" fill=\"${color(baseColor)}\">")
                val boundaries=(listOf(start,end)+format.spans.flatMap {listOf(it.start,it.end)}.filter {it>start && it<end}).distinct().sorted()
                boundaries.zipWithNext().forEach {(a,b)->val s=format.styleAt(a)
                    append("<tspan font-family=\"${NoteFonts.svgFamily(s.font)}\" font-size=\"${s.size*scale}\" letter-spacing=\"${s.tracking*scale}\" fill=\"${color(s.color ?: baseColor)}\" font-weight=\"${if(s.bold)"bold" else "normal"}\" font-style=\"${if(s.italic)"italic" else "normal"}\"")
                    if(s.underline || s.strike)append(" text-decoration=\"${listOfNotNull(if(s.underline)"underline" else null,if(s.strike)"line-through" else null).joinToString(" ")}\"")
                    append(">${xml(value.substring(a,b).trimEnd('\n'))}</tspan>")
                }
                append("</text>")
            }
        }
        if(InkDocument.MAIN_TEXT_ID !in ink.hiddenIds)text(body,ink.formatting,frame.x,frame.y,frame.width,46f,0xff232b3c.toInt(),frame.scale)
        ink.textBoxes.filterNot {it.id in ink.hiddenIds}.forEach {text(it.text,it.formatting,it.x,it.y,it.width,it.size,it.color,it.size/46f)}
        append("</svg>")
    }
}
