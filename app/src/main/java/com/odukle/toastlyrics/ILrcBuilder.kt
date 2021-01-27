package com.odukle.toastlyrics

interface ILrcBuilder {
    fun getLrcRows(rawLrc: String?): List<LrcRow?>?
}