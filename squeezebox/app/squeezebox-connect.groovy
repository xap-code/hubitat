/**
 *  Squeezebox Connect
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
 * 26/11/2022 - v2.2.1 - Replace app name literal with app.name
 * 01/06/2022 - v2.2 - Add support for better synchronized player updates
 * 27/09/2021 - v2.1 - Indicate connection status on app label
 * 26/09/2021 - v2.0.1 - Fix bug causing requests not to be URL encoded
 * 26/09/2021 - v2.0 - Replace player HTTP commands and polling with LMS CLI commands and subscription
 * 26/09/2021 - v1.0.1 - Fix bug causing players list to reset during app configuration
 * 25/09/2021 - v1.0 - Integration into Hubitat Package Manager
 * 24/09/2021 - Set HTTP timeout to 60s
 * 24/09/2021 - Only skip 2 server requests before resetting busy flag
 * 21/11/2020 - Add child switch device for extra player power switch
 * 10/09/2020 - Replace ugly scheduling code with better solution
 * 29/05/2020 - Don't poll details for disabled player devices
 * 27/04/2020 - Reset busy status after skipping 10 server status updates
 * 20/04/2020 - Support player excludeFromPolling preference
 * 13/04/2020 - Skip server status updates when busy
 * 13/04/2020 - merge PR to include git hub link in header
 * 13/04/2020 - Adjust busy state logic
 * 10/04/2019 - Change sync mechanism to warning if server requests are overlapping
 * 05/04/2019 - Add basic sync mechanism to prevent multiple server status requests building up
 * 09/02/2019 - Changed server polling to use Async HTTP call
 * 15/10/2018 - Add child switch device for Enable/Disable All Alarms
 * 14/10/2018 - Added transferPlaylist
 * 14/10/2018 - Added support for player synchronization
 * 13/10/2018 - Added support for password protection
 */
definition(
  name: "Squeezebox Connect",
  namespace: "xap",
  author: "Ben Deitch",
  description: "Integrates a Squeezebox Server instance into Hubitat.",
  category: "My Apps",
  importUrl: "https://raw.githubusercontent.com/xap-code/hubitat/master/squeezebox/app/squeezebox-connect.groovy",
  iconUrl: "",  iconX2Url: "",  iconX3Url: "")

