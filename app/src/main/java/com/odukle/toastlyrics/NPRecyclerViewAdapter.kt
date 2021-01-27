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
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView

private const val TAG = "NPRecyclerViewAdapter"
private const val ITEM_TYPE_LINE = 0
private const val ITEM_TYPE_AD = 1

class NPRecyclerViewAdapter(
    private val context: Context,
    private val mList: MutableList<Any>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        fun prepareMixedList() {
            mixedList.clear()
            for (i in 0 until listRows.size) {
                if (i % 11 == 0 && i != 0) {
                    mixedList.add(MainActivity.adRequest)
                    mixedList.add(listRows[i])
                } else {
                    mixedList.add(listRows[i])
                }
            }
        }
    }

    inner class LyricsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tv: TextView = view as TextView
    }

    inner class AdViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val adView: AdView = view.findViewById(R.id.ad_view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        Log.d(TAG, "onCreateViewHolder: called")
        return when (viewType) {
            ITEM_TYPE_AD -> {
                val view =
                    LayoutInflater.from(context).inflate(R.layout.banner_ad_layout, parent, false)
                AdViewHolder(view)
            }

            else -> {
                val view =
                    LayoutInflater.from(context).inflate(R.layout.lyrics_rv_layout, parent, false)
                LyricsViewHolder(view)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        Log.d(TAG, "onBindViewHolder: called")
        when (getItemViewType(position)) {

            ITEM_TYPE_LINE -> {
                val row = (mList[position] as LrcRow)
                (holder as LyricsViewHolder).tv.text = row.content

                holder.tv.setTextAppearance(R.style.LyricsStyleNormal)
            }

            ITEM_TYPE_AD -> {
                (holder as AdViewHolder).adView.loadAd(MainActivity.adRequest)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (mList[position] is LrcRow) {
            return ITEM_TYPE_LINE
        } else if (mList[position] is AdRequest) {
            return ITEM_TYPE_AD
        }

        return ITEM_TYPE_LINE
    }

    override fun getItemCount(): Int {
        return mList.size
    }

}