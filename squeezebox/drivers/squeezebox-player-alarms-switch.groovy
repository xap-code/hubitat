/**
 *  Squeezebox Player Alarms Switch
 *
 *  Copyright 2017 Ben Deitch
 *
 */

/* ChangeLog:
 * 15/10/2018 - Add child switch device for Enable/Disable All Alarms
 */
metadata {
  definition (name: "Squeezebox Player Alarms Switch", namespace: "xap", author: "Ben Deitch") {
    capability "Actuator"
    capability "Sensor"
    capability "Switch"
  }
}

def off() {
  getParent().disableAlarms()
}

def on() {
  getParent().enableAlarms()
}

def update(isOn) {
  sendEvent(name: "switch", value: isOn ? "on" : "off", display: true)
}
