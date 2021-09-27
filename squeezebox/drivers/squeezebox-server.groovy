/**
 *  Squeezebox Server
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

/* ChangeLog:
 * 27/09/2021 - v2.2.1 - Add 'newmetadata' to subscription to update track details when listening to stream
 * 27/09/2021 - v2.2 - Indicate connection status on app label
 * 26/09/2021 - v2.1.2 - Add generic Actuator, Sensor capabilities
 * 26/09/2021 - v2.1.1 - Unschedule when switching off() to prevent automatic reconnection
 * 26/09/2021 - v2.1 - Add Switch capability to enable/disable communication with server
 * 26/09/2021 - v2.0 - Initial Implementation
 */
metadata {
  definition (
    name: "Squeezebox Server",
    namespace: "xap",
    author: "Ben Deitch",
    importUrl: "https://raw.githubusercontent.com/xap-code/hubitat/master/squeezebox/drivers/squeezebox-server.groovy"
  ) {
    capability "Actuator"
    capability "Initialize"
	capability "Sensor"
    capability "Switch"
    capability "Telnet"
  }
}

def log(message) {
  if (getParent().debugLogging) {
    log.debug message
  }
}

def installed() {
  initialize()
}

def uninstalled() {
  telnetClose()
}

def initialize() {

  telnetClose()

  try {
    telnetConnect parent.serverIP, parent.getServerCliPort(), null, null
    log.info "Squeezebox CLI Connected: ${parent.serverIP}:${parent.getServerCliPort()}"
    if (parent.isPasswordProtected()) {
      sendLogin()
    }
    sendSubscribe()
    setConnected(true)
  } catch (ConnectException ex) {
    log.error("Unable to connect to CLI (will reattempt in 1 minute): ${ex.getMessage()}")
    runIn(60, "initialize")
    setConnected(false)
  }
}

def on() {
  initialize()
}

def off() {
  unschedule()
  telnetClose()
  setConnected(false)
}

def parse(message) {
  
  String[] decoded = message.split(" ").collect { URLDecoder.decode(it) }

  // assumes that player IDs are always MAC addresses
  if (decoded[0] =~ /^(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}$/) {
    log "Received player message: ${decoded}"
    parent.playerMessageReceived(decoded)
  } else {
    log "Ignoring non-player message: ${decoded}"
  }
}

def telnetStatus(message) {
  log.error "CLI Connection Failure (${parent.isPasswordProtected() ? "check username/password correct; ": ""}attempting to reconnect): ${message}"
  runIn(10, "initialize")
  setConnected(false)
}

def sendMsg(message) {
  if (state.connected) {
    log "CLI Send: ${message}"
    send message
  } else {
    log.warn "Cannot send message while disconnected, connect using initialise() or on()"
  }
}

private send(message) {
  sendHubCommand new hubitat.device.HubAction(message, hubitat.device.Protocol.TELNET)
}

private sendLogin() {
  log "CLI Login (username: ${parent.username})"
  send("login ${parent.username} ${parent.password}")
}

private sendSubscribe() {
  log "Start CLI subscription"
  send("subscribe playlist,prefset,sync,newmetadata")
}

private setConnected(connected) {
  state.connected = connected
  parent.setConnected(connected)
  sendEvent(name: "switch", value: connected ? "on" : "off", display: true)
}
