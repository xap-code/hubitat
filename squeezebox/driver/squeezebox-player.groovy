/**
 *  Squeezebox Player
 *
 *  Copyright 2017 Ben Deitch
 *
 */

metadata {

  definition (name: "Squeezebox Player", namespace: "xap", author: "Ben Deitch") {
    capability "Actuator"
    capability "MusicPlayer"
    capability "Refresh"
    capability "Sensor"
    capability "Speech Synthesis"
    capability "Switch"

    attribute "serverHostAddress", "STRING"
    attribute "playerMAC", "STRING"
    attribute "syncGroup", "STRING"
      
    command "fav1"
    command "fav2"
    command "fav3"
    command "fav4"
    command "fav5"
    command "fav6"
    command "playFavorite", ["NUMBER"]
    command "playTextAndRestore", ["STRING","NUMBER"]
    command "playTextAndResume", ["STRING","NUMBER"]
    command "playTrackAndRestore", ["STRING", "NUMBER", "NUMBER"]
    command "playTrackAndResume", ["STRING", "NUMBER", "NUMBER"]
    command "playTrackAtVolume", ["STRING","NUMBER"]
    command "speak", ["STRING"]
    command "sync", ["STRING"]
    command "unsync"
    command "unsyncAll"
  }
}

def configure(serverHostAddress, playerMAC, auth) {

  state.serverHostAddress = serverHostAddress
  sendEvent(name: "serverHostAddress", value: state.serverHostAddress, displayed: false, isStateChange: true)

  state.playerMAC = playerMAC
  sendEvent(name: "playerMAC", value: state.playerMAC, displayed: false, isStateChange: true)
    
  state.auth = auth
}

def processJsonMessage(msg) {

  //log.debug "Squeezebox Player Message [${device.name}]: ${msg}"

  def command = msg.params[1][0]

  switch (command) {
    case "status":
      processStatus(msg)
  }
}

private processStatus(msg) {

  updatePower(msg.result?.get("power"))
  updateVolume(msg.result?.get("mixer volume"))
  updatePlayPause(msg.result?.get("mode"))
  updateSyncGroup(msg.result?.get("sync_master"), msg.result?.get("sync_slaves"))
  def trackDetails = msg.result?.playlist_loop?.get(0)
  updateTrackUri(trackDetails?.url)
  String track
  if (trackDetails) {
    track = trackDetails.artist ? "${trackDetails.title} by ${trackDetails.artist}" : trackDetails.title
  }
  updateTrackDescription(track)
}

private updatePower(onOff) {

  String current = device.currentValue("switch")
  String onOffString = String.valueOf(onOff) == "1" ? "on" : "off"

  if (current != onOffString) {
    sendEvent(name: "switch", value: onOffString, displayed: true)
    return true
 
  } else {
    return false
  }
}

private updateVolume(volume) {
  String absVolume = Math.abs(Integer.valueOf(volume)).toString()
  sendEvent(name: "level", value: absVolume, displayed: true)
}

private updatePlayPause(playpause) {

  String status
  switch (playpause) {
    case "play":
      status = "playing"
      break
    case "pause":
      status = "paused"
      break
    case "stop":
      status = "stopped"
      break
    default:
      status = playpause
  }

  sendEvent(name: "status", value: status, displayed: true)
}

private updateTrackUri(trackUri) {
  sendEvent(name: "trackUri", value: trackUri, displayed: true)
}

private updateTrackDescription(trackDescription) {
  sendEvent(name: "trackDescription", value: trackDescription, displayed: true)
}

private updateSyncGroup(syncMaster, syncSlaves) {

  def parent = getParent()

  def syncGroup = syncMaster
    ? "${syncMaster},${syncSlaves}"
      .tokenize(",")
      .collect { parent.getChildDeviceName(it) }
    : null

  state.syncGroup = syncGroup
  sendEvent(name: "syncGroup", value: syncGroup, displayed: true)
}

/************
 * Commands *
 ************/

def refresh() {
  executeCommand(["status", "-", 1, "tags:abclsu"]) 
}

//--- Power
def on() {
  executeCommand(["power", 1])
  refresh()
}
def off() {
  executeCommand(["power", 0])
  refresh()  
}

//--- Volume
private setVolume(volume) {
  executeCommand(["mixer", "volume", volume])
}
def setLevel(level) {
  setVolume(level)
  refresh()
}
def mute() {
  executeCommand(["mixer", "muting", 1])
  refresh() 
}
def unmute() {
  executeCommand(["mixer", "muting", 0])
  refresh() 
}

