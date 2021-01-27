package com.odukle.toastlyrics

import android.util.Log

private const val TAG = "LrcRow"

class LrcRow(val id: Long, val content: String, val strTime: String) : Comparable<LrcRow> {

    /**
     * create LrcRows by standard Lrc Line , if not standard lrc line,
     * return false<br></br>
     * [00:00:20] balabalabalabala
     */

    companion object {
        fun createRows(standardLrcLine: String): List<LrcRow>? {
            return try {
                if (standardLrcLine.indexOf("[") != 0 || standardLrcLine.indexOf("]") != 9) {
                    val listTimes: MutableList<LrcRow> = ArrayList()
                    val content = standardLrcLine.replace("[", "🎵").replace("]", "🎵")
                    val lrcRow = LrcRow(-1L, content, "50:00.00")
                    listTimes.add(lrcRow)
                    return listTimes
                } else if (!standardLrcLine.contains(":") && !standardLrcLine.contains(".")) {
                    val listTimes: MutableList<LrcRow> = ArrayList()
                    val content = standardLrcLine.replace("[", "🎵").replace("]", "🎵")
                    val lrcRow = LrcRow(-1L, content, "50:00.00")
                    listTimes.add(lrcRow)
                    return listTimes
                }
                val lastIndexOfRightBracket = standardLrcLine.lastIndexOf("]")
                val content =
                    standardLrcLine.substring(lastIndexOfRightBracket + 1, standardLrcLine.length)

                // times [mm:ss.SS][mm:ss.SS] -> *mm:ss.SS**mm:ss.SS*
                val times =
                    standardLrcLine.substring(0, lastIndexOfRightBracket + 1).replace("[", "-")
                        .replace("]", "-")
                val arrTimes = times.split("-".toRegex()).toTypedArray()
                val listTimes: MutableList<LrcRow> = ArrayList()
                for (temp in arrTimes) {
                    if (temp.trim { it <= ' ' }.isEmpty()) {
                        continue
                    }
                    val lrcRow = LrcRow(timeConvert(temp), content, temp)
                    listTimes.add(lrcRow)
                }
                listTimes
            } catch (e: Exception) {
                Log.e(TAG, "createRows exception:" + e.message)
                null
            }
        }


    }


    override fun compareTo(other: LrcRow): Int {
        return (id - other.id).toInt()
    }

    operator fun component1(): Long {
        return id
    }

    operator fun component2(): String {
        return content
    }

    operator fun component3(): String {
        return strTime
    }
}

fun timeConvert(timeString: String): Long {
    var timeString = timeString
    timeString = timeString.replace('.', ':')
    val times = timeString.split(":".toRegex()).toTypedArray()
    // mm:ss:SS
    return (Integer.valueOf(times[0]) * 60 * 1000 + Integer.valueOf(times[1]) * 1000 + (
            when (times[2].length) {
                3 -> {
                    Integer.valueOf(times[2]).toLong()
                }

                2 -> {
                    Integer.valueOf(times[2]).toLong() * 10
                }

                1 -> {
                    Integer.valueOf(times[2]).toLong() * 100
                }
                else -> {
                    0
                }
            }))
}