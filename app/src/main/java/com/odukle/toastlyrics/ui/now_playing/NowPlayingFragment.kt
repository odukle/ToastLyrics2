package com.odukle.toastlyrics.ui.now_playing

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.BitmapDrawable
import android.media.AudioManager
import android.media.MediaMetadata
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.odukle.toastlyrics.*
import com.odukle.toastlyrics.ui.home.IS_SERVICE_STOPPED
import es.dmoral.toasty.Toasty
import kotlinx.android.synthetic.main.fragment_now_playing.*
import kotlin.math.absoluteValue

private const val TAG = "NowPlayingFragment"
var npShowing = false
var delay = 0
var bAdCount = 0
const val I_AD_COUNT = "iAdCount"
const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-9193191601772541/2896242023"

class NowPlayingFragment : Fragment() {

    private lateinit var nowPlayingViewModel: NowPlayingViewModel
    private lateinit var audioManager: AudioManager
    private lateinit var adapter: NPRecyclerViewAdapter
    private lateinit var sharedPref: SharedPreferences

    private var track: String? = null
    private var artist: String? = null
    private var album: String? = null
    private var duration: Long? = null

    companion object {
        lateinit var instance: NowPlayingFragment
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        instance = this
        audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        sharedPref = requireContext().getSharedPreferences("pref", Context.MODE_PRIVATE)

        var iAdCount = sharedPref.getInt(I_AD_COUNT, 0)
        iAdCount++
        sharedPref.edit().putInt(I_AD_COUNT, iAdCount).apply()

        if (iAdCount >= 10) {
            if (MainActivity.mInterstitialAd.isLoaded) {
                MainActivity.mInterstitialAd.show()
                iAdCount = 0
                sharedPref.edit().putInt(I_AD_COUNT, iAdCount).apply()
            }
        }

        nowPlayingViewModel =
            ViewModelProvider(this).get(NowPlayingViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_now_playing, container, false)

        nowPlayingViewModel.track.observe(viewLifecycleOwner, {
            track_tv.text = it
            track = it

            setIcons()
        })

        nowPlayingViewModel.artist.observe(viewLifecycleOwner, {
            artist = it
            artist_tv.text = it
        })

        nowPlayingViewModel.album.observe(viewLifecycleOwner, {
            album = it
        })

        nowPlayingViewModel.duration.observe(viewLifecycleOwner, {
            duration = it
            seekbar.max = it?.toInt() ?: 0
        })

        nowPlayingViewModel.position.observe(viewLifecycleOwner, {
            seekbar.progress = it?.toInt() ?: 0
        })

        return root
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        npShowing = true

        Handler(Looper.getMainLooper()).postDelayed({
            MainActivity.menuMain[0].isVisible = false
            MainActivity.menuMain[1].isVisible = true
        }, 500)

        if (albumArt != null) {
            album_art_iv.setImageBitmap(albumArt)
            if (sharedPref.getBoolean(CONTAINER_HAS_BG, false)) {
                val container = requireActivity().findViewById<ConstraintLayout>(R.id.container)
                val blurred = fastBlur(albumArt!!, 0.5f, 20)
                container.background = BitmapDrawable(requireContext().resources, blurred)
            }
        } else {
            if (mediaController != null && isOnline(requireContext())) {
                val track1 = currentTrack?.replace("/", "")?.replace("\\", "") ?: "Home"
                val artist1 = currentArtist?.replace("/", "")?.replace("\\", "") ?: "Davis"
                val query = "$track1 $artist1"

                NowPlayingViewModel.setAlbumArt(query)
            }
        }

        adCard_np.visibility = View.GONE
        close_ad.setOnClickListener {
            adCard_np.visibility = View.GONE
            player_card.visibility = View.VISIBLE
        }

        if (track != null && artist != null) {
            track_tv.text = track
            artist_tv.text = artist
        }

        Toasty.Config.getInstance().allowQueue(false).apply()

        setIcons()

        val submitListenerOn = View.OnClickListener {

            val dialog = MaterialAlertDialogBuilder(requireContext())
            dialog.setTitle("Submit change?")
                .setPositiveButton("Submit") { d, _ ->
                    if (isOnline(requireContext())) {
                        if (track != null && artist != null) {
                            nowPlayingViewModel.submitLyrics(track!!, artist!!)
                        }
                    } else {
                        Toasty.error(requireContext(), "No internet").show()
                    }
                }
                .setNegativeButton("Cancel") { d, _ ->
                    d.dismiss()
                }
                .setCancelable(false)
                .show()

        }

        val submitListenerOff = View.OnClickListener {
            Toasty.info(requireContext(), "No Change").show()
        }

        NPRecyclerViewAdapter.prepareMixedList()
        setRecyclerView()
        adapter = NPRecyclerViewAdapter(requireContext(), mixedList)
        lyrics_rv.layoutManager = LinearLayoutManager(requireContext())
        lyrics_rv.adapter = adapter
        requireActivity().runOnUiThread {
            adapter.notifyDataSetChanged()
        }

        play_pause_btn.setOnClickListener {
            if (mediaController != null) {
                if (audioManager.isMusicActive) {
                    mediaController!!.transportControls.pause()
                    refresh_img.setImageDrawable(
                        ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.ic_twotone_play_arrow_24
                        )
                    )

                    toggle_play_btn.setImageDrawable(
                        ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.ic_twotone_play_circle_filled_24
                        )
                    )
                } else {
                    mediaController!!.transportControls.play()
                    refresh_img.setImageDrawable(
                        ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.pause_green
                        )
                    )

                    toggle_play_btn.setImageDrawable(
                        ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.ic_twotone_pause_circle_filled_24
                        )
                    )
                }
            } else {
                Toasty.error(
                    requireContext(),
                    "No music Playing!\n(if music is already playing then pause and play it again)"
                ).show()
            }
        }

        toggle_play_btn.setOnClickListener {
            if (mediaController != null) {
                if (audioManager.isMusicActive) {
                    mediaController!!.transportControls.pause()
                    toggle_play_btn.setImageDrawable(
                        ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.ic_twotone_play_circle_filled_24
                        )
                    )

                    refresh_img.setImageDrawable(
                        ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.ic_twotone_play_arrow_24
                        )
                    )
                } else {
                    mediaController!!.transportControls.play()
                    toggle_play_btn.setImageDrawable(
                        ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.ic_twotone_pause_circle_filled_24
                        )
                    )

                    refresh_img.setImageDrawable(
                        ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.pause_green
                        )
                    )
                }
            } else {
                Toasty.error(
                    requireContext(),
                    "No music Playing!\n(if music is already playing then pause and play it again)"
                ).show()
            }
        }

        forwad_5s.setOnClickListener {
            if (mediaController != null) {
                val pos = mediaController!!.playbackState?.position
                if (pos != null) {
                    mediaController!!.transportControls.seekTo(pos + 5000L)
                }
            } else {
                Toasty.error(
                    requireContext(),
                    "No music Playing!\n(if music is already playing then pause and play it again)"
                ).show()
            }
        }

        replay_5s.setOnClickListener {
            if (mediaController != null) {
                if (mediaController != null) {
                    val pos = mediaController!!.playbackState?.position
                    if (pos != null) {
                        mediaController!!.transportControls.seekTo(pos - 5000L)
                    }
                }
            } else {
                Toasty.error(
                    requireContext(),
                    "No music Playing!\n(if music is already playing then pause and play it again)"
                ).show()
            }
        }

        skip_next.setOnClickListener {
            if (mediaController != null) {
                mediaController!!.transportControls.skipToNext()
                delay = 0
                bAdCount++
                if (bAdCount >= 5) {
                    bAdCount = 0
                    bannerAd_np.loadAd(MainActivity.adRequest)
                    adCard_np.visibility = View.VISIBLE
                    player_card.visibility = View.GONE
                }
                btn_submit_np.setOnClickListener(submitListenerOff)

                Handler(Looper.getMainLooper()).postDelayed({
                    if (audioManager.isMusicActive) {
                        toggle_play_btn.setImageDrawable(
                            ContextCompat.getDrawable(
                                requireContext(),
                                R.drawable.ic_twotone_pause_circle_filled_24
                            )
                        )
                    } else {
                        toggle_play_btn.setImageDrawable(
                            ContextCompat.getDrawable(
                                requireContext(),
                                R.drawable.ic_twotone_play_circle_filled_24
                            )
                        )
                    }
                }, 500)

                Handler(Looper.getMainLooper()).postDelayed({
                    mediaController!!.transportControls.pause()
                    mediaController!!.transportControls.play()
                }, 1000)

            } else {
                Toasty.error(
                    requireContext(),
                    "No music Playing!\n(if music is already playing then pause and play it again)"
                ).show()
            }
        }

        skip_previous.setOnClickListener {
            if (mediaController != null) {
                mediaController!!.transportControls.skipToPrevious()
                delay = 0
                btn_submit_np.setOnClickListener(submitListenerOff)

                Handler(Looper.getMainLooper()).postDelayed({
                    if (audioManager.isMusicActive) {
                        toggle_play_btn.setImageDrawable(
                            ContextCompat.getDrawable(
                                requireContext(),
                                R.drawable.ic_twotone_pause_circle_filled_24
                            )
                        )
                    } else {
                        toggle_play_btn.setImageDrawable(
                            ContextCompat.getDrawable(
                                requireContext(),
                                R.drawable.ic_twotone_play_circle_filled_24
                            )
                        )
                    }
                }, 500)

                Handler(Looper.getMainLooper()).postDelayed({
                    mediaController!!.transportControls.pause()
                    mediaController!!.transportControls.play()
                }, 1000)

            } else {
                Toasty.error(
                    requireContext(),
                    "No music Playing!\n(if music is already playing then pause and play it again)"
                ).show()
            }
        }

        seekbar.max = duration?.toInt() ?: 0
        seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    if (mediaController != null) {
                        mediaController!!.transportControls.seekTo(progress.toLong())
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}

        })

        delay_card.setOnClickListener {
            if (mediaController != null) {

                if (mixedList.size > 1) {
                    delay += 500
                    val delayed = if (delay < 0) "hastened" else "delayed"
                    btn_submit_np.setOnClickListener(submitListenerOn)
                    if (synced) {
                        Toasty.normal(
                            requireContext(),
                            "Lyrics $delayed by ${(delay.toDouble() / 1000).absoluteValue} sec"
                        ).show()
                        try {
                            nowPlayingViewModel.delay()
                        } finally {
                            refreshSong()
                        }

                    } else {
                        Toasty.info(
                            requireContext(),
                            "Lyrics not synced, you can sync them by clicking on the sync tab below"
                        ).show()
                    }
                }


            } else {
                Toasty.error(
                    requireContext(),
                    "No music Playing!\n(if music is already playing then pause and play it again)"
                ).show()
            }
        }

        hasten_card.setOnClickListener {
            if (mediaController != null) {

                if (mixedList.size > 1) {
                    delay -= 500
                    val delayed = if (delay < 0) "hastened" else "delayed"
                    btn_submit_np.setOnClickListener(submitListenerOn)
                    if (synced) {
                        Toasty.normal(
                            requireContext(),
                            "Lyrics $delayed by ${(delay.toDouble() / 1000).absoluteValue} sec"
                        )
                            .show()
                        try {
                            nowPlayingViewModel.hasten()
                        } finally {
                            refreshSong()
                        }
                    } else {
                        Toasty.info(
                            requireContext(),
                            "Lyrics not synced, you can sync them by clicking on the sync tab below."
                        ).show()
                    }
                }

            } else {
                Toasty.error(
                    requireContext(),
                    "No music Playing!\n(if music is already playing then pause and play it again)"
                ).show()
            }
        }

        btn_submit_np.setOnClickListener(submitListenerOff)

    }

    override fun onPause() {
        super.onPause()
        npShowing = false
    }

    override fun onResume() {
        super.onResume()
        npShowing = true
        setRecyclerView()
        requireActivity().runOnUiThread {
            lyrics_rv.adapter?.notifyDataSetChanged()

            if (currentTrack != null && currentArtist != null) {
                track_tv.text = currentTrack
                artist_tv.text = currentArtist
                seekbar.progress = nowPlayingViewModel.position.value?.toInt() ?: 0
            } else if (mediaController != null) {
                val metadata = mediaController!!.metadata
                val mTrack = metadata!!.getString(MediaMetadata.METADATA_KEY_TITLE)
                val mArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)

                track_tv.text = mTrack
                artist_tv.text = mArtist
            }
        }
        setIcons()
    }

    private fun setIcons() {
        try {
            if (mediaController != null) {
                if (audioManager.isMusicActive) {
                    toggle_play_btn.setImageDrawable(
                        ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.ic_twotone_pause_circle_filled_24
                        )
                    )

                    refresh_img.setImageDrawable(
                        ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.pause_green
                        )
                    )
                } else {
                    toggle_play_btn.setImageDrawable(
                        ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.ic_twotone_play_circle_filled_24
                        )
                    )

                    refresh_img.setImageDrawable(
                        ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.ic_twotone_play_arrow_24
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "setIcons: ${e.stackTraceToString()}")
        }
    }

    private fun setRecyclerView() {
        if (NotificationManagerCompat.getEnabledListenerPackages(requireContext())
                .contains(requireContext().packageName)
        ) {
            if (mixedList.isEmpty()) {
                val iString = if (mediaController == null) {
                    if (!sharedPref.getBoolean(IS_SERVICE_STOPPED, false)) {
                        "Play music and come back here"
                    } else {
                        "Lyrics Service is stopped"
                    }
                } else {
                    "No music Playing!\n" +
                            "(if music is already playing then pause and play it again)"
                }

                val instruction = LrcRow(0, iString, "00:00.00")
                mixedList.clear()
                listRows.clear()
                mixedList.add(instruction)
                listRows.add(instruction)
            } else {
                if (sharedPref.getBoolean(IS_SERVICE_STOPPED, false)) {
                    listRows.clear()
                    mixedList.clear()
                    val instruction = LrcRow(0, "Lyrics Service is stopped", "00:00.00")
                    mixedList.add(instruction)
                    listRows.add(instruction)
                } else {
                    if (mediaController == null) {
                        listRows.clear()
                        mixedList.clear()
                        val instruction = LrcRow(0, "Play music and come back here", "00:00.00")
                        mixedList.add(instruction)
                        listRows.add(instruction)
                    }
                }
            }
        } else {
            listRows.clear()
            mixedList.clear()
            val instruction = LrcRow(
                0,
                "Please allow notification access so that we can detect currently playing music",
                "00:00.00"
            )
            mixedList.add(instruction)
            listRows.add(instruction)
        }

    }


}