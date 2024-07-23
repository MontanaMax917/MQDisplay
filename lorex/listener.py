# Listener.py
# https://gist.github.com/heisvoid/602ad1e989f21c05b767e5f9683e4bce

import pycurl
import json
from datetime import datetime
import paho.mqtt.client as mqtt
from paho.mqtt.client import MQTT_ERR_SUCCESS, MQTT_ERR_NO_CONN

MQTT_HOST = "192.168.1.3"
MQTT_PORT = 1883

camera = {
    1: 'Back Door',
    2: 'Driveway',
    3: 'Garage',
    4: 'Shop Door',
    5: 'Side Door'
    }

# MQTT ---------------------------------------------
def connect_mqtt():
    mqttc = mqtt.Client()
    mqttc.connect(MQTT_HOST, MQTT_PORT, 60)
    print("MQTT Connected to {}".format(MQTT_HOST))
    mqttc.loop_start()
    mqttc.on_disconnect = on_disconnect
    return mqttc

def disconnect_mqtt(mqttc):
    mqttc.loop_stop()
    mqttc.disconnect()

def on_disconnect(client, userdata, rc):
    if rc != 0:
        print("MQTT Unexpected disconnection.")
        client.reconnect()

def publish(mqttc, topic, payload):    
#   topic = "/camera/event"
    (result, mid) = mqttc.publish(topic, payload)    
    if result == MQTT_ERR_SUCCESS:
        print("Published {} to {}".format(payload, topic))
    elif result == MQTT_ERR_NO_CONN:
        print("Connection error")
    else:
        print("Unknown error")
        
# cURL ---------------------------------------------
def on_receive(data):
#    print("data= ",data)
    now = datetime.now()  # add timestamp
    now = now.strftime("%Y/%m/%d %H:%M:%S")
    with open(r"Listener_raw.txt", "ab") as f:    # open log file
        f.write(bytes(now, 'UTF-8'))
        f.write(b'--------------------------------------------------------\r\n')
        f.write(data)
        f.write(b'\r\n')
        
    d = data.decode()  # convert to string
    data_dict = parse(d)
    report(data_dict)
    
def parse(data): # return dictionary of values
#    print("data= ",data)
    data_dict = {}
    objType = 'Unknown'
    for line in data.splitlines():  # splits lines in data{} section
        line = line.replace('"','')  # remove quotes
#        print("line=", line)
        if line.startswith('Code='):
            for item in line.split(";"):
#                print("item=", item)
                parts = item.split("=", 1)
                if len(parts) == 2:
#                    print("parts=", parts[0].strip(),parts[1].strip())
                    data_dict[parts[0].strip()] = parts[1].strip()  # code, action, index keys               
        elif (line.find('ObjectType') != -1):
            parts = line.split(":", 1)
            if len(parts) == 2:
                objType = parts[1].strip()
#                data_dict[parts[0].strip()] = parts[1].strip()
#                print("FOUND ObjectType")
    
    # cleanup entries in dictionary
    keyval = data_dict.pop('data', None)  # remove unused data key
    
#     if ('ObjectType' not in data_dict):  # add object type if not found
#         data_dict['ObjectType'] = 'Unknown'
    data_dict['ObjectType'] = objType  # add object type
    
    now = datetime.now()  # add timestamp
    data_dict['LocalTime'] = now.strftime("%Y/%m/%d %H:%M:%S")
        
    global camera  # add camera name to payload
    data_dict["camera"] = camera[int(data_dict["index"])]
    
    return data_dict

def report(data_dict: dict):  # publish info to MQTT broker
    js = json.dumps(data_dict)  # convert dict to json format
    print(js)
    
    with open(r"Listener_mqtt.txt", "a") as f:   # open log file
        f.write(js)
        f.write('\n')

    try:
        mqttc = connect_mqtt()  # publish to MQTT broker
        publish(mqttc, "/camera/event", js)  
    except KeyboardInterrupt:
        print("Exiting")
    finally:
        print("Disconnecting from {}".format(MQTT_HOST))
        disconnect_mqtt(mqttc)  # close connection to broker

# Main ---------------------------------------------
def main():
    # attach listener to NVR server, parse message, and post to MQTT broker
#     dbg = False  # True, False
#     if dbg:
#         data = "Code=VideoMotion;action=Start;index=4"
#         data = bytes(data, 'UTF-8')
#         on_receive(data)
#         return
#     with open(r"Listener_sample0.txt", "rb") as f:
#         data = f.read()
#         on_receive(data)
#     return

    print("Starting...")
    url = "http://user:password@192.168.1.20:80/cgi-bin/eventManager.cgi?action=attach&codes=[All]"
    c = pycurl.Curl()
    c.setopt(c.URL, url)
    c.setopt(c.CONNECTTIMEOUT, 10)
    c.setopt(c.TCP_KEEPALIVE, 1)
    c.setopt(c.HTTPAUTH, pycurl.HTTPAUTH_DIGEST)
    c.setopt(c.WRITEFUNCTION, on_receive)

    c.perform()
    c.close()
    print("Ending...")

if "__main__" == __name__:
    main()
    
  
