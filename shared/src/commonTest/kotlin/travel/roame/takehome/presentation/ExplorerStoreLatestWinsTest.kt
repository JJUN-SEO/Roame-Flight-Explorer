package travel.roame.takehome.presentation

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import travel.roame.takehome.testing.FakeFlightRepository
import travel.roame.takehome.testing.FakeSavedFlightsRepository
import travel.roame.takehome.testing.flight
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** DOMAIN_RULES PERF-2, PERF-3. The reducer here takes 100 ms of virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExplorerStoreLatestWinsTest {

    @Test
    fun perf3_rapidInputOnlyEverEndsOnTheLatestValue() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = ExplorerStore(FakeFlightRepository(listOf(flight())), FakeSavedFlightsRepository(), backgroundScope, dispatcher, reducer = {
            delay(100)
            reduce(it)
        })
        val seen = mutableListOf<String>()
        backgroundScope.launch(dispatcher) { store.state.collect { seen += it.originQuery } }
        runCurrent()

        store.setOriginQuery("b")
        advanceTimeBy(50)
        store.setOriginQuery("bo")
        advanceTimeBy(50)
        store.setOriginQuery("bos")
        advanceTimeBy(1_000)

        assertEquals("bos", store.state.value.originQuery)
        assertEquals(listOf("", "bos"), seen.distinct())
    }

    @Test
    fun perf3_aSlowOldResultCanNotOverwriteANewerOne() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = ExplorerStore(FakeFlightRepository(listOf(flight())), FakeSavedFlightsRepository(), backgroundScope, dispatcher, reducer = { snapshot ->
            // The older value takes far longer than the newer one, so it would finish last.
            delay(if (snapshot.inputs.filters.maxPoints == 10_000) 5_000 else 10)
            reduce(snapshot)
        })
        runCurrent()
        advanceTimeBy(100)

        store.setMaxPoints(10_000)
        advanceTimeBy(100)
        store.setMaxPoints(20_000)
        advanceTimeBy(20_000)

        assertEquals(20_000, store.state.value.filters.maxPoints)
    }

    @Test
    fun perf2_reducingRunsOnTheGivenDispatcherAndNotTheCallers() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler, name = "reducer")
        var usedDispatcher: CoroutineDispatcher? = null
        ExplorerStore(FakeFlightRepository(listOf(flight())), FakeSavedFlightsRepository(), backgroundScope, dispatcher, reducer = { snapshot ->
            usedDispatcher = currentCoroutineContext()[ContinuationInterceptor] as CoroutineDispatcher
            reduce(snapshot)
        })
        runCurrent()

        assertSame(dispatcher, usedDispatcher)
    }
}
