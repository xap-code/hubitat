/**
 *  Miele Event Stream
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
 * 08/01/2023 - v1.0.0 - Initial implementation
 */
metadata {
  definition (name: "Miele Event Stream", namespace: "xap", author: "Ben Deitch") {
    capability "Initialize"
    command "close"
  }
	preferences {
		input name: "debugEnabled", title: "Enable debug logging", type: "bool"
	}
}

import groovy.transform.Field

// define constants
@Field static final long MAX_LAST_MESSAGE_AGE_MILLIS = 30 * 1000

def configure(url, accessToken) {
  logDebug("configured with accessToken and url: ${url}")
  state.url = url;
  state.accessToken = encrypt(accessToken);
}

def initialize() {
  logDebug("connecting to event stream...")
  scheduleReconnectCheck()
  connectStream()
}

def close() {
  logDebug("closing event stream...")
  unschedule()
  closeStream()
}

def parse(message) {
  logDebug("received: ${message}")
  state.lastMessageReceived = now()
  if (!"ping".equals(message.trim())) {
    parent.eventReceived(message)
  }
}

def eventStreamStatus(message) {
  switch (getEventStreamStatusType(message)) {

    case "START":
      logInfo(message)
      break;
    
    case "STOP":
      logDebug(message + " (NB: if event stream has been re-initialized this message may refer to the previous event stream that has been replaced)")
      break;
      
    case "ERROR":
      logWarn(message)
      break;
    
    default:
      logWarn("unrecognized event stream status type: ${message}")
  }
}

private scheduleReconnectCheck() {
  unschedule()
  runEvery1Minute("reconnectCheck")
}

private reconnectCheck() {
  long lastMessageAgeMillis = now() - state.lastMessageReceived
  if (lastMessageAgeMillis > MAX_LAST_MESSAGE_AGE_MILLIS) {
    logWarn("reconnecting to Miele")
    connectStream()
  }
}

private connectStream() {
  interfaces.eventStream.connect(
    state.url,
    [ 
      headers: [ "Authorization": "Bearer ${decrypt(state.accessToken)}" ]
    ]
  )
}

private closeStream() {
  interfaces.eventStream.close()
}

private getEventStreamStatusType(message) {
  int end = message.indexOf(":")
  return end > -1 ? message.substring(0, end).trim() : null;
}

private logInfo(message) {
  log.info buildLogMessage(message)
}

private logWarn(message) {
  log.warn buildLogMessage(message)
}

private logDebug(message) {
  if (debugEnabled) {
    log.debug buildLogMessage(message)
  }
}

private buildLogMessage(message) {
  "[${device.name}] ${message}"
}
