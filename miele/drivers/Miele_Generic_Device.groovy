/**
 *  Miele Generic Device
 *
 *  Copyright 2023 Ben Deitch
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

/* ChangeLog:
 * 19/01/2023 - v1.3.0 - Add child devices
 * 17/01/2023 - v1.2.0 - Add signal and remote enable attributes
 * 16/01/2023 - v1.1.0 - Add preferences to enable/disable text and description events
 * 08/01/2023 - v1.0.0 - Initial read-only implementation without actions
 */
metadata {
  definition (name: "Miele Generic Device", namespace: "xap", author: "Ben Deitch") {
    capability "Sensor"
    
    attribute "elapsedTime", "number"
    attribute "elapsedTimeText", "string"
    attribute "elapsedTimeDescription", "string"
    attribute "finishTime", "date"
    attribute "finishTimeText", "string"
    attribute "finishTimeDescription", "string"
    attribute "fullRemoteControlEnabled", "bool"
    attribute "mobileStartEnabled", "bool"
    attribute "program", "string"
    attribute "programPhase", "string"
    attribute "programDescription", "string"
    attribute "remainingTime", "number"
    attribute "remainingTimeText", "string"
    attribute "remainingTimeDescription", "string"
    attribute "signalInfo", "bool"
    attribute "signalFailure", "bool"
    attribute "signalDoor", "bool"
    attribute "smartGridEnabled", "bool"
    attribute "startTime", "date"
    attribute "startTimeText", "string"
    attribute "startTimeDescription", "string"
    attribute "status", "string"
  }
  preferences {
    // event preferences
    input name: "textEventsEnabled", title: "Enable text events", type: "bool", defaultValue: true, description: "<small>Raise events for values as short text descriptions.</small>"
    input name: "descriptionEventsEnabled", title: "Enable description events", type: "bool", defaultValue: true, description: "<small>Raise events for values as longer text descriptions.</small>"
    // child device preferences
    input name: "ecoFeedbackEnabled", title: "Enable Eco Feedback child device", type: "bool", defaultValue: true, description: "<small>Eco Feedback child device is only created if ecoFeedback data is received. Disabling will delete child device.</small>"
    input name: "lightEnabled", title: "Enable Light child device", type: "bool", defaultValue: true, description: "<small>Light child device is only created if light data is received. Disabling will delete child device.</small>"
    input name: "ambientLightEnabled", title: "Enable Ambient Light child device", type: "bool", defaultValue: true, description: "<small>Ambient Light child device is only created if ambientLight data is received. Disabling will delete child device.</small>"
    input name: "temperaturesEnabled", title: "Enable Temperature child devices", type: "bool", defaultValue: true, description: "<small>Up to three Temperature child devices may be created. Temperature child devices are only created if temperature data is received. Disabling will delete child devices.</small>"
    input name: "coreTemperatureEnabled", title: "Enable Core Temperature child device", type: "bool", defaultValue: true, description: "<small>Core Temperature child device is only created if coreTemperature data is received. Disabling will delete child device.</small>"
    // other preferences
    input name: "debugEnabled", title: "Enable debug logging", type: "bool"
  }
}

import groovy.transform.Field
import java.time.ZoneId
import java.time.format.DateTimeFormatter
  
// define constants
@Field static final String DNI_PREFIX = "miele:"
@Field static final String ECO_FEEDBACK_DNI_SUFFIX = ":ecofeedback"
@Field static final String LIGHT_DNI_SUFFIX = ":light"
@Field static final String AMBIENT_LIGHT_DNI_SUFFIX = ":ambientlight"
@Field static final String TEMPERATURE_DNI_SUFFIX = ":temperature"
@Field static final String CORE_TEMPERATURE_DNI_SUFFIX = ":coretemperature"
@Field static final String RESET_STRING = " "
@Field static final Date RESET_DATE = new Date(0)
@Field static final int MISSING_INT = -32768
@Field static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

