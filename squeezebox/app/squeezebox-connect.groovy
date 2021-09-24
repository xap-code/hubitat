/**
 *  Squeezebox Connect
 *
 *  Git Hub Raw Link - Use for Import into Hubitat
 *  https://raw.githubusercontent.com/xap-code/hubitat/master/squeezebox/app/squeezebox-connect.groovy
 *
 *  Copyright 2017 Ben Deitch
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
 * 13/10/2018 - Added support for password protection
 * 14/10/2018 - Added support for player synchronization
 * 14/10/2018 - Added transferPlaylist
 * 15/10/2018 - Add child switch device for Enable/Disable All Alarms
 * 09/02/2019 - Changed server polling to use Async HTTP call
 * 05/04/2019 - Add basic sync mechanism to prevent multiple server status requests building up
 * 10/04/2019 - Change sync mechanism to warning if server requests are overlapping
 * 13/04/2020 - Adjust busy state logic
 * 13/04/2020 - merge PR to include git hub link in header
 * 13/04/2020 - Skip server status updates when busy
 * 20/04/2020 - Support player excludeFromPolling preference
 * 27/04/2020 - Reset busy status after skipping 10 server status updates
 * 29/05/2020 - Don't poll details for disabled player devices
 * 10/09/2020 - Replace ugly scheduling code with better solution
 * 21/11/2020 - Add child switch device for extra player power switch
 * 24/09/2021 - Only skip 2 server requests before resetting busy flag
 * 24/09/2021 - Set HTTP timeout to 60s
 * 24/09/2021 - Reformat indentation
 */
definition(
  name: "Squeezebox Connect",
  namespace: "xap",
  author: "Ben Deitch",
  description: "Integrates a Squeezebox Server instance into Hubitat.",
  category: "My Apps",
  iconUrl: "http://cdn.device-icons.smartthings.com/Entertainment/entertainment2-icn.png",
  iconX2Url: "http://cdn.device-icons.smartthings.com/Entertainment/entertainment2-icn@2x.png",
  iconX3Url: "http://cdn.device-icons.smartthings.com/Entertainment/entertainment2-icn@3x.png")

preferences {
  page(name: "serverPage", title: "<h2>Configure Squeezebox Server</h2>", nextPage: "optionsPage", install: false, uninstall: true) {
    section("<h3>Connection Details</h3>") {
      input(name: "serverIP", type: "text", required: true, title: "Server IP Address")
      input(name: "serverPort", type: "number", required: true, title: "Server Port Number")
    }
  }
  page(name: "optionsPage", title: "<h2>Options</h2>", nextPage: "playersPage", install: false, uninstall: false) {
    section("<h3>Refresh Interval</h3>") {
      paragraph("Number of seconds between each call to the Squeezebox Server to update players' status.")
      paragraph("If you want to display player status from Hubitat or build rules that react quickly to changes in player status then use low values e.g. 2. If you are just sending commands to the players then higher values are recommended.")
      input(name: "refreshSeconds", type: "enum", options: [2, 4, 10, 30, 60], required: true, title: "Players Status Refresh Interval")
    }
    section("<h3>Security (optional)</h3>") {
      paragraph("If you have enabled password protection on the Squeezebox Server then you can enter the authentication details here.")
      input(name: "passwordProtection", type: "enum", options: ["Password Protection", "No Password Protection"], required: false, title: "Password Protection")
      input(name: "username", type: "text", required: false, title: "Username")
      input(name: "password", type: "password", required: false, title: "Password")
    }
    section("<h3>Other Settings</h3>") {
      paragraph("Enables/disables debug logging for the Squeezebox Connect app and all the Squeezebox Player child devices.")
      input(name: "debugLogging", type: "bool", title: "Enable debug logging?", defaultValue: false, required: false)
    }
  }
  page(name: "playersPage", title: "<h2>Select Squeezebox Players</h2>", nextPage: "playerOptionsPage", install: false, uninstall: false)
  page(name: "playerOptionsPage", title: "<h2>Player Options</h2>", install: true, uninstall: false) {
    section("<h3>Enable/Disable Alarms Switch</h3>") {
      paragraph("Selected players will create a child switch device which can be used to enable/disable all alarms on that player.")
      input(name: "createAlarmsSwitchPlayers", type: "enum", title: "Create Alarms Switch For Players", multiple: true, options: selectedPlayers)
    }
    section("<h3>Enable/Disable Extra Power Switch</h3>") {
      paragraph("Selected players will create a child switch device which can be used to switch that player on or off. This is an extra switch that can be used to link the player with Google Home to switch it on and off without Google mistaking the player as a light.")
      input(name: "createPowerSwitchPlayers", type: "enum", title: "Create Power Switch For Players", multiple: true, options: selectedPlayers)
    }
  }
}

