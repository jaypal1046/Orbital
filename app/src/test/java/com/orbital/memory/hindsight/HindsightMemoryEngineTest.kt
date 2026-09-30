package com.orbital.memory.hindsight

import android.content.Context
import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class HindsightMemoryEngineTest {

    private lateinit var database: HindsightDatabase
    private lateinit var dao: HindsightDao
    private lateinit var engine: HindsightMemoryEngine

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, HindsightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.hindsightDao()
        engine = HindsightMemoryEngineImpl(dao, LocalFastEmbeddingProvider())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `retain stores memory entity into database with raw embedding`() = runTest {
        val id = engine.retain(
            bankId = "user_1",
            category = MemoryType.HABIT,
            contextKey = "cab_booking",
            summary = "User always prefers Economy cab tier"
        )

        assertThat(id).isGreaterThan(0L)
        val memories = dao.getMemoriesByContext("user_1", "cab_booking")
        assertThat(memories).hasSize(1)
        assertThat(memories.first().summary).isEqualTo("User always prefers Economy cab tier")
        assertThat(memories.first().rawEmbedding).isNotEmpty()
    }

    @Test
    fun `recall returns highest scored memory matching query semantically`() = runTest {
        engine.retain(
            bankId = "user_1",
            category = MemoryType.HABIT,
            contextKey = "cab_booking",
            summary = "User always prefers Economy cab tier and pays via UPI"
        )
        engine.retain(
            bankId = "user_1",
            category = MemoryType.APP_QUIRK,
            contextKey = "com.spotify.music",
            summary = "Spotify search bar requires double click to focus"
        )

        val results = engine.recall(
            bankId = "user_1",
            query = "Book a ride to airport with cheap price",
            contextKey = "cab_booking"
        )

        assertThat(results).isNotEmpty()
        val topMatch = results.first()
        assertThat(topMatch.memory.category).isEqualTo(MemoryType.HABIT)
        assertThat(topMatch.memory.summary).contains("Economy cab")
        assertThat(topMatch.score).isGreaterThan(0.40f)
    }

    @Test
    fun `buildPromptContext formats recalled memories as system context`() = runTest {
        engine.retain(
            bankId = "user_1",
            category = MemoryType.HABIT,
            contextKey = "food_delivery",
            summary = "User is vegetarian and prefers spicy food"
        )

        val prompt = engine.buildPromptContext(
            bankId = "user_1",
            query = "Find dinner restaurant nearby",
            contextKey = "food_delivery"
        )

        assertThat(prompt).contains("RECALLED LONG-TERM MEMORIES")
        assertThat(prompt).contains("[HABIT] User is vegetarian and prefers spicy food")
    }

    @Test
    fun `forget removes memory entity from database`() = runTest {
        val id = engine.retain(
            bankId = "user_1",
            category = MemoryType.FACTUAL_RELATION,
            contextKey = "contact",
            summary = "Mom's phone number alias is Home Contact"
        )

        engine.forget(id)

        val memories = dao.getMemoriesByContext("user_1", "contact")
        assertThat(memories).isEmpty()
    }
}