def configure(mieleDeviceType) {
  logDebug("configured with mieleDeviceType: ${mieleDeviceType}")
  state.mieleDeviceType = mieleDeviceType
}

def updated() {
  deleteDisabledChildDevices()
  resetDisabledAttributes()
}

def getMieleDeviceId() {
  device.deviceNetworkId.substring(DNI_PREFIX.size())
}

def getMieleDeviceType() {
  state.mieleDeviceType
}

// ** Device Events **

def eventReceived(data) {
  sendStatusEvent data.status
  sendProgramEvents data.ProgramID, data.programPhase
  sendDurationEvents data.remainingTime, "remaining", "remainingTime"
  sendDurationEvents data.elapsedTime, "elapsed", "elapsedTime"
  sendStartTimeEvents data.startTime
  sendFinishTimeEvents data.startTime, data.remainingTime
  sendBooleanEvent data.signalInfo, "signalInfo"
  sendBooleanEvent data.signalFailure, "signalFailure"
  sendBooleanEvent data.signalDoor, "signalDoor"
  sendRemoteEnableEvents data.remoteEnable
  delegateEcoFeedback data.ecoFeedback
  delegateLight data.light
  delegateAmbientLight data.ambientLight
  delegateTemperatures data.temperature, data.targetTemperature
  delegateCoreTemperature data.coreTemperature, data.coreTargetTemperature
  logDebug("received: ${data}")
}

private resetDisabledAttributes() {
  if (!textEventsEnabled) {
    resetDeviceAttributesEndingWith(device, "Text")
  }
  if (!descriptionEventsEnabled) {
    resetDeviceAttributesEndingWith(device, "Description")
  }
}

private sendStatusEvent(status) {
 sendEvent name: "status", value: status.value_localized
}

private sendProgramEvents(program, programPhase) {

  if (program.value_raw == null || program.value_raw < 0) return

  programValue = program.value_localized ?: RESET_STRING
  sendEvent name: "program", value: programValue

  if (programPhase.value_raw != null) {
    programPhaseValue = programPhase.value_localized ?: RESET_STRING
    sendEvent name: "programPhase", value: programPhaseValue
  }
 
  if (programValue == RESET_STRING) {
    programDescription = RESET_STRING
  } else if (programPhaseValue && programPhaseValue != RESET_STRING) {
    programDescription = "${programValue} (${programPhaseValue})"
  } else {
    programDescription = programValue
  }
  sendEvent name: "programDescription", value: programDescription
}

private sendDurationEvents(duration, durationName, eventName) {

  if (!duration) return

  hours = duration[0]
  minutes = duration[1]
  totalMinutes = hours * 60 + minutes
  if (totalMinutes < 0) {
    return
  } else if (totalMinutes > 0) {
    durationText = buildDurationText(hours, minutes)
    durationDescription = buildDurationDescription(hours, minutes, durationName)
  } else {
    durationText = RESET_STRING
    durationDescription = RESET_STRING
  }
  
  sendEvent name: eventName, value: totalMinutes, unit: "minutes"
  if (textEventsEnabled) {
    sendEvent name: eventName + "Text", value: durationText
  }
  if (descriptionEventsEnabled) {
    sendEvent name: eventName + "Description", value: durationDescription
  }
}

private buildDurationText(hours, minutes) {
  String.format("%02d:%02d", hours, minutes)
}

private buildDurationDescription(hours, minutes, durationName) {
  hours == 0 
  ? "${buildTimeValueDescription(minutes, 'minute')} ${durationName}"
  : "${buildTimeValueDescription(hours, 'hour')} ${buildTimeValueDescription(minutes, 'minute')} ${durationName}"
}

private buildTimeValueDescription(value, unit) {
  value == 1 ? "1 ${unit}" : "${value} ${unit}s"
}

