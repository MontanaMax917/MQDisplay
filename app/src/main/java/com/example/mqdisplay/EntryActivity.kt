package com.example.mqdisplay

import android.app.Activity
import android.content.Intent
import android.os.Bundle

// https://stackoverflow.com/questions/17474793/conditionally-set-first-activity-in-android
class EntryActivity : Activity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // launch a different activity
        val launchIntent = Intent()
        var launchActivity: Class<*>?

        try {
            val className = screenClassName
            launchActivity = Class.forName(className)
        } catch (e: ClassNotFoundException) {
            launchActivity = SettingFragment::class.java
        }
        launchIntent.setClass(applicationContext, launchActivity!!)
        startActivity(launchIntent)

        finish()
    }

    private val screenClassName: String
        /** return Class name of Activity to show  */
        get() {
            // NOTE - Place logic here to determine which screen to show next
//            var host: String
//            var broker: String
            // Fetching the stored data from the SharedPreference
            val sh = getSharedPreferences("MQDisplay", MODE_PRIVATE)
            val host = sh.getString("host", "0").toString()
            val broker = sh.getString("broker", "0").toString()

            println("screenClassName --- $host --- $broker ---")

            if (host.isNotEmpty() && broker.length > 3) {
                val activity = MainActivity::class.java.name
                println("set Activity $activity ---------------")
                return activity
            } else {
                // Default is used in this demo code
                val activity = SettingFragment::class.java.name
                println("not set Activity $activity ---------------")
                return activity
            }

        }

}