package com.odukle.toastlyrics.ui.home

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.odukle.toastlyrics.*
import es.dmoral.toasty.Toasty
import kotlinx.android.synthetic.main.fragment_home.*
import java.util.*
import kotlin.properties.Delegates

const val IS_SERVICE_STOPPED = "serviceStopped"
const val CHANNEL_ID = "channelId"
const val START_ON_BOOT = "sob"
private const val TAG = "HomeFragment"
private var anaWasClicked = false
var hfShowing = false

class HomeFragment : Fragment() {

    private lateinit var homeViewModel: HomeViewModel
    private lateinit var sharedPref: SharedPreferences
    private lateinit var notificationManager: NotificationManager
    private lateinit var audioManager: AudioManager
    private var expanded by Delegates.notNull<Boolean>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        sharedPref = requireActivity().getSharedPreferences("pref", Context.MODE_PRIVATE)
        audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        expanded = false

        homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        hfShowing = true
        Handler(Looper.getMainLooper()).postDelayed({
            MainActivity.menuMain[0].isVisible = true
            MainActivity.menuMain[1].isVisible = false
        }, 500)

        val container = requireActivity().findViewById<ConstraintLayout>(R.id.container)
        container.setBackgroundResource(0)

        Toasty.Config.getInstance().allowQueue(false).apply()
        notificationManager =
            requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (sharedPref.getBoolean(IS_SERVICE_STOPPED, false)) notificationManager.cancelAll()

        banner_ad_main.loadAd(MainActivity.adRequest)

        showNotificationOnStart()

        val colorList = listOf(
            ToastColor("Default", "#213131"),
            ToastColor("Red", "#F44336"),
            ToastColor("Pink", "#FF4081"),
            ToastColor("Indigo", "#3F51B5"),
            ToastColor("Blue", "#448AFF"),
            ToastColor("Cyan", "#00BCD4"),
            ToastColor("Teal", "#009688"),
            ToastColor("Green", "#4CAF50"),
            ToastColor("Orange", "#FF5722"),
            ToastColor("Lime", "#CDDC39")
        )
        color_grid.adapter = GridRecyclerViewAdapter(requireContext(), colorList)
        color_grid.layoutManager = GridLayoutManager(requireContext(), 5)

