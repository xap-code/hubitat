/**
 *  Squeezebox Player Child Switch
 *
 *  Copyright 2017 Ben Deitch
 *
 */

/* ChangeLog:
 * 15/10/2018 - First version
 */
metadata {
  definition (name: "Squeezebox Player Child Switch", namespace: "xap", author: "Ben Deitch") {
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
