package com.odukle.toastlyrics.ui.now_playing

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.InterstitialAd
import com.odukle.toastlyrics.*
import es.dmoral.toasty.Toasty
import kotlinx.android.synthetic.main.fragment_now_playing.*
import kotlin.math.absoluteValue

private const val TAG = "NowPlayingFragment"
var npShowing = false
var delay = 0
var bAdCount = 0
var iAdCountNP = 0
const val I_AD_COUNT_NP = "iAdCountNP"
const val I_AD_COUNT_SF = "iAdCountSF"
const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-9193191601772541/2896242023"

class NowPlayingFragment : Fragment() {

    private lateinit var nowPlayingViewModel: NowPlayingViewModel
    private lateinit var mInterstitialAd: InterstitialAd
    private lateinit var audioManager: AudioManager
    private lateinit var adapter: NPRecyclerViewAdapter
    private lateinit var sharedPref: SharedPreferences

    private var track: String? = null
    private var artist: String? = null
    private var album: String? = null
    private var duration: Long? = null

    companion object {
        lateinit var instance: NowPlayingFragment

        fun isInitialized(): Boolean {
            return this::instance.isInitialized
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        instance = this
        audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        sharedPref = requireContext().getSharedPreferences("pref", Context.MODE_PRIVATE)
        mInterstitialAd = InterstitialAd(requireContext())
        mInterstitialAd.adUnitId = INTERSTITIAL_AD_UNIT_ID

        iAdCountNP = sharedPref.getInt(I_AD_COUNT_NP, 0)
        iAdCountNP++
        sharedPref.edit().putInt(I_AD_COUNT_NP, iAdCountNP).apply()

        if (iAdCountNP >= 5) {

        }

        nowPlayingViewModel =
            ViewModelProvider(this).get(NowPlayingViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_now_playing, container, false)

        nowPlayingViewModel.track.observe(viewLifecycleOwner, {
            track = it
            track_tv.text = track
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
            if (isOnline(requireContext())) {
                if (track != null && artist != null) {
                    nowPlayingViewModel.submitLyrics(track!!, artist!!)
                }
            } else {
                Toasty.normal(requireContext(), "No internet").show()
            }
        }

        val submitListenerOff = View.OnClickListener {
            Toasty.normal(requireContext(), "No Change").show()
        }

        adapter = NPRecyclerViewAdapter(requireContext(), listRows)
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
                Toasty.normal(
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
                Toasty.normal(
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
                Toasty.normal(
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
                Toasty.normal(
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
                if (bAdCount >= 3) {
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
                Toasty.normal(
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
                Toasty.normal(
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
                delay += 500
                val delayed = if (delay < 0) "hastened" else "delayed"
                btn_submit_np.setOnClickListener(submitListenerOn)
                if (synced) {
                    Toasty.normal(
                        requireContext(),
                        "Lyrics $delayed by ${(delay.toDouble() / 1000).absoluteValue} sec"
                    )
                        .show()
                    nowPlayingViewModel.delay()
                    if (audioManager.isMusicActive) {
                        mediaController!!.transportControls.pause()
                        mediaController!!.transportControls.play()
                    } else {
                        mediaController!!.transportControls.play()
                        mediaController!!.transportControls.pause()
                    }
                } else {
                    Toasty.normal(
                        requireContext(),
                        "Lyrics not synced, you can sync them by clicking on the sync tab below"
                    ).show()
                }
            } else {
                Toasty.normal(
                    requireContext(),
                    "No music Playing!\n(if music is already playing then pause and play it again)"
                ).show()
            }
        }

        hasten_card.setOnClickListener {
            if (mediaController != null) {
                delay -= 500
                val delayed = if (delay < 0) "hastened" else "delayed"
                btn_submit_np.setOnClickListener(submitListenerOn)
                if (synced) {
                    Toasty.normal(
                        requireContext(),
                        "Lyrics $delayed by ${delay.toDouble() / 1000} sec"
                    )
                        .show()
                    nowPlayingViewModel.hasten()
                    if (audioManager.isMusicActive) {
                        mediaController!!.transportControls.pause()
                        mediaController!!.transportControls.play()
                    } else {
                        mediaController!!.transportControls.play()
                        mediaController!!.transportControls.pause()
                    }
                } else {
                    Toasty.normal(
                        requireContext(),
                        "Lyrics not synced, you can sync them by clicking on the sync tab below."
                    ).show()
                }
            } else {
                Toasty.normal(
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
        requireActivity().runOnUiThread {
            lyrics_rv.adapter?.notifyDataSetChanged()
            if (mediaController != null) {
                if (audioManager.isMusicActive) {
                    mediaController!!.transportControls.pause()
                    mediaController!!.transportControls.play()
                }
            }
        }

        setIcons()
    }

    private fun setIcons() {
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
        } else {
            refresh_img.setImageDrawable(
                ContextCompat.getDrawable(
                    requireContext(),
                    R.drawable.ic_twotone_error_24
                )
            )
        }
    }

}