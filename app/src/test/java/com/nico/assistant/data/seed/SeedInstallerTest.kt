package com.nico.assistant.data.seed

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nico.assistant.data.db.AppDatabase
import com.nico.assistant.data.repo.AutomationRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeedInstallerTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: AutomationRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AutomationRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `les seeds s installent dans une base vide`() = runTest {
        val seeds = SeedAutomations.all()

        val installed = SeedInstaller.install(db, seeds)

        assertEquals(seeds.size, installed)
        assertEquals(seeds.size, repository.count())
        assertEquals(
            seeds.map { it.name }.toSet(),
            repository.observeAllOnce().map { it.name }.toSet()
        )
    }

    @Test
    fun `les chaines d actions survivent a l installation`() = runTest {
        SeedInstaller.install(db, SeedAutomations.all(musicPackage = "com.exemple.musique"))

        val musique = repository.enabledAutomations().first { it.name == "Musique" }

        assertEquals(2, musique.actions.size)
        assertEquals("com.exemple.musique", musique.actions.first().params["app"])
        assertEquals("{titre}", musique.actions.first().params["query"])
        assertEquals(800L, musique.actions[1].delayMsBefore)
    }

    @Test
    fun `une base deja peuplee n est jamais re-semee`() = runTest {
        SeedInstaller.install(db, SeedAutomations.all())
        val premier = repository.count()

        val second = SeedInstaller.install(db, SeedAutomations.all())

        assertEquals(0, second)
        assertEquals(premier, repository.count())
    }

    @Test
    fun `supprimer une seed ne la fait pas revenir`() = runTest {
        SeedInstaller.install(db, SeedAutomations.all())
        val lampe = repository.enabledAutomations().first { it.name == "Lampe" }

        repository.delete(lampe.id)
        SeedInstaller.install(db, SeedAutomations.all())

        assertTrue(repository.enabledAutomations().none { it.name == "Lampe" })
    }

    private suspend fun AutomationRepository.observeAllOnce() = enabledAutomations()
}
