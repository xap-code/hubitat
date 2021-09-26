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
 * ??/??/???? - v1.0 - Initial Implementation
 */
metadata {
  definition (
    name: "Squeezebox Server",
    namespace: "xap",
    author: "Ben Deitch",
    importUrl: "https://raw.githubusercontent.com/xap-code/hubitat/master/squeezebox/drivers/squeezebox-server.groovy"
  ) {
    capability "Initialize"
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
    log.info "CLI Connected: ${parent.serverIP}:${parent.getServerCliPort()}"
    // TODO Login
    sendSubscribe()
  } catch (ConnectException ex) {
    log.error("Unable to connect to CLI (will reattempt in 1 minute): ${ex.getMessage()}")
    runIn(60, "initialize")
  }
}

def parse(message) {
  
  String[] decoded = message.split(" ").collect { URLDecoder.decode(it) }

  // assumes that player IDs are always MAC addresses
  if (decoded[0] =~ /^(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}$/) {
    log "Received player message: ${decoded}"
  } else {
    log "Ignoring non-player message: ${decoded}"
  }
}

def telnetStatus(message) {
  log.error "CLI Connection Failure (attempting to reconnect): ${message}"
  runIn(5, "initialize")
}

def sendMsg(message) {
  log "CLI Send: ${message}"
  sendHubCommand new hubitat.device.HubAction(message, hubitat.device.Protocol.TELNET)
}

private sendSubscribe() {
  log "Start CLI subscription"
  sendMsg("subscribe playlist,prefset,sync")
}