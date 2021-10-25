/**
 *  POPP Thermostatic Radiator Valve
 *
 *  Copyright 2021 Ben Deitch
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
import groovy.transform.Field

@Field static final List TAMPER_PROTECTION_VALUES = ["unprotected", "locked"]

metadata {
	definition (name: "POPP Thermostatic Radiator Valve", namespace: "xap", author: "Ben Deitch") {
    capability "Actuator"
    capability "Battery"
    capability "Sensor"
    capability "Thermostat"

    // POPP 010101
    fingerprint deviceId: "A010", inClusters: "0x80,0x46,0x81,0x72,0x8F,0x75,0x31,0x43,0x86,0x84", outClusters: "0x46,0x81,0x8F", mfr: "0002", prod: "0115", deviceJoinName: "POPP Thermostatic Radiator Valve"
                         
    command "test"
    command "setTamperProtection", [[name: "tamperProtection", type: "ENUM", constraints: TAMPER_PROTECTION_VALUES]]
    
    attribute "tamperProtection", "ENUM", TAMPER_PROTECTION_VALUES
	}

	preferences {
    input (
      name: "wakeUpInterval",
      title: "Wake Up interval",
      description: "<small>How often the device should wake up and synchronise with the hub. Higher values will give longer battery life.<br/>(range: 1-30 minutes, default: 5 minutes)</small>",
      type: "number",
      range: "1..30",
      defaultValue: 5,
      required: false
    )
    // TODO: Set debug logging default to false
    input (
      name: "enableDebugLogging",
      title: "Enable debug logging",
      type: "bool",
      defaultValue: true,
      required: false
    )
	}
}

/*******************
 * Device Commands *
 *******************/
def heat() {
}

def off() {
}

def setHeatingSetpoint(temperature) {
}

def setThermostatMode(thermostatMode) {
}

def setTamperProtection(tamperProtection) {
}

/**************************
 * Not Supported Commands *
 **************************/
def auto() { warnNotSupported("Auto") }
def cool() { warnNotSupported("Cool") }
def emergencyHeat() { warnNotSupported("Emergency Heat") }
def fanAuto() { warnNotSupported("Fan Auto") }
def fanCirculate() { warnNotSupported("Fan Circulate") }
def fanOn() { warnNotSupported("Fan On") }
def setCoolingSetpoint(ignored) { warnNotSupported("Set Cooling Setpoint") }
def setSchedule(ignored) { warnNotSupported("Set Schedule") }
def setThermostatFanMode(ignored) { warnNotSupported("Set Thermostat Fan Mode") }
private warnNotSupported(operation) {
  log.warn("'${operation}' operation is not supported.")
}

/*****************
 * Configuration *
 *****************/
def installed() {
  debugLog "${device.displayName} installed"
}

def updated() {
  debugLog "${device.displayName} updated"
}

/*******************
 * Z-Wave Handling *
 *******************/
def parse(String description) {
  
  debugLog "parse() >> description: ${description}"
  
  // Supported incoming Z-Wave commands
  // 0x80 = Battery v1
  // 0x72 = ManufacturerSpecific v2
  // 0x42 = ThermostatOperatingState v1
  // 0x43 = ThermostatSetpoint v2
  // 0x31 = SensorMultilevel v3
  // 0x84 = WakeUp v2
  // 0x75 = Protection v1
  // 0x8F = MultiCmd v1
  def cmd = zwave.parse(description, [0x80:1, 0x72:2, 0x42:1, 0x43:2, 0x31:3, 0x84:2, 0x75:1, 0x8F:1])
  if (cmd) {
    return zwaveEvent(cmd)
  }
  
  log.warn "${device.displayName} failed to parse Z-Wave message: ${description}"
}

def zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
  
	debugLog "${device.displayName} received BatteryReport [batteryLevel: ${cmd.batteryLevel}]"

  def map = [name: "battery", unit: "%"]
	if (cmd.batteryLevel == 0xFF) {
		map.value = 1
		map.isStateChange = true
		map.descriptionText = "${device.displayName} has a low battery"
	} else {
		map.value = cmd.batteryLevel
	}
  
	createEvent map
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
  
  debugLog "${device.displayName} received ManufacturerSpecificReport"

  if (cmd.manufacturerName) {
		updateDataValue("manufacturerName", cmd.manufacturerName)
	}
	if (cmd.productTypeId) {
		updateDataValue("productTypeId", cmd.productTypeId.toString())
	}
	if (cmd.productId) {
		updateDataValue("productId", cmd.productId.toString())
	}
}

def zwaveEvent(hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport cmd) {
  
  debugLog "${device.displayName} received ThermostatOperatingState"

  def map = [name: "thermostatOperatingState"]
  
  switch (cmd.operatingState) {
    case hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_IDLE:
      map.value = "idle"
      break
    case hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_HEATING:
      map.value = "heating"
      break
    default:
      log.warn "${device.displayName} received unrecognised thermostatOperatingState: ${cmd.operatingState}"
  }
  
  if (map.value) {
    map.descriptionText = "${device.displayName} is ${map.value}"
    createEvent map
  }
}

def zwaveEvent(hubitat.zwave.commands.thermostatsetpointv2.ThermostatSetpointReport cmd) {

  debugLog "${device.displayName} received ThermostatSetpointReport"
}

def zwaveEvent(hubitat.zwave.commands.sensormultilevelv3.SensorMultilevelReport cmd) {
  
  debugLog "${device.displayName} received SensorMultiLevelReport"
}

def zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpNotification cmd) {

  debugLog "${device.displayName} received WakeUpNotification"
}

def zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpIntervalReport cmd) {

  debugLog "${device.displayName} received WakeUpReport [seconds: ${cmd.seconds}]"
}

def zwaveEvent(hubitat.zwave.commands.protectionv1.ProtectionReport cmd) {

  debugLog "${device.displayName} received ProtectionReport"
}

def zwaveEvent(hubitat.zwave.commands.multicmdv1.MultiCmdEncap cmd) {
  
  debugLog "${device.displayName} received MultiCmdEncap [numberOfCommands: ${cmd.numberOfCommands}]"

  cmd.encapsulatedCommands().collect { zwaveEvent(it)	}.flatten()
}

def zwaveEvent(hubitat.zwave.Command cmd) {
  log.warn "${device.displayName} received unexpected Z-Wave command: ${cmd}"
}

/*******************
 * Utility Methods *
 *******************/
def debugLog(message) {
  if (settings.enableDebugLogging) {
    log.debug(message)
  }
}

def test() {
  sendEvent name: "heatingSetpoint", value: 22.5
  sendEvent name: "supportedThermostatFanModes", value: ["off"]
  sendEvent name: "supportedThermostatModes", value: ["off", "heat"]
  sendEvent name: "temperature", value: 20.0
  sendEvent name: "thermostatMode", value: "heat"
  sendEvent name: "thermostatOperatingState", value: "heating"
  sendEvent name: "thermostatSetpoint", value: 22.5
  sendEvent name: "tamperProtection", value: "unprotected"
}