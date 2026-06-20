package com.example.repository

import com.example.model.Direction
import com.example.model.PaardensprongPuzzle
import com.example.model.Question
import com.example.model.QuestionRecord
import com.example.model.QuestionSummary
import com.example.model.QuestionType
import com.example.model.RoundInventory
import com.example.model.TaartpuzzelPuzzle
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UIntIdTable
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

/**
 * Exposed/R2DBC-backed [QuestionRepository] using table-per-type: a base
 * `questions` table plus one detail table per puzzle type, linked by question id.
 */
class ExposedQuestionRepository(private val database: R2dbcDatabase) : QuestionRepository {

    object Questions : UIntIdTable("questions") {
        val type = enumerationByName("type", 16, QuestionType::class)
        val category = varchar("category", 64)
        val correctAnswer = varchar("correct_answer", 255)
        val correctLetter = varchar("correct_letter", 1)
        val questionText = text("question_text").nullable()
    }

    object PaardensprongPuzzles : Table("paardensprong_puzzles") {
        val question = reference("question_id", Questions, onDelete = ReferenceOption.CASCADE)
        val grid = varchar("grid", 9)
        override val primaryKey = PrimaryKey(question)
    }

    object TaartpuzzelPuzzles : Table("taartpuzzel_puzzles") {
        val question = reference("question_id", Questions, onDelete = ReferenceOption.CASCADE)
        val missingIndex = integer("missing_index")
        val direction = enumerationByName("direction", 16, Direction::class)
        override val primaryKey = PrimaryKey(question)
    }

    suspend fun createSchema() {
        suspendTransaction(database) {
            SchemaUtils.create(Questions, PaardensprongPuzzles, TaartpuzzelPuzzles)
        }
    }

    override suspend fun create(question: Question): UInt = suspendTransaction(database) {
        val id = Questions.insertAndGetId {
            it[type] = question.type
            it[category] = question.category
            it[correctAnswer] = question.correctAnswer
            it[correctLetter] = question.correctLetter
            it[questionText] = question.questionText
        }

        question.paardensprong?.let { puzzle ->
            PaardensprongPuzzles.insert {
                it[PaardensprongPuzzles.question] = id
                it[grid] = puzzle.grid
            }
        }
        question.taartpuzzel?.let { puzzle ->
            TaartpuzzelPuzzles.insert {
                it[TaartpuzzelPuzzles.question] = id
                it[missingIndex] = puzzle.missingIndex
                it[direction] = puzzle.direction
            }
        }

        id.value
    }

    override suspend fun read(id: UInt): Question? = suspendTransaction(database) {
        val base = Questions.selectAll()
            .where { Questions.id eq id }
            .singleOrNull() ?: return@suspendTransaction null

        val paardensprong = if (base[Questions.type] == QuestionType.PAARDENSPRONG) {
            PaardensprongPuzzles.selectAll()
                .where { PaardensprongPuzzles.question eq id }
                .map { PaardensprongPuzzle(grid = it[PaardensprongPuzzles.grid]) }
                .singleOrNull()
        } else null

        val taartpuzzel = if (base[Questions.type] == QuestionType.TAARTPUZZEL) {
            TaartpuzzelPuzzles.selectAll()
                .where { TaartpuzzelPuzzles.question eq id }
                .map {
                    TaartpuzzelPuzzle(
                        missingIndex = it[TaartpuzzelPuzzles.missingIndex],
                        direction = it[TaartpuzzelPuzzles.direction],
                    )
                }
                .singleOrNull()
        } else null

        Question(
            type = base[Questions.type],
            category = base[Questions.category],
            correctAnswer = base[Questions.correctAnswer],
            correctLetter = base[Questions.correctLetter],
            questionText = base[Questions.questionText],
            paardensprong = paardensprong,
            taartpuzzel = taartpuzzel,
        )
    }

    override suspend fun findAll(): List<QuestionRecord> = suspendTransaction(database) {
        val paardensprongById = PaardensprongPuzzles.selectAll()
            .map { it[PaardensprongPuzzles.question].value to PaardensprongPuzzle(grid = it[PaardensprongPuzzles.grid]) }
            .toList()
            .toMap()

        val taartpuzzelById = TaartpuzzelPuzzles.selectAll()
            .map {
                it[TaartpuzzelPuzzles.question].value to TaartpuzzelPuzzle(
                    missingIndex = it[TaartpuzzelPuzzles.missingIndex],
                    direction = it[TaartpuzzelPuzzles.direction],
                )
            }
            .toList()
            .toMap()

        Questions.selectAll()
            .map { row ->
                val questionId = row[Questions.id].value
                QuestionRecord(
                    id = questionId,
                    question = Question(
                        type = row[Questions.type],
                        category = row[Questions.category],
                        correctAnswer = row[Questions.correctAnswer],
                        correctLetter = row[Questions.correctLetter],
                        questionText = row[Questions.questionText],
                        paardensprong = paardensprongById[questionId],
                        taartpuzzel = taartpuzzelById[questionId],
                    ),
                )
            }
            .toList()
    }

