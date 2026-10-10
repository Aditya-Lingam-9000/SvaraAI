package com.sarvaai.app.audio.player

object SentenceChunker {
    // Splits on standard and Indic sentence terminators followed by whitespace
    private val SENTENCE_SPLIT_REGEX = Regex("(?<=[.!?।॥])\\s+")

    /**
     * Splits input text into discrete sentence chunks suitable for rapid streaming TTS synthesis.
     * Keeps chunk length below [maxChars] (default 160 characters) to optimize Time-To-First-Audio (TTFA).
     * Ensures every chunk contains speakable characters to prevent API 422 errors.
     */
    fun splitIntoChunks(text: String, maxChars: Int = 160): List<String> {
        if (text.isBlank()) return emptyList()

        val rawSentences = text.split(SENTENCE_SPLIT_REGEX)
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.any { c -> c.isLetterOrDigit() } }

        if (rawSentences.isEmpty()) {
            return if (text.any { it.isLetterOrDigit() }) listOf(text.trim()) else emptyList()
        }

        val chunks = mutableListOf<String>()
        var currentChunk = StringBuilder()

        for (sentence in rawSentences) {
            if (sentence.length <= maxChars) {
                if (currentChunk.isEmpty()) {
                    currentChunk.append(sentence)
                } else if (currentChunk.length + sentence.length + 1 <= maxChars) {
                    currentChunk.append(" ").append(sentence)
                } else {
                    chunks.add(currentChunk.toString().trim())
                    currentChunk = StringBuilder(sentence)
                }
            } else {
                // If a single sentence exceeds maxChars, flush current chunk and sub-split by clauses/words
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toString().trim())
                    currentChunk = StringBuilder()
                }
                chunks.addAll(splitLongSentence(sentence, maxChars))
            }
        }

        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString().trim())
        }

        return chunks.filter { it.isNotBlank() && it.any { c -> c.isLetterOrDigit() } }
    }

    private fun splitLongSentence(sentence: String, maxChars: Int): List<String> {
        // Attempt clause split on commas/semicolons first
        val clauses = sentence.split(Regex("(?<=[,;،])\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.any { c -> c.isLetterOrDigit() } }

        if (clauses.size > 1) {
            val clauseChunks = mutableListOf<String>()
            var clauseBuilder = StringBuilder()
            for (clause in clauses) {
                if (clause.length <= maxChars) {
                    if (clauseBuilder.isEmpty()) {
                        clauseBuilder.append(clause)
                    } else if (clauseBuilder.length + clause.length + 1 <= maxChars) {
                        clauseBuilder.append(" ").append(clause)
                    } else {
                        clauseChunks.add(clauseBuilder.toString().trim())
                        clauseBuilder = StringBuilder(clause)
                    }
                } else {
                    if (clauseBuilder.isNotEmpty()) {
                        clauseChunks.add(clauseBuilder.toString().trim())
                        clauseBuilder = StringBuilder()
                    }
                    clauseChunks.addAll(splitByWords(clause, maxChars))
                }
            }
            if (clauseBuilder.isNotEmpty()) {
                clauseChunks.add(clauseBuilder.toString().trim())
            }
            return clauseChunks
        } else {
            return splitByWords(sentence, maxChars)
        }
    }

    private fun splitByWords(text: String, maxChars: Int): List<String> {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val result = mutableListOf<String>()
        var current = StringBuilder()

        for (word in words) {
            if (current.isEmpty()) {
                current.append(word)
            } else if (current.length + word.length + 1 <= maxChars) {
                current.append(" ").append(word)
            } else {
                if (current.isNotEmpty() && current.any { it.isLetterOrDigit() }) {
                    result.add(current.toString().trim())
                }
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty() && current.any { it.isLetterOrDigit() }) {
            result.add(current.toString().trim())
        }
        return result
    }
}
