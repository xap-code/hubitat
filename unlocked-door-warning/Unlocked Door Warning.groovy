/**
 *  Unlocked Door Warning
 *
 *  Copyright 2017 Ben Deitch
 *
 */

/* ChangeLog:
 * 22/10/2018 - Initial Implementation
 */
definition(
  name: "Unlocked Door Warning",
  namespace: "xap",
  author: "Ben Deitch",
  description: "Warns if a door has been left unlocked.",
  category: "My Apps",
  iconUrl: "http://cdn.device-icons.smartthings.com/Home/home3-icn.png",
  iconX2Url: "http://cdn.device-icons.smartthings.com/Home/home3-icn@2x.png",
  iconX3Url: "http://cdn.device-icons.smartthings.com/Home/home3-icn@3x.png")

preferences {
    section("<h3>Devices</h3>") {
        input name: "doorName", type: "STRING", required: true, title: "Door Name"
		input name: "lock", type: "capability.lock", required: true, title: "Select Lock"
        input name: "speaker", type: "capability.speechSynthesis", required: true, title: "Select Speaker"
    }
    section("<h3>Timings</h3>") {
		input name: "warnAfterMinutes", type: "NUMBER", required: true, title: "If the door is left unlocked for this many minutes the system will start warning."
		input name: "warnEveryMinutes", type: "NUMBER", required: true, title: "Minutes between each repeated warning whilst door is still unlocked."
    }
}

def log(message) {
    //log.debug message
}

def getSwitchDni() {
	"${lock.deviceNetworkId}-unlocked-warning"
}

def getChildSwitch() {
	getChildDevice(switchDni)
}

def getChildSwitchIsOn() {
    childSwitch.currentState("switch").value == 'on'
}

def getLockIsLocked() {
    lock.currentState("lock").value == 'locked'
}

def installed() {
	initialize()
    def childSwitch = addChildDevice("hubitat", "Virtual Switch", switchDni)
    childSwitch.name = "${doorName} Unlocked Warning"
    childSwitch.on()
}

def uninstalled() {
  removeChildDevices(getChildDevices())
}

def removeChildDevices(delete) {
  delete.each {deleteChildDevice(it.deviceNetworkId)}
}

def updated() {
	initialize()
}

def initialize() {
	unschedule()
	unsubscribe()
	subscribeToEvents()
}

def subscribeToEvents() {
	subscribe lock, "lock", handleLock
}

def warnIfStillUnlocked() {
    
    log "Checking if ${doorName} is locked..."
    
    if (childSwitchIsOn && !lockIsLocked) {
        
        log "${doorName} is not locked, speaking warning"
	    speaker.speak "The ${doorName} is not locked."

        log "scheduling repeat check in ${warnEveryMinutes} minute(s)"
        runIn(warnEveryMinutes * 60, warnIfStillUnlocked)
    }
}

private doorUnlocked() {
    
    log "Door Unlocked"
    
    if (childSwitchIsOn) {
        log "${doorName} is unlocked, scheduling check in ${warnAfterMinutes} minute(s)"
	    runIn(warnAfterMinutes * 60, warnIfStillUnlocked)
    }        
}

private doorLocked() {
	log "Door Locked"

    unschedule()
    if (!childSwitchIsOn) {
        childSwitch.on()
        speaker.speak "The \"${childSwitch.name}\" has been reset back to on."
    }
}

def handleLock(evt) {

    switch (evt.value) {
        case 'unlocked':
        	doorUnlocked()
        	break
        case 'locked':
        	doorLocked()
        	break
        default:
            log.warn "Lock event type not recognised: ${evt.value}"
    }
}
