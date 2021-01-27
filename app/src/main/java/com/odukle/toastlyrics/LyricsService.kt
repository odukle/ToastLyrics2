package com.odukle.toastlyrics

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.*
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.odukle.toastlyrics.ui.home.IS_SERVICE_STOPPED
import com.odukle.toastlyrics.ui.now_playing.NowPlayingFragment
import com.odukle.toastlyrics.ui.now_playing.NowPlayingViewModel
import com.odukle.toastlyrics.ui.now_playing.albumArt
import com.odukle.toastlyrics.ui.now_playing.npShowing
import com.odukle.toastlyrics.ui.sync.SyncFragment
import com.odukle.toastlyrics.ui.sync.currentLine
import com.odukle.toastlyrics.ui.sync.sfShowing
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import com.squareup.okhttp.Response
import es.dmoral.toasty.Toasty
import kotlinx.android.synthetic.main.fragment_now_playing.*
import kotlinx.android.synthetic.main.fragment_sync.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong


private const val TAG = "LyricsService"
var currentTrack: String? = null
var currentArtist: String? = null
var previousTrack: String? = null

var listRows: MutableList<LrcRow> = mutableListOf()
var mixedList = mutableListOf<Any>()

var synced = true
var eCount = 0

var timer: CountDownTimer? = null
var mTimer: CountDownTimer? = null
const val FIRST_TIME = "firstTime"
const val TRACK = "track"
const val ARTIST = "artist"
const val ALBUM = "album"
const val DURATION = "duration"

class LyricsService : Service() {

    private lateinit var notificationManager: NotificationManager
    private lateinit var audioManager: AudioManager
    private lateinit var sharedPref: SharedPreferences
    private lateinit var db: FirebaseFirestore
    private lateinit var functions: FirebaseFunctions
    private lateinit var queryUrl: String
    private lateinit var lyricBoxPath: String
    private lateinit var lyricBoxElement: String
    private lateinit var toast: Toast

    init {
        instance = this
    }

