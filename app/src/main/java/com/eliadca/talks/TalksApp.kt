package com.eliadca.talks

import android.app.Application
import android.content.Context
import com.eliadca.talks.core.sample.SampleContent
import com.eliadca.talks.data.AppSettings
import com.eliadca.talks.data.SettingsRepository
import com.eliadca.talks.data.SpeechRepository
import com.eliadca.talks.data.db.TalksDatabase
import com.eliadca.talks.speech.SpeechEngine
import com.eliadca.talks.speech.VoskModelController
import com.eliadca.talks.speech.VoskModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TalksApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // If the app died while Talks had the sound muted, give the volume back.
        com.eliadca.talks.speech.SoundMuter(this).restoreIfLeftMuted()
        container.startUp()
    }
}

/** Hand-wired application services; small enough that a DI framework would only add weight. */
class AppContainer(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: TalksDatabase by lazy { TalksDatabase.build(context) }
    val settings: SettingsRepository by lazy { SettingsRepository(context) }
    val speeches: SpeechRepository by lazy { SpeechRepository(database) }
    val voskModel: VoskModelController by lazy { VoskModelController(VoskModelManager(context), scope) }

    /** Test hook: when set, Talks mode listens through this engine instead of the real recogniser. */
    @Volatile
    var speechEngineFactory: ((AppSettings) -> SpeechEngine)? = null

    fun startUp() {
        scope.launch {
            speeches.purgeOldTrash()
            seedIfFirstRun()
        }
    }

    /**
     * On the very first launch, create the welcome note, a speech to practise Talks mode with and
     * the template for an AI assistant. Installs from before the template existed get it once.
     */
    private suspend fun seedIfFirstRun() {
        val s = settings.settings.first()
        if (s.seeded && s.templateSeeded) return
        if (!s.seeded && speeches.count() == 0) {
            speeches.create(SampleContent.PRACTICE_TITLE, SampleContent.practice)
            speeches.create(SampleContent.AGENT_TEMPLATE_TITLE, SampleContent.agentTemplate)
            speeches.create(SampleContent.WELCOME_TITLE, SampleContent.welcome)
        } else if (!s.templateSeeded) {
            speeches.create(SampleContent.AGENT_TEMPLATE_TITLE, SampleContent.agentTemplate)
        }
        settings.update { it.copy(seeded = true, templateSeeded = true) }
    }
}

val Context.container: AppContainer
    get() = (applicationContext as TalksApp).container
