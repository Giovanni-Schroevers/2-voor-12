package com.example.service

import com.example.model.Direction
import com.example.model.PaardensprongPuzzle
import com.example.model.PuzzlePreference
import com.example.model.Question
import com.example.model.QuestionRecord
import com.example.model.QuestionSummary
import com.example.model.QuestionType
import com.example.model.RoundInventory
import com.example.model.TaartpuzzelPuzzle
import com.example.repository.QuestionRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val MUSIC = "Muziek"

/** In-memory [QuestionRepository] backed by a fixed list of records. */
private class FakeQuestionRepository(private val records: List<QuestionRecord>) : QuestionRepository {

    override suspend fun roundInventory(): RoundInventory {
        val byLetter = records.map { it.question.correctLetter.uppercase() to it.question }
        return RoundInventory(
            regularByLetter = byLetter
                .filter { it.second.type == QuestionType.REGULAR }
                .groupingBy { it.first }
                .eachCount(),
            musicLetters = byLetter
                .filter { it.second.type == QuestionType.REGULAR && it.second.category.equals(MUSIC, ignoreCase = true) }
                .map { it.first }
                .toSet(),
            paardensprongLetters = byLetter
                .filter { it.second.type == QuestionType.PAARDENSPRONG }
                .map { it.first }
                .toSet(),
            taartpuzzelLetters = byLetter
                .filter { it.second.type == QuestionType.TAARTPUZZEL }
                .map { it.first }
                .toSet(),
        )
    }

    override suspend fun randomByLetter(
        letter: String,
        type: QuestionType,
        category: String?,
        exclude: Set<UInt>,
    ): QuestionRecord? = records.firstOrNull { record ->
        record.question.correctLetter.equals(letter, ignoreCase = true) &&
            record.question.type == type &&
            (category == null || record.question.category.equals(category, ignoreCase = true)) &&
            record.id !in exclude
    }

    override suspend fun create(question: Question): UInt = error("unused")
    override suspend fun read(id: UInt): Question? = error("unused")
    override suspend fun findAll(): List<QuestionRecord> = error("unused")
    override suspend fun update(id: UInt, question: Question): Boolean = error("unused")
    override suspend fun delete(id: UInt): Boolean = error("unused")
    override suspend fun summarize(): QuestionSummary = error("unused")
}

/** Replays a fixed sequence of words; the last one repeats once exhausted. */
private class FakeWordGenerator(private val words: List<String>) : WordGenerator {
    var calls = 0
        private set

    override suspend fun generateWord(
        inventory: RoundInventory,
        puzzleType: QuestionType,
        feedback: String?,
    ): String = words[minOf(calls++, words.lastIndex)]
}

private fun regular(id: UInt, letter: Char, category: String = "general") = QuestionRecord(
    id = id,
    question = Question(
        type = QuestionType.REGULAR,
        category = category,
        correctAnswer = "Answer$id",
        correctLetter = letter.toString(),
        questionText = "Question $id",
    ),
)

private fun paardensprong(id: UInt, letter: Char) = QuestionRecord(
    id = id,
    question = Question(
        type = QuestionType.PAARDENSPRONG,
        category = "general",
        correctAnswer = "ANSWERAA",
        correctLetter = letter.toString(),
        paardensprong = PaardensprongPuzzle(grid = "ABCDEFGHI"),
    ),
)

private fun taartpuzzel(id: UInt, letter: Char) = QuestionRecord(
    id = id,
    question = Question(
        type = QuestionType.TAARTPUZZEL,
        category = "general",
        correctAnswer = "RONDJESAA",
        correctLetter = letter.toString(),
        taartpuzzel = TaartpuzzelPuzzle(missingIndex = 0, direction = Direction.CLOCKWISE),
    ),
)

class SoloGameServiceTest {

