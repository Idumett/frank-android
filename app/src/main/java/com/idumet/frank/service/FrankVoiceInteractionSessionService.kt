package com.idumet.frank.service

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.idumet.frank.FrankAssistantScreen
import com.idumet.frank.ui.theme.FrankTheme

class FrankVoiceInteractionSessionService :
    VoiceInteractionSessionService() {

    override fun onNewSession(
        args: Bundle?
    ): VoiceInteractionSession {
        return FrankVoiceInteractionSession(this)
    }
}

class FrankVoiceInteractionSession(
    context: Context
) : VoiceInteractionSession(context),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)

    private val savedStateController =
        SavedStateRegistryController.create(this)

    private val sessionViewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = sessionViewModelStore

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)

        super.onCreate()

        lifecycleRegistry.handleLifecycleEvent(
            Lifecycle.Event.ON_CREATE
        )
    }

    override fun onCreateContentView(): View {
        return ComposeView(context).apply {

            setViewTreeLifecycleOwner(
                this@FrankVoiceInteractionSession
            )

            setViewTreeViewModelStoreOwner(
                this@FrankVoiceInteractionSession
            )

            setViewTreeSavedStateRegistryOwner(
                this@FrankVoiceInteractionSession
            )

            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnLifecycleDestroyed(
                    this@FrankVoiceInteractionSession
                )
            )

            setContent {
                FrankTheme {
                    FrankAssistantScreen(
                        assistantMode = true,
                        onClose = ::finish
                    )
                }
            }
        }
    }

    override fun onShow(
        args: Bundle?,
        showFlags: Int
    ) {
        super.onShow(args, showFlags)

        lifecycleRegistry.handleLifecycleEvent(
            Lifecycle.Event.ON_START
        )

        lifecycleRegistry.handleLifecycleEvent(
            Lifecycle.Event.ON_RESUME
        )
    }

    override fun onHide() {
        lifecycleRegistry.handleLifecycleEvent(
            Lifecycle.Event.ON_PAUSE
        )

        lifecycleRegistry.handleLifecycleEvent(
            Lifecycle.Event.ON_STOP
        )

        super.onHide()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(
            Lifecycle.Event.ON_DESTROY
        )

        sessionViewModelStore.clear()

        super.onDestroy()
    }
}