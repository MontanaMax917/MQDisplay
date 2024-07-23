Readme.md

MQDisplay

Android display controlled by MQTT commands.
The camera view can change to follow activity. An audio file can be played
to announce 'car in driveway', 'person at door', 'garage door open'. 
The brightness and volume can be adjusted for the time of day. 

Features:
- simply connect to MQTT broker and control display using topics
- set rtsp camera url to display
- set http audio file to play
- set audio volume
- set screen brightness
- set short screen message


Supported devices:
works with local wifi connected devices
- Android tablets (Android 8 and newer)
- Android TV (Onn tv box)
- Android phones

Typical setup:
Lorex nvr -> event monitor -> Node Red -> mqtt broker -> MQDisplay

Note:
This is an introductory readme file and will be update when the code is posted.







