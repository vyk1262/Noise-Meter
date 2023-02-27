package co.novolytics.noisemeter

import android.Manifest
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.media.MediaRecorder
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.concurrent.thread
import kotlin.math.log10

const val REQUEST_CODE = 200

class MainActivity : AppCompatActivity(), Timer.OnTimerTickListener, Decibel.OnDecibelListener {

    var permissions = arrayOf(Manifest.permission.RECORD_AUDIO)
    private var permissionGranted = false

    private lateinit var recorder: MediaRecorder

    private var dirPath = ""
    private var filename = ""

    private var isRecording = false
    private var isPaused = false

    private var dbList = ArrayList<Double>()
    var id = ""

    var startTime: LocalDateTime = LocalDateTime.now()

    private lateinit var timer: Timer
    private lateinit var decibel: Decibel

    val dbData : MutableMap<String, Any> = HashMap()
    val fireStoreDatabase = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        permissionGranted = ActivityCompat.checkSelfPermission(this, permissions[0]) == PackageManager.PERMISSION_GRANTED

        if(!permissionGranted)
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE)

        timer = Timer(this)
        decibel = Decibel(this)

        id = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        btnRecord.setOnClickListener {
            when{
                isPaused -> resumeRecorder()
                isRecording -> pauseRecorder()
                else -> startRecording()
            }
        }

        btnDone.setOnClickListener {
            stopRecorder()
            File("$dirPath$filename.mp3")
            Toast.makeText(this, "Record Saved", Toast.LENGTH_SHORT).show()
        }

        btnDone.isClickable = false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if(requestCode == REQUEST_CODE)
            permissionGranted = grantResults[0] == PackageManager.PERMISSION_GRANTED
    }

    private fun pauseRecorder(){
        recorder.pause()
        isPaused = true
        btnRecord.setImageResource(R.drawable.ic_record)

        timer.pause()
        decibel.pause()
    }

    private fun resumeRecorder(){
        recorder.resume()
        isPaused = false
        btnRecord.setImageResource(R.drawable.ic_pause)

        timer.start()
        decibel.start()
    }

    private fun startRecording() {
        if (!permissionGranted) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE)
            return
        }

        // start recording
        recorder = MediaRecorder()
        dirPath = "${externalCacheDir?.absolutePath}/"

        var simpleDateFormat = SimpleDateFormat("yyyy.MM.DD_hh.mm.ss")
        var date = simpleDateFormat.format(Date())
        filename = "audio_record_$date"

        startTime = LocalDateTime.now()

        recorder.apply{
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile("$dirPath$filename.mp3")

            try {
                prepare()
            }catch (e: IOException){}

            start()

//            tvDecibel.text = recorder.maxAmplitude.toString()
        }

        btnRecord.setImageResource(R.drawable.ic_pause)
        isRecording = true
        isPaused = false

        timer.start()
        decibel.start()

        btnDone.isClickable = true
        btnDone.setImageResource(R.drawable.ic_done)
    }

    private fun stopRecorder() {
        timer.stop()
        decibel.stop()
        recorder.apply {
            stop()
            release()
        }
        isPaused = false
        isRecording = false

        val stopTime = LocalDateTime.now()

        val finalDbList = dbList.drop(1)
        println("dbList $finalDbList")
        println("StartTime: $startTime")
        println("StopTime: $stopTime")
        println("android Id: $id")
        dbList = ArrayList<Double>()

        dbData["Decibels"] = finalDbList
        dbData["StartTime"] = startTime
        dbData["StopTime"] = stopTime
        dbData["DeviceId"] = id

        fireStoreDatabase.collection("dbData")
            .add(dbData)
            .addOnSuccessListener {
                Log.d(TAG, "Added document with ID ${it.id}")
            }
            .addOnFailureListener {
                Log.d(TAG, "Error adding document $it")
            }

        btnDone.isClickable = false
        btnDone.setImageResource(R.drawable.ic_done_disabled)
        btnRecord.setImageResource(R.drawable.ic_record)

        tvTimer.text = "00:00.00"
    }

    override fun onTimerTick(duration: String) {
        tvTimer.text = duration
    }

    override fun onDecibel(duration: String) {
        val amp = recorder.maxAmplitude.toDouble()
        val lamp = 20 * log10(amp)
        val new = String.format("%.2f", lamp).toDouble()
        dbList.add(new)
        //tvDecibel.text = recorder.maxAmplitude.toString()
    }
}