        color_grid.visibility = View.GONE
        color_dropdown.setOnClickListener {
            if (!expanded) {
                color_grid.visibility = View.VISIBLE
                color_dropdown_icn.setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.ic_round_keyboard_arrow_up_24
                    )
                )
                expanded = true
            } else {
                color_grid.visibility = View.GONE
                color_dropdown_icn.setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.ic_round_keyboard_arrow_down_24
                    )
                )
                expanded = false
            }
        }

        switch_sob.isChecked = sharedPref.getBoolean(START_ON_BOOT, false)
        switch_sob.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                sharedPref.edit().putBoolean(START_ON_BOOT, true).apply()
                val sob = sharedPref.getBoolean(START_ON_BOOT, false)
                Log.d(TAG, "onViewCreated: switch clicked --> $sob")
            } else {
                sharedPref.edit().putBoolean(START_ON_BOOT, false).apply()
                val sob = sharedPref.getBoolean(START_ON_BOOT, false)
                Log.d(TAG, "onViewCreated: switch clicked --> $sob")
            }
        }

        if (NotificationManagerCompat.getEnabledListenerPackages(requireContext())
                .contains(requireContext().packageName)
        ) {
            btn_allow_notification.visibility = View.GONE
            allow_notification_request.visibility = View.GONE

            val isServiceStopped = sharedPref.getBoolean(IS_SERVICE_STOPPED, false)

            if (isServiceStopped) {
                service_status_tv.text = "Service is not running"
                service_status_tv.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.red
                    )
                )
            } else {
                service_status_tv.text = "Service is running"
                service_status_tv.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.holo_green_light
                    )
                )

            }
        } else {
            switch_ic.setImageDrawable(
                ContextCompat.getDrawable(
                    requireContext(),
                    R.drawable.ic_twotone_error_24
                )
            )

            sharedPref.edit().putBoolean(IS_SERVICE_STOPPED, true).apply()
            notificationManager.cancelAll()
            service_status_tv.text = "Service is not running, allow notification access"
            service_status_tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))

        }

        switch_card.setOnClickListener {
            if (!NotificationManagerCompat.getEnabledListenerPackages(requireContext())
                    .contains(requireContext().packageName)
            ) {
                Toasty.normal(requireContext(), "Please allow notification access!").show()
            } else {
                val isServiceStopped = sharedPref.getBoolean(IS_SERVICE_STOPPED, true)
                if (isServiceStopped) {
                    switch_ic.setImageDrawable(
                        ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.ic_twotone_stop_circle_24
                        )
                    )
                    service_status_tv.text = "Service is running"
                    service_status_tv.setTextColor(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.holo_green_light
                        )
                    )
                    sharedPref.edit().putBoolean(IS_SERVICE_STOPPED, false).apply()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        requireActivity().startForegroundService(
                            Intent(
                                requireContext(),
                                MusicNotificationListener::class.java
                            )
                        )
                    } else {
                        requireActivity().startService(
                            Intent(
                                requireContext(),
                                MusicNotificationListener::class.java
                            )
                        )
                    }
                } else {
                    switch_ic.setImageDrawable(
                        ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.ic_round_power_settings_new_24
                        )
                    )
                    service_status_tv.text = "Service is not running"
                    service_status_tv.setTextColor(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.red
                        )
                    )
                    sharedPref.edit().putBoolean(IS_SERVICE_STOPPED, true).apply()
                    notificationManager.cancel(1)
                    MusicNotificationListener.terminateService()
                }
            }

        }

        btn_allow_notification.setOnClickListener {
            anaWasClicked = true
            requireActivity().startActivityForResult(
                Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                ), 1
            )
        }

        request_support.setOnClickListener {
            val dialog = MaterialAlertDialogBuilder(requireContext())
            dialog.setView(R.layout.request_dialog_layout)
                .setPositiveButton("Send Request") { d, _ ->
                    if (isOnline(requireContext())) {

                        val dialogView = d as Dialog
                        val appNameTV = dialogView.findViewById<TextView>(R.id.req_app_name)
                        val appLinkTV = dialogView.findViewById<TextView>(R.id.req_link)

                        if (!appNameTV.text.isNullOrEmpty() && !appNameTV.text.isNullOrBlank()) {
                            val db = FirebaseFirestore.getInstance()
                            val appName = appNameTV.text.toString().toLowerCase(Locale.getDefault())
                            val appLink = appLinkTV.text.toString()
                            db.collection("requests").document(appName).set(
                                hashMapOf(
                                    "name" to appName,
                                    "link" to appLink
                                ), SetOptions.merge()
                            ).addOnSuccessListener {
                                Toasty.success(requireContext(), "Request sent successfully").show()
                            }.addOnFailureListener {
                                Toasty.error(
                                    requireContext(),
                                    "Something went wrong: ${it.message}"
                                ).show()
                            }
                        } else {
                            Toasty.warning(requireContext(), "App name is blank").show()
                        }

                    } else {
                        Toasty.error(requireContext(), "No Internet").show()
                    }
                    d.dismiss()
                }
                .setNegativeButton("Cancel") { d, _ ->
                    d.dismiss()
                }
                .setCancelable(false)
                .show()
        }

        supported_apps.setOnClickListener {
            val dialog = MaterialAlertDialogBuilder(requireContext())
            dialog.setView(R.layout.supported_apps_dialog)
                .show()
                .also {
                    val dialogView = it as Dialog
                    val lv = dialogView.findViewById<ListView>(R.id.supported_apps_lv)
                    val adapter = ArrayAdapter(
                        requireContext(),
                        R.layout.supported_app_name_layout,
                        MusicNotificationListener.appsArray
                    )
                    lv.adapter = adapter
                }
        }

    }

    override fun onPause() {
        super.onPause()
        hfShowing = false
    }

    @SuppressLint("SetTextI18n")
    override fun onResume() {
        super.onResume()
        hfShowing = true
//        serviceRunning = true
        if (!NotificationManagerCompat.getEnabledListenerPackages(requireContext())
                .contains(requireContext().packageName)
        ) {

            if (anaWasClicked) {
                sharedPref.edit().putBoolean(IS_SERVICE_STOPPED, false).apply()
                anaWasClicked = false
            }

            btn_allow_notification.visibility = View.VISIBLE
            allow_notification_request.visibility = View.VISIBLE
            switch_ic.setImageDrawable(
                ContextCompat.getDrawable(
                    requireContext(),
                    R.drawable.ic_twotone_error_24
                )
            )
            service_status_tv.text = "Service is not running, allow notification access"
            service_status_tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))

        } else {
            btn_allow_notification.visibility = View.GONE
            allow_notification_request.visibility = View.GONE

            val isServiceStopped = sharedPref.getBoolean(IS_SERVICE_STOPPED, true)

            if (isServiceStopped) {
                service_status_tv.text = "Service is not running"
                service_status_tv.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.red
                    )
                )
            } else {
                service_status_tv.text = "Service is running"
                service_status_tv.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.holo_green_light
                    )
                )
            }
        }

        showNotificationOnStart()

    }

    private fun showNotificationOnStart() {
        val isServiceStopped = sharedPref.getBoolean(IS_SERVICE_STOPPED, false)
        Log.d(TAG, "showNotificationOnStart: $isServiceStopped")
        if (isServiceStopped) {
            switch_ic.setImageDrawable(
                ContextCompat.getDrawable(
                    requireContext(),
                    R.drawable.ic_round_power_settings_new_24
                )
            )
        } else {
            switch_ic.setImageDrawable(
                ContextCompat.getDrawable(
                    requireContext(),
                    R.drawable.ic_twotone_stop_circle_24
                )
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requireActivity().startForegroundService(
                    Intent(
                        requireContext(),
                        MusicNotificationListener::class.java
                    )
                )
            } else {
                requireActivity().startService(
                    Intent(
                        requireContext(),
                        MusicNotificationListener::class.java
                    )
                )
            }
        }
    }

}