    override suspend fun update(id: UInt, question: Question): Boolean = suspendTransaction(database) {
        val updated = Questions.update({ Questions.id eq id }) {
            it[type] = question.type
            it[category] = question.category
            it[correctAnswer] = question.correctAnswer
            it[correctLetter] = question.correctLetter
            it[questionText] = question.questionText
        }
        if (updated == 0) return@suspendTransaction false

        // The question's type may have changed, so reset its detail rows to match.
        PaardensprongPuzzles.deleteWhere { PaardensprongPuzzles.question eq id }
        TaartpuzzelPuzzles.deleteWhere { TaartpuzzelPuzzles.question eq id }

        val questionId = EntityID(id, Questions)
        question.paardensprong?.let { puzzle ->
            PaardensprongPuzzles.insert {
                it[PaardensprongPuzzles.question] = questionId
                it[grid] = puzzle.grid
            }
        }
        question.taartpuzzel?.let { puzzle ->
            TaartpuzzelPuzzles.insert {
                it[TaartpuzzelPuzzles.question] = questionId
                it[missingIndex] = puzzle.missingIndex
                it[direction] = puzzle.direction
            }
        }
        true
    }

    override suspend fun delete(id: UInt): Boolean = suspendTransaction(database) {
        Questions.deleteWhere { Questions.id eq id } > 0
    }

    override suspend fun summarize(): QuestionSummary = suspendTransaction(database) {
        val facets = Questions
            .selectAll()
            .map {
                Facet(
                    type = it[Questions.type],
                    category = it[Questions.category],
                    correctLetter = it[Questions.correctLetter],
                )
            }
            .toList()

        QuestionSummary(
            total = facets.size,
            byCorrectLetter = facets.groupingBy { it.correctLetter }.eachCount(),
            byCategory = facets.groupingBy { it.category }.eachCount(),
            byType = facets.groupingBy { it.type }.eachCount(),
        )
    }

    override suspend fun roundInventory(): RoundInventory = suspendTransaction(database) {
        val facets = Questions
            .selectAll()
            .map {
                Facet(
                    type = it[Questions.type],
                    category = it[Questions.category],
                    correctLetter = it[Questions.correctLetter].uppercase(),
                )
            }
            .toList()

        RoundInventory(
            regularByLetter = facets
                .filter { it.type == QuestionType.REGULAR }
                .groupingBy { it.correctLetter }
                .eachCount(),
            musicLetters = facets
                .filter { it.type == QuestionType.REGULAR && it.category.equals(MUSIC_CATEGORY, ignoreCase = true) }
                .map { it.correctLetter }
                .toSet(),
            paardensprongLetters = facets
                .filter { it.type == QuestionType.PAARDENSPRONG }
                .map { it.correctLetter }
                .toSet(),
            taartpuzzelLetters = facets
                .filter { it.type == QuestionType.TAARTPUZZEL }
                .map { it.correctLetter }
                .toSet(),
        )
    }

    override suspend fun randomByLetter(
        letter: String,
        type: QuestionType,
        category: String?,
        exclude: Set<UInt>,
    ): QuestionRecord? = suspendTransaction(database) {
        var condition: Op<Boolean> =
            (Questions.correctLetter.upperCase() eq letter.uppercase()) and (Questions.type eq type)
        if (category != null) {
            condition = condition and (Questions.category.lowerCase() eq category.lowercase())
        }
        if (exclude.isNotEmpty()) {
            condition = condition and (Questions.id notInList exclude)
        }

        val row = Questions.selectAll()
            .where(condition)
            .orderBy(Random())
            .limit(1)
            .singleOrNull() ?: return@suspendTransaction null

        val id = row[Questions.id].value
        val paardensprong = if (type == QuestionType.PAARDENSPRONG) {
            PaardensprongPuzzles.selectAll()
                .where { PaardensprongPuzzles.question eq id }
                .map { PaardensprongPuzzle(grid = it[PaardensprongPuzzles.grid]) }
                .singleOrNull()
        } else null

        val taartpuzzel = if (type == QuestionType.TAARTPUZZEL) {
            TaartpuzzelPuzzles.selectAll()
                .where { TaartpuzzelPuzzles.question eq id }
                .map {
                    TaartpuzzelPuzzle(
                        missingIndex = it[TaartpuzzelPuzzles.missingIndex],
                        direction = it[TaartpuzzelPuzzles.direction],
                    )
                }
                .singleOrNull()
        } else null

        QuestionRecord(
            id = id,
            question = Question(
                type = row[Questions.type],
                category = row[Questions.category],
                correctAnswer = row[Questions.correctAnswer],
                correctLetter = row[Questions.correctLetter],
                questionText = row[Questions.questionText],
                paardensprong = paardensprong,
                taartpuzzel = taartpuzzel,
            ),
        )
    }

    private data class Facet(
        val type: QuestionType,
        val category: String,
        val correctLetter: String,
    )

    private companion object {
        const val MUSIC_CATEGORY = "Muziek"
    }
}
