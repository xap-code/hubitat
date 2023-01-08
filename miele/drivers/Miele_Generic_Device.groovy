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
    attribute "program", "string"
    attribute "programPhase", "string"
    attribute "programDescription", "string"
    attribute "remainingTime", "number"
    attribute "remainingTimeText", "string"
    attribute "remainingTimeDescription", "string"
    attribute "startTime", "date"
    attribute "startTimeText", "string"
    attribute "startTimeDescription", "string"
    attribute "status", "string"
  }
	preferences {
		input name: "debugEnabled", title: "Enable debug logging", type: "bool"
	}
}

import groovy.transform.Field
import java.time.ZoneId
import java.time.format.DateTimeFormatter
  
// define constants
@Field static final String DNI_PREFIX = "miele:"
@Field static final String RESET_STRING = " "
@Field static final Date RESET_DATE = new Date(0)
@Field static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

def configure(mieleDeviceType) {
  logDebug("configured with mieleDeviceType: ${mieleDeviceType}")
  state.mieleDeviceType = mieleDeviceType
}

def getMieleDeviceId() {
  device.deviceNetworkId.substring(DNI_PREFIX.size())
}

def getMieleDeviceType() {
  state.mieleDeviceType
}

def eventReceived(data) {
  logDebug("received: ${data}")
  sendStatusEvent(data.status)
  sendProgramEvents(data.ProgramID, data.programPhase)
  sendDurationEvents(data.remainingTime, "remaining", "remainingTime")
  sendDurationEvents(data.elapsedTime, "elapsed", "elapsedTime")
  sendStartTimeEvents(data.startTime)
  sendFinishTimeEvents(data.startTime, data.remainingTime)
 }

private sendStatusEvent(status) {
 sendEvent name: "status", value: status.value_localized
}

private sendProgramEvents(program, programPhase) {

  if (program.value_raw == null) {
    return
  }
  
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
  
  sendEvent name: eventName, value: totalMinutes
  sendEvent name: eventName + "Text", value: durationText
  sendEvent name: eventName + "Description", value: durationDescription
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
  hours = startTime[0]
  minutes = startTime[1]
  sendTimeEvents(hours, minutes, "Start", "startTime")
}

private sendFinishTimeEvents(startTime, remainingTime) {
  hours = Math.max(0, startTime[0]) + remainingTime[0]
  minutes = Math.max(0, startTime[1]) + remainingTime[1]
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
  sendEvent name: eventName + "Text", value: timeText
  sendEvent name: eventName + "Description", value: timeDescription
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

private logDebug(message) {
  if (debugEnabled) {
    log.debug buildLogMessage(message)
  }
}

private buildLogMessage(message) {
  "[${device.name}] ${message}"
}