    companion object {
        lateinit var instance: LyricsService

        fun terminateService() {
            instance.onDestroy()
        }

        fun isInitialized(): Boolean {
            return this::instance.isInitialized
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Handler(Looper.getMainLooper()).postDelayed({
                startForeground(1, MusicNotificationListener.notification)
            }, 500)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        timer?.cancel()
        mTimer?.cancel()
        timer = null
        sharedPref = getSharedPreferences("pref", Context.MODE_PRIVATE)
        sharedPref.edit().putBoolean(IS_SERVICE_STOPPED, true).apply()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toasty.error(applicationContext, "Lyrics service has stopped").show()
        } else {
            toast.cancel()
            toast.duration = Toast.LENGTH_SHORT
            toast.setText("Lyrics service has stopped")
            toast.show()
        }
        stopForeground(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Handler(Looper.getMainLooper()).postDelayed({
                startForeground(1, MusicNotificationListener.notification)
            }, 500)
        }
        sharedPref = getSharedPreferences("pref", Context.MODE_PRIVATE)
        toast = Toast.makeText(applicationContext, "", Toast.LENGTH_SHORT)

        val track = intent?.getStringExtra(TRACK)
        val artist = intent?.getStringExtra(ARTIST)
        val album = intent?.getStringExtra(ALBUM)
        val duration = intent?.getLongExtra(DURATION, 0L)

        currentTrack = track
        currentArtist = artist

        if (NowPlayingViewModel.isInitialized()) {
            NowPlayingViewModel.instance.mTrack.postValue(track)
            NowPlayingViewModel.instance.mArtist.postValue(artist)
            NowPlayingViewModel.instance.mAlbum.postValue(album)
            NowPlayingViewModel.instance.mDuration.postValue(duration)
        }

        timer?.cancel()
        mTimer?.cancel()
        timer = null

        if (track != null) {
            val track1 = track.replace("/", "").replace("\\", "")
            val artist1 = artist?.replace("/", "")?.replace("\\", "") ?: "unknown"
            val name = "$track1 $artist1.txt"
            val lyricsFile = File(filesDir, name)
            val lyricsFileX = File(filesDir, "unSynced $name")
            val lyricsFileS = File(filesDir, "userSynced $name")

            if (lyricsFile.exists() || lyricsFileS.exists() || lyricsFileX.exists()) {
                fetchLyrics(track, artist ?: "", album ?: "", duration!!)
            } else {
                if (isOnline(this)) {
                    ///// get strings from firebase
                    if (!this::functions.isInitialized) {
                        sharedPref.edit().putBoolean(FIRST_TIME, true).apply()
                        try {
                            db = FirebaseFirestore.getInstance()

                            functions = FirebaseFunctions.getInstance("asia-south1")

                            db.collection("strings").document("lyricBoxPath")
                                .get().addOnCompleteListener {
                                    lyricBoxPath = it.result?.get("path") as String
                                }

                            db.collection("strings").document("lyricBoxElement")
                                .get().addOnCompleteListener {
                                    lyricBoxElement = it.result?.get("element") as String
                                }

                            db.collection("strings").document("queryUrl").get()
                                .addOnCompleteListener {
                                    queryUrl = it.result?.get("url") as String
                                }
                        } catch (e: com.google.android.gms.tasks.RuntimeExecutionException) {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                                Toasty.error(
                                    applicationContext,
                                    "error fetching lyrics! (slow internet connection)",
                                    Toasty.LENGTH_LONG
                                ).show()
                            } else {
                                toast.cancel()
                                toast.duration = Toast.LENGTH_LONG
                                toast.setText("error fetching lyrics! (slow internet connection)")
                                toast.show()
                            }

                            listRows.clear()
                            mixedList.clear()
                            mixedList.add(
                                LrcRow(
                                    0,
                                    "error fetching lyrics! (slow internet connection)",
                                    "00:00.00"
                                )
                            )
                            listRows.add(
                                LrcRow(
                                    0,
                                    "error fetching lyrics! (slow internet connection)",
                                    "00:00.00"
                                )
                            )
                        }
                    }

                    /////

                    if (this::functions.isInitialized) {
                        if ((artist == null || artist.toLowerCase(Locale.ROOT)
                                .contains("unknown")) &&
                            (album == null || album.toLowerCase(Locale.ROOT).contains("unknown"))
                        ) {
                            fetchLyrics(track, "", "", duration!!)
                        } else if (artist == null || artist.toLowerCase(Locale.ROOT)
                                .contains("unknown")
                        ) {
                            fetchLyrics(track, "", album!!, duration!!)
                        } else if (album == null || album.toLowerCase(Locale.ROOT)
                                .contains("unknown")
                        ) {
                            fetchLyrics(track, artist, "", duration!!)
                        } else {
                            fetchLyrics(track, artist, album, duration!!)
                        }

                    } else {
                        refreshSong()
                    }
                } else {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        Toasty.warning(applicationContext, "No internet").show()
                    } else {
                        toast.cancel()
                        toast.duration = Toast.LENGTH_SHORT
                        toast.setText("No internet")
                        toast.show()
                    }
                }
            }
        }

        val ft = sharedPref.getBoolean(FIRST_TIME, true)
        if (!ft) {
            previousTrack = currentTrack
        } else {
            if (this::queryUrl.isInitialized) {
                sharedPref.edit().putBoolean(FIRST_TIME, false).apply()
            }
        }

        return START_NOT_STICKY
    }

    private fun fetchLyrics(
        track: String,
        artist: String,
        album: String,
        duration: Long
    ) {
        if (previousTrack != currentTrack) albumArt = null

        Toasty.Config.getInstance().allowQueue(false).apply()

        val track1 = track.replace("/", "").replace("\\", "")
        val artist1 = artist.replace("/", "").replace("\\", "")
        val name = "$track1 $artist1.txt"
        val query = "$track1 $artist1"
        val lyricsFile = File(filesDir, name)
        val lyricsFileX = File(filesDir, "unSynced $name")
        val lyricsFileS = File(filesDir, "userSynced $name")

        if (npShowing) {
            if (previousTrack != currentTrack) {
                val np = NowPlayingFragment.instance
                np.album_art_iv.setImageDrawable(
                    ContextCompat.getDrawable(
                        np.requireContext(),
                        R.drawable.ic_twotone_music_note_24
                    )
                )

                if (isOnline(np.requireContext())) {
                    NowPlayingViewModel.setAlbumArt(query)
                }
            }
            NowPlayingFragment.instance.requireActivity().runOnUiThread {
                NowPlayingFragment.instance.lyrics_rv.adapter?.notifyDataSetChanged()
            }
        }


        if (sfShowing) {
            SyncFragment.instance.requireActivity().runOnUiThread {
                val sf = SyncFragment.instance
                sf.sync_rv.adapter?.notifyDataSetChanged()

                val vh = sf.sync_rv.findViewHolderForAdapterPosition(currentLine)
                val tv = vh?.itemView as TextView?

                tv?.background =
                    ContextCompat.getDrawable(sf.requireContext(), R.drawable.lyric_text_bg)
                tv?.setTextAppearance(R.style.LyricsStyleBold)
            }
        }

        if (lyricsFileS.exists()) {
            listRows.clear()
            mixedList.clear()
            synced = true
            val lyricsStr = parseLyrics(lyricsFileS)
            val rows = DefaultLrcBuilder().getLrcRows(lyricsStr)
            rows?.forEach {
                listRows.add(it)
            }
            NPRecyclerViewAdapter.prepareMixedList()

            if (previousTrack != currentTrack) {

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    Toasty.info(
                        applicationContext,
                        "Lyrics are available offline",
                        Toasty.LENGTH_SHORT
                    ).show()
                } else {
                    toast.cancel()
                    toast.duration = Toast.LENGTH_SHORT
                    toast.setText("Lyrics are available offline")
                    toast.show()
                }

                if (npShowing) {
                    NowPlayingFragment.instance.requireActivity().runOnUiThread {
                        NowPlayingFragment.instance.lyrics_rv.adapter?.notifyDataSetChanged()
                    }
                }

                if (sfShowing) {
                    SyncFragment.instance.requireActivity().runOnUiThread {
                        val sf = SyncFragment.instance
                        sf.sync_rv.adapter?.notifyDataSetChanged()

                        val vh = sf.sync_rv.findViewHolderForAdapterPosition(currentLine)
                        val tv = vh?.itemView as TextView?

                        tv?.background =
                            ContextCompat.getDrawable(sf.requireContext(), R.drawable.lyric_text_bg)
                        tv?.setTextAppearance(R.style.LyricsStyleBold)
                    }
                }
            }
            showLyrics(duration, mixedList)
        } else if (lyricsFile.exists()) {
            listRows.clear()
            mixedList.clear()
            synced = true
            val lyricsStr = parseLyrics(lyricsFile)
            val rows = DefaultLrcBuilder().getLrcRows(lyricsStr)
            rows?.forEach {
                listRows.add(it)
            }
            NPRecyclerViewAdapter.prepareMixedList()

            if (previousTrack != currentTrack) {

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    Toasty.info(
                        applicationContext,
                        "Lyrics are available offline",
                        Toasty.LENGTH_SHORT
                    ).show()
                } else {
                    toast.cancel()
                    toast.duration = Toast.LENGTH_SHORT
                    toast.setText("Lyrics are available offline")
                    toast.show()
                }

                if (npShowing) {
                    NowPlayingFragment.instance.requireActivity().runOnUiThread {
                        NowPlayingFragment.instance.lyrics_rv.adapter?.notifyDataSetChanged()
                    }
                }

                if (sfShowing) {
                    SyncFragment.instance.requireActivity().runOnUiThread {
                        val sf = SyncFragment.instance
                        sf.sync_rv.adapter?.notifyDataSetChanged()

                        val vh = sf.sync_rv.findViewHolderForAdapterPosition(currentLine)
                        val tv = vh?.itemView as TextView?

                        tv?.background =
                            ContextCompat.getDrawable(sf.requireContext(), R.drawable.lyric_text_bg)
                        tv?.setTextAppearance(R.style.LyricsStyleBold)
                    }
                }
            }
            showLyrics(duration, mixedList)

        } else if (lyricsFileX.exists()) {
            listRows.clear()
            mixedList.clear()
            synced = false
            val lyricsStr = parseLyrics(lyricsFileX)
            val rows = DefaultLrcBuilder().getLrcRows(lyricsStr)
            rows?.forEach {
                listRows.add(it)
            }
            NPRecyclerViewAdapter.prepareMixedList()

            if (previousTrack != currentTrack) {

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    Toasty.info(
                        applicationContext,
                        "Synced lyrics not available for: $track\n" +
                                "Open app to view or sync lyrics",
                        Toasty.LENGTH_LONG
                    ).show()
                } else {
                    toast.cancel()
                    toast.duration = Toast.LENGTH_LONG
                    toast.setText(
                        "Synced lyrics not available for: $track\n" +
                                "Open app to view or sync lyrics"
                    )
                    toast.show()
                }



                if (npShowing) {
                    NowPlayingFragment.instance.requireActivity().runOnUiThread {
                        NowPlayingFragment.instance.lyrics_rv.adapter?.notifyDataSetChanged()
                    }
                }

                if (sfShowing) {
                    SyncFragment.instance.requireActivity().runOnUiThread {
                        val sf = SyncFragment.instance
                        sf.sync_rv.adapter?.notifyDataSetChanged()

                        val vh = sf.sync_rv.findViewHolderForAdapterPosition(currentLine)
                        val tv = vh?.itemView as TextView?

                        tv?.background =
                            ContextCompat.getDrawable(sf.requireContext(), R.drawable.lyric_text_bg)
                        tv?.setTextAppearance(R.style.LyricsStyleBold)
                    }
                }
            }

        } else {

            var trackName1 = track.removeSpecialChars()
            if (trackName1.length > 48) trackName1 = trackName1.removeRange(48, trackName1.length)

            val trackName =
                if (track.indexOf("(") != -1) track.substring(0, track.indexOf("("))
                    .removeSpecialChars()
                else track.removeSpecialChars()

            val albumName =
                if (album.indexOf("(") != -1) album.substring(0, album.indexOf("("))
                    .removeSpecialChars()
                else album.removeSpecialChars()

            var artistName = artist.removeSpecialChars()

            if (artistName.length > 20) artistName = artistName.removeRange(20, artistName.length)

            val trackNameWords = listOfWords("$trackName1 $artistName")

            if (previousTrack != currentTrack) {
                listRows.clear()
                mixedList.clear()
                if (artist.isNotEmpty() && artist.isNotBlank() && !artist.contains("unknown")) {
                    val hexCode = sharedPref.getString("color", "#212121")
                    val color = Color.parseColor(hexCode)

                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        Toasty.custom(
                            applicationContext,
                            "Fetching Lyrics",
                            ContextCompat.getDrawable(
                                applicationContext,
                                R.drawable.ic_check_white_24dp
                            ),
                            color,
                            Color.WHITE,
                            Toasty.LENGTH_LONG,
                            false,
                            true
                        ).show()
                    } else {
                        toast.cancel()
                        toast.duration = Toast.LENGTH_LONG
                        toast.setText("Fetching Lyrics")
                        toast.show()
                    }


                    mixedList.add(LrcRow(0, "Fetching Lyrics...", "00:00.00"))
                    listRows.add(LrcRow(0, "Fetching Lyrics...", "00:00.00"))

//                    if (npShowing) {
//                        listRows.add(LrcRow(0, "Fetching Lyrics...", "00:00.00"))
//                        NowPlayingFragment.instance.lyrics_rv.adapter?.notifyDataSetChanged()
//                    }

                    try {
                        // val url = lyricUrl(trackName1.trim(), artistName.trim())
//                            val url = queryUrl + URLEncoder.encode(
//                                "${trackName1.trim()} ${artistName.trim()}",
//                                "UTF-8"
//                            )

//                            val okHttp = OkHttpClient()
//                            okHttp.setConnectTimeout(20, TimeUnit.SECONDS)
//                            val request = Request.Builder().url(url).get().build()
//                            val document =
//                                Jsoup.parse(okHttp.newCall(request).execute().body().string())

                        getId(trackName1, artistName).addOnCompleteListener {
                            if (it.isSuccessful) {
                                val map = it.result
                                val title1 = (map["title1"] as String).toLowerCase(Locale.ROOT)
                                    .removeSpecialChars()
                                val title2 = (map["title2"] as String).toLowerCase(Locale.ROOT)
                                    .removeSpecialChars()
                                val title3 = (map["title3"] as String).toLowerCase(Locale.ROOT)
                                    .removeSpecialChars()
                                val title4 = (map["title4"] as String).toLowerCase(Locale.ROOT)
                                    .removeSpecialChars()

                                val url1 = map["url1"] as String
                                val url2 = map["url2"] as String
                                val url3 = map["url1"] as String
                                val url4 = map["url4"] as String

                                val id1 = map["id1"] as String
                                val id2 = map["id2"] as String
                                val id3 = map["id3"] as String
                                val id4 = map["id4"] as String

                                val titles = arrayOf(
                                    mapOf(
                                        "t" to title1,
                                        "u" to url1,
                                        "i" to id1
                                    ), mapOf(
                                        "t" to title2,
                                        "u" to url2,
                                        "i" to id2
                                    ), mapOf(
                                        "t" to title3,
                                        "u" to url3,
                                        "i" to id3
                                    ), mapOf(
                                        "t" to title4,
                                        "u" to url4,
                                        "i" to id4
                                    )
                                )

                                var matchFound = false
                                for (title in titles) {
                                    if (searchConditions(
                                            title["t"] ?: error(""),
                                            trackName,
                                            artistName,
                                            albumName,
                                            listOfWords(title["t"] ?: error("")),
                                            trackNameWords
                                        )
                                    ) {
                                        matchFound = true
                                        Log.d(TAG, "fetchLyrics: title ---> ${title["t"]}")
                                        Log.d(TAG, "fetchLyrics: trackName ---> $trackName")
                                        getListRows(
                                            track, artist,
                                            title["u"] ?: error(""),
                                            title["i"] ?: error(""),
                                            duration,
                                            lyricsFile
                                        )
                                        break
                                    } else continue
                                }

                                if (!matchFound) {

                                    val track2 = track.replace("/", "").replace("\\", "")
                                    val artist2 = artist.replace("/", "").replace("\\", "")
                                    val name1 = "userSynced $track2 $artist2.txt"
                                    val lyFile = File(filesDir, name1)

                                    val lyricsRef =
                                        FirebaseStorage.getInstance().reference.child("userSynced/$name1")
                                    lyricsRef.getFile(lyFile).addOnSuccessListener {
                                        Log.d(TAG, "fetchLyrics: downloaded from firebase")

                                        if (currentTrack == track) {
                                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                                                Toasty.info(
                                                    applicationContext,
                                                    "Lyrics Downloaded for: $track"
                                                ).show()
                                            } else {
                                                toast.cancel()
                                                toast.duration = Toast.LENGTH_SHORT
                                                toast.setText("Lyrics Downloaded for: $track")
                                                toast.show()
                                            }
                                        }

                                        refreshSong()

                                    }.addOnFailureListener {

                                        val hexCode1 =
                                            sharedPref.getString("color", "#212121")
                                        val color1 = Color.parseColor(hexCode1)

                                        if (currentTrack == track) {
                                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                                                Toasty.custom(
                                                    applicationContext,
                                                    "Please wait ⏳",
                                                    ContextCompat.getDrawable(
                                                        applicationContext,
                                                        R.drawable.ic_check_white_24dp
                                                    ),
                                                    color1,
                                                    Color.WHITE,
                                                    Toasty.LENGTH_LONG,
                                                    false,
                                                    true
                                                ).show()
                                            } else {
                                                toast.cancel()
                                                toast.duration =
                                                    Toast.LENGTH_LONG
                                                toast.setText("Please wait ⏳")
                                                toast.show()
                                            }
                                        }

                                        fetchLyricsFromAZ(track, artist, duration)
                                    }
                                }

                            } else {
                                CoroutineScope(Main).launch {
                                    Log.e(
                                        TAG,
                                        "fetchLyrics: ${it.exception?.stackTraceToString()}"
                                    )
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                                        Toasty.info(
                                            applicationContext,
                                            "(1)Something went wrong: ${it.exception?.message}"
                                        ).show()
                                    } else {
                                        toast.cancel()
                                        toast.duration = Toast.LENGTH_SHORT
                                        toast.setText("Something went wrong: ${it.exception?.message}")
                                        toast.show()
                                    }
                                }
                            }
                        }