def playersPage() {

  // set the authorization token
  state.auth = buildAuth()
    
  // send the server status request so that the connectedPlayers property can be populated from the response
  getServerStatus()
  
  def playerNames = connectedPlayerNames()
  // once we have some players then stop refreshing the page so that we don't reset user selections on refresh
  def playerRefreshInterval = playerNames?.isEmpty() ? 4 : null
    
  dynamicPage(name: "playersPage", refreshInterval: playerRefreshInterval) {
    section("<h3>Connected Players</h3>") {
      paragraph("Select the players you want to integrate to Hubitat:")
      input(name: "selectedPlayers", type: "enum", title: "Select Players (${playerNames.size()} found)", multiple: true, options: playerNames)
    }
    section("<h3>Device Naming (optional)</h3>") {
      paragraph("If configured, adds the specified prefix before each player device name when creating child devices for each Squeezebox.")
      input(name: "deviceNamePrefix", type: "string", title: "Device Name Prefix", required: false)
      paragraph("If configured, adds the specified suffix after each player device name when creating child devices for each Squeezebox.")
      input(name: "deviceNameSuffix", type: "string", title: "Device Name Suffix", required: false)
      paragraph("NB: Spaces need to be explicitly included if required.")
    }
  }
}

def log(message) {
  if (debugLogging) {
    log.debug message
  }
}

def connectedPlayerNames() {
  state.connectedPlayers ? state.connectedPlayers?.collect( {it.name} ) : []
}

def installed() {
  initialize()
}

def updated() {
  initialize()
}

def uninstalled() {
  removeChildDevices(getChildDevices())
}

def removeChildDevices(delete) {
  delete.each {deleteChildDevice(it.deviceNetworkId)}
}

def initialize() {
  unschedule()
  unsubscribe()
  initializePlayers()
  scheduleServerStatus()
}

def buildAuth() {
    
    if (passwordProtection != "Password Protection") {
        return null
    }
    
    def auth = "${username}:${password}"
    auth.bytes.encodeBase64().toString()
}

def initializePlayers() {

  def hub = location.hubs[0].id
    
  def serverHostAddress = "${serverIP}:${serverPort}"
    
  // build selected and unselected lists by comparing the selected names to the connectedPlayers state property
  def selected = state.connectedPlayers?.findAll { selectedPlayers.contains(it.name) }
  def unselected = state.connectedPlayers?.findAll { !selected.contains(it) }

  // add devices for players that have been newly selected
  selected?.each {
    def player = getChildDevice(it.mac)
    if (!player) {
      def prefixedPlayerName = deviceNamePrefix ? "${deviceNamePrefix}${it.name}" : it.name
      def playerName = deviceNameSuffix ? "${prefixedPlayerName}${deviceNameSuffix}" : prefixedPlayerName

      log "Add child device [${playerName} > ${it.mac}]"

      player = addChildDevice(
        "xap", 
        "Squeezebox Player", 
        it.mac, // use the MAC address as the DNI to allow easy retrieval of player devices based on ID (MAC) returned by Squeezebox Server
        hub,
        ["name": playerName, "label": playerName]
      )
    }
    // always configure the player in case the server settings have changed
    player.configure(serverHostAddress, it.mac, state.auth, createAlarmsSwitchPlayers?.contains(it.name), createPowerSwitchPlayers?.contains(it.name))
    // refresh the player to initialise state
    player.refresh()
  }
    // delete any child devices for players that are no longer selected
  unselected?.each {
    def player = getChildDevice(it.mac)
    if (player) {

      log "Delete child device [${player.name} > ${it.mac}]"

      deleteChildDevice(it.mac)
    }
  }
}

def scheduleServerStatus() {
  int refreshSecondsInt = refreshSeconds.toInteger()

  if (refreshSecondsInt < 60) {
    log "Scheduling refresh every ${refreshSecondsInt} seconds"
    schedule("${Math.round(Math.random() * refreshSecondsInt)}/${refreshSecondsInt} * * ? * * *", "getServerStatus")
  } else {
    log "Scheduling refresh every minute"
    runEvery1Minute("getServerStatus")
  }

  // remove busy indicator in case rescheduling occurred whilst waiting for response
  unsetBusy()
}

def processJsonMessage(msg) {

  log "Squeezebox Connect Received [${msg.params[0]}]: ${msg}"

  def playerId = msg.params[0]
  if (playerId == "") {
    // if no playerId in message params then it must be a general server message
    processServerMessage(msg)
  } else {
    // otherwise retrieve the child device via the player's MAC address and pass the message to it for processing
    def player = getChildDevice(playerId)
    if (player) {
      player.processJsonMessage(msg)
    }
  }
}

