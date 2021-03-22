package com.rikkuu.testingcompressffmpeg

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatSpinner
import androidx.core.app.ActivityCompat
import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.FFmpeg
import com.arthenica.mobileffmpeg.Statistics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal

class MainActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var btnPickImage : AppCompatButton
    private lateinit var btnCompress : AppCompatButton
    private lateinit var tvResponse : TextView
    private lateinit var spinnerCodec : AppCompatSpinner

    private var selectedCodec = ""
    private var durationVideo : Long = 0

    private var uriCompress: Uri? = null
    private var statistics : Statistics? = null

    private var progressDialog : AlertDialog? = null

    companion object {
        const val PICK_IMAGE_REQ_CODE = 1991
        const val STORAGE_PERMISSION_REQ_CODE = 12321
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initDialog(this)
        initComponent()
    }

    private fun initComponent() {
        btnPickImage = findViewById(R.id.btnPickImage)
        btnCompress = findViewById(R.id.btnCompress)
        tvResponse = findViewById(R.id.tvResponse)
        spinnerCodec = findViewById(R.id.codecSpinner)
        val adapter = ArrayAdapter.createFromResource(this, R.array.video_codec, R.layout.spinner_layout)
        spinnerCodec.adapter = adapter
        spinnerCodec.onItemSelectedListener =  object : AdapterView.OnItemSelectedListener{
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                toast(parent?.getItemAtPosition(position).toString())
                selectedCodec = parent?.getItemAtPosition(position).toString()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}

        }
        btnCompress.isEnabled = false
        btnPickImage.setOnClickListener(this)
        btnCompress.setOnClickListener(this)
    }

    private fun compressVideo(uri: Uri?, filename : String) {
        log("Compress Video is started")
        showDialog()
        getDurationVideo()
        if (durationVideo <= 0){
            toast("Duration is zero")
            return
        }
        log("Duration Video $durationVideo")
        if (uri == null){
            log("uri is null")
            return
        }
        val path = File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_MOVIES + "/TestingFFMPEG")
        try {
            if (!path.exists()){
                path.mkdir()
            }
        }catch (securityException: Exception){
            securityException.printStackTrace()
            log("${securityException.message}")
        }

        val date = System.currentTimeMillis()
        val cv = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$filename.mp4")
            put(MediaStore.Video.Media.DATA, path.path + "/$filename.mp4")
            put(MediaStore.Video.Media.DATE_ADDED, date)
            put(MediaStore.Video.Media.DATE_MODIFIED, date)
        }
        contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv)

        val command = "-i ${getRealPathByUri(this, uri)} -vsync 2 -async 1 ${getCustomOptions()} -c:v ${getSelectedCodec()} ${path.path}/$filename.${getExtension()}"
        log(command)

        val executionId = FFmpeg.executeAsync(command) { _, returnCode ->
            hideDialog()
            when(returnCode) {
                Config.RETURN_CODE_SUCCESS -> {
                    toast("Success compress")
                    log("Execution complete successfully")
                }
                Config.RETURN_CODE_CANCEL -> {
                    log("Execution cancelled by user")
                }
                else -> {
                    log("Else branch, with returnCode $returnCode")
                }
            }
        }
        log("End Compress, executionId : $executionId")
    }

    private fun getExtension() : String {
        return when(selectedCodec){
            "vp8", "vp9" -> "webm"
            "aom" -> "mkv"
            "theora" -> "ogv"
            "hap" -> "mov"
            else -> "mp4"
        }
    }

    private fun getCustomOptions() : String {
        return when(selectedCodec) {
            "x265" -> "-crf 28 -preset fast "
            "vp8" -> "-b:v 1M -crf 10 "
            "vp9" ->  "-b:v 2M "
            "aom" -> "-crf 30 -strict experimental "
            "theora" -> "-qscale:v 7 "
            "hap" -> "-format hap_q "
            else -> ""
        }
    }

    private fun getSelectedCodec() : String {
        return when(selectedCodec){
            "x264" -> "libx264"
            "openh264" -> "libopenh264"
            "x265" -> "libx265"
            "xvid" -> "libxvid"
            "vp8" -> "libvpx"
            "vp9" -> "libvpx-vp9"
            "aom" -> "libaom-av1"
            "kvazaar" -> "libkvazaar"
            "theora" -> "libtheora"
            else -> ""
        }
    }

    private fun setActive(){
        Config.enableStatisticsCallback { stats ->
            GlobalScope.launch {
                withContext(Dispatchers.Main){
                    statistics = stats
                    updateDialog(durationVideo)
                }
            }
        }
    }

    private fun updateDialog(totalDuration: Long){
        statistics?.let { stats ->
            val timeInMillis = stats.time
            log("TimeInMillis: $timeInMillis")
            if (timeInMillis > 0){
                // Wrong total duration
//                val totalDuration = 9000
                val completePercentage = BigDecimal(timeInMillis).multiply(BigDecimal(100)).divide(
                    BigDecimal(totalDuration), 0, BigDecimal.ROUND_HALF_UP
                ).toString()
                val tv = progressDialog?.findViewById<TextView>(R.id.progressDialogText)
                tv?.let {
                    tv.text = String.format("Encoding video : %% %s.", completePercentage)
                }
            }
        }
    }

    private fun getDurationVideo(){
        uriCompress?.let {
            val mediaMetadataRetriever = MediaMetadataRetriever()
            mediaMetadataRetriever.setDataSource(getRealPathByUri(this, it))
            val time = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationVideo = time?.toLong() ?: 0
        }
    }

    private fun checkPermission(onGranted:() -> Unit){
        val readStorage = ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        val writeStorage = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (readStorage == PackageManager.PERMISSION_GRANTED && writeStorage == PackageManager.PERMISSION_GRANTED) {
            onGranted()
        }else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_PERMISSION_REQ_CODE)
        }
    }

    private fun showDialog(){
        Config.resetStatistics()
        if (progressDialog == null ){
            initDialog(this)
        }
        progressDialog?.show()
    }

    private fun hideDialog(){
        progressDialog?.dismiss()
        GlobalScope.launch {
            withContext(Dispatchers.Main){
                initDialog(this@MainActivity)
            }
        }
    }

    private fun initDialog(context: Context){
        progressDialog = DialogUtil.createProgressDialog(context, "Encoding video")
    }

    override fun onResume() {
        super.onResume()
        setActive()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode){
            STORAGE_PERMISSION_REQ_CODE -> {
                checkPermission {
                    pickVideo()
                }
            }
            else -> {
                toast("Request Code not found onRequestPermissionResult")
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when(requestCode){
            PICK_IMAGE_REQ_CODE -> {
                log("requestCode $requestCode, resultCode : $resultCode, data : ${data.toString()}")
                if (resultCode == RESULT_OK && data != null){
                    btnCompress.isEnabled = true
                    uriCompress = data.data
                }
            }
            else -> {
                log("Request Code Not Found")
            }
        }
    }

    override fun onClick(v: View?) {
        when(v?.id){
            R.id.btnCompress -> {
                log("Btn compress clicked")
                val date = System.currentTimeMillis()
                compressVideo(uriCompress, "compress_$date")
            }
            R.id.btnPickImage -> {
                checkPermission {
                    pickVideo()
                }
            }
            else -> {

            }
        }
    }

    private fun pickVideo(){
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).apply {
            type = "video/mp4"
        }
        startActivityForResult(intent, PICK_IMAGE_REQ_CODE)
    }

    private fun getRealPathByUri(context : Context, uri : Uri) : String? {
        var result : String? = null
        var cursor : Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.let{ resultCursor ->
                if (resultCursor.moveToFirst()){
                    val columnId = resultCursor.getColumnIndex(MediaStore.Video.Media.DATA)
                    result = cursor.getString(columnId)
                }
            }
        }catch (exception: Exception){
            exception.printStackTrace()
            log("${exception.message}")
        }finally {
            cursor?.close()
        }
        log("Path : $result")
        return result
    }

    private fun log(msg: String){
        Log.d("IniLogDoang", "Msg: $msg")
    }

    private fun toast(msg : String){
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

}