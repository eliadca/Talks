package com.eliadca.talks.core.ink

import kotlinx.serialization.Serializable

@Serializable
data class NotePageTemplate(val id:String,val name:String,val document:InkDocument)

fun InkDocument.applyTemplate(template:InkDocument):InkDocument {
    var id=nextId
    val mapping=mutableMapOf<Long,Long>()
    fun allocate(old:Long):Long=id++.also {mapping[old]=it}
    val ink=template.strokes.map {it.copy(id=allocate(it.id))}
    val texts=template.textBoxes.map {it.copy(id=allocate(it.id))}
    val pictures=template.images.map {it.copy(id=allocate(it.id))}
    val rules=template.areas.map {it.copy(id=allocate(it.id))}
    return copy(strokes=strokes+ink,textBoxes=textBoxes+texts,images=images+pictures,areas=rules,
        paperColor=template.paperColor,dotted=template.dotted,gridVisible=template.gridVisible,snapToGrid=template.snapToGrid,
        gridSpacing=template.gridSpacing,wholePageRuled=template.wholePageRuled,spacing=template.spacing,
        nextId=id,lockedIds=lockedIds+template.lockedIds.mapNotNull {mapping[it]},hiddenIds=hiddenIds+template.hiddenIds.mapNotNull {mapping[it]},
        groups=groups+template.groups.map {g->g.mapNotNull {mapping[it]}.toSet()}.filter {it.size>1})
        .resizePage(template.pageWidth,maxOf(height,template.height))
}

object NoteTemplates {
    fun builtIn():List<NotePageTemplate> {
        val plain=InkDocument()
        var music=plain
        for(row in 0..6)for(line in 0..4)music=music.addStroke(PenKind.BALLPOINT,0xff8795a7.toInt(),1f,listOf(InkPoint(50f,100f+row*190+line*18),InkPoint(950f,100f+row*190+line*18)),geometric=true)
        music=music.copy(lockedIds=music.strokes.map {it.id}.toSet())
        val cornell=plain.addStroke(PenKind.BALLPOINT,0xff8795a7.toInt(),2f,listOf(InkPoint(280f,100f),InkPoint(280f,1200f)),geometric=true)
            .addStroke(PenKind.BALLPOINT,0xff8795a7.toInt(),2f,listOf(InkPoint(40f,1200f),InkPoint(960f,1200f)),geometric=true)
            .addTextBox("Claves / preguntas",40f).addTextBox("Resumen",1250f)
        val study=cornell.copy(textBoxes=cornell.textBoxes.map {it.copy(size=28f,height=50f,width=400f)},lockedIds=(cornell.strokes.map {it.id}+cornell.textBoxes.map {it.id}).toSet())
        val planner=plain.addTextBox("Objetivos del día",40f).addTextBox("Tareas",480f).addTextBox("Ideas y recordatorios",950f)
        val schedule=planner.copy(textBoxes=planner.textBoxes.map {it.copy(size=32f,height=70f)},wholePageRuled=true,lockedIds=planner.textBoxes.map {it.id}.toSet())
        return listOf(NotePageTemplate("plain","Blanca",plain),NotePageTemplate("ruled","Renglones",plain.copy(wholePageRuled=true)),
            NotePageTemplate("grid","Cuadrícula",plain.copy(gridVisible=true)),NotePageTemplate("dots","Puntos",plain.copy(dotted=true)),
            NotePageTemplate("cornell","Cornell / estudio",study),NotePageTemplate("planner","Agenda diaria",schedule),NotePageTemplate("music","Pentagramas",music))
    }
}
