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
import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription.MIMETYPE_TEXT_PLAIN
import android.content.ClipboardManager
import com.google.protobuf.ByteString
import java.nio.ByteBuffer
import java.nio.ByteOrder


val GALLERY_REQUEST_CODE = 1000

class ClientSocket(host: String, port: Int) : Socket(host, port) {
    var rstream = inputStream
    var wstream =  outputStream

    fun get_msg(msg: ClipMsg.ClipMessage): ClipMsg.ClipMessage {
        var magic = byteArrayOf(0xd, 0xa)
        var data = msg.toByteArray()
        var magic_size = ByteBuffer.allocate(4)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(magic.size + data.size)
            .array();

        wstream.write(magic_size + magic + data)
        wstream.flush()
        Log.d("mytag", "write clip all ok")
        var img = rstream.readBytes()
        var msg = ClipMsg.ClipMessage.parseFrom(img)
        Log.d("mytag", "get clip all ok")
        this.close()
        return msg
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
                var msg = createClipMessageDefault()
                    .newBuilderForType()
                    .setStType(ClipMsg.ClipMessage.msgtype.MSG_GET)
                    .build()

                var s = ClientSocket(remote_host, remote_port)
                var msgret = s.get_msg(msg)
                if (msgret.stPaddingtype == ClipMsg.ClipMessage.paddingtype.PNG) {
                    imgWrite(msgret.stPadding.toByteArray())
                    Log.d("mytag", "get png")
                } else if (msgret.stPaddingtype == ClipMsg.ClipMessage.paddingtype.TXT) {
                    var clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager;
                    var txt = msgret.stPadding.toStringUtf8()
                    var clipdata = ClipData.newPlainText("labeltest", txt);
                    clipboard.primaryClip = clipdata;
                    Log.d("mytag", "get txt")
                } else {
                    // nothing
                    Log.e("mytag", msgret.toString())
                }
                runOnUiThread {
                    Toast.makeText(this, "get clip OK", Toast.LENGTH_SHORT).show()
                }
            }.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun pickFromGallery() {
        //Create an Intent with action as ACTION_PICK
        val intent = Intent(Intent.ACTION_PICK)
        // Sets the type as image/*. This ensures only components of type image are selected
        intent.type = "image/*"
        //We pass an extra array with the accepted mime types. This will ensure only components with these MIME types as targeted.
        val mimeTypes = arrayOf("image/png")
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        // Launching the Intent
        startActivityForResult(intent, GALLERY_REQUEST_CODE)
    }

    private fun push_txt() {
        var clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager;
        var pastedata: String = "";
        var ispasteallow = when {
            !clipboard.hasPrimaryClip() -> {
                false
            }
            !(clipboard.primaryClipDescription.hasMimeType(MIMETYPE_TEXT_PLAIN)) -> {
                // This disables the paste menu item, since the clipboard has data but it is not plain text
                false
            }
            else -> {
                // This enables the paste menu item, since the clipboard contains plain text.
                true
            }
        };
        if (ispasteallow) {
            val item = clipboard.primaryClip.getItemAt(0);
            var text = item.text;
            pastedata = if (text != null) {
                // If the string contains data, then the paste operation is done
                text.toString()
            } else {
                // The clipboard does not contain text.
                // If it contains a URI, attempts to get data from it
                val pasteUri: Uri? = item.uri

                if (pasteUri != null) {
                    // If the URI contains something, try to get text from it

                    // calls a routine to resolve the URI and get data from it. This routine is not
                    // presented here.
                    pasteUri.toString()
                } else {
                    // Something is wrong. The MIME type was plain text, but the clipboard does not
                    // contain either text or a Uri. Report an error.
                    Log.e("mytag","Clipboard contains an invalid data type")
                    ""
                }
            }
        }
        if (pastedata != "") {
            var data = ByteString.copyFrom(pastedata, "utf-8")
            var msg = createClipMessageDefault()
                .newBuilderForType()
                .setStSize(data.size())
                .setStType(ClipMsg.ClipMessage.msgtype.MSG_PUSH)
                .setStPadding(data)
                .setStPaddingtype(ClipMsg.ClipMessage.paddingtype.TXT)
                .build()
            var sharedpref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            var remote_host = sharedpref.getString("remote_host", "127.0.0.1");
            var remote_port_s = sharedpref.getString("remote_port", "0");
            if (remote_host == "127.0.0.1") {return}
            if (remote_port_s.toIntOrNull() == null) {return}
            var remote_port = remote_port_s.toInt()
            try {
                Thread {
                    var s = ClientSocket(remote_host, remote_port)
                    s.get_msg(msg)
                    runOnUiThread {
                        Toast.makeText(this, "push clip OK", Toast.LENGTH_SHORT).show()
                    }
                }.start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            Log.e("mytag","clipboard empty")
        }
    }

    @Throws(IOException::class)
    private fun readBytes(uri: Uri): ByteArray? =
        contentResolver.openInputStream(uri)?.buffered()?.use { it.readBytes() }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // Result code is RESULT_OK only if the user selects an Image
        if (resultCode == Activity.RESULT_OK)
            when (requestCode) {
                GALLERY_REQUEST_CODE -> {
                    //data.getData returns the content URI for the selected Image
                    val selectedImage = data!!.data
//                    imageView.setImageURI(selectedImage)

                    val input_data = ByteString.copyFrom(readBytes(selectedImage))

                    val textView: TextView = findViewById(R.id.hello) as TextView
                    textView.text = "select ok"

                    var msg = createClipMessageDefault()
                        .newBuilderForType()
                        .setStSize(input_data.size())
                        .setStType(ClipMsg.ClipMessage.msgtype.MSG_PUSH)
                        .setStPadding(input_data)
                        .setStPaddingtype(ClipMsg.ClipMessage.paddingtype.PNG)
                        .build()

                    var sharedpref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                    var remote_host = sharedpref.getString("remote_host", "127.0.0.1");
                    var remote_port_s = sharedpref.getString("remote_port", "0");
                    if (remote_host == "127.0.0.1") {return}
                    if (remote_port_s.toIntOrNull() == null) {return}
                    var remote_port = remote_port_s.toInt()
                    try {
                        Thread {
                            var s = ClientSocket(remote_host, remote_port)
                            s.get_msg(msg)
                            runOnUiThread {
                                Toast.makeText(this, "push clip OK", Toast.LENGTH_SHORT).show()
                            }
                        }.start()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
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

        fab3.setOnClickListener {
            pickFromGallery()
        }

        fab_push_txt.setOnClickListener {
            push_txt()
        }
    }

    private fun createClipMessageDefault(): ClipMsg.ClipMessage {
        var data = ByteString.EMPTY;
        val msg_push = ClipMsg.ClipMessage.newBuilder()
            .setStName(1)
            .setStSize(data.size())
            .setStType(ClipMsg.ClipMessage.msgtype.MSG_PUSH)
            .setStPadding(data)
            .setStPaddingtype(ClipMsg.ClipMessage.paddingtype.PNG)
            .build()
        return msg_push
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
