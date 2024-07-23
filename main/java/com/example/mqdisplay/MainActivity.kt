package com.example.mqdisplay

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import java.io.IOException
import org.videolan.libvlc.MediaPlayer as vlcMediaPlayer

private const val USE_TEXTURE_VIEW = false      // not surfaceView
private const val ENABLE_SUBTITLES = false      // no subtitles from ip cameras
private var mpAudio = MediaPlayer()             // prevent garbage collect
private var audios = mutableListOf<String>()    // list of audio files to play

class MainActivity : AppCompatActivity() {
    private var mLibVLC: LibVLC? = null
    private var mpVideo: vlcMediaPlayer? = null

    //    MQTT initial settings
    //    private var audio = "http://192.168.1.3/voice/speaker_setup_complete.wav"  // served by
    //    private var video = "rtsp://admin:password@192.168.1.20:554/cam/realmonitor?channel=3&subtype=2"  // Lorex Dahua camers
    private var host: String = ""   // kitchen, shop, phone, etc
    private var broker: String = "" // tcp://192.168.1.3:1883
//    private var audio = " "         // must have at least one char
    private var video = " "         // must have at least one char
//    private var device = ""         // filled in later
    private var output = "ring"     // default audio channel
    private var volume = "80"       // audio volume 0 to 100%
//    private var brightness = "80"   // screen brightness 0 to 100% (0 to 1.0f)
//    private var mMessage = "Hello"  // message on screen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fetching the stored data from the SharedPreference
        val sh = getSharedPreferences("MQDisplay", MODE_PRIVATE)
        host = sh.getString("host", "0").toString()
        broker = sh.getString("broker", "0").toString()

        initVideo()         // create video player view
        initMQTT("init")    // connect to MQTT broker
    }

    override fun onStart() {
        super.onStart()
        playVideo(video)        // (re)start default video stream
    }

    override fun onStop() {
        super.onStop()
        mpVideo?.stop()
        mpVideo?.detachViews()
    }

    override fun onDestroy() {
        super.onDestroy()
        initMQTT("destroy")   // cheesy way to terminate mqtt - can't reference 'client' from here
        mpVideo?.release()
        mLibVLC?.release()
    }
// Video routines -------------------------------------------------------------------------------------
    private fun initVideo() {
        setContentView(R.layout.activity_main)  // layout for video screen
        mLibVLC = LibVLC(this, ArrayList<String>().apply {
            add("--drop-late-frames")    // reduce latency
            add("--skip-frames")
//            add("--no-rtsp-tcp")      // use UDP  - causes out of sequence frames
            add("--rtsp-tcp")           // use TCP
            add("-vv")                 // verbose logging #vs = log level 1,2,3
            add("--no-audio")           // turn off camera audio
            add("--network-caching=150")
            add("--clock-jitter=0")
            add("--clock-synchro=0")
        })
        mpVideo = vlcMediaPlayer(mLibVLC)  // create the video mediaplayer
    }  // initVideo

    private fun playVideo(url: String) {
        mpVideo?.attachViews(findViewById(R.id.videoLayout), null, ENABLE_SUBTITLES, USE_TEXTURE_VIEW)
        try {
            Media(mLibVLC, Uri.parse(url)).apply {     // create a media class and configure it
                setHWDecoderEnabled(true, false)
                mpVideo?.media = this  // assign it to the media player
            }.release()  // release as no longer needed

            mpVideo?.play()  // play the media
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }  // playVideo

// Audio routines -------------------------------------------------------------------------------
    private fun playAudio(url: String, output: String, volume: String) {

        if (url.length < 2) {   // exit if invalid
            return
        }
        audios.add(url)         // add to list of files to play
        if (mpAudio.isPlaying) {
            return             // exit if already playing
        }
        val vol = string2float(volume)
        mpAudio.setVolume(vol, vol)

        mpAudio.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(output2int(output)) // set audio output channel
                .build()
        )
        mpAudio.setOnPreparedListener {
            mpAudio.start()
        }
        mpAudio.setOnCompletionListener {
            if (audios.size > 0) {          // play audio files in sequence
                mpAudio.reset()
                mpAudio.setDataSource(audios.removeFirst())
                mpAudio.prepareAsync()      // prepare in different thread
            } else {
            mpAudio.stop()
            mpAudio.reset()
//            mpAudio.release()  // reuse original player
            }
        }
        mpAudio.setDataSource(audios.removeFirst())
        mpAudio.prepareAsync()   // prepare in different thread
    }   // playAudio