//                            for (i in 1..4) {
//                                val rows = getListRows(
//                                    i,
//                                    document,
//                                    track,
//                                    artist,
//                                    trackName,
//                                    trackName1,
//                                    trackNameWords,
//                                    artistName,
//                                    albumName,
//                                    lyricsFile
//                                )

//                                if (rows.isNotEmpty() && rows.size >= 10) {
//                                    synced = true
//                                    withContext(Main) {
//
//                                        rows.forEach {
//                                            listRows.add(it)
//                                        }
//                                        NPRecyclerViewAdapter.prepareMixedList()
//                                        if (currentTrack == track) {
//                                            showLyrics(duration, mixedList)
//
//                                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
//                                                Toasty.info(
//                                                    applicationContext,
//                                                    "Lyrics Downloaded for: $track"
//                                                ).show()
//                                            } else {
//                                                toast.cancel()
//                                                toast.duration = Toast.LENGTH_SHORT
//                                                toast.setText("Lyrics Downloaded for: $track")
//                                                toast.show()
//                                            }
//
//                                            if (npShowing) {
//                                                NowPlayingFragment.instance.requireActivity()
//                                                    .runOnUiThread {
//                                                        NowPlayingFragment.instance.lyrics_rv.adapter?.notifyDataSetChanged()
//                                                    }
//                                            }
//
//                                            if (sfShowing) {
//                                                SyncFragment.instance.requireActivity()
//                                                    .runOnUiThread {
//                                                        SyncFragment.instance.sync_rv.adapter?.notifyDataSetChanged()
//                                                    }
//                                            }
//                                        }
//
//                                    }
//
//                                    refreshSong()
//
//                                    break
//                                } else continue
//                            }

