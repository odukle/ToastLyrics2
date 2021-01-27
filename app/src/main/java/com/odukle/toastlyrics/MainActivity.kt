package com.odukle.toastlyrics

import android.app.*
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.gms.ads.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.odukle.toastlyrics.ui.home.CHANNEL_ID
import com.odukle.toastlyrics.ui.now_playing.INTERSTITIAL_AD_UNIT_ID
import com.odukle.toastlyrics.ui.now_playing.albumArt
import com.odukle.toastlyrics.ui.now_playing.fastBlur
import es.dmoral.toasty.Toasty
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_home.*


private const val TAG = "MainActivity"
const val CONTAINER_HAS_BG = "chbg"
const val RATED = "rated"

class MainActivity : AppCompatActivity() {

    private lateinit var sharedPref: SharedPreferences
    private lateinit var auth: FirebaseAuth

    companion object {
        lateinit var adRequest: AdRequest
        lateinit var mInterstitialAd: InterstitialAd
        lateinit var menuMain: Menu

        fun getNotification(
            context: Context,
            notificationManager: NotificationManager
        ): Notification {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationChannel = NotificationChannel(
                    CHANNEL_ID,
                    "Lyrics Service",
                    NotificationManager.IMPORTANCE_DEFAULT
                )

                // Configure the notification channel.
                notificationChannel.description =
                    "Shown when the lyrics service is running in the background"
                notificationChannel.enableLights(true)
                notificationChannel.lightColor = Color.RED
                notificationChannel.setSound(null, null)
                notificationChannel.enableVibration(false)
                notificationManager.createNotificationChannel(notificationChannel)
            }

            val notificationIntent =
                Intent(context, StopService::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                notificationIntent,
                0
            )

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setAutoCancel(false)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_check_white_24dp)
                .setTicker("Lyrics Service is running")
                .setOnlyAlertOnce(true)
                .setContentTitle("Toast Lyrics")
                .setContentText("Service is running")
                .setContentInfo("Info")
                .setOngoing(true)
                .addAction(R.drawable.ic_check_white_24dp, "Stop Service", pendingIntent)
                .build()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPref = getSharedPreferences("pref", Context.MODE_PRIVATE)
        auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnCompleteListener {
                    Log.d(TAG, "onCreate: Signed in Successfully")
                }.addOnFailureListener {
                    Log.e(TAG, "onCreate: Sign in failed --> ${it.stackTraceToString()}")
                }
        } else {
            Log.d(TAG, "onCreate: Already signed in")
        }

        MobileAds.initialize(this)
        adRequest = AdRequest.Builder().build()
        mInterstitialAd = InterstitialAd(this)
        mInterstitialAd.adUnitId = INTERSTITIAL_AD_UNIT_ID
        mInterstitialAd.loadAd(adRequest)
        mInterstitialAd.adListener = object : AdListener() {
            override fun onAdClosed() {
                mInterstitialAd.loadAd(AdRequest.Builder().build())
            }
        }

        if (isDarkTheme(this)) {
            nav_view.background = ContextCompat.getDrawable(this, R.drawable.nav_bg_dark)
        } else {
            nav_view.background = ContextCompat.getDrawable(this, R.drawable.nav_bg_light)
        }

        setSupportActionBar(toolbar_home)

        val navView: BottomNavigationView = findViewById(R.id.nav_view)

        val navController = findNavController(R.id.nav_host_fragment)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_now_playing, R.id.navigation_sync
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {

        menuInflater.inflate(R.menu.menu_main, menu)
        menuMain = menu

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {
            R.id.blurred_bg -> {
                if (albumArt != null) {
                    val chb = sharedPref.getBoolean(CONTAINER_HAS_BG, false)
                    if (chb) {
                        sharedPref.edit().putBoolean(CONTAINER_HAS_BG, false).apply()
                        container.setBackgroundResource(0)
                        item.icon =
                            ContextCompat.getDrawable(this, R.drawable.ic_twotone_blur_on_24)
                    } else {
                        sharedPref.edit().putBoolean(CONTAINER_HAS_BG, true).apply()
                        val blurred = fastBlur(albumArt!!, 0.5f, 20)
                        container.background = BitmapDrawable(resources, blurred)
                        item.icon =
                            ContextCompat.getDrawable(this, R.drawable.ic_baseline_blur_off_24)
                    }
                } else {
                    Toasty.info(this, "Cover art is not loaded yet").show()
                }

            }

            R.id.other_apps -> {
                startActivity(Intent(this, OtherApps::class.java))
            }
        }

        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                btn_allow_notification.visibility = View.GONE
            }
        }
    }

    override fun onStop() {
        super.onStop()
        previousTrack = null
    }

    override fun onBackPressed() {

        val rated = sharedPref.getBoolean(RATED, false)

        if (!rated) {
            val dialog = MaterialAlertDialogBuilder(this)
            dialog.setTitle("Rate us on playstore 😀")
                .setPositiveButton(
                    "Rate"
                ) { dialog, _ ->
                    sharedPref.edit().putBoolean(RATED, true).apply()
                    val appPackageName = packageName
                    try {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=$appPackageName")
                            )
                        )
                    } catch (e: ActivityNotFoundException) {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")
                            )
                        )
                    }
                }.setNegativeButton(
                    "Later"
                ) { dialog, which ->
                    finish()
                }
                .setView(R.layout.dialog_ad_layout)
                .setCancelable(false)
                .show()
                .also {
                    val dialogView = it as Dialog
                    val adView = dialogView.findViewById<AdView>(R.id.dialog_ad_view)
                    adView.loadAd(adRequest)
                }
        } else {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        this.cacheDir.deleteRecursively()
    }

    private fun isDarkTheme(activity: Activity): Boolean {
        return activity.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }
}



