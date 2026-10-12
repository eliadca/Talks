package com.eliadca.talks.core.ink

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class NoteTemplatesTest {
    @Test fun applyingATemplatePreservesExistingWritingAndAllocatesNewObjectIds() {
        val old=InkDocument().addStroke(PenKind.PENCIL,7,5f,listOf(InkPoint(80f,80f),InkPoint(140f,140f))).addTextBox("Una idea").copy(images=listOf(NoteImage(3,"asset")),nextId=4)
        val template=NoteTemplates.builtIn().first {it.id=="cornell"}
        val changed=old.applyTemplate(template.document)
        assertEquals(old.strokes.first(),changed.strokes.first());assertEquals(old.textBoxes.first(),changed.textBoxes.first());assertEquals(old.images,changed.images)
        val ids=changed.strokes.map {it.id}+changed.textBoxes.map {it.id}+changed.images.map {it.id}
        assertEquals(ids.size,ids.distinct().size);assertTrue(changed.nextId>ids.max());assertTrue(changed.lockedIds.all {it>=4})
    }
    @Test fun imagesAndVectorContoursRemainEditableAndSerializable() {
        val a=InkPoint(80f,80f);val b=InkPoint(160f,160f)
        val stroke=InkStroke(1,PenKind.BALLPOINT,8,4f,listOf(a,b),geometric=true,contours=listOf(listOf(a.copy(x=200f),b.copy(x=250f))),fillColor=7)
        val doc=InkDocument(strokes=listOf(stroke),images=listOf(NoteImage(2,"asset",x=80f,y=80f)),nextId=3)
        val transformed=doc.transformSelection(setOf(1L,2L),40f,60f,1.5f)
        assertEquals(120f,transformed.images.single().x,0f);assertEquals(300f,transformed.strokes.single().contours.single().first().x,0f)
        val template=NotePageTemplate("test","Prueba",doc)
        assertEquals(template,Json.decodeFromString<NotePageTemplate>(Json.encodeToString(template)))
    }
    @Test fun lassoSelectsACrossingStrokeAndProtectsLockedTemplateLines() {
        val doc=InkDocument().addStroke(PenKind.BALLPOINT,1,3f,listOf(InkPoint(0f,100f),InkPoint(500f,100f)))
            .addStroke(PenKind.BALLPOINT,1,3f,listOf(InkPoint(0f,120f),InkPoint(500f,120f))).copy(lockedIds=setOf(2L))
        val region=listOf(InkPoint(80f,80f),InkPoint(160f,80f),InkPoint(160f,160f),InkPoint(80f,160f),InkPoint(80f,80f))
        assertEquals(setOf(1L),doc.selectLasso(region))
    }
    @Test fun alignAndDistributeKeepOtherObjectsAndSpaceThreeBoxesEvenly() {
        val doc=InkDocument(textBoxes=listOf(NoteTextBox(1,"a",0f,40f,width=100f),NoteTextBox(2,"b",130f,80f,width=100f),NoteTextBox(3,"c",600f,120f,width=100f)),nextId=4)
        val result=doc.distributeObjects(setOf(1L,2L,3L),true)
        assertEquals(300f,result.textBoxes[1].x,0f)
        val aligned=result.alignObjects(setOf(1L,2L,3L),3)
        assertTrue(aligned.textBoxes.all {it.y==40f})
    }
}