preferences {
  page(name: "serverPage", title: "<h2>Configure Squeezebox Server</h2>", nextPage: "optionsPage", install: false, uninstall: true) {
    section("<h3>Connection Details</h3>") {
      input(name: "serverIP", type: "text", required: true, title: "Server IP Address")
      input(name: "serverPort", type: "number", required: true, title: "Server Port Number")
    }
  }
  page(name: "optionsPage", title: "<h2>Options</h2>", nextPage: "playersPage", install: false, uninstall: false) {
    section("<h3>Security (optional)</h3>") {
      paragraph("If you have enabled password protection on the Squeezebox Server then you can enter the authentication details here.")
      input(name: "passwordProtection", type: "enum", options: ["Password Protection", "No Password Protection"], required: false, title: "Password Protection")
      input(name: "username", type: "text", required: false, title: "Username")
      input(name: "password", type: "password", required: false, title: "Password")
    }
    section("<h3>Other Settings</h3>") {
      paragraph("Enables/disables debug logging for the app and all child devices.")
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
  queryServerStatus()
  
  def playerNames = connectedPlayerNames
  // once we have some players then stop refreshing the page so that we don't reset user selections on refresh
  def playerRefreshInterval = playerNames?.isEmpty() ? 4 : 0
    
  dynamicPage(name: "playersPage", refreshInterval: playerRefreshInterval) {
    section("<h3>Connected Players${playerRefreshInterval ? " (discovering players, please wait...)" : ""}</h3>") {
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

def getConnectedPlayerNames() {
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
  initializeServer()
  initializePlayers()
}

def isPasswordProtected() {
  passwordProtection == "Password Protection"
}

private buildAuth() {
   
  if (!isPasswordProtected()) {
    return null
  }
    
  def auth = "${username}:${password}"
  auth.bytes.encodeBase64().toString()
}

private getServerDni() {
  state.serverUuid
}

private getServer() {
  getChildDevice(serverDni)
}

private initializeServer() {
  
  if (!server) {
    queryCliPort()
    runIn(2, "createServer")
  }
}

private createServer() {

  addChildDevice(
      "xap",
      "Squeezebox Server",
      serverDni,
      ["name": "Squeezebox Server", label: "Squeezebox Server"]
    )
}

private initializePlayers() {
    
  // build selected and unselected lists by comparing the selected names to the connectedPlayers state property
  def selected = state.connectedPlayers?.findAll { selectedPlayers.contains(it.name) }
  def unselected = state.connectedPlayers?.findAll { !selected.contains(it) }

  // add devices for players that have been newly selected
  selected?.each {
    def player = getChildDevice(it.id)
    if (!player) {
      def prefixedPlayerName = deviceNamePrefix ? "${deviceNamePrefix}${it.name}" : it.name
      def playerName = deviceNameSuffix ? "${prefixedPlayerName}${deviceNameSuffix}" : prefixedPlayerName

      log "Add child device [${playerName} > ${it.id}]"

      player = addChildDevice(
        "xap", 
        "Squeezebox Player", 
        it.id, // use the ID as the DNI to allow easy retrieval of player devices based on ID returned by Squeezebox Server
        ["name": playerName, "label": playerName]
      )
    }
    // always configure the player in case the server settings have changed
    player.configure(createAlarmsSwitchPlayers?.contains(it.name), createPowerSwitchPlayers?.contains(it.name))
  }
  
  // delete any child devices for players that are no longer selected
  unselected?.each {
    def player = getChildDevice(it.id)
    if (player) {

      log "Delete child device [${player.name} > ${it.id}]"

      deleteChildDevice(it.id)
    }
  }
}

private processJsonMessage(msg) {

  log "Received [${msg.params[0]}]: ${msg}"

  def playerId = msg.params[0]
  if (playerId == "") {
    // if no playerId in message params then it must be a general server message
    processServerMessage(msg)
  } else {
    log "Ignoring player message: ${msg}"
  }
}

private queryServerStatus() {

  executeCommand(["", ["serverstatus", 0, 99]])
}

private queryCliPort() {
  
  executeCommand(["", ["pref", "plugin.cli:cliport", "?"]])
}

private processServerMessage(msg) {

  // extract the server command from the message
  def command = msg.params[1][0]

  // process the command
  switch (command) {
    case "serverstatus":
      processServerStatus(msg)
      break
    case "pref":
      processCliPort(msg)
      break
  }
}

private processServerStatus(msg) {

  // extract the player ID and name for all players connected to Squeezebox Server
  def connectedPlayers = msg.result.players_loop.collect {[
    name: it.name,
    id: it.playerid
  ]}

  // hold the server UUID in state
  state.serverUuid = msg.result.uuid
  // hold the currently connected players in state
  state.connectedPlayers = connectedPlayers
}

private processCliPort(msg) {
  
  if (msg.params[1][1] == "plugin.cli:cliport") {
    int port = Integer.parseInt(msg.result._p2)
    
    log "Detected CLI port as ${port}"
    
    state.cliPort = port
    
  } else {
    log.warn("Unexpected pref message: ${msg}")
  }
}

/****************************
 * Methods for Child Devices *
 ****************************/
def sendPlayerCommand(player, params) {
  String encoded = params.collect { URLEncoder.encode String.valueOf(it) }.join(" ")
  server.sendMsg "${player.device.deviceNetworkId} ${encoded}"
}

def playerMessageReceived(msg) {

  def player = getChildDevice(msg[0])
  if (player) {
    player.processMessage(msg)
  } else {
    log "Ignoring message for unregistered player: $msg"
  }
}

def getServerCliPort() {
  state.cliPort
}

def getServerUsername() {
  username
}

def getServerPassword() {
  password
}

def setConnected(connected) {
  app.updateLabel(connected ? app.name : "${app.name} <span style='color:red'>(Server Disconnected)</span>")
}

private getChildDeviceName(id) {
  getChildDevice(id)?.name
}

private getChildDeviceId(name) {
  def trimmedName = name.trim()
  def result = getChildDevices().findResult {it.name == trimmedName ? it.deviceNetworkId : null} 
  result ? result : getChildDevice(trimmedName)?.deviceNetworkId
}

private actOnPlayers(playerIds, action) {
  playerIds?.each { 
    player = getChildDevice(it)
    if (player) {
      action(player)
    }
  }
}

def unsync(playerIds) {
  log "unsync(${playerIds})"
  actOnPlayers(playerIds, { player -> player.unsync() })
}

def refreshStatus(playerIds) {
  log "refreshStatus(${playerIds})"
  actOnPlayers(playerIds, { player -> player.refreshStatus() })
}

def updatePlayPauseIfOn(playerIds, playPause) {
  log "updatePlayPauseIfOn(${playerIds}, ${playPause})"
  actOnPlayers(playerIds, { player -> 
    if (player?.currentValue("switch") == "on") {
      player.updatePlayPause(playPause)
    }
  })
}

def transferPlaylist(destination, tempPlaylist, time) {
  log "transferPlaylist(\"${destination}\", \"${tempPlaylist}\", ${time})"

  def destinationId = getChildDeviceId(destination)
  def destinationPlayer = getChildDevice(destinationId)
    
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
 
private executeCommand(params) {

  log "Send: ${params}"

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
private buildJsonRequest(params) {
 
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
}
