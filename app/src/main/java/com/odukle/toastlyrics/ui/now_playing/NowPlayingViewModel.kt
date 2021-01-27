package com.odukle.toastlyrics.ui.now_playing

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.odukle.toastlyrics.*
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import com.squareup.okhttp.Response
import com.squareup.picasso.MemoryPolicy
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import es.dmoral.toasty.Toasty
import kotlinx.android.synthetic.main.fragment_now_playing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt


private const val TAG = "NowPlayingViewModel"
var albumArt: Bitmap? = null

class NowPlayingViewModel(application: Application) : AndroidViewModel(application) {

    init {
        instance = this
    }

    var mTrack = MutableLiveData<String?>(null)

    var mArtist = MutableLiveData<String?>(null)

    var mAlbum = MutableLiveData<String?>(null)

    var mDuration = MutableLiveData<Long?>(null)

    var mPosition = MutableLiveData<Long?>(null)

    val track: LiveData<String?> = mTrack
    val artist: LiveData<String?> = mArtist
    val album: LiveData<String?> = mAlbum
    val duration: LiveData<Long?> = mDuration
    val position: LiveData<Long?> = mPosition

    companion object {
        lateinit var instance: NowPlayingViewModel

        fun isInitialized(): Boolean {
            return this::instance.isInitialized
        }

        fun setAlbumArt(query: String) {
            Log.d(TAG, "getAlbumArtUrl: called")
            var imgUrl: String?
            try {
                val client = OkHttpClient()
                val request: Request = Request.Builder()
                    .url("https://genius.p.rapidapi.com/search?q=$query")
                    .get()
                    .addHeader(
                        "x-rapidapi-key",
                        "1d29c431f2mshd1d882586bf4547p18f81fjsn7941d721e540"
                    )
                    .addHeader("x-rapidapi-host", "genius.p.rapidapi.com")
                    .build()

                val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
                    throwable.printStackTrace()
                }
                CoroutineScope(IO + exceptionHandler).launch {
                    try {
                        val response: Response = client.newCall(request).execute()
                        val jsonArray = JSONObject(response.body().string())
                            .getJSONObject("response")
                            .get("hits") as JSONArray

                        val result = jsonArray.getJSONObject(0).getJSONObject("result")
                        imgUrl = result.getString("header_image_url")
                        Log.d(TAG, "getAlbumArtUrl: $imgUrl")

                        if (npShowing) {
                            val np = NowPlayingFragment.instance
                            withContext(Dispatchers.Main) {
                                Picasso.Builder(np.requireContext()).build()
                                    .load(imgUrl)
                                    .memoryPolicy(MemoryPolicy.NO_CACHE)
                                    .networkPolicy(NetworkPolicy.NO_CACHE)
                                    .resize(1000, 1000)
                                    .centerCrop()
                                    .onlyScaleDown()
                                    .into(object : Target {
                                        override fun onBitmapLoaded(
                                            bitmap: Bitmap?,
                                            from: Picasso.LoadedFrom?
                                        ) {
                                            Log.d(TAG, "onBitmapLoaded: loaded")
                                            albumArt = bitmap

                                            try {
                                                np.album_art_iv.setImageBitmap(bitmap)
                                                val layout = np.requireActivity()
                                                    .findViewById<ConstraintLayout>(R.id.container)

                                                val sharedPref =
                                                    NowPlayingFragment.instance.requireContext()
                                                        .getSharedPreferences(
                                                            "pref",
                                                            Context.MODE_PRIVATE
                                                        )

                                                if (sharedPref.getBoolean(
                                                        CONTAINER_HAS_BG,
                                                        false
                                                    )
                                                ) {
                                                    bitmap?.apply {
                                                        val b = fastBlur(this, 0.5f, 20)
                                                        layout.background =
                                                            BitmapDrawable(
                                                                np.requireContext().resources,
                                                                b
                                                            )
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Log.e(
                                                    TAG,
                                                    "onBitmapLoaded: ${e.stackTraceToString()}"
                                                )
                                            }
                                        }

                                        override fun onBitmapFailed(
                                            e: Exception?,
                                            errorDrawable: Drawable?
                                        ) {
                                            Log.w(
                                                TAG,
                                                "onBitmapFailed: failed --> ${e?.stackTraceToString()}"
                                            )
                                        }

                                        override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
                                            Log.d(TAG, "onPrepareLoad: loading image")
                                        }

                                    })
                            }

                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "getAlbumArtUrl: ${e.stackTraceToString()}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "setAlbumArt: ${e.stackTrace}")
            }
        }

    }

    fun submitLyrics(track: String, artist: String) {

        Toasty.Config.getInstance().allowQueue(false).apply()

        val track1 = track.replace("/", "").replace("\\", "")
        val artist1 = artist.replace("/", "").replace("\\", "")
        val name = "userSynced $track1 $artist1.txt"
        val context = this.getApplication<Application>()
        val lyricsFile = File(context.filesDir, name)

        Toasty.normal(context, "Submitting change").show()

        val lyricsRef = FirebaseStorage.getInstance().reference.child("userSynced/$name")
        lyricsRef.putFile(lyricsFile.toUri()).addOnSuccessListener {
            Log.d(TAG, "submitLyrics: File Uploaded")
            Toasty.success(context, "Change submitted").show()
            lyricsRef.downloadUrl.addOnSuccessListener {
                val data = hashMapOf(
                    "uri" to it.toString()
                )
                val db = FirebaseFirestore.getInstance()
                db.collection("userSynced").document(name).set(data, SetOptions.merge())
            }
        }.addOnFailureListener {
            Log.d(TAG, "submitLyrics: ${it.stackTraceToString()}")
        }
    }

    fun delay() {
        if (synced) {
            val rows = mutableListOf<LrcRow>()
            rows.clear()
            try {
                Log.d(TAG, "delay: listRows size --> ${mixedList.size}")
                for (i in 0 until mixedList.size) {
                    if (mixedList[i] is LrcRow) {
                        val mRow = mixedList[i] as LrcRow
                        if (mRow.id != -1L) {
                            val id = mRow.id + 500L
                            val strTime = millisToStr(id)
                            val content = mRow.content
                            val row = LrcRow(id, content, strTime)
                            rows.add(row)
                        }
                    }

                }
                Log.d(TAG, "delay: rows size --> ${rows.size}")
            } finally {
                val sb = StringBuilder()
                sb.clear()
                try {
                    for (i in 0 until rows.size) {
                        val content = rows[i].content
                        val strTime = "[" + rows[i].strTime + "]"
                        sb.append("$strTime$content")
                    }
                } finally {
                    Log.d(TAG, "delay: $sb")
                    val i = sb.toString().indexOf("[")

                    val lyrics = sb.toString().replace("[", "\n[").trim()
                    if (track.value != null && artist.value != null) {
                        writeToFile(lyrics, track.value!!, artist.value!!)
                    }
                }
            }
        }
    }

    fun hasten() {
        if (synced) {
            val rows = mutableListOf<LrcRow>()
            rows.clear()
            try {
                Log.d(TAG, "hasten: listRows size --> ${mixedList.size}")

                for (i in 0 until mixedList.size) {
                    if (mixedList[i] is LrcRow) {
                        val mRow = mixedList[i] as LrcRow
                        if (mRow.id != -1L) {
                            val id = mRow.id - 500L
                            val strTime = millisToStr(id)
                            val content = mRow.content
                            val row = LrcRow(id, content, strTime)
                            rows.add(row)
                        }
                    }

                }

                Log.d(TAG, "hasten: listRows size --> ${rows.size}")
            } finally {
                val sb = StringBuilder()
                sb.clear()
                try {
                    for (i in 0 until rows.size) {
                        val content = rows[i].content
                        val strTime = "[" + rows[i].strTime + "]"
                        sb.append("$strTime$content")

                    }
                } finally {
                    Log.d(TAG, "hasten: $sb")
                    val i = sb.toString().indexOf("[")

                    val lyrics = sb.toString().replace("[", "\n[").trim()
                    if (track.value != null && artist.value != null) {
                        writeToFile(lyrics, track.value!!, artist.value!!)
                    }
                }

            }


        } else {
            Toasty.warning(
                getApplication(),
                "Lyrics are not synced (to sync lyrics click on the sync tab in the bottom bar)"
            ).show()
        }
    }


    private fun writeToFile(lyrics: String?, track: String, artist: String) {
        try {
            val track1 = track.replace("/", "").replace("\\", "")
            val artist1 = artist.replace("/", "").replace("\\", "")
            val name = "userSynced $track1 $artist1.txt"
            val nameOg = "$track1 $artist1.txt"
            val lyricsFile = File(this.getApplication<Application>().filesDir, name)
            val lyricsFileOg = File(this.getApplication<Application>().filesDir, nameOg)
            if (lyricsFileOg.exists()) lyricsFileOg.delete()
            val fileWriter = FileWriter(lyricsFile)
            fileWriter.write(lyrics)
            fileWriter.flush()
            fileWriter.close()

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

}

@SuppressLint("SimpleDateFormat")
fun millisToStr(mil: Long): String {
    val sdf = SimpleDateFormat("mm:ss:SSS")
    val time = Date(mil)
    var str = sdf.format(time)
    var mm = (str.substring(0, str.indexOf(":")).toInt() - 30).toString()
    if (mm.length < 2) mm = "0$mm"
    str = str.replaceRange(0, str.indexOf(":"), mm).removeRange(str.lastIndex - 1, str.lastIndex)
    val replacement = str.substring(str.indexOf(":") + 1).replace(":", ".")
    str = str.replaceAfter(":", replacement)
    return str
}

fun fastBlur(sentBitmap: Bitmap, scale: Float, radius: Int): Bitmap? {
    var sentBitmap = sentBitmap
    val width = (sentBitmap.width * scale).roundToInt()
    val height = (sentBitmap.height * scale).roundToInt()
    sentBitmap = Bitmap.createScaledBitmap(sentBitmap, width, height, false)
    val bitmap = sentBitmap.copy(sentBitmap.config, true)
    if (radius < 1) {
        return null
    }
    val w = bitmap.width
    val h = bitmap.height
    val pix = IntArray(w * h)
    Log.e("pix", w.toString() + " " + h + " " + pix.size)
    bitmap.getPixels(pix, 0, w, 0, 0, w, h)
    val wm = w - 1
    val hm = h - 1
    val wh = w * h
    val div = radius + radius + 1
    val r = IntArray(wh)
    val g = IntArray(wh)
    val b = IntArray(wh)
    var rsum: Int
    var gsum: Int
    var bsum: Int
    var x: Int
    var y: Int
    var i: Int
    var p: Int
    var yp: Int
    var yi: Int
    var yw: Int
    val vmin = IntArray(w.coerceAtLeast(h))
    var divsum = div + 1 shr 1
    divsum *= divsum
    val dv = IntArray(256 * divsum)
    i = 0
    while (i < 256 * divsum) {
        dv[i] = i / divsum
        i++
    }
    yi = 0
    yw = yi
    val stack = Array(div) {
        IntArray(
            3
        )
    }
    var stackpointer: Int
    var stackstart: Int
    var sir: IntArray
    var rbs: Int
    val r1 = radius + 1
    var routsum: Int
    var goutsum: Int
    var boutsum: Int
    var rinsum: Int
    var ginsum: Int
    var binsum: Int
    y = 0
    while (y < h) {
        bsum = 0
        gsum = bsum
        rsum = gsum
        boutsum = rsum
        goutsum = boutsum
        routsum = goutsum
        binsum = routsum
        ginsum = binsum
        rinsum = ginsum
        i = -radius
        while (i <= radius) {
            p = pix[yi + wm.coerceAtMost(i.coerceAtLeast(0))]
            sir = stack[i + radius]
            sir[0] = p and 0xff0000 shr 16
            sir[1] = p and 0x00ff00 shr 8
            sir[2] = p and 0x0000ff
            rbs = r1 - StrictMath.abs(i)
            rsum += sir[0] * rbs
            gsum += sir[1] * rbs
            bsum += sir[2] * rbs
            if (i > 0) {
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
            } else {
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
            }
            i++
        }
        stackpointer = radius
        x = 0
        while (x < w) {
            r[yi] = dv[rsum]
            g[yi] = dv[gsum]
            b[yi] = dv[bsum]
            rsum -= routsum
            gsum -= goutsum
            bsum -= boutsum
            stackstart = stackpointer - radius + div
            sir = stack[stackstart % div]
            routsum -= sir[0]
            goutsum -= sir[1]
            boutsum -= sir[2]
            if (y == 0) {
                vmin[x] = (x + radius + 1).coerceAtMost(wm)
            }
            p = pix[yw + vmin[x]]
            sir[0] = p and 0xff0000 shr 16
            sir[1] = p and 0x00ff00 shr 8
            sir[2] = p and 0x0000ff
            rinsum += sir[0]
            ginsum += sir[1]
            binsum += sir[2]
            rsum += rinsum
            gsum += ginsum
            bsum += binsum
            stackpointer = (stackpointer + 1) % div
            sir = stack[stackpointer % div]
            routsum += sir[0]
            goutsum += sir[1]
            boutsum += sir[2]
            rinsum -= sir[0]
            ginsum -= sir[1]
            binsum -= sir[2]
            yi++
            x++
        }
        yw += w
        y++
    }
    x = 0
    while (x < w) {
        bsum = 0
        gsum = bsum
        rsum = gsum
        boutsum = rsum
        goutsum = boutsum
        routsum = goutsum
        binsum = routsum
        ginsum = binsum
        rinsum = ginsum
        yp = -radius * w
        i = -radius
        while (i <= radius) {
            yi = 0.coerceAtLeast(yp) + x
            sir = stack[i + radius]
            sir[0] = r[yi]
            sir[1] = g[yi]
            sir[2] = b[yi]
            rbs = r1 - StrictMath.abs(i)
            rsum += r[yi] * rbs
            gsum += g[yi] * rbs
            bsum += b[yi] * rbs
            if (i > 0) {
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
            } else {
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
            }
            if (i < hm) {
                yp += w
            }
            i++
        }
        yi = x
        stackpointer = radius
        y = 0
        while (y < h) {

            // Preserve alpha channel: ( 0xff000000 & pix[yi] )
            pix[yi] =
                -0x1000000 and pix[yi] or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
            rsum -= routsum
            gsum -= goutsum
            bsum -= boutsum
            stackstart = stackpointer - radius + div
            sir = stack[stackstart % div]
            routsum -= sir[0]
            goutsum -= sir[1]
            boutsum -= sir[2]
            if (x == 0) {
                vmin[y] = (y + r1).coerceAtMost(hm) * w
            }
            p = x + vmin[y]
            sir[0] = r[p]
            sir[1] = g[p]
            sir[2] = b[p]
            rinsum += sir[0]
            ginsum += sir[1]
            binsum += sir[2]
            rsum += rinsum
            gsum += ginsum
            bsum += binsum
            stackpointer = (stackpointer + 1) % div
            sir = stack[stackpointer]
            routsum += sir[0]
            goutsum += sir[1]
            boutsum += sir[2]
            rinsum -= sir[0]
            ginsum -= sir[1]
            binsum -= sir[2]
            yi += w
            y++
        }
        x++
    }
    Log.e("pix", w.toString() + " " + h + " " + pix.size)
    bitmap.setPixels(pix, 0, w, 0, 0, w, h)
    return bitmap
}


