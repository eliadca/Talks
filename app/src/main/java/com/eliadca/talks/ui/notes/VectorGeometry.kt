package com.eliadca.talks.ui.notes

import android.graphics.Path
import android.graphics.PathMeasure
import com.eliadca.talks.core.ink.*

internal fun vectorPath(stroke:InkStroke):Path = Path().apply {
    fillType=Path.FillType.EVEN_ODD
    (listOf(stroke.points)+stroke.contours).forEach {points->if(points.isNotEmpty()) {
        moveTo(points[0].x,points[0].y)
        if(stroke.bezier && points.size>=4)for(i in 1..points.size-3 step 3)cubicTo(points[i].x,points[i].y,points[i+1].x,points[i+1].y,points[i+2].x,points[i+2].y)
        else points.drop(1).forEach {lineTo(it.x,it.y)}
        if(points.first()==points.last())close()
    }}
}

/** Real Android path booleans. Every contour, including holes, remains part of one editable object. */
internal fun combineShapes(document:InkDocument,ids:Set<Long>,operation:Path.Op):InkDocument {
    val shapes=document.strokes.filter {it.id in ids && it.geometric && it.id !in document.lockedIds && it.id !in document.hiddenIds}
    require(shapes.size>=2 && shapes.size==ids.size) {"Selecciona al menos dos formas vectoriales."}
    val path=vectorPath(shapes.first())
    shapes.drop(1).forEach {check(path.op(vectorPath(it),operation)) {"No se pudo combinar las formas."}}
    val measure=PathMeasure(path,false);val contours=mutableListOf<List<InkPoint>>();val position=FloatArray(2)
    do {
        val length=measure.length
        if(length>0) {
            val count=(length/2).toInt().coerceIn(4,4000)
            val points=(0..count).mapNotNull {i->if(measure.getPosTan(length*i/count,position,null))InkPoint(position[0],position[1]) else null}
            if(points.isNotEmpty())contours.add(if(measure.isClosed)points+points.first() else points)
        }
    } while(measure.nextContour())
    val without=document.removeSelection(ids)
    if(contours.isEmpty())return without
    val first=shapes.first().copy(id=without.nextId,points=contours.first(),contours=contours.drop(1),bezier=false,fillColor=shapes.first().fillColor ?: shapes.first().color)
    return without.copy(strokes=without.strokes+first,nextId=without.nextId+1)
}
