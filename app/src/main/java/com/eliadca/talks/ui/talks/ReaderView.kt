package com.eliadca.talks.ui.talks

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Layout
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ScrollView
import com.eliadca.talks.core.doc.RichDoc
import com.eliadca.talks.core.track.CharSpan
import com.eliadca.talks.data.ReaderTheme
import com.eliadca.talks.editor.EditorStyle
import com.eliadca.talks.editor.NoteDecor
import com.eliadca.talks.editor.SpannableCodec
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Colours of one reading theme (ARGB). */
data class ReaderPalette(
    val background: Int,
    val text: Int,
    val accent: Int,
    val muted: Int,
) {
    companion object {
        fun of(theme: ReaderTheme): ReaderPalette = when (theme) {
            ReaderTheme.NIGHT -> ReaderPalette(0xFF000000.toInt(), 0xFFF2F2F2.toInt(), 0xFFFFC107.toInt(), 0xFF8C8C8C.toInt())
            ReaderTheme.STAGE -> ReaderPalette(0xFF000000.toInt(), 0xFFFFE680.toInt(), 0xFF00E5FF.toInt(), 0xFF9C8B45.toInt())
            ReaderTheme.DAY -> ReaderPalette(0xFFFFFFFF.toInt(), 0xFF101010.toInt(), 0xFF1E4DD8.toInt(), 0xFF7A7A7A.toInt())
            ReaderTheme.SEPIA -> ReaderPalette(0xFFF4ECD8.toInt(), 0xFF3B2F1E.toInt(), 0xFFB5532A.toInt(), 0xFF8A7A5E.toInt())
        }
    }
}

data class ReaderConfig(
    val fontSp: Float,
    val lineSpacing: Float,
    val serif: Boolean,
    val palette: ReaderPalette,
    /** Where the line being read sits, as a fraction of the screen height from the top. */
    val anchor: Float,
    val dimSpoken: Boolean,
    /** Shows the reading line while following (when nothing is marked). */
    val guide: Boolean = false,
)

/**
 * Shows the whole speech in large type and follows the speaker: what was said is dimmed, what to
 * say next is highlighted and underlined (only the words to say, never notes or headings), and the
 * text glides so that the current line stays at a fixed height. Touching the text by hand pauses the
 * following for a few seconds.
 *
 * In [manual] mode nothing moves by itself: the speaker scrolls, and a reading line marks the
 * height where the current line should be, so the app can pick up from there later.
 */
class ReaderView(context: Context) : ScrollView(context) {

    interface Listener {
        /** The user pressed and held at text offset [offset]. */
        fun onLongPress(offset: Int)
        fun onTap()
        /** The user scrolled by hand, so automatic following is suspended. */
        fun onUserScroll()
        /** Automatic following resumed. */
        fun onFollowResumed()
    }

    var listener: Listener? = null

    private val content = ContentView(context)
    private val handler = Handler(Looper.getMainLooper())
    private var animator: ValueAnimator? = null
    private var programmaticScroll = false

    /** Known during measurement, before View.height has been set by the first layout. */
    private var viewportHeight = 0
    private var alignAfterLayout = true

    private var doc: RichDoc = RichDoc.EMPTY
    private var cfg = ReaderConfig(44f, 1.35f, false, ReaderPalette.of(ReaderTheme.NIGHT), 0.35f, true)

    private var spokenEnd = 0
    private var focus = 0
    private var marks: List<CharSpan> = emptyList()

    /** When the marked block last changed, for its soft appearance; 0 when it shows fully. */
    private var markShownAt = 0L

    /** Where the running scroll animation is heading. */
    private var animTarget = -1

    /** True while the view is moving the text by itself. */
    var autoFollow = true
        private set

    /** Everything automatic off: no following, no highlight; a reading line shows where to read. */
    var manual: Boolean = false
        private set

