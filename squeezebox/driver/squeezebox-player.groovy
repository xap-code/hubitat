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

    attribute "serverHostAddress", "string"
    attribute "playerMAC", "string"

    command "fav1"
    command "fav2"
    command "fav3"
    command "fav4"
    command "fav5"
    command "fav6"
    command "playFavorite", ["number"]
    command "playTextAndRestore", ["string","number"]
	  command "playTextAndResume", ["string","number"]
    command "playTrackAndRestore", ["string", "number", "number"]
    command "playTrackAndResume", ["string", "number", "number"]
    command "playTrackAtVolume", ["string","number"]
    command "speak", ["string"]
  }
}

def configure(serverHostAddress, playerMAC) {

  state.serverHostAddress = serverHostAddress
  sendEvent(name: "serverHostAddress", value: state.serverHostAddress, displayed: false, isStateChange: true)

  state.playerMAC = playerMAC
  sendEvent(name: "playerMAC", value: state.playerMAC, displayed: false, isStateChange: true)
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

    //log.debug "Squeezebox Player [${device.name}]: updating power: ${current} -> ${onOffString}"
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

/************
 * Commands *
 ************/

def refresh() {
  executeCommand(["status", "-", 1, "tags:abclu"]) 
}

//--- Power
def on() {
  log.debug "Executing 'on'"
  executeCommand(["power", 1])
  refresh()
}
def off() {
  log.debug "Executing 'off'"
  executeCommand(["power", 0])
  refresh()  
}

//--- Volume
private setVolume(volume) {
  executeCommand(["mixer", "volume", volume])
}
def setLevel(level) {
  log.debug "Executing 'setLevel'"
  setVolume(level)
  refresh()
}
def mute() {
  log.debug "Executing 'mute'"
  executeCommand(["mixer", "muting", 1])
  refresh() 
}
def unmute() {
  log.debug "Executing 'unmute'"
  executeCommand(["mixer", "muting", 0])
  refresh() 
}

//--- Playback
def setPlaybackStatus() {
  log.debug "setPlaybackStatus not implemented"
  // TODO: handle 'setPlaybackStatus' command
}
def play() {
  log.debug "Executing 'play'"
  executeCommand(["play"])
  refresh()
}
def pause() {
  log.debug "Executing 'pause'"
  executeCommand(["pause"])
  refresh() 
}
def stop() {
  log.debug "Executing 'stop'"
  executeCommand(["stop"])
  refresh() 
}
def nextTrack() {
  log.debug "Executing 'nextTrack'"
  executeCommand(["playlist", "jump", "+1"])
  refresh()  
}
def previousTrack() {
  log.debug "Executing 'previousTrack'"
  executeCommand(["playlist", "jump", "-1"])
  refresh() 
}
def setTrack(trackToSet) {
  log.debug "setTrack not implemented"
}
def resumeTrack(trackToResume) {
  log.debug "resumeTrack not implemented"
}
def restoreTrack(trackToRestore) {
  log.debug "restoreTrack not implemented"
}
def playTrack(trackToPlay) {
  log.debug "Executing 'playTrack'"
  playUri(trackToPlay)
}
def playTrackAtVolume(uri, volume) {
  log.debug "Executing 'playTrackAtVolume'"
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
  log.debug "Resuming temp playlist"
  def tempPlaylist = "tempplaylist_" + state.playerMAC.replace(":", "")
  executeCommand(["playlist", "resume", tempPlaylist, "wipePlaylist:1"])
  if (state.previousVolume) {
    setVolume(state.previousVolume)
  }
  refresh()
}
def restore() {
  log.debug "Restoring temp playlist"
  def tempPlaylist = "tempplaylist_" + state.playerMAC.replace(":", "")
  executeCommand(["playlist", "preview", "cmd:stop"])
  refresh()
}

def playTrackAndResume(uri, duration, volume=null) {
  log.debug "Executing 'playTrackAndResume'"
  def delay = previewAndGetDelay(uri, duration, volume)
  runIn(delay, resume)
}
def playTrackAndRestore(uri, duration, volume=null) {
  log.debug "Executing 'playTrackAndRestore"
  def delay = previewAndGetDelay(uri, duration, volume)
  runIn(delay, restore)
}

//--- Favorites
def playFavorite(index) {
  log.debug "Playing favorite ${index}"
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

def speak(text) {
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
