package com.miku.ray.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceRestartLifecycleTest {

    @Test
    fun duplicateRestartIsRejectedWhileFirstRestartIsActive() = runBlocking {
        val lifecycle = ServiceRestartLifecycle(Dispatchers.Unconfined)
        val release = CompletableDeferred<Unit>()

        assertTrue(lifecycle.launch(onStarting = {}) { release.await() })
        assertFalse(lifecycle.launch(onStarting = {}) {})

        lifecycle.cancel()
        Unit
    }

    @Test
    fun completedWorkRemainsRestartingUntilReplacementReportsResult() = runBlocking {
        val lifecycle = ServiceRestartLifecycle(Dispatchers.Unconfined)
        val finished = CompletableDeferred<Unit>()
        var token: ServiceRestartLifecycle.Token? = null

        assertTrue(lifecycle.launch(onStarting = {}) {
            token = it
            finished.complete(Unit)
        })
        withTimeout(1_000) { finished.await() }

        assertTrue(lifecycle.isActive())
        assertTrue(lifecycle.complete(requireNotNull(token)))
        assertFalse(lifecycle.isActive())
    }

    @Test
    fun stopCancelsOwnedRestartWorkBeforeReplacementStarts() = runBlocking {
        val lifecycle = ServiceRestartLifecycle(Dispatchers.Unconfined)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        var replacementStarted = false

        assertTrue(lifecycle.launch(onStarting = {}) { token ->
            try {
                entered.complete(Unit)
                release.await()
                replacementStarted = lifecycle.isCurrent(token)
            } finally {
                finished.complete(Unit)
            }
        })
        withTimeout(1_000) { entered.await() }

        assertTrue(lifecycle.cancel())
        release.complete(Unit)
        withTimeout(1_000) { finished.await() }

        assertFalse(replacementStarted)
        assertFalse(lifecycle.isActive())
    }

    @Test
    fun staleRestartCannotCompleteAReplacementRestart() = runBlocking {
        val lifecycle = ServiceRestartLifecycle(Dispatchers.Unconfined)
        var firstToken: ServiceRestartLifecycle.Token? = null
        var secondToken: ServiceRestartLifecycle.Token? = null

        assertTrue(lifecycle.launch(onStarting = {}) { firstToken = it })
        assertNotNull(firstToken)
        assertTrue(lifecycle.cancel())
        assertTrue(lifecycle.launch(onStarting = {}) { secondToken = it })
        assertNotNull(secondToken)

        assertFalse(lifecycle.complete(requireNotNull(firstToken)))
        assertTrue(lifecycle.isCurrent(requireNotNull(secondToken)))

        lifecycle.cancel()
        Unit
    }
}
