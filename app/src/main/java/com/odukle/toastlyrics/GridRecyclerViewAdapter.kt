package com.odukle.toastlyrics

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import es.dmoral.toasty.Toasty

var previousColor = -1

class GridRecyclerViewAdapter(
    private val context: Context,
    private val colorList: List<ToastColor>
) : RecyclerView.Adapter<GridRecyclerViewAdapter.ColorViewHolder>() {

    inner class ColorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val colorNameTV: TextView = view.findViewById(R.id.color_name)
        val colorCard: CardView = view.findViewById(R.id.color_card)
        val colorCheck: ImageView = view.findViewById(R.id.color_check)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
        val rootView =
            LayoutInflater.from(context).inflate(R.layout.color_grid_layout, parent, false)
        return ColorViewHolder(rootView)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
        holder.colorNameTV.text = colorList[position].name
        val color = Color.parseColor(colorList[position].hexCode)
        holder.colorCard.setCardBackgroundColor(color)

        if (previousColor == position) {
            holder.colorCheck.visibility = View.VISIBLE
        } else {
            holder.colorCheck.visibility = View.GONE
        }

        holder.colorCard.setOnClickListener {
            val sharedPref = context.getSharedPreferences("pref", Context.MODE_PRIVATE)
            sharedPref.edit().putString("color", colorList[position].hexCode).apply()

            previousColor = position

            Toasty.custom(
                context,
                "Toast will look like this",
                ContextCompat.getDrawable(context, R.drawable.ic_check_white_24dp),
                color,
                Color.WHITE,
                Toasty.LENGTH_SHORT,
                false,
                true
            ).show()

            notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int {
        return colorList.size
    }
}