//--- Playback
def play() {
  executeCommand(["play"])
  refresh()
}
def pause() {
  executeCommand(["pause"])
  refresh() 
}
def stop() {
  executeCommand(["stop"])
  refresh() 
}
def nextTrack() {
  executeCommand(["playlist", "jump", "+1"])
  refresh()  
}
def previousTrack() {
  executeCommand(["playlist", "jump", "-1"])
  refresh() 
}
def setTrack(trackToSet) {
  executeCommand(["playlist", "stop", trackToSet])
  stop()  
}
def resumeTrack(trackToResume) {
  playUri(trackToResume)
}
def restoreTrack(trackToRestore) {
  playUri(trackToRestore)
}
def playTrack(trackToPlay) {
  playUri(trackToPlay)
}
def playTrackAtVolume(uri, volume) {
  setVolume(volume)
  playUri(uri)
}
def playUri(uri) {
  executeCommand(["playlist", "play", uri])
  refresh()  
}

//--- resume/restore methods
private previewAndGetDelay(uri, duration, volume=null) {
  executeCommand(["playlist", "preview", "url:${uri}", "silent:1"])
  if (volume != null) {
    state.previousVolume = device.currentValue("level");
    setVolume(volume)
  }
  return 2 + duration as int
}
def resume() {
  def tempPlaylist = "tempplaylist_" + state.playerMAC.replace(":", "")
  executeCommand(["playlist", "resume", tempPlaylist, "wipePlaylist:1"])
  if (state.previousVolume) {
    setVolume(state.previousVolume)
  }
  refresh()
}
def restore() {
  def tempPlaylist = "tempplaylist_" + state.playerMAC.replace(":", "")
  executeCommand(["playlist", "preview", "cmd:stop"])
  refresh()
}

def playTrackAndResume(uri, duration, volume=null) {
  def delay = previewAndGetDelay(uri, duration, volume)
  runIn(delay, resume)
}
def playTrackAndRestore(uri, duration, volume=null) {
  def delay = previewAndGetDelay(uri, duration, volume)
  runIn(delay, restore)
}

//--- Favorites
def playFavorite(index) {
  executeCommand(["favorites", "playlist", "play", "item_id:${index - 1}"])
  refresh() 
}

def fav1() { playFavorite(1) }
def fav2() { playFavorite(2) }
def fav3() { playFavorite(3) }
def fav4() { playFavorite(4) }
def fav5() { playFavorite(5) }
def fav6() { playFavorite(6) }

//--- Speech
private getTts(text) {
  if (text) {
    textToSpeech(text)
  } else {
    log.warning "No text provided for speak() method"
  }
}

def playText(text) {
  def tts = getTts(text)
  if (tts) {
    playUri(tts.uri)
  }
}
def playTextAndRestore(text, volume=null) {
  def tts = getTts(text)
  if (tts) {
    playTrackAndRestore(tts.uri, tts.duration, volume)
  }
}
def playTextAndResume(text, volume=null) {
  def tts = getTts(text)
  if (tts) {
    playTrackAndResume(tts.uri, tts.duration, volume)
  }
}
def speak(text) {
  playText(text)
}

//--- Synchronization
private getPlayerMacs(players) {
  players?.collect { parent.getChildDeviceMac(it) }
    .findAll { it != null }
}

def sync(slaves) {
  def parent = getParent()
  def slaveMacs = getPlayerMacs(slaves.tokenize(","))
  if (slaveMacs) {
    slaveMacs.each { executeCommand(["sync", it]) }
    refresh()
  }
}

def unsync() {
  executeCommand(["sync", "-"])
  refresh()
}

def unsyncAll() {
  def syncGroupMacs = getPlayerMacs(state.syncGroup)
  if (syncGroupMacs) {
    getParent().unsyncAll(syncGroupMacs)
  }
}
/*******************
 * Utility Methods *
 *******************/
 
private executeCommand(params) {
  //log.debug "Squeezebox Send: ${params}"
    
  def jsonBody = buildJsonRequest(params)
   
  def postParams = [
    uri: "http://${state.serverHostAddress}",
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
 
private buildJsonRequest(params) {
 
  def request = [
    id: 1,
    method: "slim.request",
    params: [state.playerMAC, params]
  ]
    
  def json = new groovy.json.JsonBuilder(request)

  json
}