    fun setManualMode(on: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            post { setManualMode(on) }
            return
        }
        if (manual == on) return
        manual = on
        handler.removeCallbacks(resumeFollowing)
        animator?.cancel()
        if (on) autoFollow = false
        content.invalidate()
        invalidate()
    }

    /** How strongly the marked block shows (0..1): new blocks appear softly instead of jumping in. */
    private fun markAlpha(): Float {
        if (markShownAt == 0L) return 1f
        val t = (SystemClock.uptimeMillis() - markShownAt).toFloat() / MARK_FADE_MS
        if (t >= 1f) {
            markShownAt = 0L
            return 1f
        }
        return 0.2f + 0.8f * t
    }

    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val guidePath = Path()

    private val resumeFollowing = Runnable { resumeFollow(animated = true) }

    init {
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        isHapticFeedbackEnabled = true
        isFillViewport = true
        addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    // ======================================================================================
    // Public API
    // ======================================================================================

    fun setDocument(doc: RichDoc) {
        this.doc = doc
        content.rebuild()
    }

    fun configure(config: ReaderConfig) {
        if (config == cfg && content.layout != null) return
        val relayout = config.fontSp != cfg.fontSp || config.lineSpacing != cfg.lineSpacing ||
            config.serif != cfg.serif || config.anchor != cfg.anchor
        cfg = config
        setBackgroundColor(config.palette.background)
        content.rebuild(forceLayout = relayout)
        // The reading line is drawn by this view itself.
        invalidate()
    }

    /**
     * Updates what is dimmed (everything before [spokenEnd]) and marked ([marks]) and, if following,
     * scrolls so that the line of [focus] sits at the reading line. Repeating the same values does
     * nothing, so it can be fed every state update.
     */
    fun setProgress(spokenEnd: Int, focus: Int, marks: List<CharSpan>, jump: Boolean = false) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            // Callers may collect the session state on a background thread; views and animators need the main one.
            post { setProgress(spokenEnd, focus, marks, jump) }
            return
        }
        if (!jump && spokenEnd == this.spokenEnd && focus == this.focus && marks == this.marks) return
        // A block that replaces the previous one (rather than a window sliding along) fades in.
        val old = this.marks
        val overlaps = old.isNotEmpty() && marks.isNotEmpty() &&
            marks.first().start < old.last().end && marks.last().end > old.first().start
        if (jump) markShownAt = 0L else if (marks.isNotEmpty() && !overlaps) markShownAt = SystemClock.uptimeMillis()
        this.spokenEnd = spokenEnd
        this.focus = focus
        this.marks = marks
        content.invalidate()
        if (autoFollow) scrollToOffset(focus, animated = !jump)
    }

    /** One marked stretch [nextStart, nextEnd), for callers that have no segments. */
    fun setProgress(spokenEnd: Int, nextStart: Int, nextEnd: Int, jump: Boolean = false) =
        setProgress(spokenEnd, nextStart, if (nextEnd > nextStart) listOf(CharSpan(nextStart, nextEnd)) else emptyList(), jump)

    /** Resumes following the speaker and brings the current line back to its place. */
    fun resumeFollow(animated: Boolean = true) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            post { resumeFollow(animated) }
            return
        }
        if (manual) return
        handler.removeCallbacks(resumeFollowing)
        val was = autoFollow
        autoFollow = true
        scrollToOffset(focus, animated)
        invalidate()
        if (!was) listener?.onFollowResumed()
    }

    /** Text offset at the start of the line now at reading height: where the speaker is looking. */
    fun readingLineOffset(): Int {
        val l = content.layout ?: return 0
        val y = scrollY + height * cfg.anchor + content.lineHeightPx() / 2f - content.padTop
        val line = l.getLineForVertical(y.toInt().coerceAtLeast(0))
        return l.getLineStart(line)
    }

    /** Scrolls by [lines] lines of text (negative goes back), for remotes and keys in manual mode. */
    fun scrollLines(lines: Int) {
        animator?.cancel()
        smoothScrollBy(0, (lines * content.lineHeightPx()).toInt())
    }

    // ======================================================================================
    // Scrolling
    // ======================================================================================

    private fun maxScroll(): Int = max(0, content.height - height)

    private fun targetFor(offset: Int): Int {
        val l = content.layout ?: return scrollY
        val line = l.getLineForOffset(offset.coerceIn(0, l.text.length))
        val y = content.padTop + l.getLineTop(line)
        return (y - height * cfg.anchor).toInt().coerceIn(0, maxScroll())
    }

    private fun scrollToOffset(offset: Int, animated: Boolean) {
        if (height == 0 || content.layout == null) return
        val target = targetFor(offset)
        val tolerance = content.lineHeightPx() * 0.3f
        val running = animator?.isRunning == true
        // Already gliding there: let it finish instead of starting over.
        if (animated && running && abs(target - animTarget) < tolerance) return
        animator?.cancel()
        val delta = target - scrollY
        if (delta == 0 || (animated && abs(delta) < tolerance)) return
        if (!animated) {
            programmaticScroll = true
            scrollTo(0, target)
            programmaticScroll = false
            return
        }
        animTarget = target
        val duration = (320 + min(560f, abs(delta).toFloat() / height * 520f)).toLong()
        animator = ValueAnimator.ofInt(scrollY, target).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener {
                programmaticScroll = true
                scrollTo(0, it.animatedValue as Int)
                programmaticScroll = false
            }
            start()
        }
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (manual) return
        if (!programmaticScroll && t != oldt) {
            // The user is moving the text; stop following for a while.
            animator?.cancel()
            if (autoFollow) {
                autoFollow = false
                listener?.onUserScroll()
            }
            handler.removeCallbacks(resumeFollowing)
            handler.postDelayed(resumeFollowing, RESUME_FOLLOW_MS)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val viewport = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) 0
            else MeasureSpec.getSize(heightMeasureSpec)
        if (viewport != viewportHeight) {
            viewportHeight = viewport
            // ScrollView gives its child the same unbounded height spec even after rotation.
            // Invalidate that measurement so the spacer reflects the new viewport immediately.
            content.forceLayout()
            alignAfterLayout = true
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val wasProgrammatic = programmaticScroll
        programmaticScroll = true
        try {
            // ScrollView may clamp its position when the content shrinks; that is not a gesture.
            super.onLayout(changed, l, t, r, b)
        } finally {
            programmaticScroll = wasProgrammatic
        }
        // Align only after the text and its trailing spacer have their final geometry.
        if (autoFollow && (changed || alignAfterLayout)) scrollToOffset(focus, animated = false)
        alignAfterLayout = false
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(resumeFollowing)
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (manual || (cfg.guide && autoFollow)) drawReadingGuide(canvas)
    }

    /** The reading line of manual mode: a faint band across the screen with a pointer at each side. */
    private fun drawReadingGuide(canvas: Canvas) {
        val pal = cfg.palette
        val lineH = content.lineHeightPx()
        val top = scrollY + height * cfg.anchor
        guidePaint.style = Paint.Style.FILL
        guidePaint.color = (pal.accent and 0x00FFFFFF) or (0x24 shl 24)
        canvas.drawRect(0f, top, width.toFloat(), top + lineH, guidePaint)
        guidePaint.color = pal.accent
        val size = lineH * 0.22f
        val cy = top + lineH / 2f
        guidePath.reset()
        guidePath.moveTo(0f, cy - size)
        guidePath.lineTo(size * 1.1f, cy)
        guidePath.lineTo(0f, cy + size)
        guidePath.close()
        guidePath.moveTo(width.toFloat(), cy - size)
        guidePath.lineTo(width - size * 1.1f, cy)
        guidePath.lineTo(width.toFloat(), cy + size)
        guidePath.close()
        canvas.drawPath(guidePath, guidePaint)
    }

    // ======================================================================================
    // The text
    // ======================================================================================

    private inner class ContentView(context: Context) : View(context) {

        var layout: StaticLayout? = null
            private set

        private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private val path = Path()
        private var builtWidth = -1
        private var style = EditorStyle(1f, 0, 0)
        private var text: Spanned = SpannableCodec.toSpannable(RichDoc.EMPTY, style, withMarker = false)

        val padTop: Int get() = (viewportHeight * cfg.anchor).toInt()
        // Enough room below the last line to bring it all the way up to the reading height.
        private val padBottom: Int get() = viewportHeight - padTop

        /** The left margin holds the arrow that points at the next words; the right one is just a breath. */
        private val sideMargin: Float get() = max(minMargin, fontPx() * 0.72f)
        private val rightMargin: Float get() = max(minMargin, fontPx() * 0.4f)
        private val minMargin: Float get() = resources.displayMetrics.density * 16f

        private val detector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    listener?.onTap()
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    listener?.onLongPress(offsetAt(e.x, e.y))
                }
            },
        )

        init {
            setWillNotDraw(false)
        }

        private fun fontPx(): Float =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, cfg.fontSp, resources.displayMetrics)

        fun lineHeightPx(): Float = fontPx() * cfg.lineSpacing

        /** Rebuilds the styled text (when the document, theme or size changed) and the layout. */
        fun rebuild(forceLayout: Boolean = true) {
            val scale = fontPx() / TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 18f, resources.displayMetrics)
            style = EditorStyle(resources.displayMetrics.density * scale, cfg.palette.accent, cfg.palette.muted)
            text = SpannableCodec.toSpannable(doc, style, withMarker = false)
            paint.color = cfg.palette.text
            paint.textSize = fontPx()
            paint.textLocale = SPANISH
            paint.typeface = if (cfg.serif) Typeface.SERIF else Typeface.SANS_SERIF
            if (forceLayout) {
                builtWidth = -1
                alignAfterLayout = true
            }
            requestLayout()
            invalidate()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            if (w != builtWidth || layout == null) {
                val textWidth = max(100, (w - sideMargin - rightMargin).toInt())
                // Whole words only: a word split across lines is harder to say aloud.
                layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, textWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, cfg.lineSpacing)
                    .setIncludePad(false)
                    .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
                    .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                    .build()
                builtWidth = w
            }
            val h = (layout?.height ?: 0) + padTop + padBottom
            setMeasuredDimension(w, h)
        }

        // --- drawing ------------------------------------------------------------------------------

        override fun onDraw(canvas: Canvas) {
            val l = layout ?: return
            val pal = cfg.palette
            canvas.save()
            canvas.translate(sideMargin, padTop.toFloat())

            val follow = !manual && marks.isNotEmpty()
            val alpha = if (follow) markAlpha() else 1f
            if (follow) drawChunkTint(canvas, l, pal, alpha)
            NoteDecor.draw(canvas, l, text, style)
            l.draw(canvas)
            if (!manual && cfg.dimSpoken && spokenEnd > 0) drawDimmedPast(canvas, l, pal)
            if (follow) {
                drawUnderline(canvas, l, pal, alpha)
                drawMarker(canvas, l, pal, alpha)
            }
            canvas.restore()
            if (alpha < 1f) postInvalidateOnAnimation()
        }

        private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

        private fun lineSegments(l: Layout, from: Int, to: Int, block: (line: Int, left: Float, right: Float) -> Unit) {
            val len = l.text.length
            val s = from.coerceIn(0, len)
            val e = to.coerceIn(s, len)
            if (e <= s) return
            val firstLine = l.getLineForOffset(s)
            val lastLine = l.getLineForOffset(e - 1)
            for (line in firstLine..lastLine) {
                val left = if (line == firstLine) l.getPrimaryHorizontal(s) else l.getLineLeft(line)
                val right = if (line == lastLine) l.getPrimaryHorizontal(e) else l.getLineRight(line)
                // Wrapped text can report the end of a segment at the start of the next line.
                if (right > left) block(line, left, right)
            }
        }

        private fun drawChunkTint(canvas: Canvas, l: Layout, pal: ReaderPalette, alpha: Float) {
            fill.style = Paint.Style.FILL
            fill.color = withAlpha(pal.accent, (0x33 * alpha).toInt())
            val pad = fontPx() * 0.12f
            val r = fontPx() * 0.18f
            for (m in marks) {
                lineSegments(l, m.start, m.end) { line, left, right ->
                    rect.set(left - pad, l.getLineTop(line).toFloat(), right + pad, l.getLineBottom(line).toFloat())
                    canvas.drawRoundRect(rect, r, r, fill)
                }
            }
        }

        private fun drawDimmedPast(canvas: Canvas, l: Layout, pal: ReaderPalette) {
            fill.style = Paint.Style.FILL
            fill.color = (pal.background and 0x00FFFFFF) or (0xAA shl 24)
            val len = l.text.length
            val end = spokenEnd.coerceIn(0, len)
            val line = l.getLineForOffset(end)
            val top = l.getLineTop(line).toFloat()
            canvas.drawRect(-sideMargin, -padTop.toFloat(), width.toFloat(), top, fill)
            val x = l.getPrimaryHorizontal(end)
            if (x > l.getLineLeft(line)) {
                canvas.drawRect(-sideMargin, top, x, l.getLineBottom(line).toFloat(), fill)
            }
        }

        private fun drawUnderline(canvas: Canvas, l: Layout, pal: ReaderPalette, alpha: Float) {
            fill.style = Paint.Style.FILL
            fill.color = withAlpha(pal.accent, (255 * alpha).toInt())
            val thick = max(resources.displayMetrics.density * 3f, fontPx() * 0.075f)
            val gap = paint.descent() * 0.35f
            for (m in marks) {
                lineSegments(l, m.start, m.end) { line, left, right ->
                    val y = l.getLineBaseline(line) + gap
                    rect.set(left, y, right, y + thick)
                    canvas.drawRoundRect(rect, thick / 2, thick / 2, fill)
                }
            }
        }

        /** A small arrow in the left margin on the line where the next words start. */
        private fun drawMarker(canvas: Canvas, l: Layout, pal: ReaderPalette, alpha: Float) {
            val len = l.text.length
            val line = l.getLineForOffset((marks.firstOrNull()?.start ?: focus).coerceIn(0, len))
            val size = min(fontPx() * 0.42f, sideMargin * 0.62f)
            val cx = -sideMargin * 0.5f
            val cy = (l.getLineTop(line) + l.getLineBottom(line)) / 2f
            fill.style = Paint.Style.FILL
            fill.color = withAlpha(pal.accent, (255 * alpha).toInt())
            path.reset()
            path.moveTo(cx - size * 0.5f, cy - size * 0.6f)
            path.lineTo(cx + size * 0.6f, cy)
            path.lineTo(cx - size * 0.5f, cy + size * 0.6f)
            path.close()
            canvas.drawPath(path, fill)
        }

        // --- touch --------------------------------------------------------------------------------

        private fun offsetAt(x: Float, y: Float): Int {
            val l = layout ?: return 0
            val line = l.getLineForVertical((y - padTop).toInt().coerceAtLeast(0))
            return l.getOffsetForHorizontal(line, x - sideMargin)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            detector.onTouchEvent(event)
            // Always claim the gesture so the ScrollView gets the move events for scrolling.
            return true
        }
    }

    private companion object {
        const val RESUME_FOLLOW_MS = 7_000L
        const val MARK_FADE_MS = 220f
        val SPANISH: java.util.Locale = java.util.Locale.forLanguageTag("es-ES")
    }
}