private sendStartTimeEvents(startTime) {
  if (!startTime) return
  hours = startTime[0]
  minutes = startTime[1]
  sendTimeEvents(hours, minutes, "Start", "startTime")
}

private sendFinishTimeEvents(startTime, remainingTime) {

  if (!remainingTime) return

  if (startTime) {
    startHours = Math.max(0, startTime[0])
    startMinutes = Math.max(0, startTime[1])
  } else {
    startHours = 0
    startMinutes = 0
  }

  hours = startHours + remainingTime[0]
  minutes = startMinutes + remainingTime[1]
  sendTimeEvents(hours, minutes, "Finish", "finishTime")
}

private sendTimeEvents(hours, minutes, timeName, eventName) {
  
  timeSum = hours + minutes
  if (hours + minutes > 0) {
    time = calculateTruncatedTimeFromNow(hours, minutes)
    timeText = buildLocalTimeText(time)
    timeDescription = "${timeName} at ${timeText}"
  } else {
    time = RESET_DATE
    timeText = RESET_STRING
    timeDescription = RESET_STRING
  }
  
  sendEvent name: eventName, value: time
  if (textEventsEnabled) {
    sendEvent name: eventName + "Text", value: timeText
  }
  if (descriptionEventsEnabled) {
    sendEvent name: eventName + "Description", value: timeDescription
  }
}

private calculateTruncatedTimeFromNow(hours, minutes) {    
  nowMillis = new Date().toInstant().toEpochMilli()
  nowMinutes = nowMillis - nowMillis % (60 * 1000)
  new Date(nowMinutes + (hours * 60 * 60 * 1000) + (minutes * 60 * 1000))
}

private buildLocalTimeText(time) {
  time
  .toInstant()
  .atZone(ZoneId.systemDefault())
  .toLocalDateTime()
  .format(TIME_FORMATTER)
}

private sendRemoteEnableEvents(remoteEnable) {
  if (!remoteEnable) return
  sendBooleanEvent remoteEnable.fullRemoteControl, "fullRemoteControlEnabled"
  sendBooleanEvent remoteEnable.smartGrid, "smartGridEnabled"
  sendBooleanEvent remoteEnable.mobileStart, "mobileStartEnabled"
}

private sendBooleanEvent(value, eventName) {
  if (value == null) return
  sendEvent name: eventName, value: value
}

// ** Child Devices **

private delegateEcoFeedback(ecoFeedback) {
  if (!ecoFeedbackEnabled) return 
  if (ecoFeedback != null || existsEcoFeedbackChildDevice()) {
    ecoFeedbackChildDevice.eventReceived(ecoFeedback)
  }
}

private delegateLight(light) {
  if (lightEnabled && light) {
    lightChildDevice.eventReceived(light)
  }
}

private delegateAmbientLight(ambientLight) {
  if (ambientLightEnabled && ambientLight) {
    ambientLightChildDevice.eventReceived(ambientLight)
  }
}

private delegateTemperatures(temperatureList, targetTemperatureList) {
  if (!temperaturesEnabled || !temperatureList) return
  for (i = 0; i < 3; i++) {
    delegateTemperature(temperatureList[i], targetTemperatureList[i], i + 1)
  }
}

private delegateTemperature(temperature, targetTemperature, temperatureNumber) {
  if (isTemperaturePresent(temperature)) {
    getTemperatureChildDevice(temperatureNumber).eventReceived(temperature, isTemperaturePresent(targetTemperature) ? targetTemperature : null)
  }
}

private delegateCoreTemperature(coreTemperatureList, coreTargetTemperatureList) {
  if (!coreTemperatureEnabled) return
  coreTemperature = coreTemperatureList[0]
  if (isTemperaturePresent(coreTemperature)) {
    coreTargetTemperature = isTemperaturePresent(coreTargetTemperatureList[0]) ? coreTargetTemperatureList[0] : null
    coreTemperatureChildDevice.eventReceived(coreTemperature, coreTargetTemperature)
  }
}

