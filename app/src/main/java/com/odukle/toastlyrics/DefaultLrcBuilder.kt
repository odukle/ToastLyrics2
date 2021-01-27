package com.odukle.toastlyrics

import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.StringReader

class DefaultLrcBuilder : ILrcBuilder {
    override fun getLrcRows(rawLrc: String?): MutableList<LrcRow>? {
        Log.d(TAG, "getLrcRows by rawString")
        if (rawLrc == null || rawLrc.isEmpty()) {
            Log.e(TAG, "getLrcRows rawLrc null or empty")
            return null
        }

        val reader = StringReader(rawLrc)
        val br = BufferedReader(reader)
        var line: String? = null
        val rows: MutableList<LrcRow> = mutableListOf()
        try {
            do {
                line = br.readLine()
                Log.d(TAG, "lrc raw line:$line")
                if (line != null && line.isNotEmpty()) {
                    val lrcRows: List<LrcRow>? = LrcRow.createRows(line)
                    if (lrcRows != null && lrcRows.isNotEmpty()) {
                        for (row in lrcRows) {
                            rows.add(row)
                        }
                    }
                }
            } while (line != null)
            if (rows.size > 0) {
                // sort by time:
                rows.sort()
            }
        } catch (e: Exception) {
            Log.e(TAG, "parse exception:" + e.message)
            return null
        } finally {
            try {
                br.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            reader.close()
        }
        return rows
    }

    companion object {
        const val TAG = "DefaultLrcBuilder"
    }
}