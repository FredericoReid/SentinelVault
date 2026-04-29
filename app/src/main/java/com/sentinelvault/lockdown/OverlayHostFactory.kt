package com.sentinelvault.lockdown

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
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

/**
 * Builds the [View] hierarchy that backs the soft-lock overlay. ComposeViews that live
 * outside an `Activity`/`Fragment` need explicit Lifecycle / ViewModelStore /
 * SavedStateRegistry owners attached to the view tree, otherwise the Compose runtime
 * crashes during the first composition. This factory wires all three onto a synthetic
 * owner whose lifecycle moves through CREATED → STARTED → RESUMED on attach and back to
 * DESTROYED on detach.
 *
 * Split out from [WindowManagerOverlayController] so unit tests can substitute a fake
 * factory that returns a plain `View` (no Compose, no main-looper requirement).
 */
fun interface OverlayHostFactory {
    fun create(context: Context, content: @Composable () -> Unit): OverlayHost

    companion object {
        val DEFAULT: OverlayHostFactory = OverlayHostFactory { context, content ->
            ComposeOverlayHost(context, content)
        }
    }
}

interface OverlayHost {
    val view: View
    fun onAttached()
    fun onDetached()
}

private class ComposeOverlayHost(
    context: Context,
    content: @Composable () -> Unit
) : OverlayHost, LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this).also {
        it.performAttach()
        it.performRestore(null)
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val composeView: ComposeView = ComposeView(context).also { cv ->
        cv.setViewTreeLifecycleOwner(this)
        cv.setViewTreeViewModelStoreOwner(this)
        cv.setViewTreeSavedStateRegistryOwner(this)
        cv.setContent(content)
    }

    override val view: View get() = composeView

    override fun onAttached() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDetached() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
