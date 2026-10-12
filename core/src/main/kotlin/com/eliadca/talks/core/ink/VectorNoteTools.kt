package com.eliadca.talks.core.ink

import kotlin.math.*

enum class NoteShape(val label: String) { LINE("Línea"), ARROW("Flecha"), RECTANGLE("Rectángulo"), ELLIPSE("Elipse"), TRIANGLE("Triángulo"), STAR("Estrella"), BEZIER("Curva Bézier") }

/** Shapes remain editable ink objects, usable by the same eraser, selection and undo tools. */
fun shapePoints(shape: NoteShape, a: InkPoint, b: InkPoint): List<InkPoint> {
    if(!a.x.isFinite() || !a.y.isFinite() || !b.x.isFinite() || !b.y.isFinite()) return emptyList()
    val l=min(a.x,b.x);val r=max(a.x,b.x);val t=min(a.y,b.y);val d=max(a.y,b.y)
    fun p(x:Float,y:Float)=InkPoint(x,y)
    return when(shape) {
        NoteShape.BEZIER -> listOf(a,p(a.x+(b.x-a.x)/3,a.y),p(a.x+(b.x-a.x)*2/3,b.y),b)
        NoteShape.LINE -> listOf(a,b)
        NoteShape.ARROW -> {
            val angle=atan2(b.y-a.y,b.x-a.x);val length=min(60f,hypot(b.x-a.x,b.y-a.y)*.3f)
            listOf(a,b,p(b.x-length*cos(angle-.55f),b.y-length*sin(angle-.55f)),b,p(b.x-length*cos(angle+.55f),b.y-length*sin(angle+.55f)))
        }
        NoteShape.RECTANGLE -> listOf(p(l,t),p(r,t),p(r,d),p(l,d),p(l,t))
        NoteShape.ELLIPSE -> (0..64).map { val angle=it*2*PI/64;p(((l+r)/2+(r-l)/2*cos(angle)).toFloat(),((t+d)/2+(d-t)/2*sin(angle)).toFloat()) }
        NoteShape.TRIANGLE -> listOf(p((l+r)/2,t),p(r,d),p(l,d),p((l+r)/2,t))
        NoteShape.STAR -> (0..10).map { val angle=-PI/2+it*PI/5;val radius=if(it%2==0)1.0 else .42;p(((l+r)/2+(r-l)/2*radius*cos(angle)).toFloat(),((t+d)/2+(d-t)/2*radius*sin(angle)).toFloat()) }
    }
}

fun InkDocument.rotateInk(requestedIds: Set<Long>, degrees: Float): InkDocument {
    val ids=requestedIds-lockedIds-hiddenIds
    if(!degrees.isFinite())return this
    val bounds=selectionBounds(ids) ?: return this
    val cx=(bounds.left+bounds.right)/2;val cy=(bounds.top+bounds.bottom)/2
    val radians=degrees*PI/180;val c=cos(radians).toFloat();val s=sin(radians).toFloat()
    val result=strokes.map {stroke -> if(stroke.id !in ids)stroke else stroke.mapPoints {p->p.copy(x=cx+(p.x-cx)*c-(p.y-cy)*s,y=cy+(p.x-cx)*s+(p.y-cy)*c)}}
    val changed=copy(strokes=result)
    val newBounds=changed.selectionBounds(ids) ?: return changed
    // Grow paper rather than clip rotated artwork.
    return changed.copy(pageWidth=max(pageWidth,newBounds.right+max(0f,-newBounds.left)+32),height=max(height,newBounds.bottom+max(0f,-newBounds.top)+100))
        .transformSelection(ids,dx=max(0f,-newBounds.left),dy=max(0f,-newBounds.top))
}

fun InkDocument.flipInk(requestedIds:Set<Long>, horizontal:Boolean):InkDocument {
    val ids=requestedIds-lockedIds-hiddenIds
    val bounds=selectionBounds(ids) ?: return this
    return copy(strokes=strokes.map {stroke->if(stroke.id !in ids)stroke else stroke.mapPoints {p-> if(horizontal)p.copy(x=bounds.left+bounds.right-p.x) else p.copy(y=bounds.top+bounds.bottom-p.y)}})
}

fun InkDocument.styleSelection(ids:Set<Long>,color:Int,width:Float):InkDocument = copy(
    strokes=strokes.map {if(it.id in ids && it.id !in lockedIds && it.id !in hiddenIds)it.copy(color=color,width=width.coerceIn(1f,32f)) else it},
    textBoxes=textBoxes.map {if(it.id in ids && it.id !in lockedIds && it.id !in hiddenIds)it.copy(color=color,formatting=it.formatting.apply(0,it.text.length){s->s.copy(color=color)}) else it})

fun InkDocument.smoothInk(ids:Set<Long>):InkDocument = copy(strokes=strokes.map {stroke->
    if(stroke.id !in ids || stroke.id in lockedIds || stroke.id in hiddenIds || stroke.points.size<3)stroke else stroke.copy(points=stroke.points.mapIndexed {index,p->
        if(index==0 || index==stroke.points.lastIndex)p else p.copy(x=(stroke.points[index-1].x+p.x*2+stroke.points[index+1].x)/4,y=(stroke.points[index-1].y+p.y*2+stroke.points[index+1].y)/4)
    })
})