//                            if (mixedList.size <= 1) {
//
//                                val track2 = track.replace("/", "").replace("\\", "")
//                                val artist2 = artist.replace("/", "").replace("\\", "")
//                                val name1 = "userSynced $track2 $artist2.txt"
//                                val lyFile = File(filesDir, name1)
//
//                                val lyricsRef =
//                                    FirebaseStorage.getInstance().reference.child("userSynced/$name1")
//                                lyricsRef.getFile(lyFile).addOnSuccessListener {
//                                    Log.d(TAG, "fetchLyrics: downloaded from firebase")
//
//                                    if (currentTrack == track) {
//                                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
//                                            Toasty.info(
//                                                applicationContext,
//                                                "Lyrics Downloaded for: $track"
//                                            ).show()
//                                        } else {
//                                            toast.cancel()
//                                            toast.duration = Toast.LENGTH_SHORT
//                                            toast.setText("Lyrics Downloaded for: $track")
//                                            toast.show()
//                                        }
//                                    }
//
//                                    refreshSong()
//
//                                }.addOnFailureListener {
//
//                                    val hexCode1 = sharedPref.getString("color", "#212121")
//                                    val color1 = Color.parseColor(hexCode1)
//
//                                    if (currentTrack == track) {
//                                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
//                                            Toasty.custom(
//                                                applicationContext,
//                                                "Please wait ⏳",
//                                                ContextCompat.getDrawable(
//                                                    applicationContext,
//                                                    R.drawable.ic_check_white_24dp
//                                                ),
//                                                color1,
//                                                Color.WHITE,
//                                                Toasty.LENGTH_LONG,
//                                                false,
//                                                true
//                                            ).show()
//                                        } else {
//                                            toast.cancel()
//                                            toast.duration = Toast.LENGTH_LONG
//                                            toast.setText("Please wait ⏳")
//                                            toast.show()
//                                        }
//                                    }
//
//                                    fetchLyricsFromAZ(track, artist, duration)
//                                }
//                            }
                    } catch (e: Exception) {
                        Log.d(TAG, "fetchLyrics: ${e.stackTraceToString()}")
//                            withContext(Main) {

                        if (e is SocketTimeoutException) {

                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                                Toasty.error(
                                    applicationContext,
                                    "Server timed out (busy server or weak internet connection)"
                                ).show()
                            } else {
                                toast.cancel()
                                toast.duration = Toast.LENGTH_SHORT
                                toast.setText("Server timed out (busy server or weak internet connection)")
                                toast.show()
                            }

                            if (currentTrack == track) {
                                if (npShowing) {
                                    listRows.clear()
                                    mixedList.clear()
                                    mixedList.add(
                                        LrcRow(
                                            500,
                                            "Server timed out (busy server or weak internet connection)",
                                            "00:00.50"
                                        )
                                    )

                                    listRows.add(
                                        LrcRow(
                                            500,
                                            "Server timed out (busy server or weak internet connection)",
                                            "00:00.50"
                                        )
                                    )
                                    NowPlayingFragment.instance.lyrics_rv.adapter?.notifyDataSetChanged()
                                }
                            }

                        }
//                            }
                    }

//                    }
                } else {

                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        Toasty.info(
                            applicationContext,
                            "Incorrect or incomplete song info (If not then skip to next song and then skip to previous again)",
                            Toasty.LENGTH_LONG
                        ).show()
                    } else {
                        toast.cancel()
                        toast.duration = Toast.LENGTH_LONG
                        toast.setText("Incorrect or incomplete song info (If not then skip to next song and then skip to previous again")
                        toast.show()
                    }

                }
            }
        }
    }

