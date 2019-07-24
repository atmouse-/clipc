package com.atmouse.clipc

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.preference.PreferenceManager
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity;
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast

import kotlinx.android.synthetic.main.activity_main.*
import java.io.*
import java.net.Socket
import java.util.*
import android.support.v4.content.FileProvider



class ClientSocket(host: String, port: Int) : Socket(host, port) {
    var rstream = inputStream
    var wstream =  outputStream

    fun update(): ByteArray {
        var magic = byteArrayOf(0x10, 0x1, 0x18, 0x3)
        wstream.write(magic)
        Log.d("mytag", "write clip all ok")
        var img = rstream.readBytes()
        Log.d("mytag", "get clip all ok")
        this.close()
        return img
    }

    fun File.copyInputStreamToFile(inputStream: InputStream) {
        inputStream.use { input ->
            this.outputStream().use { fileOut ->
                input.copyTo(fileOut)
            }
        }
    }
}

class MainActivity : AppCompatActivity() {
    companion object {
        const val REQUEST_PERMISSION = 1
    }

    private fun imgWrite(img: ByteArray) {
        val file = "%1\$tY%1\$tm%1\$td%1\$tH%1\$tM%1\$tS.png".format(Date())
        this.latest_imgname = file
        File(getExternalFilesDir(null), this.latest_imgname).writeBytes(img)
    }

    private var latest_imgname: String = ""

    private fun imgShare() {
        if (latest_imgname == "") {return}
        var imgfile = File(getExternalFilesDir(null), this.latest_imgname)
        val imgUri = FileProvider.getUriForFile(
            this,
            BuildConfig.APPLICATION_ID + ".fileprovider", imgfile
        )
        val imgIntent = Intent(Intent.ACTION_VIEW)
        imgIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        imgIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        imgIntent.setDataAndType(imgUri, "image/png")
        startActivity(imgIntent)
    }

    private fun getClip() {
        var sharedpref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        var remote_host = sharedpref.getString("remote_host", "127.0.0.1");
        var remote_port_s = sharedpref.getString("remote_port", "0");
        if (remote_host == "127.0.0.1") {return}
        if (remote_port_s.toIntOrNull() == null) {return}
        var remote_port = remote_port_s.toInt()
        try {
            Thread {
                var s = ClientSocket(remote_host, remote_port)
                var img = s.update()
                imgWrite(img)
                runOnUiThread {
                    Toast.makeText(this, "get clip OK", Toast.LENGTH_SHORT).show()
                }
            }.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStart() {
        super.onStart()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_PERMISSION -> if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        fab.setOnClickListener { view ->
            val textView: TextView = findViewById(R.id.hello) as TextView
            textView.text = "get OK"
            getClip()
        }

        fab2.setOnClickListener {
            imgShare()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> {
                var intent = Intent(this, PrefsActivity::class.java)
                startActivity(intent)
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
