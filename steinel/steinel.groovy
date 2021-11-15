/**
 *  Steinel Z-Wave
 *
 *  Copyright 2018 Nick Veenstra
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
  definition (name: "Steinel Z-Wave", namespace: "CopyCat73", author: "Nick Veenstra") {
    capability "Motion Sensor"
    capability "Switch"
    capability "Illuminance Measurement"
    capability "Refresh"
    capability "Configuration"
    fingerprint mfr: "0271", prod: "0002", model: "1A72",deviceJoinName: "Steinel Z-Wave"
  }
    
  preferences {
    input name: "lightDuration", type: "number", title: "Duration of light after motion detection\n\nAvailable settings: 5-900 seconds.", description: "Enter value", range: "5..900", required: false
    input name: "lightThreshold", type: "number", title: "Light threshold\n\nAvailable settings: 0-2000 lux.\n2000 - is used as daylight (always night mode).\n0 - run Learn ambient light sequence.\n2-1999 lux.", description: "Enter value", range: "0..2000", required: false
    input name: "sensitivity", type: "number", title: "Sensitivity\n\nAvailable settings: 2-100%\nPotentiometer State.", description: "Enter value", range: "2..100", required: false
    input name: "globalLight", type: "enum", title: "Global Light\n\nAvailable settings:\nExternal ambient light value.\n0 - Enable\n1 - Disable", options: ["0": "Enable", "1": "Disable"], description: "Select Enable/Disable", required: false      
    input name: "slaveMode", type: "enum", title: "Slave Mode\n\nAvailable settings:\nDisable local control.\n0 - Normal mode\n1 - Slave mode without gateway checking\n2 - Normal mode with lifeline error signalisation\n3 - Slave mode with gateway checking\n4 - Stupid mode - lamp permanently on", options: ["0": "Normal mode", "1": "Slave mode without gateway checking", "2": "Normal mode with lifeline error signalisation", "3": "Slave mode with gateway checking", "4": "Stupid mode - lamp permanently on"], description: "Select slave mode", required: false
    input name: "offBehaviour", type: "number", title: "Off behaviour\n\nAvailable settings: 0-255.\n0 - Lamp is switched off and remains so until any new motion event (local or remote) is received.\n1 to 100 (Default) timeout: 1 second (1) to 100 seconds (100) in 1-second resolution.\n101 to 200 timeout: 1 minute (101) to 100 minutes (200) 1-minute resolution.\n201 to 209 timeout: 1 hour (201) to 9 hours (209) in 1-hour resolution.\n255 Lamp is switched off for TIME (cfg 1). It does not wait for a motion event and works normally via current motion evaluation.", description: "Enter value", range: "0..255", required: false
    input name: "onBehaviour", type: "number", title: "On behaviour\n\nAvailable settings: 0-255.\nSimilar than Off behaviour.\nAvailable settings: 0-255. \n0 - Lamp is switched on and remains so until any new motion event (local or remote) is received. It then works normally via current motion evaluation. Notice - during the day, this mode cannot be ended remotely due to motion events not being transmitted - only via local motion sensor if enabled.", description: "Enter value", range: "0..255", required: false
    input name: "onBehaviourTimeOver", type: "number", title: "On behaviour time over\n\nAvailable settings: 0-255.\n0 No additional waiting for motion.\n1 to 100 timeout: 1 second (1) to 100 seconds (100) in 1-second resolution.\n101 to 200 timeout: 1 minute (101) to 100 minutes (200) 1-minute resolution.\n201 to 209 (Default) timeout: 1 hour (201) to 9 hours (209) in 1-hour resolution.\n255 Never stop waiting before motion.", description: "Enter value", range: "0..209", required: false
    input name: "sequenceOnOffBehaviour", type: "number", title: "Sequence On-Off behaviour\n\nAvailable settings: 0-255.\n0 - Lamp is switched off and remains so until any new motion event (local or remote) is received.\n1 to 100 timeout: 1 second (1) to 100 seconds (100) in 1-second resolution.\n101 to 200 timeout: 1 minute (101) to 100 minutes (200) 1-minute resolution.\n201 to 209 (Default) timeout: 1 hour (201) to 9 hours (209) in 1-hour resolution.\n255 device ignores ON - OFF sequence and uses OFF behavior.", description: "Enter value", range: "0..255", required: false
    input name: "sequenceOffOnBehaviour", type: "number", title: "Sequence Off-On behaviour\n\nAvailable settings: 0-255.\n0 - Lamp is switched on and remains so until any new motion event (local or remote) is received. It then works normally via current motion evaluation. Notice - during the day, this mode cannot be ended remotely due to motion events not being transmitted - only via local motion sensor if enabled.\n1 to 100 timeout: 1 second (1) to 100 seconds (100) in 1-second resolution.\n101 to 200 timeout: 1 minute (101) to 100 minutes (200) 1-minute resolution.\n201 to 209 (Default) timeout: 1 hour (201) to 9 hours (209) in 1-hour resolution.\n255 device ignores OFF - ON sequence and uses ON behaviour.", description: "Enter value", range: "0..255", required: false
    input name: "sequenceTiming", type: "number", title: "Sequence Timing\n\nAvailable settings: 10-50.", description: "Value between 10 and 50", range: "10..50", required: false
    input name: "motionOffBehaviour", type: "number", title: "Motion Off behaviour\n\nAvailable settings:\n0 (Default) BASIC SET to Motion sensor endpoint ignored, BASIC to root is mapped to relay endpoint, motion sensor still enabled.\n1 to 100 timeout: 1 second (1) to 100 seconds (100) in 1-second resolution.\n101 to 200 timeout: 1 minute (101) to 100 minutes (200) 1-minute resolution.\n201 to 209 timeout: 1 hour (201) to 9 hours (209) in 1-hour resolution.\n255 BASIC SET to Motion sensor endpoint ignored, BASIC to root is mapped to relay endpoint, motion sensor still disabled", description: "Enter value", range: "0..255", required: false
  }
}

def parse(String description) {

  def cmd = zwave.parse(description, [0x60: 3])
  if (cmd) {
    //log.debug "Parsed event: ${cmd}"
    return zwaveEvent(cmd)
  }
   
  log.debug "Non-parsed event: ${description}"
}

def zwaveEvent(hubitat.zwave.commands.meterpulsev1.MeterPulseReport cmd) {
  log.debug "Ignoring received event: ${cmd}"
}

def zwaveEvent(hubitat.zwave.commands.deviceresetlocallyv1.DeviceResetLocallyNotification cmd) {
  log.debug "Ignoring received event: ${cmd}"
}

def zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationGet cmd) {
  log.debug "Ignoring received event: ${cmd}"
}

def zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationSet cmd) {
  log.debug "Ignoring received event: ${cmd}"
}

def zwaveEvent(hubitat.zwave.commands.securitypanelmodev1.SecurityPanelModeGet cmd) {
  log.debug "Ignoring received event: ${cmd}"
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv2.SwitchBinarySet cmd) {
  log.debug "Ignoring received event: ${cmd}"
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv2.SwitchBinaryGet cmd) {
  log.debug "Ignoring received event: ${cmd}"
}

def zwaveEvent(hubitat.zwave.commands.notificationv8.EventSupportedGet cmd) {
  log.debug "Ignoring received event: ${cmd}"
}

def zwaveEvent(hubitat.zwave.commands.protectionv2.ProtectionSupportedReport cmd) {
  log.debug "Ignoring received event: ${cmd}"
}

def zwaveEvent(hubitat.zwave.commands.applicationstatusv1.ApplicationBusy cmd) {
  log.debug "Ignoring received event: ${cmd}"
}

def zwaveEvent(hubitat.zwave.commands.associationv3.AssociationReport cmd) {
  log.debug "Ignoring received event: ${cmd}"
}

def zwaveEvent(hubitat.zwave.commands.sensormultilevelv11.SensorMultilevelGet cmd) {
  log.debug "Ignoring received event: ${cmd}"
}

def zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCapabilityReport cmd) {
  log.debug "Ignoring received event: ${cmd}"
}

def zwaveEvent(hubitat.zwave.commands.versionv3.VersionCommandClassReport cmd) {
  log.debug "Ignoring received event: ${cmd}"
}

def zwaveEvent(hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {

  if (cmd.sensorType == 3) {
    createEvent(name: "illuminance", value: cmd.scaledSensorValue.toInteger().toString(), unit: "lux")
  }
}

def zwaveEvent(hubitat.zwave.commands.notificationv3.NotificationReport cmd) {
    
  if (cmd.notificationType == 7) {
    switch (cmd.event) {
      case 0:
      if (cmd.eventParameter[0] == 3) {
        return createEvent(name: "tamper", value: "inactive", displayed: true)
      } else if (cmd.eventParameter[0] == 8) {
        return createEvent(name: "motion", value: "inactive", displayed: true)
      }
      break
                
      case 3:
      return createEvent(name: "tamper", value: "active", displayed: true)
                
      case 8:
      return createEvent(name: "motion", value: "active", displayed: true)
    }
  }
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
  createEvent(name:"switch", value: cmd.value ? "on" : "off")
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
  createEvent(name:"switch", value: cmd.value ? "on" : "off")
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv2.SwitchBinaryReport cmd) {
  createEvent(name:"switch", value: cmd.value ? "on" : "off")
}

def ping() {
	refresh()
}	
	
def refresh() {
  delayBetween([
    zwave.switchBinaryV2.switchBinaryGet().format(), // get switch
    zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType:3, scale:1).format(),  // get lux
  ], 1200)
}

def updated() {
  if (!state.updatedLastRanAt || now() >= state.updatedLastRanAt + 2000) {
    log.debug "Updated"
    state.updatedLastRanAt = now()
    response(configure())
  }
}

def configure() {
  log.debug "Sending Configuration to device" 
  def cmds = []
  if (lightDuration) { cmds << zwave.configurationV1.configurationSet(parameterNumber: 1, size: 2, scaledConfigurationValue: lightDuration.toInteger()).format() }   
  if (lightThreshold) { cmds << zwave.configurationV1.configurationSet(parameterNumber: 2, size: 2, scaledConfigurationValue: lightThreshold.toInteger()).format() } 
  if (sensitivity) { cmds << zwave.configurationV1.configurationSet(parameterNumber: 5, size: 1, scaledConfigurationValue: sensitivity.toInteger()).format() }
  if (globalLight) { cmds << zwave.configurationV1.configurationSet(parameterNumber: 8, size: 1, scaledConfigurationValue: globalLight.toInteger()).format() } 	
  if (slaveMode) { cmds << zwave.configurationV1.configurationSet(parameterNumber: 9, size: 1, scaledConfigurationValue: slaveMode.toInteger()).format() }   
  if (offBehaviour) { cmds << zwave.configurationV1.configurationSet(parameterNumber: 10, size: 2, scaledConfigurationValue: offBehaviour.toInteger()).format() }   
  if (onBehaviour) { cmds << zwave.configurationV1.configurationSet(parameterNumber: 11, size: 2, scaledConfigurationValue: onBehaviour.toInteger()).format() }     
  if (onBehaviourTimeOver) { cmds << zwave.configurationV1.configurationSet(parameterNumber: 12, size: 2, scaledConfigurationValue: onBehaviourTimeOver.toInteger()).format() }   
  if (sequenceOnOffBehaviour) { cmds << zwave.configurationV1.configurationSet(parameterNumber: 13, size: 2, scaledConfigurationValue: sequenceOnOffBehaviour.toInteger()).format() }     
  if (sequenceOffOnBehaviour) { cmds << zwave.configurationV1.configurationSet(parameterNumber: 14, size: 2, scaledConfigurationValue: sequenceOffOnBehaviour.toInteger()).format() }   
  if (sequenceTiming) { cmds << zwave.configurationV1.configurationSet(parameterNumber: 15, size: 1, scaledConfigurationValue: sequenceTiming.toInteger()).format() }     
  if (motionOffBehaviour) { cmds << zwave.configurationV1.configurationSet(parameterNumber: 16, size: 2, scaledConfigurationValue: motionOffBehaviour.toInteger()).format() }   
  delayBetween(cmds,1500) 
}

def on() {
  zwave.basicV1.basicSet(value: 0xFF).format()
 // delayBetween([
 //   zwave.basicV1.basicSet(value: 0xFF).format(),
 //   zwave.switchBinaryV1.switchBinaryGet().format()
 // ], 2000)
}

def off() {
  zwave.basicV1.basicSet(value: 0x00).format()
}
