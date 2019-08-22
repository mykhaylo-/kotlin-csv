package com.github.doyaaaaaken.kotlincsv.client

import com.github.doyaaaaaken.kotlincsv.dsl.context.CsvReaderContext
import com.github.doyaaaaaken.kotlincsv.parser.CsvParser
import com.github.doyaaaaaken.kotlincsv.util.MalformedCSVException
import java.io.BufferedReader
import java.io.Closeable

/**
 * CSV Reader class, which controls file I/O flow.
 *
 * @author doyaaaaaken
 */
class CsvFileReader internal constructor(
        private val ctx: CsvReaderContext,
        private val reader: BufferedReader
) : Closeable {

    private val parser = CsvParser()

    private val lineSeparator = System.lineSeparator()

    fun readAll(): List<List<String>> {
        return readAllAsSequence().toList()
    }

    fun readAllWithHeader(): List<Map<String, String>> {
        return readWithHeader(reader)
    }

    fun readAllAsSequence(): Sequence<List<String>> {
        return generateSequence { readNext(reader) }
    }

    override fun close() {
        reader.close()
    }

    private fun readWithHeader(br: BufferedReader): List<Map<String, String>> {
        val headers = readNext(br)
        val duplicated = headers?.let(::findDuplicate)
        if (duplicated != null) throw MalformedCSVException("header '$duplicated' is duplicated")

        return generateSequence { readNext(reader) }.map { fields ->
            if (requireNotNull(headers).size != fields.size) {
                throw MalformedCSVException("fields num  ${fields.size} is not matched with header num ${headers.size}")
            }
            headers.zip(fields).toMap()
        }.toList()
    }

    private tailrec fun readNext(br: BufferedReader, leftOver: String = ""): List<String>? {
        val nextLine = br.readLine()
        return if (nextLine == null) {
            if (leftOver.isNotEmpty()) {
                throw MalformedCSVException("Malformed format: leftOver \"$leftOver\" on the tail of file")
            } else {
                null
            }
        } else {
            val value = if (leftOver.isEmpty()) {
                "$nextLine$lineSeparator"
            } else {
                "$leftOver$lineSeparator$nextLine$lineSeparator"
            }
            val parsedLine = parser.parseRow(value, ctx.quoteChar, ctx.delimiter, ctx.escapeChar)
            if (parsedLine == null) {
                readNext(br, "$leftOver$nextLine")
            } else {
                parsedLine
            }
        }
    }

    private fun findDuplicate(headers: List<String>): String? {
        val set = mutableSetOf<String>()
        headers.forEach { h ->
            if (set.contains(h)) {
                return h
            } else {
                set.add(h)
            }
        }
        return null
    }
}
