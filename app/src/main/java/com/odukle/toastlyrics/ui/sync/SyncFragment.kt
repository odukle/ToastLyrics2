package com.odukle.toastlyrics.ui.sync

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.media.AudioManager
import android.media.MediaMetadata
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.get
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.odukle.toastlyrics.*
import com.odukle.toastlyrics.ui.home.IS_SERVICE_STOPPED
import com.odukle.toastlyrics.ui.now_playing.*
import es.dmoral.toasty.Toasty
import kotlinx.android.synthetic.main.fragment_sync.*
import java.io.File
import java.io.FileWriter
import java.io.IOException

var sfShowing = false
var currentLine = -1
val syncedRows = mutableListOf<LrcRow>()

private const val TAG = "SyncFragment"

class SyncFragment : Fragment() {

    private lateinit var nowPlayingViewModel: NowPlayingViewModel
    private lateinit var audioManager: AudioManager
    private lateinit var adapter: SyncRVAdapter
    private lateinit var sharedPref: SharedPreferences

    private var track: String? = null
    private var artist: String? = null
    private var album: String? = null
    private var duration: Long? = null

    companion object {
        lateinit var instance: SyncFragment
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        instance = this

        audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        sharedPref = requireContext().getSharedPreferences("pref", Context.MODE_PRIVATE)

        nowPlayingViewModel =
            ViewModelProvider(this).get(NowPlayingViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_sync, container, false)

        nowPlayingViewModel.track.observe(viewLifecycleOwner, {
            Log.d(TAG, "onCreateView: trk changed")
            if ((track != null && track != it) || track == null) {
                track = it
                track_tv_sync.text = track
                currentLine = -1
                syncedRows.clear()
                setIcon()
            }
        })

        nowPlayingViewModel.artist.observe(viewLifecycleOwner, {
            artist = it
            artist_tv_sync.text = it
        })

        nowPlayingViewModel.duration.observe(viewLifecycleOwner, {
            duration = it
        })

        nowPlayingViewModel.album.observe(viewLifecycleOwner, {
            album = it
        })

        return root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sfShowing = true

        Handler(Looper.getMainLooper()).postDelayed({
            MainActivity.menuMain[0].isVisible = false
            MainActivity.menuMain[1].isVisible = true
        }, 500)

        if (sharedPref.getBoolean(CONTAINER_HAS_BG, false)) {
            if (albumArt != null) {
                val container = requireActivity().findViewById<ConstraintLayout>(R.id.container)
                val blurred = fastBlur(albumArt!!, 0.2f, 20)
                container.background = BitmapDrawable(requireContext().resources, blurred)
            }
        }

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

        setIcon()

        adapter = SyncRVAdapter(requireContext(), listRows)
        sync_rv.layoutManager = LinearLayoutManager(requireContext())
        sync_rv.adapter = adapter
        requireActivity().runOnUiThread {
            adapter.notifyDataSetChanged()
        }

        setRecyclerView()

        btn_restart.setOnClickListener {

            val vh = sync_rv.findViewHolderForAdapterPosition(currentLine)
            val tv = vh?.itemView as TextView?

            tv?.setBackgroundResource(0)
            tv?.setTextAppearance(R.style.LyricsStyleNormal)

            sync_rv.smoothScrollToPosition(0)

            currentLine = -1
            if (mediaController != null) {
                if (audioManager.isMusicActive) {
                    syncedRows.clear()
                    mediaController!!.transportControls.seekTo(0)
                    btn_restart.text = "Restart"
                    btn_restart.paintFlags = 0
                } else {
                    syncedRows.clear()
                    mediaController!!.transportControls.seekTo(0)
                    mediaController!!.transportControls.play()
                    btn_restart.text = "Restart"
                    btn_restart.paintFlags = 0
                }
            } else {
                syncedRows.clear()
                btn_restart.text = "Start"
                btn_restart.paintFlags = Paint.STRIKE_THRU_TEXT_FLAG

                Toasty.normal(
                    requireContext(),
                    "No music Playing!\n(if music is already playing then pause and play it again)"
                ).show()
            }
        }

        next_line.setOnClickListener {
            if (mediaController != null) {
                if (audioManager.isMusicActive) {

                    if (currentLine == adapter.itemCount - 1) {
                        btn_submit_sync.performClick()
                    }

                    //// adjust scroll behaviour

                    if (currentLine < adapter.itemCount) {
                        currentLine++
                        Log.d(TAG, "onViewCreated: $currentLine")
                        val llm = sync_rv.layoutManager as LinearLayoutManager
                        val vh = sync_rv.findViewHolderForAdapterPosition(currentLine)
                        val height = vh?.itemView?.measuredHeight
                        val pvh =
                            if (currentLine != 0) sync_rv.findViewHolderForAdapterPosition(
                                currentLine - 1
                            ) else null
                        val tv = vh?.itemView as TextView?
                        val ptv = pvh?.itemView as TextView?
                        tv?.background =
                            ContextCompat.getDrawable(requireContext(), R.drawable.lyric_text_bg)
                        tv?.setTextAppearance(R.style.LyricsStyleBold)

                        ptv?.setBackgroundResource(0)
                        ptv?.setTextAppearance(R.style.LyricsStyleNormal)

                        val lastVisiblePosition = llm.findLastVisibleItemPosition()
                        val firstVisiblePosition = llm.findFirstVisibleItemPosition()

                        if (currentLine >= lastVisiblePosition) {
                            if ((currentLine + 8) >= adapter.itemCount) sync_rv.smoothScrollToPosition(
                                currentLine
                            ) else sync_rv.smoothScrollToPosition(
                                currentLine + 8
                            )
                        } else if (currentLine <= firstVisiblePosition) {
                            if ((currentLine - 4) <= 0) sync_rv.smoothScrollToPosition(currentLine) else sync_rv.smoothScrollToPosition(
                                currentLine - 4
                            )
                        } else if (currentLine >= 6) {
                            val scrollY =
                                if (currentLine == lastVisiblePosition) height?.times(6) else height
                            sync_rv.smoothScrollBy(0, scrollY ?: 0)
                        }

                        ///// create lrc rows

                        if (currentLine < listRows.size) {
                            val position = mediaController!!.playbackState?.position
                            val content = listRows[currentLine].content
                            if (position != null) {
                                syncedRows.add(LrcRow(position, content, millisToStr(position)))
                            }
                        }
                    } else {
                        Toasty.normal(requireContext(), "Restart the song to begin sync").show()
                    }
                } else {
                    Toasty.normal(requireContext(), "Start the song to begin sync").show()
                }

            } else {
                Toasty.normal(
                    requireContext(),
                    "No music Playing!\n(if music is already playing then pause and play it again)"
                ).show()
            }

        }

        previous_line.setOnClickListener {
            if (mediaController != null) {
                if (audioManager.isMusicActive) {
                    // adjust scrolling behaviour

                    if (currentLine >= 0) {
                        currentLine--
                        Log.d(TAG, "onViewCreated: $currentLine")
                        val llm = sync_rv.layoutManager as LinearLayoutManager
                        val vh =
                            if (currentLine >= 0) sync_rv.findViewHolderForAdapterPosition(
                                currentLine
                            ) else null
                        val height = vh?.itemView?.measuredHeight
                        val nvh =
                            if (currentLine < adapter.itemCount - 1) sync_rv.findViewHolderForAdapterPosition(
                                currentLine + 1
                            ) else null
                        val tv = vh?.itemView as TextView?
                        val ntv = nvh?.itemView as TextView?

                        val lastVisiblePosition = llm.findLastVisibleItemPosition()
                        val firstVisiblePosition = llm.findFirstVisibleItemPosition()

                        /// adjust scroll

                        if (currentLine >= lastVisiblePosition) {
                            if ((currentLine - 7) <= 0) sync_rv.smoothScrollToPosition(
                                currentLine
                            ) else sync_rv.smoothScrollToPosition(
                                currentLine + 7
                            )
                        } else if (currentLine <= firstVisiblePosition) {
                            if ((currentLine - 4) <= 0 && currentLine > -1) sync_rv.smoothScrollToPosition(
                                currentLine
                            ) else sync_rv.smoothScrollToPosition(
                                currentLine - 4
                            )
                        } else if (currentLine >= 6) {
                            val scrollY =
                                if (currentLine == lastVisiblePosition) height?.times(6) else height
                            sync_rv.smoothScrollBy(0, scrollY?.times(-1) ?: 0)
                        }

                        //// set line background

                        tv?.background =
                            ContextCompat.getDrawable(requireContext(), R.drawable.lyric_text_bg)
                        tv?.setTextAppearance(R.style.LyricsStyleBold)

                        ntv?.setBackgroundResource(0)
                        ntv?.setTextAppearance(R.style.LyricsStyleNormal)
                    }
                } else {
                    Toasty.normal(requireContext(), "Start the song to begin sync").show()
                }

                // create lrc rows

                if (syncedRows.isNotEmpty() && currentLine != -1) {
                    mediaController!!.transportControls.seekTo(syncedRows[currentLine].id)
                }
                if (currentLine + 1 < syncedRows.size) syncedRows.removeAt(currentLine + 1)

            } else {
                Toasty.normal(
                    requireContext(),
                    "No music Playing!\n(if music is already playing then pause and play it again)"
                ).show()
            }

        }

        btn_submit_sync.setOnClickListener {
            if (syncedRows.size >= listRows.size - 3) {
                currentLine = -1
                if (listRows.size > 0) {
                    val dialog = MaterialAlertDialogBuilder(requireContext())
                    dialog.setView(R.layout.submit_dialog_layout)
                        .setPositiveButton(
                            "Submit"
                        ) { dialog, _ ->
                            val dialogView = dialog as Dialog
                            val et = dialogView.findViewById<EditText>(R.id.user_name)
                            val userName = et.text.toString()
                            submitLyrics(userName)
                        }
                        .setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .setCancelable(false)
                        .show()
                } else {
                    Toasty.normal(requireContext(), "No lyrics to sync!")
                        .show()
                }
            } else {
                Toasty.normal(requireContext(), "Complete all the lines before submitting!").show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        currentLine = -1
        syncedRows.clear()
        sfShowing = false
    }

    override fun onResume() {
        super.onResume()
        sfShowing = true
        currentLine = -1
        requireActivity().runOnUiThread {
            sync_rv.adapter?.notifyDataSetChanged()

            if (currentTrack != null && currentArtist != null) {
                track_tv_sync.text = currentTrack
                artist_tv_sync.text = currentArtist
            } else if (mediaController != null) {
                val metadata = mediaController!!.metadata
                val mTrack = metadata!!.getString(MediaMetadata.METADATA_KEY_TITLE)
                val mArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)

                track_tv_sync.text = mTrack
                artist_tv_sync.text = mArtist
            }
        }
        setIcon()
        setRecyclerView()
    }

    @SuppressLint("SetTextI18n")
    private fun setIcon() {
        if (mediaController != null) {
            if (audioManager.isMusicActive) {
                btn_restart.text = "Restart"
                btn_restart.paintFlags = 0
            } else {
                btn_restart.text = "Start"
                btn_restart.paintFlags = 0
            }
        } else {
            btn_restart.text = "Start"
            btn_restart.paintFlags = Paint.STRIKE_THRU_TEXT_FLAG
        }
    }

    private fun writeToFile(lyrics: String?, track: String, artist: String) {
        try {
            val track1 = track.replace("/", "").replace("\\", "")
            val artist1 = artist.replace("/", "").replace("\\", "")
            val name = "userSynced $track1 $artist1.txt"
            val lyricsFile = File(requireContext().filesDir, name)
            if (lyricsFile.exists()) lyricsFile.delete()
            val fileWriter = FileWriter(lyricsFile)
            fileWriter.write(lyrics)
            fileWriter.flush()
            fileWriter.close()

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun submitLyrics(userName: String) {
        val sb = StringBuilder()
        if (userName.isNotEmpty() && userName.isNotBlank()) {
            sb.append("[00:00.50]Synced by : $userName")
        }
        syncedRows.forEach {
            sb.append("[${it.strTime}]${it.content}")
        }
        var lyrics = sb.toString()
        lyrics = lyrics.replace("[", "\n[")
        Log.d(TAG, "onViewCreated: $lyrics")

        if (track != null && artist != null) {

            // save lyrics to local storage

            writeToFile(lyrics, track!!, artist!!)
            Toasty.normal(requireContext(), "Lyrics synced successfully 🎉").show()

            // upload to firebase

            if (isOnline(requireContext())) {
                val track1 = track!!.replace("/", "").replace("\\", "")
                val artist1 = artist!!.replace("/", "").replace("\\", "")
                val name = "userSynced $track1 $artist1.txt"
                val lyricsFile = File(requireContext().filesDir, name)

                val lyricsRef = FirebaseStorage.getInstance().reference.child("userSynced/$name")
                lyricsRef.putFile(lyricsFile.toUri()).addOnSuccessListener {
                    Log.d(TAG, "submitLyrics: File Uploaded")
                    Toasty.normal(requireContext(), "Lyrics submitted").show()
                    lyricsRef.downloadUrl.addOnSuccessListener {
                        val data = hashMapOf(
                            "uri" to it.toString()
                        )
                        val db = FirebaseFirestore.getInstance()
                        db.collection("userSynced").document(name).set(data, SetOptions.merge())
                    }
                }.addOnFailureListener {
                    Log.e(TAG, "submitLyrics: ${it.stackTraceToString()}")
                }
            } else {
                Toasty.normal(requireContext(), "No internet").show()
            }

            //
        }
    }

    private fun setRecyclerView() {
        if (NotificationManagerCompat.getEnabledListenerPackages(requireContext())
                .contains(requireContext().packageName)
        ) {
            if (listRows.isEmpty()) {
                val iString = if (mediaController == null) {
                    if (!sharedPref.getBoolean(IS_SERVICE_STOPPED, false)) {
                        "Play music and come back here"
                    } else {
                        "Lyrics Service is stopped"
                    }
                } else {
                    "No Music Playing"
                }

                val instruction = LrcRow(0, iString, "00:00.00")
                listRows.add(instruction)
            } else {
                if (sharedPref.getBoolean(IS_SERVICE_STOPPED, false)) {
                    listRows.clear()
                    val instruction = LrcRow(0, "Lyrics Service is stopped", "00:00.00")
                    listRows.add(instruction)
                } else {
                    if (mediaController == null) {
                        listRows.clear()
                        val instruction = LrcRow(0, "Play music and come back here", "00:00.00")
                        listRows.add(instruction)
                    }
                }
            }
        } else {
            listRows.clear()
            val instruction = LrcRow(
                0,
                "Please allow notification access so that we can detect currently playing music",
                "00:00.00"
            )
            listRows.add(instruction)
        }

    }

}