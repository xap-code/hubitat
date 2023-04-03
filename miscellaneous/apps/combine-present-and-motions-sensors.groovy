/**
 *  Combine Presence and Motion Sensors
 *
 */
definition(
  name: "Combine Presence and Motion Sensors",
  namespace: "xap",
  author: "Ben Deitch",
  description: "Combines a presence and motion devices as a contact device.",
  category: "My Apps",
  iconUrl: "http://cdn.device-icons.smartthings.com/Home/home3-icn.png",
  iconX2Url: "http://cdn.device-icons.smartthings.com/Home/home3-icn@2x.png",
  iconX3Url: "http://cdn.device-icons.smartthings.com/Home/home3-icn@3x.png")

preferences {
	section("<h3>Devices</h3>") {
		input name: "presenceSensors", type: "capability.presenceSensor", required: true, multiple: true, title: "Select Presence Sensors"
		input name: "motionSensors", type: "capability.motionSensor", required: true, multiple: true, title: "Select Motion Sensors"
	}
    section("<h3>Timings</h3>") {
		input name: "alertWindow", type: "NUMBER", required: true, title: "Number of minutes within which both devices must trigger to cause the contact device to trigger."
    }
    section("<h3>Other Settings</h3>") {
        input name: "contactSensorName", type: "STRING", required: true, title: "Virtual Contact Sensor Name"
		input name: "debugLogging", type: "bool", title: "Enable/disable debug logging", defaultValue: false, required: false
	}
}

def log(message) {
    if (debugLogging) {
	    log.debug message
    }
}

def installed() {
    createChild()
	initialize()
}

def updated() {
    if (!child) {
        createChild()
    }
	initialize()
}

def uninstalled() {
    deleteChildren()
}

def initialize() {
	unschedule()
	unsubscribe()
	subscribeToEvents()
    state.windowMillis = alertWindow * 60 * 1000
}

def getChildDni() {
  "${app.id}-motion-combined"
}

def createChild() {
    if (!child) {
        def newContactSensor = addChildDevice("hubitat", "Virtual Contact Sensor", childDni)
        newContactSensor.name = "${contactSensorName}"
        newContactSensor.label = newContactSensor.name
    }
}

def getChild() {
    getChildDevice(childDni)
}

def deleteChildren() {
  getChildDevices().each {deleteChildDevice(it.deviceNetworkId)}
}

def subscribeToEvents() {
	subscribe presenceSensors, "presence", handlePresence
	subscribe motionSensors, "motion", handleMotion
}

def handlePresence(evt) {
	log "${evt.device} is ${evt.value}"
  if (evt.value == 'present') {
    state.lastPresentTime = new Date().time
    if (state.lastActiveTime && state.lastPresentTime - state.lastActiveTime <= state.windowMillis)  {
      openContactSensor()
    }
  } else {
      closeContactSensor()
  }
}

def handleMotion(evt) {
  log "${evt.device} is ${evt.value}"
  if (evt.value == 'active') {
    state.lastActiveTime = new Date().time
    if (state.lastPresentTime && state.lastActiveTime - state.lastPresentTime <= state.windowMillis) {
      openContactSensor()
    }
  } else {
    closeContactSensor()
  }
}

def openContactSensor() {
  if (child.currentValue("contact") != 'open') {
    log "Open contact sensor"
    child.open()
  }
}

def closeContactSensor() {
  if (child.currentValue("contact") != 'closed') {
    log "Close contact sensor"
    child.close()
  }
}
