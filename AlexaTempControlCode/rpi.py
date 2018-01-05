#!env/bin/python3

import signal
import sys
import ssl
import os
import json
import glob
import time
import threading
import paho.mqtt.client as mqtt
import RPi.GPIO as GPIO

# set to BCM mode
GPIO.setmode(GPIO.BCM)

##################
# CTRL+C HANDLER #
##################
def ctrl_c_handler(signal, frame):
    print('')
    GPIO.cleanup()
    client.disconnect()
    sys.exit(0)

signal.signal(signal.SIGINT, ctrl_c_handler)

# mode toggle timers
timers = {
    'room0': None,
    'room1': None,
    'room2': None
}

#####################
# HEATER GPIO STUFF #
#####################
heat_pins = {
    'room0': 17,
    'room1': 22,
    'room2': 15
}
for key, pin in heat_pins.items():
    GPIO.setup(pin, GPIO.OUT)
    GPIO.output(pin, GPIO.HIGH) # HIGH is off and LOW is on

# switch on or off a room's heater, for a number of minutes
def switch_heater(power, key, minutes=0):
    if power:
        switch_fan(False, key)
        GPIO.output(heat_pins[key], GPIO.LOW)
        state[key]['mode'] = 'HEAT'
        if minutes > 0:
            if not timers[key] == None:
                timers[key].cancel()
                timers[key] = None
            timers[key] = threading.Timer(minutes * 60, switch_heater, [False, key])
            timers[key].start()
            state[key]['time'] = minutes
    else:
        GPIO.output(heat_pins[key], GPIO.HIGH)
        timers[key] = None
        state[key]['time'] = 0
        state[key]['mode'] = 'OFF'

##################
# FAN GPIO STUFF #
##################
fan_pins = {
    'room0': 27,
    'room1': 14,
    'room2': 18
}
for key, pin in fan_pins.items():
    GPIO.setup(pin, GPIO.OUT)
    GPIO.output(pin, GPIO.HIGH) # HIGH is off and LOW is on

# switch on or off a room's fan, for a number of minutes
def switch_fan(power, key, minutes=0):
    if power:
        switch_heater(False, key)
        GPIO.output(fan_pins[key], GPIO.LOW)
        state[key]['mode'] = 'COOL'
        if minutes > 0:
            if not timers[key] == None:
                timers[key].cancel()
                timers[key] = None
            timers[key] = threading.Timer(minutes * 60, switch_fan, [False, key])
            timers[key].start()
            state[key]['time'] = minutes
    else:
        GPIO.output(fan_pins[key], GPIO.HIGH)
        timers[key] = None
        state[key]['time'] = 0
        state[key]['mode'] = 'OFF'

############################
# TEMPERATURE SENSOR STUFF #
############################

# set up temperature sensor drivers (only if not done before running script)
# os.system('modprobe w1-gpio')
# os.system('modprobe w1-therm')

# detect temperature sensors
base_dir = '/sys/bus/w1/devices/'
device_folders = glob.glob(base_dir + '28*')
device_files = {}
for i in range(len(device_folders)):
    device_files['room' + str(i)] = device_folders[i] + '/w1_slave'

# read raw temperature data for a specific room
def read_temp_raw(key):
    f = open(device_files[key], 'r')
    lines = f.readlines()
    f.close()
    return lines

# read temperature for a specific room
def read_temp(key):
    lines = read_temp_raw(key)
    while lines[0].strip()[-3:] != 'YES':
        time.sleep(0.2)
        lines = read_temp_raw(key)
    equals_pos = lines[1].find('t=')
    if equals_pos != -1:
        temp_string = lines[1][equals_pos+2:]
        temp_c = float(temp_string) / 1000.0
        # temp_f = temp_c * 9.0 / 5.0 + 32.0
        return temp_c

####################
# AWS SHADOW STUFF #
####################

# aws shadow info
aws_url = 'a38diupx41d3lu.iot.us-east-1.amazonaws.com'
rootca = './deviceSDK/root-ca.pem'
certfile = './deviceSDK/5d8b0c6cff-certificate.pem.crt'
keyfile = './deviceSDK/5d8b0c6cff-private.pem.key'
client_id = 'MyRaspberryPi'

# shadow
state = {
    'room0': {
        'time': 0,
        'temp': round(read_temp('room0'), 3),
        'mode': 'OFF'
    },
    'room1': {
        'time': 0,
        'temp': round(read_temp('room1'), 3),
        'mode': 'OFF'
    },
    'room2': {
        'time': 0,
        'temp': round(read_temp('room2'), 3),
        'mode': 'OFF'
    }
}

# on connect, subscribe to /update/delta
def on_connect(mqttc, obj, flags, rc):
    if rc == 0:
        print('connected')
        client.subscribe('$aws/things/MyRaspberryPi/shadow/update/delta', qos=0)

# on subscribe
def on_subscribe(mqttc, obj, mid, granted_qos):
    print('subscribed')

# on message, do stuff based on topic
def on_message(mqttc, obj, msg):
    request = json.loads(msg.payload.decode())
    if msg.topic == '$aws/things/MyRaspberryPi/shadow/update/delta':
        for key, room in request['state'].items():
            minutes = room.get('time', 0)
            if 'mode' in room:
                if room['mode'] == 'OFF':
                    switch_fan(False, key)
                    switch_heater(False, key)
                elif room['mode'] == 'HEAT':
                    switch_heater(True, key, minutes)
                elif room['mode'] == 'COOL':
                    switch_fan(True, key, minutes)
        delta = {}
        for key, room in state.items():
            delta[key] = {
                'mode': room['mode'],
                'time': room['time']
            }
        client.publish('$aws/things/MyRaspberryPi/shadow/update', json.dumps({
            'state': {
                'reported': delta
            }
        }))

# initialize aws shadow client using mqtt library
client = mqtt.Client(client_id='MyRaspberryPi', clean_session=True)
client.on_connect = on_connect
client.on_subscribe = on_subscribe
client.on_message = on_message
client.tls_set(rootca, certfile=certfile, keyfile=keyfile, tls_version=ssl.PROTOCOL_TLSv1_2, ciphers=None)

# connect the aws shadow client
client.connect(aws_url, port=8883)

# update shadow to current state
client.publish('$aws/things/MyRaspberryPi/shadow/update', json.dumps({
    'state': {
        'reported': state
    }
}))

# loop forever and wait for subscriptions
client.loop_forever()
