/**
 *  Squeezebox Connect
 *
 *  Copyright 2017 Ben Deitch
 *
 */

/* ChangeLog:
 * 13/10/2018 - Added support for password protection
 * 14/10/2018 - Added support for player synchronization
 * 14/10/2018 - Added transferPlaylist
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
      input "debugLogging", "bool", title: "Enable debug logging?", defaultValue: false, required: false
    }
  }
  page(name: "playersPage", title: "<h2>Select Squeezebox Players</h2>", install: true, uninstall: true)
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
    player.configure(serverHostAddress, it.mac, state.auth)
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

def scheduleServerStatus(seconds) {
    schedule("${seconds} * * * * ?", "getServerStatus${seconds}" )
}

def scheduleServerStatus() {
  int refreshSecondsInt = refreshSeconds.toInteger()
  for (int i = 0; i < 60; i++) {
    if (i % refreshSecondsInt ==0) {
      scheduleServerStatus(i)
    }
  }
}

// have to create distinct methods otherwise successive calls
// to schedule(...) just replace the previous call
def getServerStatus0() { getServerStatus() }
def getServerStatus2() { getServerStatus() }
def getServerStatus4() { getServerStatus() }
def getServerStatus6() { getServerStatus() }
def getServerStatus8() { getServerStatus() }
def getServerStatus10() { getServerStatus() }
def getServerStatus12() { getServerStatus() }
def getServerStatus14() { getServerStatus() }
def getServerStatus16() { getServerStatus() }
def getServerStatus18() { getServerStatus() }
def getServerStatus20() { getServerStatus() }
def getServerStatus22() { getServerStatus() }
def getServerStatus24() { getServerStatus() }
def getServerStatus26() { getServerStatus() }
def getServerStatus28() { getServerStatus() }
def getServerStatus30() { getServerStatus() }
def getServerStatus32() { getServerStatus() }
def getServerStatus34() { getServerStatus() }
def getServerStatus36() { getServerStatus() }
def getServerStatus38() { getServerStatus() }
def getServerStatus40() { getServerStatus() }
def getServerStatus42() { getServerStatus() }
def getServerStatus44() { getServerStatus() }
def getServerStatus46() { getServerStatus() }
def getServerStatus48() { getServerStatus() }
def getServerStatus50() { getServerStatus() }
def getServerStatus52() { getServerStatus() }
def getServerStatus54() { getServerStatus() }
def getServerStatus56() { getServerStatus() }
def getServerStatus58() { getServerStatus() }

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

def getServerStatus() {
  // instructs Squeezebox Server to give high level status info on all connected players
  executeCommand(["", ["serverstatus", 0, 99]])
}

def updatePlayers() {

  // iterate over connected players to update their status
  state.connectedPlayers?.each {
      
    // look to see if we have a device for the player
    def player = getChildDevice(it.mac)

    // if we have then, if it is switched on (or its power status has changed), get detailed status information to update it with;
    // otherwise, just update its power state to save spamming the server with constant requests for updates for players that aren't even switched on
    if (player && (it.power == 1 || player.updatePower(it.power))) {
      executeCommand([it.mac, ["status", "-", 1, "tags:abclsu"]])
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
    body: jsonBody.toString()
  ]
    
  if (state.auth) {
    postParams.headers = ["Authorization": "Basic ${state.auth}"]
  }
     
  httpPost(postParams) { resp ->
    processJsonMessage(resp.data)
  }
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