// MQTT routines ---------------------------------------------------------------
    @Throws(MqttException::class)
    fun initMQTT(job:String) {
        lateinit var client: MqttClient
        val clientId = "MQDisplay-$host"    // no spaces else fails
        val subQos = 0                      // do not retain messages
        val tAll = "MQDisplay/all/+"        // all devices
        val tThis = "MQDisplay/$host/+"     // this device
        val tDevice = "MQDisplay/$host/device"

        if (job == "destroy") {
            if (client.isConnected) {
                client.unsubscribe(tAll)    // fix issue with buffering messages
                client.unsubscribe(tThis)
                client.disconnect()  // stops mqtt
                client.close()
                return
            }
        }
        // job == "init"
        try {
                client = MqttClient(
                broker,  //URI
                clientId,  //ClientId
                MemoryPersistence()    //Persistence
            )

            val options = MqttConnectOptions()
            options.isAutomaticReconnect = true
            options.isCleanSession = false
            client.connect(options)

//          callback runs in thread not main UiThread
            if (client.isConnected) {
                client.setCallback(object : MqttCallback {
                    @Throws(Exception::class)
                    override fun messageArrived(topic: String, message: MqttMessage) {
                        println("topic: $topic")
                        println("message content: " + String(message.payload))
//                        val msg = getMsg(message)
//                        showToast("$topic $msg")  // thread safe toast
                        val arr = topic.split("/")
                        val theTopic = arr.last()
                        val theMessage = getMsg(message)

                        when (theTopic) {
                            "audio" -> playAudio(url = theMessage, output = output, volume = volume)
                            "video" -> {
                                video = theMessage
                                mpVideo?.play(Uri.parse(video))     // change video stream
                            }
                            "output" -> output = theMessage
                            "volume" -> volume = theMessage
                            "brightness" -> setBrightness(theMessage)
                            "message" -> showToast(theMessage)
                        }
                    }

                    override fun connectionLost(cause: Throwable) {
                        println("connectionLost: " + cause.message)
                        showToast("MQTT connection lost")
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken) {
                        println("deliveryComplete: " + token.isComplete)
                    }
                })
                client.subscribe(tAll, subQos)      // all devices
                client.subscribe(tThis, subQos)     // this device
                client.publish(tDevice, makeMsg(getDeviceName()))   // publish device info
            }

        } catch (e: MqttException) {
            e.printStackTrace()
            showToast("MQTT connect failed")
        }
        return
    } // initMQTT

// helper routines -------------------------------------------------------
    private fun output2int(outp: String): Int {
        if (outp.equals("media", true)) { return 1 }
        if (outp.equals("alarm", true)) { return 4 }
        if (outp.equals("notification", true)) { return 5 }
        if (outp.equals("ring", true)) { return 6 }
        return 1 // default
    }  // output2int
    private fun string2float(str: String): Float {
        val vol = str.toFloatOrNull()
        if (vol != null) {
            return (vol / 100f)       // convert percent to float
        } else {
            return 0f
        }
    }  // string2float

    private fun makeMsg(message: String): MqttMessage {
        val msg = MqttMessage(message.toByteArray())
        msg.qos = 0
        msg.isRetained = false
        return msg
    }  // makeMsg

    private fun getMsg(message: MqttMessage): String {
        val msg = String(message.payload).trim()
        return msg
    }  // getMsg

    private fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        if (model.startsWith(manufacturer)) {
            return model
        }
        return "$manufacturer $model"
    } // getDeviceName

    private fun setBrightness(bright: String) {     // thread safe brightness
        val br = string2float(bright)       // percent to float
        runOnUiThread {
            window.attributes = window.attributes.also {
                it.screenBrightness = br
            }
        }
    }   // setBrightness

    private fun showToast(toast: String?) {         //  thread safe toast
        runOnUiThread {
            Toast.makeText(this@MainActivity, toast, Toast.LENGTH_SHORT).show()
        }
    }  // showToast
}  // MainActivity