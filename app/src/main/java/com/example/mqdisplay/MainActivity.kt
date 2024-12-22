package com.example.mqdisplay

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.widget.TextView
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
        setContentView(R.layout.activity_main)

        // Fetching the stored data from the SharedPreference
        val sh = getSharedPreferences("MQDisplay", MODE_PRIVATE)
        host = sh.getString("host", "0").toString()
        broker = sh.getString("broker", "0").toString()

        applicationContext.cacheDir.deleteRecursively()  // clear cache

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
            add("--no-video-title-show")    // xyz
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
                            "text" -> showText(theMessage)
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

    private fun showText(text: String) {
        // see activity_main.xml to change font, color, etc.
        val textview: TextView = findViewById(R.id.textLayout) as TextView
        var txt: String = text

//        txt = "45 deg\\n27 mph\\n21 ppm"
//        txt = "html: <font color=green>45</font>&deg<br> <font color=blue>27</font>&nwarr<br> <font color=red>21</font>&micro"

        if (txt.startsWith("html:",0,true)) {
            txt = txt.substring(5,txt.length)   // remove 'html:' from string
            txt = txt.trim()                    // remove leading space if exists
            textview.setText(Html.fromHtml(txt,0))
        } else {
            // plain text - fix newline
            txt = txt.replace("\\n", System.getProperty("line.separator"))
            textview.text = txt
        }

    }

}  // MainActivity

/*

    ← &#8592; &larr; Left arrow.
    ↑ &#8593; &uarr; Up arrow.
    → &#8594; &rarr; Right arrow.
    ↓ &#8594; &darr; Down arrow.
    ↖ &#8598; &nwarr; Northwest arrow.
    ↗ &#8599; &nearr; Northeast arrow.
    ↙ &#8600; &swarr; Southwest arrow.
    ↘ &#8601; &searr; Southeast arrow.

* REFERENCE       DESCRIPTION
--------------  -----------
&#00; - &#08;   Unused
&#09;     Horizontal tab
&#10;     Line feed
&#11; - &#12;   Unused
&#13;     Carriage Return
&#14; - &#31;   Unused
&#32;     Space
&#33;     Exclamation mark
&#34;     Quotation mark
&#35;     Number sign
&#36;     Dollar sign
&#37;     Percent sign
&#38;     Ampersand
&#39;     Apostrophe
&#40;     Left parenthesis
&#41;     Right parenthesis
&#42;     Asterisk
&#43;     Plus sign
&#44;     Comma
&#45;     Hyphen
&#46;     Period (fullstop)
&#47;     Solidus (slash)
&#48; - &#57;   Digits 0-9
&#58;     Colon
&#59;     Semi-colon
&#60;     Less than
&#61;     Equals sign
&#62;     Greater than
&#63;     Question mark
&#64;     Commercial at
&#65; - &#90;   Letters A-Z
&#91;     Left square bracket
&#92;     Reverse solidus (backslash)
&#93;     Right square bracket
&#94;     Caret
&#95;     Horizontal bar (underscore)
&#96;     Acute accent
&#97; - &#122;  Letters a-z
&#123;   Left curly brace
&#124;   Vertical bar
&#125;   Right curly brace
&#126;   Tilde
&#127; - &#159; Unused
&#160;          Non-breaking Space
&#161;   Inverted exclamation
&#162;   Cent sign
&#163;   Pound sterling
&#164;   General currency sign
&#165;   Yen sign
&#166;   Broken vertical bar
&#167;   Section sign
&#168;   Umlaut (dieresis)
&#169;   Copyright
&#170;   Feminine ordinal
&#171;   Left angle quote, guillemotleft
&#172;   Not sign
&#173;   Soft hyphen
&#174;   Registered trademark
&#175;   Macron accent
&#176;   Degree sign
&#177;   Plus or minus
&#178;   Superscript two
&#179;   Superscript three
&#180;   Acute accent
&#181;   Micro sign
&#182;   Paragraph sign
&#183;   Middle dot
&#184;   Cedilla
&#185;   Superscript one
&#186;   Masculine ordinal
&#187;   Right angle quote, guillemotright
&#188;   Fraction one-fourth
&#189;   Fraction one-half
&#190;   Fraction three-fourths
&#191;   Inverted question mark
&#192;   Capital A, grave accent
&#193;   Capital A, acute accent
&#194;   Capital A, circumflex accent
&#195;   Capital A, tilde
&#196;   Capital A, dieresis or umlaut mark
&#197;   Capital A, ring
&#198;   Capital AE dipthong (ligature)
&#199;   Capital C, cedilla
&#200;   Capital E, grave accent
&#201;   Capital E, acute accent
&#202;   Capital E, circumflex accent
&#203;   Capital E, dieresis or umlaut mark
&#204;   Capital I, grave accent
&#205;   Capital I, acute accent
&#206;   Capital I, circumflex accent
&#207;   Capital I, dieresis or umlaut mark
&#208;   Capital Eth, Icelandic
&#209;   Capital N, tilde
&#210;   Capital O, grave accent
&#211;   Capital O, acute accent
&#212;   Capital O, circumflex accent
&#213;   Capital O, tilde
&#214;   Capital O, dieresis or umlaut mark
&#215;   Multiply sign
&#216;   Capital O, slash
&#217;   Capital U, grave accent
&#218;   Capital U, acute accent
&#219;   Capital U, circumflex accent
&#220;   Capital U, dieresis or umlaut mark
&#221;   Capital Y, acute accent
&#222;   Capital THORN, Icelandic
&#223;   Small sharp s, German (sz ligature)
&#224;   Small a, grave accent
&#225;   Small a, acute accent
&#226;   Small a, circumflex accent
&#227;   Small a, tilde
&#228;   Small a, dieresis or umlaut mark
&#229;   Small a, ring
&#230;   Small ae dipthong (ligature)
&#231;   Small c, cedilla
&#232;   Small e, grave accent
&#233;   Small e, acute accent
&#234;   Small e, circumflex accent
&#235;   Small e, dieresis or umlaut mark
&#236;   Small i, grave accent
&#237;   Small i, acute accent
&#238;   Small i, circumflex accent
&#239;   Small i, dieresis or umlaut mark
&#240;   Small eth, Icelandic
&#241;   Small n, tilde
&#242;   Small o, grave accent
&#243;   Small o, acute accent
&#244;   Small o, circumflex accent
&#245;   Small o, tilde
&#246;   Small o, dieresis or umlaut mark
&#247;   Division sign
&#248;   Small o, slash
&#249;   Small u, grave accent
&#250;   Small u, acute accent
&#251;   Small u, circumflex accent
&#252;   Small u, dieresis or umlaut mark
&#253;   Small y, acute accent
&#254;   Small thorn, Icelandic
&#255;   Small y, dieresis or umlaut mark
* */