package com.odukle.toastlyrics

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView

private const val TAG = "SyncRVAdapter"

class SyncRVAdapter(
    private val context: Context,
    private val listRow: MutableList<LrcRow>
) :
    RecyclerView.Adapter<SyncRVAdapter.LyricsViewHolder>() {


    inner class LyricsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tv: TextView = view as TextView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LyricsViewHolder {
        Log.d(TAG, "onCreateViewHolder: called")
        val rootView =
            LayoutInflater.from(context).inflate(R.layout.lyrics_rv_layout, parent, false)
        return LyricsViewHolder(rootView)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onBindViewHolder(holder: LyricsViewHolder, position: Int) {
        Log.d(TAG, "onBindViewHolder: called")
        listRow[position].content
        holder.tv.text = listRow[position].content
        holder.tv.setTextAppearance(R.style.LyricsStyleNormal)
    }

    override fun getItemCount(): Int {
        return listRow.size
    }

}