/*
 *  GivEnergy Connect
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
 * 03/06/2023 - v0.1 - Initial implementation
 */

definition(
  name: "GivEnergy Connect",
  namespace: "xap",
  author: "Ben Deitch",
  description: "Integrates GivEnergy Battery System into Hubitat via local GivTCP service.",
  category: "My Apps",
  iconUrl: "", iconX2Url: "", iconX3Url: ""
)

preferences {
    section("<h3>GivTCP</h3>") {
      input name: "serviceAddress", type: "string", required: true, title: "GivTCP Service IP Address"
      input name: "servicePort", type: "number", required: true, title: "GivTCP Service Port"
      input name: "pollingInterval", type: "number", required: true, range: "1..59", title: "Polling Interval (seconds)"
    }
    section("<h3>Other Settings</h3>") {
      input name: "debugLogging", type: "bool", title: "Enable/disable debug logging", defaultValue: false, required: false
    }
}

def log(message) {
    if (debugLogging) {
	    log.debug message
    }
}

def installed() {
  initialize()
}

def uninstalled() {
  removeChildren()
}

def updated() {
  initialize()
}

def initialize() {
  unschedule()
  createChildren()
  scheduleQuery()
}

def getChildDniRoot() {
  "givenergy-connect"
}

def getBatteryDni() {
  childDniRoot + "-batt"
}

def getBattery() {
  getChildDevice batteryDni
}

def getGridDni() {
  childDniRoot + "-grid"
}

def getGrid() {
  getChildDevice gridDni
}

def getLoadDni() {
  childDniRoot + "-load"
}

def getLoad() {
  getChildDevice loadDni
}

def getPvDni() {
  childDniRoot + "-pv"
}

def getPv() {
  getChildDevice pvDni
}

def createChildren() {
  
  if (!battery) {
    log "Create Battery Device"
    addChildDevice "xap", "Battery Power Meter Child Device", batteryDni, [name: 'GivEnergy Battery']
  }
  
  if (!grid) {
    log "Create Grid Device"
    addChildDevice "xap", "Power Meter Child Device", gridDni, [name: 'GivEnergy Grid Power']
  }
  
  if (!load) {
    log "Create Load Device"
    addChildDevice "xap", "Power Meter Child Device", loadDni, [name: 'GivEnergy Load Power']
  }
  
  if (!pv) {
    log "Create PV Device"
    addChildDevice "xap", "Power Meter Child Device", pvDni, [name: 'GivEnergy PV Power']
  }
}

def removeChildren() {
  childDevices.each {deleteChildDevice(it.deviceNetworkId)}
}

def scheduleQuery() {
  log.info "Scheduling refresh every ${pollingInterval} seconds"
  schedule("0/${pollingInterval} * * * * ? *", queryStats)
}

def refresh() {
  queryStats()
}

def queryStats() {
  log "Query for battery data"
  
  asynchttpGet "handleStatsResponse", [uri: "http://${serviceAddress}:${servicePort}/runAll"]
}
                   
def handleStatsResponse(response, data) {
  
  def json = getResponseJson(response)
  
  log json
  
  def power = json?.Power?.Power
  
  if (power) {
    battery.sendEvent (name: "power", value: power.Invertor_Power, unit: "watts")
    battery.sendEvent (name: "battery", value: power.SOC)
    grid.sendEvent (name: "power", value: power.Grid_Power, unit: "watts")
    load.sendEvent (name: "power", value: power.Load_Power, unit: "watts")
    pv.sendEvent (name: "power", value: power.PV_Power, unit: "watts")
  }
}

private getResponseJson(response) {
  
  if (response.status == 200) {
    def json = response.json
    if (json) {
      json
    } else {
      log.warn "Received response that didn't contain any JSON"
    }
  } else {
    log.warn "Received error response [${response.status}] : ${response.errorMessage}"
  }
}