fun InkDocument.reorderInk(requestedIds:Set<Long>,front:Boolean):InkDocument {
    val ids=requestedIds-lockedIds-hiddenIds
    val chosen=strokes.filter {it.id in ids};val rest=strokes.filterNot {it.id in ids}
    val boxes=textBoxes.filter {it.id in ids};val others=textBoxes.filterNot {it.id in ids}
    return copy(strokes=if(front)rest+chosen else chosen+rest,textBoxes=if(front)others+boxes else boxes+others)
}

fun InkStroke.allPoints():List<InkPoint> = points+contours.flatten()
fun InkStroke.mapPoints(transform:(InkPoint)->InkPoint):InkStroke = copy(points=points.map(transform),contours=contours.map {it.map(transform)})
fun InkStroke.resolvedPaths():List<List<InkPoint>> = (listOf(points)+contours).map {path->
    if(!bezier || path.size<4)path else buildList {
        add(path.first())
        for(i in 1..path.size-3 step 3) {
            val a=path[i-1];val b=path[i];val c=path[i+1];val d=path[i+2]
            for(n in 1..48) {val t=n/48f;val u=1-t;add(InkPoint(u*u*u*a.x+3*u*u*t*b.x+3*u*t*t*c.x+t*t*t*d.x,u*u*u*a.y+3*u*u*t*b.y+3*u*t*t*c.y+t*t*t*d.y))}
        }
    }
}
fun InkStroke.containsPoint(point:InkPoint):Boolean {
    var inside=false
    for(path in resolvedPaths())for(i in path.indices) {
        val a=path[i];val b=path[(i+1)%path.size]
        if((a.y>point.y)!=(b.y>point.y) && point.x < (b.x-a.x)*(point.y-a.y)/(b.y-a.y)+a.x)inside=!inside
    }
    return inside
}

/** Aligns/distributes objects with independent positions while keeping their content intact. */
fun InkDocument.alignObjects(ids:Set<Long>,alignment:Int):InkDocument {
    val editable=ids-lockedIds-hiddenIds
    val group=selectionBounds(editable) ?: return this
    var doc=this
    for(id in editable) {
        val b=doc.selectionBounds(setOf(id)) ?: continue
        val dx=when(alignment){0->group.left-b.left;1->(group.left+group.right-b.left-b.right)/2;2->group.right-b.right;else->0f}
        val dy=when(alignment){3->group.top-b.top;4->(group.top+group.bottom-b.top-b.bottom)/2;5->group.bottom-b.bottom;else->0f}
        doc=doc.transformSelection(setOf(id),dx,dy)
    }
    return doc
}
fun InkDocument.distributeObjects(ids:Set<Long>,horizontal:Boolean):InkDocument {
    val objects=(ids-lockedIds-hiddenIds).mapNotNull {id->selectionBounds(setOf(id))?.let {id to it}}.sortedBy {if(horizontal)it.second.left else it.second.top}
    if(objects.size<3)return this
    val start=if(horizontal)objects.first().second.left else objects.first().second.top
    val end=if(horizontal)objects.last().second.right else objects.last().second.bottom
    val sizes=objects.sumOf {if(horizontal)(it.second.right-it.second.left).toDouble() else (it.second.bottom-it.second.top).toDouble()}.toFloat()
    val gap=(end-start-sizes)/(objects.size-1)
    var position=start;var doc=this
    for((id,b) in objects){doc=doc.transformSelection(setOf(id),if(horizontal)position-b.left else 0f,if(horizontal)0f else position-b.top);position+=(if(horizontal)b.right-b.left else b.bottom-b.top)+gap}
    return doc
}

fun InkDocument.selectLasso(polygon:List<InkPoint>):Set<Long> {
    if(polygon.size<3)return emptySet()
    val region=InkStroke(-1,PenKind.BALLPOINT,0,1f,polygon)
    fun inside(p:InkPoint)=region.containsPoint(p)
    val vertices=polygon+polygon.first()
    fun cross(a:InkPoint,b:InkPoint,c:InkPoint,d:InkPoint):Boolean {
        fun orient(p:InkPoint,q:InkPoint,r:InkPoint)=(q.x-p.x)*(r.y-p.y)-(q.y-p.y)*(r.x-p.x)
        return orient(a,b,c)*orient(a,b,d)<0 && orient(c,d,a)*orient(c,d,b)<0
    }
    val strokeIds=strokes.filter {s->
        s.resolvedPaths().any {path->
            path.any(::inside) || path.zipWithNext().any {(a,b)->
                vertices.zipWithNext().any {(c,d)->cross(a,b,c,d)}
            }
        }
    }.map {it.id}
    fun box(l:Float,t:Float,r:Float,b:Float)=listOf(InkPoint(l,t),InkPoint(r,t),InkPoint(r,b),InkPoint(l,b),InkPoint((l+r)/2,(t+b)/2)).any(::inside) || polygon.any {it.x in l..r && it.y in t..b}
    val textIds=textBoxes.filter {box(it.x,it.y,it.x+it.width,it.y+it.height)}.map {it.id}
    val imageIds=images.filter {box(it.x,it.y,it.x+it.width,it.y+it.height)}.map {it.id}
    val main=bodyFrame?.takeIf {box(it.x,it.y,it.x+it.width,it.y+it.height)}?.let {listOf(InkDocument.MAIN_TEXT_ID)}.orEmpty()
    return expandSelection((strokeIds+textIds+imageIds+main).toSet())
}
