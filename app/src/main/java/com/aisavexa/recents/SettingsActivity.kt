package com.aisavexa.recents

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { copyBackground(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        bindSlider(R.id.seekW, R.id.labelW, Prefs.KEY_W, 40, 400, Prefs.getW(this), "Width")
        bindSlider(R.id.seekH, R.id.labelH, Prefs.KEY_H, 20, 120, Prefs.getH(this), "Height")
        bindSlider(R.id.seekX, R.id.labelX, Prefs.KEY_X, 0, 400, Prefs.getX(this), "X")
        bindSlider(R.id.seekY, R.id.labelY, Prefs.KEY_Y, 0, 300, Prefs.getY(this), "Y")

        findViewById<Button>(R.id.pickBgButton).setOnClickListener {
            pickImage.launch("image/*")
        }
        findViewById<Button>(R.id.resetBgButton).setOnClickListener {
            Prefs.resetBackground(this)
            toast("Background default ho gaya")
        }
        findViewById<Button>(R.id.resetPosButton).setOnClickListener {
            Prefs.resetOverlay(this)
            reloadOverlay()
            recreate()
        }
    }

    // Settings kholne par box dikhega, band karne par gayab
    override fun onResume() {
        super.onResume()
        Prefs.setCalibrating(this, true)
        reloadOverlay()
    }

    override fun onPause() {
        Prefs.setCalibrating(this, false)
        reloadOverlay()
        super.onPause()
    }

    private fun bindSlider(
        seekId: Int,
        labelId: Int,
        key: String,
        min: Int,
        max: Int,
        current: Int,
        name: String
    ) {
        val seek = findViewById<SeekBar>(seekId)
        val label = findViewById<TextView>(labelId)

        seek.max = max - min
        seek.progress = current - min
        label.text = "$name: $current dp"

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress + min
                label.text = "$name: $value dp"
                if (fromUser) {
                    Prefs.setInt(this@SettingsActivity, key, value)
                    reloadOverlay()
                }
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun copyBackground(uri: Uri) {
        runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                Prefs.bgFile(this).outputStream().use { output -> input.copyTo(output) }
            }
        }.onSuccess {
            toast("Background set ho gaya")
        }.onFailure {
            toast("Image save nahi hui")
        }
    }

    private fun reloadOverlay() {
        sendBroadcast(Intent(Prefs.ACTION_RELOAD).setPackage(packageName))
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
