package com.example.mqdisplay

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class SettingFragment : AppCompatActivity() {
    private lateinit var host: EditText
    private lateinit var broker: EditText
    private lateinit var buttonSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_setting)
        host = findViewById(R.id.hostName)
        broker = findViewById(R.id.mqttBroker)
        buttonSave = findViewById<Button>(R.id.btnSave)

        buttonSave.setOnClickListener {
            onPause()       // save settings
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()    // close activity and return to MainActivity
        }
    }

    // Fetch the stored data in onResume() Because this is what will be called when the app opens again
    override fun onResume() {
        super.onResume()
        // Fetching the stored data from the SharedPreference
        val sh = getSharedPreferences("MQDisplay", MODE_PRIVATE)
        val s1 = sh.getString("host", "")
        val s2 = sh.getString("broker", "")

        // Setting the fetched data in the EditTexts
        host.setText(s1)
        broker.setText(s2)
    }

    // Store the data in the SharedPreference in the onPause() method
    // When the user closes the application onPause() will be called and data will be stored
    override fun onPause() {
        super.onPause()
        // Creating a shared pref object with a file host "MySharedPref" in private mode
        val sharedPreferences = getSharedPreferences("MQDisplay", MODE_PRIVATE)
        val myEdit = sharedPreferences.edit()

        // write all the data entered by the user in SharedPreference and apply
        myEdit.putString("host", host.text.toString())
        myEdit.putString("broker", broker.text.toString())
        myEdit.apply()
    }


}