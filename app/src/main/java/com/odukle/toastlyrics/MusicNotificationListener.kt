package com.odukle.toastlyrics

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.MediaMetadata.*
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import com.odukle.toastlyrics.ui.home.IS_SERVICE_STOPPED
import com.odukle.toastlyrics.ui.now_playing.NowPlayingFragment
import com.odukle.toastlyrics.ui.now_playing.NowPlayingViewModel
import com.odukle.toastlyrics.ui.now_playing.npShowing
import es.dmoral.toasty.Toasty

private const val TAG = "MusicNotification"
var mediaController: MediaController? = null
var serviceRunning = false

class MusicNotificationListener : NotificationListenerService() {

    private lateinit var sharedPref: SharedPreferences
    private lateinit var notificationManager: NotificationManager
    private lateinit var audioManager: AudioManager

    init {
        instance = this
    }

    override fun onCreate() {
        super.onCreate()
        sharedPref = getSharedPreferences("pref", Context.MODE_PRIVATE)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        notification = MainActivity.getNotification(this, notificationManager)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ss = sharedPref.getBoolean(IS_SERVICE_STOPPED, false)
            if (!ss) startForeground(1, notification)
        }
    }

    companion object {
        lateinit var instance: MusicNotificationListener
        lateinit var notification: Notification
        var appsArray = arrayListOf(
            "Spotify",
            "Youtube Music (Premium)",
            "Youtube Music (Vanced)",
            "Amazon Music",
            "Google Play Music",
            "Gaana",
            "Hungama Music",
            "MusixMatch",
            "Apple Music",
            "SoundCloud",
            "Tidal",
            "Deezer",
            "Resso",
            "Jio Music",
            "Pandora",
            "Last FM",
            "MIUI Music Player",
            "HTC Music",
            "Sony Music Player",
            "Oppo Music Player",
            "Vivo Music Player",
            "PowerAmp",
            "Samsung Music PLayer",
            "LG Music Player",
            "Napster",
            "Apollo Music Player",
            "Winamp",
            "Jet Audio Player",
            "OTO Music",
            "Nell Music Player",
            "Black Player",
            "Vinyl",
            "Phonograph",
            "Lark Music Player",
            "And More..."
        )

        fun terminateService() {
            Log.d(TAG, "terminateService: called")
            instance.onDestroy()
        }

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        notification = MainActivity.getNotification(this, notificationManager)
        startForeground(1, notification)
        Toasty.Config.getInstance().allowQueue(false).apply()

        if (!serviceRunning) {

            Handler(Looper.getMainLooper()).postDelayed({
                if (Build.VERSION.SDK_INT < 28) {
                    notificationManager.activeNotifications.forEach {
                        matchCodeAndStartLS(it)
                    }
                } else {
                    this.activeNotifications.forEach {
                        matchCodeAndStartLS(it)
                    }
                }

            }, 300)

//            refreshSong(audioManager)
            if (NotificationManagerCompat.getEnabledListenerPackages(this)
                    .contains(this.packageName)
            ) {
                Toasty.success(applicationContext, "Lyrics service has started").show()
            } else {
                Toasty.error(
                    applicationContext,
                    "Please allow notification access",
                    Toasty.LENGTH_LONG
                ).show()
            }
            serviceRunning = true
        }
        sharedPref = getSharedPreferences("pref", Context.MODE_PRIVATE)
        sharedPref.edit().putBoolean(IS_SERVICE_STOPPED, false).apply()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Toasty.Config.getInstance().allowQueue(false).apply()
        sharedPref = getSharedPreferences("pref", Context.MODE_PRIVATE)
        serviceRunning = false

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toasty.error(applicationContext, "Lyrics service has stopped").show()
        } else {
            Toast.makeText(applicationContext, "Lyrics service has stopped", Toast.LENGTH_SHORT)
                .show()
        }
        if (LyricsService.isInitialized()) LyricsService.terminateService()
        sharedPref.edit().putBoolean(IS_SERVICE_STOPPED, true).apply()
        stopForeground(true)
    }

    private object ApplicationPackageNames {
        const val SPOTIFY_LITE_PACK_NAME = "com.spotify.lite"
        const val POWER_AMP_PACK_NAME = "com.maxmpz.audioplayer"
        const val YOUTUBE_PACK_NAME = "com.google.android.apps.youtube.music"
        const val YOUTUBE_VANCED_MUSIC = "com.vanced.android.apps.youtube.music"
        const val JR_STUDIO = "com.jrtstudio.music"
        const val ANDROID_MUSIC = "com.android.music"
        const val HTC_MUSIC = "com.htc.music"
        const val AUDIOMACK = "com.audiomack"
        const val LAST_FM = "fm.last.android"
        const val MIUI_MUSIC = "com.miui.player"
        const val REAL_PLAYER = "com.real.IMP"
        const val SAMASUNG_MUSIC = "com.samsung.MusicPlayer"
        const val SAMSUNG_SEC = "com.samsung.sec"
        const val SAMSUNG_PLAYER = "com.samsung.music"
        const val SAMSUNG_SEC_PLAYER = "com.samsung.sec.android.MusicPlayer"
        const val LG_MUSIC = "com.lge.music"
        const val SAMSUNG_SEC_SEC = "com.sec.android.app.music"
        const val NAPSTER = "com.rhapsody.napster"
        const val APOLLO = "com.andrew.apollo"
        const val AMAZON_MUSIC = "com.amazon.mp3"
        const val WINAMP = "com.nullsoft.winamp"
        const val JETAUDIO = "com.jetappfactory.jetaudio"
        const val JETAUDIO_PLUS = "com.jetappfactory.jetaudioplus"
        const val D_TWIST = "com.doubleTwist.androidPlayer"
        const val PLAYER_PRO = "com.tbig.playerpro"
        const val GOOGLE_MUSIC = "com.google.android.music"
        const val OTO_MUSIC = "com.piyush.music"
        const val M_PLAYER = "com.shaiban.audioplayer.mplayer"
        const val SOFT_PLAYER = "xsoftstudio.musicplayer.pro"
        const val POWERAMP_PI = "power.amp.musicplayer.pi.audioplayer"
        const val MUSIC_PRO = "music.audio.musicplayer.pro"
        const val I_POD = "com.jamesob.ipod"
        const val NELL = "com.shahin.nell.musicplayer"
        const val VMONS = "com.vmons.mediaplayer.music"
        const val BLACK_P = "com.musicplayer.blackplayerfree"
        const val GITHUB_P = "qijaz221.github.io.musicplayer"
        const val VINYL = "com.poupa.vinylmusicplayer"
        const val ANOTHER = "another.music.player"
        const val BLACK_PLAYER = "com.kodarkooperativet.blackplayerex"
        const val SONY_MUSIC = "com.sonyericsson.music"
        const val LG_MUSIC_FLOW = "com.lge.media.musicflow"
        const val OPPO = "com.oppo.music"
        const val VIVO = "com.android.bbkmusic"
        const val MEDIA_CENTER = "com.android.mediacenter"
        const val PHONOGRAPH = "com.kabouzeid.gramophone"

        const val BSB = "com.bsbportal.music"
        const val SPOTIFY_MAIN = "com.spotify.music"
        const val GAANA = "com.gaana"
        const val STREAM_HUB = "com.streamhub"
        const val HUNGAMA = "com.hungama.myplay.activity"
        const val MUSIXMATCH = "com.musixmatch.android.lyrify"
        const val APPLE = "com.apple.android.music"
        const val LARK = "com.dywx.larkplayer"
        const val SOUNDCLOUD = "com.soundcloud.android"
        const val ASPIRO = "com.aspiro.tidal"
        const val DEEZER = "deezer.android.app.nobilling"
        const val RESSO = "com.moonvideo.android.resso"
        const val JIO = "com.jio.media.jiobeats"
        const val GAANA_PUN = "com.gaana.punjabisongs"
        const val GAANA_TELUGU = "com.gaana.telugusongs"
        const val GAANA_HINDI = "com.gaana.lovesongshindi"
        const val GAANA_TAMIL = "com.gaana.tamilsongs"
        const val PANDORA = "free.pandora.music"
    }

    object InterceptedNotificationCode {
        const val SPOTIFY_CODE = 1
        const val YOUTUBE_CODE = 2
        const val POWER_AMP_CODE = 3
        const val YOUTUBE_VANCED_MUSIC_CODE = 4
        const val JR_STUDIO_CODE = 5
        const val ANDROID_MUSIC_CODE = 6
        const val HTC_MUSIC_CODE = 7
        const val AUDIOMACK_CODE = 8
        const val LAST_FM_CODE = 9
        const val MIUI_MUSIC_CODE = 10
        const val REAL_PLAYER_CODE = 11
        const val SAMSUNG_MUSIC_CODE = 12
        const val SAMSUNG_SEC_CODE = 13
        const val SAMSUNG_PLAYER_CODE = 14
        const val SAMSUNG_SEC_PLAYER_CODE = 15
        const val LG_MUSIC_CODE = 16
        const val SAMSUNG_SEC_SEC_CODE = 17
        const val NAPSTER_CODE = 18
        const val APOLLO_CODE = 19
        const val AMAZON_MUSIC_CODE = 20
        const val WINAMP_CODE = 21
        const val JETAUDIO_CODE = 22
        const val JETAUDIO_PLUS_CODE = 23


        const val D_TWIST_CODE = 24
        const val PLAYER_PRO_CODE = 25
        const val GOOGLE_MUSIC_CODE = 26
        const val OTO_MUSIC_CODE = 27
        const val M_PLAYER_CODE = 28
        const val SOFT_PLAYER_CODE = 29
        const val POWERAMP_PI_CODE = 30
        const val MUSIC_PRO_CODE = 31
        const val I_POD_CODE = 32
        const val NELL_CODE = 33
        const val VMONS_CODE = 34
        const val BLACK_P_CODE = 35
        const val GITHUB_P_CODE = 36
        const val VINYL_CODE = 37
        const val ANOTHER_CODE = 38
        const val BLACK_PLAYER_CODE = 39
        const val SONY_MUSIC_CODE = 40
        const val LG_MUSIC_FLOW_CODE = 41
        const val OPPO_CODE = 42
        const val VIVO_CODE = 43
        const val MEDIA_CENTER_CODE = 44

        /////
        const val BSB_CODE = 45
        const val SPOTIFY_MAIN_CODE = 46
        const val GAANA_CODE = 47
        const val STREAM_HUB_CODE = 48
        const val HUNGAMA_CODE = 49
        const val MUSIXMATCH_CODE = 50
        const val APPLE_CODE = 51
        const val LARK_CODE = 52
        const val SOUNDCLOUD_CODE = 53
        const val ASPIRO_CODE = 54
        const val DEEZER_CODE = 55
        const val RESSO_CODE = 56
        const val JIO_CODE = 57
        const val GAANA_PUN_CODE = 58
        const val GAANA_TELUGU_CODE = 59
        const val GAANA_HINDI_CODE = 60
        const val GAANA_TAMIL_CODE = 61
        const val PANDORA_CODE = 62

        /////
        const val PHONOGRAPH_CODE = 63

        const val OTHER_NOTIFICATIONS_CODE = 999 // We ignore all notification with code == 999
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        Handler(Looper.getMainLooper()).postDelayed({
            matchCodeAndStartLS(sbn!!)
        }, 300)

    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val notificationCode = matchNotificationCode(sbn!!)
        if (notificationCode != InterceptedNotificationCode.OTHER_NOTIFICATIONS_CODE) {
            val activeNotifications = this.activeNotifications
            if (activeNotifications != null && activeNotifications.isNotEmpty()) {
                for (i in activeNotifications.indices) {
                    if (notificationCode == matchNotificationCode(activeNotifications[i])) {
                        val intent = Intent()
                        intent.putExtra("Notification Code", notificationCode)
                        Log.d(TAG, "onNotificationRemoved: Sending Intent")
                        sendBroadcast(intent)
                        break
                    }
                }
            }
        }
    }

    private fun matchCodeAndStartLS(sbn: StatusBarNotification) {
        val notificationCode: Int = matchNotificationCode(sbn)
        if (notificationCode != InterceptedNotificationCode.OTHER_NOTIFICATIONS_CODE) {

            sharedPref = getSharedPreferences("pref", Context.MODE_PRIVATE)

            try {
                mediaController = MediaController(
                    applicationContext,
                    sbn.notification?.extras?.get("android.mediaSession") as MediaSession.Token
                )
            } catch (e: Exception) {
//                if (e is NullPointerException) {
//                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
//                        Toasty.info(
//                            applicationContext,
//                            "Your music app needs to display a notification that shows track and artist name for Toast Lyrics to work",
//                            Toasty.LENGTH_LONG
//                        ).show()
//                    } else {
//                        Toast.makeText(
//                            applicationContext,
//                            "Your music app needs to display a notification that shows track and artist name for Toast Lyrics to work",
//                            Toast.LENGTH_LONG
//                        ).show()
//                    }
//                } else {
//                    Log.d(TAG, "matchCodeAndStartLS: ${e.stackTraceToString()}")
//                    Toast.makeText(applicationContext, "${e.message}", Toast.LENGTH_LONG).show()
//                }
            }

            val mediaController = mediaController
            if (mediaController != null) {

                val metadata = mediaController.metadata
                val track = metadata!!.getString(METADATA_KEY_TITLE)
                val artist = metadata.getString(METADATA_KEY_ARTIST)
                val album = metadata.getString(METADATA_KEY_ALBUM)
                val duration = metadata.getLong(METADATA_KEY_DURATION)

                val intent = Intent(this, LyricsService::class.java).apply {
                    putExtra(TRACK, track)
                    putExtra(ARTIST, artist)
                    putExtra(DURATION, duration)
                    putExtra(ALBUM, album)
                }

                Log.d(TAG, "onNotificationPosted: attempting to start lyrics service")
                val ss = sharedPref.getBoolean(IS_SERVICE_STOPPED, false)
                if (!ss) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                } else {
                    if (NowPlayingViewModel.isInitialized()) {
                        NowPlayingViewModel.instance.mTrack.postValue(track)
                        NowPlayingViewModel.instance.mArtist.postValue(artist)
                        NowPlayingViewModel.instance.mAlbum.postValue(album)
                        NowPlayingViewModel.instance.mDuration.postValue(duration)
                    }

                    listRows.clear()
                    mixedList.clear()
                    mixedList.add(LrcRow(0, "Lyrics Service is not running", "00:00.00"))
                    listRows.add(LrcRow(0, "Lyrics Service is not running", "00:00.00"))

                    if (npShowing) {
                        val np = NowPlayingFragment.instance
                        if (isOnline(np.requireContext())) {
                            val track1 = track.replace("/", "").replace("\\", "")
                            val artist1 = artist.replace("/", "").replace("\\", "")
                            val query = "$track1 $artist1"

                            NowPlayingViewModel.setAlbumArt(query)
                        }
                    }

                }
            }
        }
    }

    private fun matchNotificationCode(sbn: StatusBarNotification): Int {
        return when (sbn.packageName) {
            ApplicationPackageNames.SPOTIFY_LITE_PACK_NAME -> {
                InterceptedNotificationCode.SPOTIFY_CODE
            }
            ApplicationPackageNames.YOUTUBE_PACK_NAME -> {
                InterceptedNotificationCode.YOUTUBE_CODE
            }
            ApplicationPackageNames.POWER_AMP_PACK_NAME -> {
                InterceptedNotificationCode.POWER_AMP_CODE
            }
            ApplicationPackageNames.YOUTUBE_VANCED_MUSIC -> {
                InterceptedNotificationCode.YOUTUBE_VANCED_MUSIC_CODE
            }
            ApplicationPackageNames.JR_STUDIO -> {
                InterceptedNotificationCode.JR_STUDIO_CODE
            }
            ApplicationPackageNames.ANDROID_MUSIC -> {
                InterceptedNotificationCode.ANDROID_MUSIC_CODE
            }
            ApplicationPackageNames.HTC_MUSIC -> {
                InterceptedNotificationCode.HTC_MUSIC_CODE
            }
            ApplicationPackageNames.AUDIOMACK -> {
                InterceptedNotificationCode.AUDIOMACK_CODE
            }
            ApplicationPackageNames.LAST_FM -> {
                InterceptedNotificationCode.LAST_FM_CODE
            }
            ApplicationPackageNames.MIUI_MUSIC -> {
                InterceptedNotificationCode.MIUI_MUSIC_CODE
            }
            ApplicationPackageNames.REAL_PLAYER -> {
                InterceptedNotificationCode.REAL_PLAYER_CODE
            }
            ApplicationPackageNames.SAMASUNG_MUSIC -> {
                InterceptedNotificationCode.SAMSUNG_MUSIC_CODE
            }
            ApplicationPackageNames.SAMSUNG_SEC -> {
                InterceptedNotificationCode.SAMSUNG_SEC_CODE
            }
            ApplicationPackageNames.SAMSUNG_PLAYER -> {
                InterceptedNotificationCode.SAMSUNG_PLAYER_CODE
            }
            ApplicationPackageNames.SAMSUNG_SEC_PLAYER -> {
                InterceptedNotificationCode.SAMSUNG_SEC_PLAYER_CODE
            }
            ApplicationPackageNames.LG_MUSIC -> {
                InterceptedNotificationCode.LG_MUSIC_CODE
            }
            ApplicationPackageNames.SAMSUNG_SEC_SEC -> {
                InterceptedNotificationCode.SAMSUNG_SEC_SEC_CODE
            }
            ApplicationPackageNames.NAPSTER -> {
                InterceptedNotificationCode.NAPSTER_CODE
            }
            ApplicationPackageNames.APOLLO -> {
                InterceptedNotificationCode.APOLLO_CODE
            }
            ApplicationPackageNames.AMAZON_MUSIC -> {
                InterceptedNotificationCode.AMAZON_MUSIC_CODE
            }
            ApplicationPackageNames.WINAMP -> {
                InterceptedNotificationCode.WINAMP_CODE
            }
            ApplicationPackageNames.JETAUDIO -> {
                InterceptedNotificationCode.JETAUDIO_CODE
            }
            ApplicationPackageNames.JETAUDIO_PLUS -> {
                InterceptedNotificationCode.JETAUDIO_PLUS_CODE
            }
            ApplicationPackageNames.D_TWIST -> {
                InterceptedNotificationCode.D_TWIST_CODE
            }
            ApplicationPackageNames.PLAYER_PRO -> {
                InterceptedNotificationCode.PLAYER_PRO_CODE
            }
            ApplicationPackageNames.GOOGLE_MUSIC -> {
                InterceptedNotificationCode.GOOGLE_MUSIC_CODE
            }
            ApplicationPackageNames.OTO_MUSIC -> {
                InterceptedNotificationCode.OTO_MUSIC_CODE
            }
            ApplicationPackageNames.M_PLAYER -> {
                InterceptedNotificationCode.M_PLAYER_CODE
            }
            ApplicationPackageNames.SOFT_PLAYER -> {
                InterceptedNotificationCode.SOFT_PLAYER_CODE
            }
            ApplicationPackageNames.POWERAMP_PI -> {
                InterceptedNotificationCode.POWERAMP_PI_CODE
            }
            ApplicationPackageNames.MUSIC_PRO -> {
                InterceptedNotificationCode.MUSIC_PRO_CODE
            }
            ApplicationPackageNames.I_POD -> {
                InterceptedNotificationCode.I_POD_CODE
            }
            ApplicationPackageNames.NELL -> {
                InterceptedNotificationCode.NELL_CODE
            }
            ApplicationPackageNames.VMONS -> {
                InterceptedNotificationCode.VMONS_CODE
            }
            ApplicationPackageNames.BLACK_P -> {
                InterceptedNotificationCode.BLACK_P_CODE
            }
            ApplicationPackageNames.GITHUB_P -> {
                InterceptedNotificationCode.GITHUB_P_CODE
            }
            ApplicationPackageNames.VINYL -> {
                InterceptedNotificationCode.VINYL_CODE
            }
            ApplicationPackageNames.ANOTHER -> {
                InterceptedNotificationCode.ANOTHER_CODE
            }
            ApplicationPackageNames.BLACK_PLAYER -> {
                InterceptedNotificationCode.BLACK_PLAYER_CODE
            }
            ApplicationPackageNames.SONY_MUSIC -> {
                InterceptedNotificationCode.SONY_MUSIC_CODE
            }
            ApplicationPackageNames.LG_MUSIC_FLOW -> {
                InterceptedNotificationCode.LG_MUSIC_FLOW_CODE
            }
            ApplicationPackageNames.OPPO -> {
                InterceptedNotificationCode.OPPO_CODE
            }
            ApplicationPackageNames.VIVO -> {
                InterceptedNotificationCode.VIVO_CODE
            }
            ApplicationPackageNames.MEDIA_CENTER -> {
                InterceptedNotificationCode.MEDIA_CENTER_CODE
            }
            ApplicationPackageNames.BSB -> {
                InterceptedNotificationCode.BSB_CODE
            }
            ApplicationPackageNames.SPOTIFY_MAIN -> {
                InterceptedNotificationCode.SPOTIFY_MAIN_CODE
            }
            ApplicationPackageNames.GAANA -> {
                InterceptedNotificationCode.GAANA_CODE
            }
            ApplicationPackageNames.STREAM_HUB -> {
                InterceptedNotificationCode.STREAM_HUB_CODE
            }
            ApplicationPackageNames.HUNGAMA -> {
                InterceptedNotificationCode.HUNGAMA_CODE
            }
            ApplicationPackageNames.MUSIXMATCH -> {
                InterceptedNotificationCode.MUSIXMATCH_CODE
            }
            ApplicationPackageNames.APPLE -> {
                InterceptedNotificationCode.APPLE_CODE
            }
            ApplicationPackageNames.LARK -> {
                InterceptedNotificationCode.LARK_CODE
            }
            ApplicationPackageNames.SOUNDCLOUD -> {
                InterceptedNotificationCode.SOUNDCLOUD_CODE
            }
            ApplicationPackageNames.ASPIRO -> {
                InterceptedNotificationCode.ASPIRO_CODE
            }
            ApplicationPackageNames.DEEZER -> {
                InterceptedNotificationCode.DEEZER_CODE
            }
            ApplicationPackageNames.RESSO -> {
                InterceptedNotificationCode.RESSO_CODE
            }
            ApplicationPackageNames.JIO -> {
                InterceptedNotificationCode.JIO_CODE
            }
            ApplicationPackageNames.GAANA_PUN -> {
                InterceptedNotificationCode.GAANA_PUN_CODE
            }
            ApplicationPackageNames.GAANA_TELUGU -> {
                InterceptedNotificationCode.GAANA_TELUGU_CODE
            }
            ApplicationPackageNames.GAANA_HINDI -> {
                InterceptedNotificationCode.GAANA_HINDI_CODE
            }
            ApplicationPackageNames.GAANA_TAMIL -> {
                InterceptedNotificationCode.GAANA_TAMIL_CODE
            }
            ApplicationPackageNames.PANDORA -> {
                InterceptedNotificationCode.PANDORA_CODE
            }
            ApplicationPackageNames.PHONOGRAPH -> {
                InterceptedNotificationCode.PHONOGRAPH_CODE
            }

            else -> {
                InterceptedNotificationCode.OTHER_NOTIFICATIONS_CODE
            }
        }
    }
}

fun refreshSong() {
    if (mediaController != null) {
        if (mediaController?.playbackState?.state == PlaybackState.STATE_PLAYING) {
            mediaController?.transportControls?.pause()
            mediaController?.transportControls?.play()
        } else {
            mediaController?.transportControls?.play()
            mediaController?.transportControls?.pause()
        }
    }
}