    @Test
    fun `paardensprong replaces exactly one non-final slot`() = runBlocking {
        val word = "ABCDEFGHIJKM"
        val records = buildList {
            word.forEachIndexed { i, c ->
                add(regular((i + 1).toUInt(), c, if (c == word.last()) MUSIC else "general"))
            }
            add(paardensprong(50u, 'C')) // only 'C' (index 2) can supply the puzzle
        }

        val service = SoloGameService(FakeQuestionRepository(records), FakeWordGenerator(listOf(word)))
        val round = service.generateRound(PuzzlePreference.PAARDENSPRONG)

        assertEquals(word, round.word)
        assertEquals(12, round.questions.size)
        assertEquals(word, round.questions.joinToString("") { it.correctLetter })
        val puzzles = round.questions.withIndex().filter { it.value.type == QuestionType.PAARDENSPRONG }
        assertEquals(1, puzzles.size)
        assertEquals(2, puzzles.single().index)
        assertNotEquals(11, puzzles.single().index)
        assertEquals(QuestionType.REGULAR, round.questions.last().type)
        assertEquals(MUSIC, round.questions.last().category)
    }

    @Test
    fun `taartpuzzel replaces exactly one non-final slot`() = runBlocking {
        val word = "ABCDEFGHIJKM"
        val records = buildList {
            word.forEachIndexed { i, c ->
                add(regular((i + 1).toUInt(), c, if (c == word.last()) MUSIC else "general"))
            }
            add(taartpuzzel(60u, 'D')) // only 'D' (index 3) can supply the puzzle
        }

        val service = SoloGameService(FakeQuestionRepository(records), FakeWordGenerator(listOf(word)))
        val round = service.generateRound(PuzzlePreference.TAARTPUZZEL)

        val puzzles = round.questions.withIndex().filter { it.value.type == QuestionType.TAARTPUZZEL }
        assertEquals(1, puzzles.size)
        assertEquals(3, puzzles.single().index)
        assertEquals(MUSIC, round.questions.last().category)
    }

    @Test
    fun `random resolves to one of the two puzzle types`() = runBlocking {
        val word = "ABCDEFGHIJKM"
        val records = buildList {
            word.forEachIndexed { i, c ->
                add(regular((i + 1).toUInt(), c, if (c == word.last()) MUSIC else "general"))
            }
            add(paardensprong(50u, 'C'))
            add(taartpuzzel(60u, 'D'))
        }

        val service = SoloGameService(FakeQuestionRepository(records), FakeWordGenerator(listOf(word)))
        val round = service.generateRound(PuzzlePreference.RANDOM)

        val puzzles = round.questions.withIndex().filter { it.value.type != QuestionType.REGULAR }
        assertEquals(1, puzzles.size)
        assertTrue(puzzles.single().value.type in setOf(QuestionType.PAARDENSPRONG, QuestionType.TAARTPUZZEL))
        assertNotEquals(11, puzzles.single().index)
        assertEquals(MUSIC, round.questions.last().category)
    }

    @Test
    fun `repeated letters draw distinct questions`() = runBlocking {
        val word = "AABCDEFGHIJM" // 'A' appears twice; 'B' (index 2) carries the puzzle
        val records = buildList {
            add(regular(1u, 'A'))
            add(regular(2u, 'A')) // a second, distinct 'A' question
            "CDEFGHIJ".forEachIndexed { i, c -> add(regular((10 + i).toUInt(), c)) }
            add(regular(99u, 'M', MUSIC))
            add(paardensprong(50u, 'B'))
        }

        val service = SoloGameService(FakeQuestionRepository(records), FakeWordGenerator(listOf(word)))
        val round = service.generateRound(PuzzlePreference.PAARDENSPRONG)

        assertEquals(word, round.questions.joinToString("") { it.correctLetter })
        // The two 'A' slots must be backed by different questions.
        assertNotEquals(round.questions[0].correctAnswer, round.questions[1].correctAnswer)
    }

    @Test
    fun `unsatisfiable bank gives up`() = runBlocking {
        val word = "ABCDEFGHIJKZ" // last letter 'Z' has no music question
        val records = buildList {
            word.dropLast(1).forEachIndexed { i, c -> add(regular((i + 1).toUInt(), c)) }
            add(regular(100u, 'M', MUSIC))
            add(paardensprong(50u, 'C')) // puzzle is satisfiable; only the music slot fails
        }

        val service = SoloGameService(
            FakeQuestionRepository(records),
            FakeWordGenerator(listOf(word)),
            maxAttempts = 3,
        )

        assertFailsWith<UnsatisfiableRoundException> {
            service.generateRound(PuzzlePreference.PAARDENSPRONG)
        }
        Unit
    }
}