//    private fun lyricUrl(track: String, artist: String): String {
//
//        var queryUrl = ""
//        db.collection("strings").document("queryUrl").get().addOnCompleteListener {
//            queryUrl = it.result?.get("url") as String
//        }.continueWith {
//            Log.d(TAG, "lyricUrl: $queryUrl")
//        }
//
//        // https://www.megalobiz.com/search/all?qry=
//
//        Log.d(TAG, "lyricUrl: returning")
//        return queryUrl + URLEncoder.encode(
//            "$track $artist",
//            "UTF-8"
//        )
//    }

    private fun writeToFile(lyrics: String?, track: String, artist: String) {
        try {
            Log.d(TAG, "writeToFile: $lyrics")
            val track1 = track.replace("/", "").replace("\\", "")
            val artist1 = artist.replace("/", "").replace("\\", "")
            val name = "$track1 $artist1.txt"
            val lyricsFile = File(filesDir, name)

            val fileWriter = FileWriter(lyricsFile)
            fileWriter.write(lyrics)
            fileWriter.flush()
            fileWriter.close()

            val lyricsRef =
                FirebaseStorage.getInstance().reference.child("lyrics/$name")
            lyricsRef.putFile(lyricsFile.toUri()).addOnSuccessListener {
                Log.d(TAG, "writeToFile: File Uploaded")
                lyricsRef.downloadUrl.addOnSuccessListener {
                    val data = hashMapOf(
                        "uri" to it.toString()
                    )
                    val db = FirebaseFirestore.getInstance()
                    db.collection("lyrics").document(name).set(data, SetOptions.merge())
                }
            }.addOnFailureListener {
                Log.d(TAG, "writeToFile: ${it.stackTraceToString()}")
            }

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun showLyrics(duration: Long, listRows1: List<Any>) {
        Log.d(TAG, "showLyrics: Called")
        Toasty.Config.getInstance().allowQueue(false).apply()

        timer?.cancel()
        mTimer?.cancel()
        timer = null
        val lyricsMap = mutableMapOf<Long, String>()
        val timeList = mutableListOf<Long?>()

        listRows1.forEach { row ->
            if (row is LrcRow) {
                val roundedTime = (row.id / 100.0).roundToLong() * 100
                lyricsMap[roundedTime] = row.content
                timeList.add(roundedTime)
            } else {
                timeList.add(null)
            }
        }

        ///////

        timer = object : CountDownTimer(duration, 100L) {

            val metrics = resources.displayMetrics
            val ratio = (metrics.heightPixels.toFloat() / metrics.widthPixels.toFloat())

            override fun onTick(millisUntilFinished: Long) {

                val position = mediaController?.playbackState?.position
                if (millisUntilFinished % 1000 < 100) {
                    if (NowPlayingViewModel.isInitialized()) {
                        NowPlayingViewModel.instance.mPosition.postValue(position)
                    }
                }

                val roundedPosition = (position?.div(100.0))?.roundToLong()?.times(100)
                val currentLy = lyricsMap[roundedPosition]
                if (currentLy != null) {
                    try {
                        if (npShowing) {
                            val np = NowPlayingFragment.instance
                            val rv = np.lyrics_rv
                            val pos = timeList.indexOf(roundedPosition)
                            val llm = rv.layoutManager as LinearLayoutManager
                            val viewHolder = rv.findViewHolderForAdapterPosition(pos)
                            val pVh = if (mixedList[pos - 1] is AdRequest) {
                                rv.findViewHolderForAdapterPosition(pos - 2)
                            } else {
                                rv.findViewHolderForAdapterPosition(pos - 1)
                            }

                            val lastVisiblePos = llm.findLastVisibleItemPosition()
                            val firstVisiblePos = llm.findFirstVisibleItemPosition()

                            if (viewHolder == null) {
                                if (ratio > 16f / 9f) {
                                    if (pos <= firstVisiblePos) {
                                        if ((pos - 3) <= 0) rv.smoothScrollToPosition(pos) else rv.smoothScrollToPosition(
                                            pos - 3
                                        )
                                    } else if (pos >= lastVisiblePos) {
                                        if ((pos + 6) >= timeList.size) rv.smoothScrollToPosition(
                                            pos
                                        ) else rv.smoothScrollToPosition(
                                            pos + 6
                                        )
                                    }
                                } else {
                                    if (pos <= firstVisiblePos) {
                                        if ((pos - 3) <= 0) rv.smoothScrollToPosition(pos) else rv.smoothScrollToPosition(
                                            pos - 3
                                        )
                                    } else if (pos >= lastVisiblePos) {
                                        if ((pos + 3) >= timeList.size) rv.smoothScrollToPosition(
                                            pos
                                        ) else rv.smoothScrollToPosition(
                                            pos + 3
                                        )
                                    }
                                }

                            }

                            val tv = viewHolder?.itemView as TextView
                            val pTv = pVh?.itemView as TextView
                            val height = tv.measuredHeight
                            if (pos >= lastVisiblePos) {
                                if (ratio > 16f / 9f) {
                                    if ((pos + 6) >= timeList.size) rv.smoothScrollToPosition(pos) else rv.smoothScrollToPosition(
                                        pos + 6
                                    )
                                } else {
                                    if ((pos + 3) >= timeList.size) rv.smoothScrollToPosition(pos) else rv.smoothScrollToPosition(
                                        pos + 3
                                    )
                                }

                                setLineBg(this@LyricsService, tv, pTv)
                            } else if (pos <= firstVisiblePos) {
                                if (ratio > 16f / 9f) {
                                    if ((pos - 4) <= 0) rv.smoothScrollToPosition(pos) else rv.smoothScrollToPosition(
                                        pos - 4
                                    )
                                } else {
                                    if ((pos - 4) <= 0) rv.smoothScrollToPosition(pos) else rv.smoothScrollToPosition(
                                        pos - 4
                                    )
                                }

                                setLineBg(this@LyricsService, tv, pTv)
                            } else if (pos >= 4) {
                                if (ratio > 16f / 9f) {
                                    val scrollY =
                                        if (pos == lastVisiblePos) height.times(4) else height
                                    rv.smoothScrollBy(0, scrollY)
                                } else {
                                    val scrollY =
                                        if (pos == lastVisiblePos) height.times(2) else height
                                    rv.smoothScrollBy(0, scrollY)
                                }


                                setLineBg(this@LyricsService, tv, pTv)
                            } else {
                                setLineBg(this@LyricsService, tv, pTv)
                            }

                        } else {
                            if (!sfShowing) {
                                val hexCode = sharedPref.getString("color", "#212121")
                                val color = Color.parseColor(hexCode)

                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                                    Toasty.custom(
                                        applicationContext,
                                        currentLy,
                                        ContextCompat.getDrawable(
                                            applicationContext,
                                            R.drawable.ic_check_white_24dp
                                        ),
                                        color,
                                        Color.WHITE,
                                        Toasty.LENGTH_SHORT,
                                        false,
                                        true
                                    ).show()
                                } else {
                                    toast.cancel()
                                    toast = Toast.makeText(
                                        applicationContext,
                                        currentLy,
                                        Toast.LENGTH_LONG
                                    )
                                    toast.show()
                                }

                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "onTick: ${e.stackTraceToString()}")
                    }
                }
            }

            override fun onFinish() {
                cancel()
            }
        }

        timer?.start()
    }

    private fun containsThreeOrMoreWords(
        titleList: MutableList<String>,
        trackList: MutableList<String>
    ): Boolean {
        var count = 0
        titleList.distinct().forEach { title ->
            trackList.distinct().forEach { track ->
                if (track != "&") {
                    if (title == track) {
                        count++
                    }
                }

            }
        }

        Log.d(TAG, "containsThreeOrMoreWords: $titleList")
        Log.d(TAG, "containsThreeOrMoreWords: $trackList")
        Log.d(TAG, "containsThreeOrMoreWords: $count")

        if (titleList.contains("kick") && titleList.contains("dialogue")) return false
        return if (titleList.size >= 6 || trackList.size >= 6) {
            count >= 4
        } else {
            count >= 3
        }
    }

    private fun getListRows(
//        resultNumber: Int,
//        document: Document,
        track: String,
        artist: String,
        url: String,
        id: String,
        duration: Long,
//        trackName: String,
//        trackName1: String,
//        trackNameWords: MutableList<String>,
//        artistName: String,
//        albumName: String,
        lyricsFile: File
    ) {

//        val rows = mutableListOf<LrcRow>()


//            val baseUrl = "https://www.megalobiz.com"
//
//            //// lyricBoxPath --> #list_entity_container > div:nth-child($resultNumber)
//
//            //// lyricBoxElement --> a
//
//            Log.d(TAG, "getListRows: ${document.title()}")
//
//            val lyricBox =
//                document.select(lyricBoxPath.replace("?", "$resultNumber"))[0].select(
//                    lyricBoxElement
//                )
//            val href = lyricBox.attr("href")
//            val lyricId = lyricBox.attr("id")
//
//            val title = lyricBox.attr("name").toLowerCase(Locale.ROOT).removeSpecialChars()
//            val titleWords = listOfWords(title)
//
//
//            Log.d(TAG, "getListRows: search trackName1 --> $trackName1")
//            Log.d(TAG, "getListRows: search title --> $title")
//            Log.d(TAG, "getListRows: trackName --> $trackName")
//            Log.d(TAG, "getListRows: artistName --> $artistName")
//            Log.d(TAG, "getListRows: albumName --> $albumName")
//
//            if (searchConditions(
//                    title,
//                    trackName,
//                    artistName,
//                    albumName,
//                    titleWords,
//                    trackNameWords
//                )
//            ) {
//
//                val okHttp = OkHttpClient()
//                okHttp.setConnectTimeout(20, TimeUnit.SECONDS)
//                val request = Request.Builder().url(baseUrl + href).get().build()
//                val lyricsDoc = Jsoup.parse(okHttp.newCall(request).execute().body().string())
//
////                val lyricsDoc =
////                    Jsoup.connect(baseUrl + href).userAgent("Mozilla/5.0").timeout(0).get()
//
//                var temp = lyricsDoc.select("#lrc_" + lyricId + "_lyrics")[0].toString()
//
//                temp = temp.replace("(?i)<br[^>]*>", "br2n")
//                temp = temp.replace("]", "]")
//                temp = temp.replace("\\[", "[")


//                var lyrics = Jsoup.parse(temp).text()

//

//
//                Log.d(TAG, "getListRows: --->  $sb")

        getLyrics(url, id).addOnCompleteListener {
            if (it.isSuccessful) {
                val map2 = it.result
                var lyrics = map2["lyrics"] as String

                lyrics = lyrics.replace(":0.", ":00.")
                lyrics = lyrics.replace(".0]", ".00]")

                val lines = lyrics.lines()
                val sb = StringBuilder()
                lines.forEach {
                    if (it.indexOf("[") != 0 || it.indexOf("]") != 9) {
                        val content = it.replace("[", "🎵").replace("]", "🎵")
                        sb.append("$content\n")
                    } else if (!it.contains(":") && !it.contains(".")) {
                        val content = it.replace("[", "🎵").replace("]", "🎵")
                        sb.append("$content\n")
                    } else {
                        sb.append("$it\n")
                    }
                }

                Log.d(TAG, "getListRows: $sb")

                writeToFile(sb.toString(), track, artist)
                val lyricsStr = parseLyrics(lyricsFile)
                val mRows = DefaultLrcBuilder().getLrcRows(lyricsStr)
//                    mRows?.forEach {row ->
//                        rows.add(row)
//                    }

                if (mRows?.isNotEmpty() == true && mRows.size >= 10) {
                    synced = true

                    CoroutineScope(Main).launch {
                        mRows.forEach { row ->
                            listRows.add(row)
                        }
                        NPRecyclerViewAdapter.prepareMixedList()
                        if (currentTrack == track) {
                            showLyrics(duration, mixedList)

                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                                Toasty.info(
                                    applicationContext,
                                    "Lyrics Downloaded for: $track"
                                ).show()
                            } else {
                                toast.cancel()
                                toast.duration = Toast.LENGTH_SHORT
                                toast.setText("Lyrics Downloaded for: $track")
                                toast.show()
                            }

                            if (npShowing) {
                                NowPlayingFragment.instance.requireActivity()
                                    .runOnUiThread {
                                        NowPlayingFragment.instance.lyrics_rv.adapter?.notifyDataSetChanged()
                                    }
                            }

                            if (sfShowing) {
                                SyncFragment.instance.requireActivity()
                                    .runOnUiThread {
                                        SyncFragment.instance.sync_rv.adapter?.notifyDataSetChanged()
                                    }
                            }
                        }
                    }

                    refreshSong()
                }
            } else {
                CoroutineScope(Main).launch {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        Toasty.info(
                            applicationContext,
                            "(2)Something went wrong: ${it.exception?.message}"
                        ).show()
                    } else {
                        toast.cancel()
                        toast.duration = Toast.LENGTH_SHORT
                        toast.setText("Something went wrong: ${it.exception?.message}")
                        toast.show()
                    }
                }
            }
        }


//            }


//        return rows
    }

    private fun getId(track: String, artist: String): Task<Map<String, Any>> {
        val data = hashMapOf(
            "mTrack" to track,
            "mArtist" to artist
        )

        return functions
            .getHttpsCallable("getId")
            .call(data)
            .continueWith { task ->
                task.result?.data as Map<String, Any>?
            }
    }

    private fun getLyrics(lyricUrl: String, id: String): Task<Map<String, Any>> {
        val data = hashMapOf(
            "lyricUrl" to lyricUrl,
            "id" to id
        )

        return functions
            .getHttpsCallable("getLyrics")
            .call(data)
            .continueWith { task ->
                task.result?.data as Map<String, Any>?
            }
    }

    private fun searchConditions(
        title: String,
        trackName: String,
        artistName: String,
        albumName: String,
        titleWords: MutableList<String>,
        trackNameWords: MutableList<String>
    ): Boolean {
        return (title.contains(trackName) && title.contains(artistName, true)) ||
                (if (albumName.isNotEmpty()) {
                    (title.contains(trackName) && title.contains(albumName, true))
                } else false) ||
                (containsThreeOrMoreWords(titleWords, trackNameWords)) ||
                (title.contains(trackName.firstTwoWords()) && title.contains(artistName.firstWord())) ||
                (title.contains(trackName.firstTwoWords()) && title.contains(artistName.firstTwoWords())) ||
                (if (trackName.isOneWord()) {
                    if (listOfWords(title).size <= 4) {
                        (title.firstWord() == trackName && title.contains(artistName.firstWord()))
                    } else false
                } else false)
    }

    private fun fetchLyricsFromAZ(
        track: String,
        artist: String,
        duration: Long
    ) {
        Log.d(TAG, "fetchLyricsFromAZ: called")

        val track1 = track.replace("/", "").replace("\\", "")
        val artist1 = artist.replace("/", "").replace("\\", "")
        val name = "$track1 $artist1.txt"
        val lyricsFileX = File(filesDir, "unSynced $name")


        if (artist.isNotEmpty() && artist.isNotBlank() && !artist.contains("unknown")) {

            CoroutineScope(IO).launch {

                try {

                    val rows = getListRowsAz(
                        track,
                        artist,
                        lyricsFileX
                    )

                    listRows.clear()
                    mixedList.clear()
                    for (i in 0 until rows.size) {
                        listRows.add(rows[i])
                    }
                    NPRecyclerViewAdapter.prepareMixedList()

                    if (mixedList.isNotEmpty()) {
                        synced = false
                        withContext(Main) {

                            if (currentTrack == track) {
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                                    Toasty.info(
                                        applicationContext,
                                        "Synced lyrics not available for: $track\n" +
                                                "Open app to view or sync lyrics",
                                        Toasty.LENGTH_LONG
                                    ).show()
                                } else {
                                    toast.cancel()
                                    toast.duration = Toast.LENGTH_LONG
                                    toast.setText(
                                        "Synced lyrics not available for: $track\n" +
                                                "Open app to view or sync lyrics"
                                    )
                                    toast.show()
                                }
                            }

                            refreshSong()

                        }

                        withContext(Main) {
                            mTimer = object : CountDownTimer(duration, 1000L) {
                                override fun onTick(millisUntilFinished: Long) {
                                    val position = mediaController?.playbackState?.position
                                    if (NowPlayingViewModel.isInitialized()) {
                                        NowPlayingViewModel.instance.mPosition.postValue(
                                            position
                                        )
                                    }
                                }

                                override fun onFinish() {
                                    cancel()
                                }
                            }
                            mTimer?.start()
                        }

                        if (currentTrack == track) {
                            if (npShowing) {
                                NowPlayingFragment.instance.requireActivity().runOnUiThread {
                                    NowPlayingFragment.instance.lyrics_rv.adapter?.notifyDataSetChanged()
                                }
                            }
                        }


                    } else {
                        withContext(Main) {

                            if (currentTrack == track) {
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                                    Toasty.error(
                                        applicationContext,
                                        "Lyrics not found for: $track !"
                                    )
                                        .show()
                                } else {
                                    toast.cancel()
                                    toast.duration = Toast.LENGTH_SHORT
                                    toast.setText("Lyrics not found for: $track !")
                                    toast.show()
                                }


                                if (npShowing) {
                                    listRows.clear()
                                    mixedList.clear()
                                    mixedList.add(
                                        LrcRow(
                                            500,
                                            "Lyrics not found for: $track !",
                                            "00:00.50"
                                        )
                                    )

                                    listRows.add(
                                        LrcRow(
                                            500,
                                            "Lyrics not found for: $track !",
                                            "00:00.50"
                                        )
                                    )
                                    NowPlayingFragment.instance.lyrics_rv.adapter?.notifyDataSetChanged()
                                }
                            }

                        }
                    }

                } catch (e: Exception) {
                    Log.d(TAG, "fetchLyricsFromAZ: ${e.stackTraceToString()}")
                    withContext(Main) {

                        if (e is SocketTimeoutException) {

                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                                Toasty.error(
                                    applicationContext,
                                    "Server timed out (busy server or weak internet connection)"
                                ).show()
                            } else {
                                toast.cancel()
                                toast.duration = Toast.LENGTH_SHORT
                                toast.setText("Server timed out (busy server or weak internet connection)")
                                toast.show()
                            }

                            if (npShowing) {
                                listRows.clear()
                                mixedList.clear()
                                mixedList.add(
                                    LrcRow(
                                        500,
                                        "Server timed out (busy server or weak internet connection)",
                                        "00:00.50"
                                    )
                                )

                                listRows.add(
                                    LrcRow(
                                        500,
                                        "Server timed out (busy server or weak internet connection)",
                                        "00:00.50"
                                    )
                                )
                                NowPlayingFragment.instance.lyrics_rv.adapter?.notifyDataSetChanged()
                            }
                        }
                    }
                }

            }
        }

    }

    private fun getListRowsAz(
        track: String,
        artist: String,
        lyricsFileX: File,
    ): MutableList<LrcRow> {
        Log.d(TAG, "getListRowsAz: called")

        val track1 = track.replace("/", "").replace("\\", "")
        val artist1 = artist.replace("/", "").replace("\\", "")
        val query = "$track1 $artist1"

        val rows = mutableListOf<LrcRow>()
        val client = OkHttpClient()
        val request: Request = Request.Builder()
            .url("https://genius.p.rapidapi.com/search?q=$query")
            .get()
            .addHeader("x-rapidapi-key", "1d29c431f2mshd1d882586bf4547p18f81fjsn7941d721e540")
            .addHeader("x-rapidapi-host", "genius.p.rapidapi.com")
            .build()

        runBlocking {
            CoroutineScope(IO).launch {
                for (i in 0..3) {
                    Log.d(TAG, "getListRowsAz: coroutine launch")
                    val response: Response = client.newCall(request).execute()
                    try {
                        val jsonArray = JSONObject(response.body().string())
                            .getJSONObject("response")
                            .get("hits") as JSONArray

                        val result = jsonArray.getJSONObject(0).getJSONObject("result")
                        val lyricsPath = result.getString("path")
                        val geniusLink = "https://genius.com$lyricsPath"

                        val okHttp = OkHttpClient()
                        okHttp.setConnectTimeout(20, TimeUnit.SECONDS)
                        val request = Request.Builder().url(geniusLink).get().build()
                        val document =
                            Jsoup.parse(okHttp.newCall(request).execute().body().string())

                        val title = document.title().removeSpecialChars()
                        val mTrack = track.removeSpecialChars()
                        val mArtist = artist.removeSpecialChars()
                        if (title.contains(mTrack, true) && title.contains(mArtist)) {
                            Log.d(TAG, "geniusLyrics: ${document.title()}")

                            val lyricsElement =
                                document.getElementsByAttributeValue("class", "lyrics")[0]

                            var temp = lyricsElement.toString()
                            temp = temp.substring(temp.indexOf("<p>") + 3, temp.indexOf("</p>"))
                            temp = temp.replace("<br>", "\n")
                            temp = temp.replace("[", "🎵").replace("]", "🎵")
                            if (temp.indexOf("<a href") != -1) {
                                (0..30).forEach { _ ->
                                    if (temp.indexOf("<a href") != -1) {
                                        temp = temp.removeRange(
                                            temp.indexOf("<a href=\"/"),
                                            temp.indexOf("</a>") + 4
                                        )
                                    }
                                }
                            }

                            val lines = temp.lines()
                            val sb = StringBuilder()
                            lines.forEach {
                                val content = it.replace("[", "🎵").replace("]", "🎵")
                                sb.append("$content\n")
                            }

                            /////////////
                            writeToFileAz(sb.toString(), track, artist)
                            val lyricsStr = parseLyrics(lyricsFileX)
                            val mRows = DefaultLrcBuilder().getLrcRows(lyricsStr)

                            mRows?.forEach {
                                rows.add(it)
                            }
                            break
                        } else {
                            break
                        }

                    } catch (e: Exception) {
                        eCount++
                        if (e is IndexOutOfBoundsException && eCount < 4) {
                            Log.d(TAG, "getListRowsAz: $eCount --> ${e.stackTraceToString()}")
                            continue
                        } else {
                            Log.e(TAG, "getListRowsAz: ${e.stackTraceToString()}")
                            eCount = 0
                            break
                        }
                    }
                }

            }.join()
        }

        Log.d(TAG, "getListRowsAz: returning")
        return rows
    }

    private fun writeToFileAz(lyrics: String?, track: String, artist: String) {
        try {
            val track1 = track.replace("/", "").replace("\\", "")
            val artist1 = artist.replace("/", "").replace("\\", "")
            val name = "unSynced $track1 $artist1.txt"
            val lyricsFile = File(filesDir, name)

            val fileWriter = FileWriter(lyricsFile)
            fileWriter.write(lyrics)
            fileWriter.flush()
            fileWriter.close()

            val lyricsRef =
                FirebaseStorage.getInstance().reference.child("unSynced/$name")
            lyricsRef.putFile(lyricsFile.toUri()).addOnSuccessListener {
                Log.d(TAG, "writeToFileAz: File Uploaded from az")
                lyricsRef.downloadUrl.addOnSuccessListener {
                    val data = hashMapOf("uri" to it.toString())
                    val db = FirebaseFirestore.getInstance()
                    db.collection("unSynced").document(name).set(data, SetOptions.merge())
                }
            }.addOnFailureListener {
                Log.d(TAG, "writeToFileAz: ${it.stackTraceToString()}")
            }

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun setLineBg(context: Context, tv: TextView, pTv: TextView) {
        pTv.setBackgroundResource(0)
        pTv.setTextAppearance(R.style.LyricsStyleNormal)

        tv.background =
            ContextCompat.getDrawable(
                context,
                R.drawable.lyric_text_bg
            )
        tv.setTextAppearance(R.style.LyricsStyleBold)
    }

}

fun String.removeSpecialChars(): String {
    return this.replace("(", "")
        .replace(")", "")
        .replace("\"", "")
        .replace("/", "")
        .replace("\\", "")
        .replace(".", "")
        .replace(",", "")
        .replace(":", "")
        .replace(";", " ")
        .replace("-", " ")
        .replace("!", "")
        .replace("[", "")
        .replace("]", "")
        .replace("|", "")
        .replace("_", " ")
        .replace("'", "")
        .replace("Feat", "", true)
        .replace("Extended", "", true)
        .replace("version", "", true)
        .replace("official music video", "", true)
        .replace("bhaari", "bhari", true)
        .toLowerCase(Locale.ROOT)
        .trim()

}

fun String.firstTwoWords(): String {
    val i = this.indexOf(" ", this.indexOf(" ") + 1)
    return if (i != -1) this.substring(0, i) else this.substring(0, this.length)
}

fun String.firstWord(): String {
    val i = this.indexOf(" ")
    return if (i != -1) this.substring(0, i) else this
}

fun String.isOneWord(): Boolean {
    return this.indexOf(" ") == -1
}

fun parseLyrics(file: File): String {
    val list = file.bufferedReader().readLines()
    val lyrics = StringBuilder()
    list.forEach {
        lyrics.append("$it\n")
    }

    return lyrics.toString()
}

fun listOfWords(str: String): MutableList<String> {
    val sb = StringBuilder()
    val list = mutableListOf<String>()
    var count = 0
    str.forEach {
        if (!it.isWhitespace()) {
            count++
            sb.append(it.toLowerCase())
            if (count == str.length) list.add(sb.toString())
        } else if (it.isWhitespace()) {
            if (sb.isNotEmpty()) list.add(sb.toString())
            sb.setLength(0)
        }
    }

    if (str.lastIndexOf(" ") != -1) {
        val lastWord = str.substring(str.lastIndexOf(" ") + 1).toLowerCase(Locale.ROOT)
        list.add(lastWord)
    }
    return list
}

@RequiresApi(Build.VERSION_CODES.M)
fun isOnline(context: Context): Boolean {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val capabilities =
        connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
    if (capabilities != null) {
        when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                Log.i("Internet", "NetworkCapabilities.TRANSPORT_CELLULAR")
                return true
            }

            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                Log.i("Internet", "NetworkCapabilities.TRANSPORT_WIFI")
                return true
            }

            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                Log.i("Internet", "NetworkCapabilities.TRANSPORT_ETHERNET")
                return true
            }
        }
    }
    return false
}