private isTemperaturePresent(temperature) {
  temperature && temperature.value_raw && temperature.value_raw > MISSING_INT
}

private getEcoFeedbackChildDevice() {
  getOrCreateChildDevice ECO_FEEDBACK_DNI_SUFFIX, "Eco Feedback", "Miele Eco Feedback Child Device"
}

private existsEcoFeedbackChildDevice() {
  dni = getChildDeviceDni(ECO_FEEDBACK_DNI_SUFFIX)
  getChildDevice(dni) != null
}

private getLightChildDevice() {
  getOrCreateChildDevice LIGHT_DNI_SUFFIX, "Light", "Miele Light Child Device"
}

private getAmbientLightChildDevice() {
  getOrCreateChildDevice AMBIENT_LIGHT_DNI_SUFFIX, "Ambient Light", "Miele Light Child Device"
}

private getTemperatureChildDevice(temperatureNumber) {
  getOrCreateChildDevice getTemperatureChildDeviceDni(temperatureNumber), "Temperature ${temperatureNumber}", "Miele Temperature Child Device"
}

private getTemperatureChildDeviceDni(temperatureNumber) {
  "${TEMPERATURE_DNI_SUFFIX}${temperatureNumber}"
}
private getCoreTemperatureChildDevice() {
  getOrCreateChildDevice CORE_TEMPERATURE_DNI_SUFFIX, "Core Temperature", "Miele Temperature Child Device"
}

private deleteDisabledChildDevices() {
  if (!ecoFeedbackEnabled) deleteDisabledChildDevice(ECO_FEEDBACK_DNI_SUFFIX)
  if (!lightEnabled) deleteDisabledChildDevice(LIGHT_DNI_SUFFIX)
  if (!ambientLightEnabled) deleteDisabledChildDevice(AMBIENT_LIGHT_DNI_SUFFIX)
  if (!coreTemperatureEnabled) deleteDisabledChildDevice(CORE_TEMPERATURE_DNI_SUFFIX)
  if (!temperaturesEnabled) {
    for (int i = 1; i <= 3; i++) {
      deleteDisabledChildDevice(getTemperatureChildDeviceDni(i))
    }
  }
}

// Child Device Shared Methods

private getOrCreateChildDevice(dniSuffix, nameSuffix, type) {
  dni = getChildDeviceDni(dniSuffix)
  getChildDevice(dni) ?: createChildDevice(dni, nameSuffix, type)
}

private getChildDeviceDni(dniSuffix) {
  "${device.deviceNetworkId}${dniSuffix}"
}

private createChildDevice(dni, nameSuffix, type) {
  child = addChildDevice(
    "xap",
    type,
    dni,
    ["name": "${device.name} ${nameSuffix}", "isComponent": true]
  )
  logInfo "Child device '${child.name}' created (dni=${child.deviceNetworkId})"
  return child
}

private deleteDisabledChildDevice(dniSuffix) {
  dni = getChildDeviceDni(dniSuffix)
  childDevice = getChildDevice(dni)
  if (childDevice) {
    deleteChildDevice(dni)
    logInfo "Child device '${childDevice.name}' deleted [${dni}]"
  }
}

// ** Logging **

private logDebug(message) {
  if (debugEnabled) {
    log.debug buildLogMessage(message)
  }
}

private logInfo(message) {
  log.info buildLogMessage(message)
}

private buildLogMessage(message) {
  "[${device.name}] ${message}"
}

// ** Utility Methods - shared with child devices **

private static resetDeviceAttributesEndingWith(device, suffix) {
  device.supportedAttributes
  .collect { it.name }
  .findAll { it.endsWith(suffix) }
  .each { resetStringAttribute device, it }
}

private static resetStringAttribute(device, attributeName) {
  String value = device.currentValue(attributeName)
  if (value && value != RESET_STRING) {
    device.sendEvent name: attributeName, value: RESET_STRING
  }
}
