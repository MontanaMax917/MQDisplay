Readme.md

MQDisplay

Android display controlled by MQTT commands.
The camera view can change to follow activity. An audio file can be played
to announce 'car in driveway', 'person at door', 'garage door open'.
The brightness and volume can be adjusted for the time of day.
Would be great addition to 'Home Assistant' projects

Features:
- simply connect to MQTT broker and control display using topics
- set rtsp camera url to display
- set http audio file to play
- set audio volume
- set screen brightness
- set short screen message
- set topic for all devices or individual device
- uses libVLC for fast, compatible video rendering
- uses paho client for mqtt communications

Supported devices:
works with local wifi connected devices
- Android tablets (Android 8 and newer)
- Android TV (Onn tv box)
- Android phones

Typical setup:
Lorex nvr -> event monitor -> Node Red -> mqtt broker -> MQDisplay

Note:
This is an introductory readme file and will be update when the code is posted.
This is my first android project. This project works great on the devices that
I have. This is minimal code. All other projects that I researched included
lots of 'extra' features with too many lines of code.

ToDo list:
- this code works, but need help to make it work better
- add documentation for all aspects of project
- update to build Google Play Store app
- update to include Android tablet, phone, tv installation from app store
- include open source license to meet git and GPStore requirements
- include instructions for:
-   event monitoring of lorex nvr
-   node red controls
-   Mosquitto (mqtt broker) setup
-   use piper text-to-speech for audio files
-   python http server for audio files

Contact info:
MontanaMax917-at-gmail.com
Comments and code updates welcome.