def processServerMessage(msg) {

  // extract the server command from the message
  def command = msg.params[1][0]

  // process the command
  switch (command) {
    case "serverstatus":
      processServerStatus(msg)
  }
}

def processServerStatus(msg) {

  // extract the player name, ID (MAC) and power state for all players connected to Squeezebox Server
  def connectedPlayers = msg.result.players_loop.collect {[
    name: it.name,
    mac: it.playerid,
    power: it.power
  ]}

  // hold the currently connected players in state
  state.connectedPlayers = connectedPlayers
   
  // update the player devices
  updatePlayers()
}

private setBusy() {
  
  state.busy = true
}

private unsetBusy() {
  
  state.remove("busy")
  state.remove("skipped")
}

private getServerStatus() {

  // very loose sync mechanism, doesn't guarantee no race conditions but should stop requests building up if there's a connection issue
  if (state.busy) {
    log.warn("Skipping request to refresh server status as still waiting on previous request. Hub network IO could be busy. If this occurs often then check network connectivity between HE Hub and LMS Server or consider increasing player refresh interval.")
    state.skipped = state.skipped ? state.skipped + 1 : 1
    if (state.skipped == 2) {
      log.warn("Skipped 2 requests. Resetting busy status to allow server status refresh on next attempt.")
      unsetBusy()
    }
  } else {
    setBusy()
    // instructs Squeezebox Server to give high level status info on all connected players
    executeCommand(["", ["serverstatus", 0, 99]])
  }
}

def updatePlayers() {

  // iterate over connected players to update their status
  state.connectedPlayers?.each {
      
    // look to see if we have a device for the player
    def player = getChildDevice(it.mac)

    // if we have then, if it is switched on (or its power status has changed), get detailed status information to update it with;
    // otherwise, just update its power state to save spamming the server with constant requests for updates for players that aren't even switched on
    if (player && (it.power == 1 || player.updatePower(it.power))) {
      if (player.isExcluded()) {
        log "Not refreshing player excluded from polling: ${player.name}"
      } else if (player.isDisabled()) {
        log "Not refreshing disabled player device: ${player.name}"
      } else {
        player.refresh()
      }
    }
  }
}

/****************************
 * Methods for Child Devices *
 ****************************/

def getChildDeviceName(mac) {
  getChildDevice(mac)?.name
}

def getChildDeviceMac(name) {
  def trimmedName = name.trim()
  def result = getChildDevices().findResult {it.name == trimmedName ? it.deviceNetworkId : null} 
  result ? result : getChildDevice(trimmedName)?.deviceNetworkId
}

def unsyncAll(playerMacs) {
  log.debug "unsyncAll(${playerMacs})"
  playerMacs?.each { getChildDevice(it)?.unsync() }
}

def transferPlaylist(destination, tempPlaylist, time) {
  log.debug "transferPlaylist(\"${destination}\", \"${tempPlaylist}\", ${time})"

  def destinationMac = getChildDeviceMac(destination)
  def destinationPlayer = getChildDevice(destinationMac)
    
  if (destinationPlayer) {
    destinationPlayer.resumeTempPlaylistAtTime(tempPlaylist, time)
    destinationPlayer.refresh()
    true
  } else {
    false
  }
}

/*******************
 * Utility Methods *
 *******************/
 
def executeCommand(params) {

  log "Squeezebox Connect Send: ${params}"

  def jsonBody = buildJsonRequest(params)

  def postParams = [
    uri: "http://${serverIP}:${serverPort}",
    path: "jsonrpc.js",
    requestContentType: 'application/json',
    contentType: 'application/json',
    timeout: 60,
    body: jsonBody.toString()
  ]

  if (state.auth) {
    postParams.headers = ["Authorization": "Basic ${state.auth}"]
  }

  asynchttpPost "receiveHttpResponse", postParams
}

// build the JSON content for the Squeezebox Server request
def buildJsonRequest(params) {
 
  def request = [
    id: 1,
    method: "slim.request",
    params: params
  ]
    
  def json = new groovy.json.JsonBuilder(request)

  json
}

// receive the Squeezebox Server response and extract the JSON
def receiveHttpResponse(response, data) {

  try {

    if (response.status == 200) {

      def json = response.json
      if (json) {
        processJsonMessage(json)
      } else {
        log.warn "Received response that didn't contain any JSON"
      }

    } else {
      log.warn "Received error response [${response.status}] : ${response.errorMessage}"
    }

  } finally {
    unsetBusy()
  }
}
