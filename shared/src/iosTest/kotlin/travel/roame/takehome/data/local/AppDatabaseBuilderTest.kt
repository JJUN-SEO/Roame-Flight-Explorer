package travel.roame.takehome.data.local

import kotlinx.coroutines.test.runTest
import travel.roame.takehome.testing.deleteDatabaseFiles
import travel.roame.takehome.testing.flight
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The file the iOS app really uses, in its Documents folder (ARCHITECTURE §5.3). */
class AppDatabaseBuilderTest {

    @AfterTest
    fun removeDatabase() = deleteDatabaseFiles(appDatabasePath())

    @Test
    fun r1_theAppDatabaseFileSurvivesReopening() = runTest {
        assertTrue(appDatabasePath().endsWith("/Documents/$DATABASE_NAME"), appDatabasePath())

        val firstRun = appDatabaseBuilder().configure()
        firstRun.flightCacheDao().replaceAll(listOf(flight(id = "kept").toCachedEntity()), now = 1_000)
        firstRun.close()

        val secondRun = appDatabaseBuilder().configure()
        val rows = secondRun.flightCacheDao().read()
        secondRun.close()

        assertEquals(listOf("kept"), rows.flights.map { it.flight.id })
        assertEquals(1_000, rows.lastSuccessAt)
    }
}
