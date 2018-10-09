/**
 *  Squeezebox Player
 *
 *  Copyright 2017 Ben Deitch
 *
 */

metadata {

  definition (name: "Squeezebox Player", namespace: "xap", author: "Ben Deitch") {
    capability "Actuator"
    capability "Music Player"
    capability "Refresh"
    capability "Sensor"
    capability "Switch"

    attribute "serverHostAddress", "string"
    attribute "playerMAC", "string"

    command "playFavorite", ["number"]
    command "fav1"
    command "fav2"
    command "fav3"
    command "fav4"
    command "fav5"
    command "fav6"
    command "playTrackAndResume", ["string", "number", "number"]
    command "playTrackAndRestore", ["string", "number", "number"]
    command "playTrackAtVolume", ["string","number"]
  }
}

def configure(serverHostAddress, playerMAC) {

  state.serverHostAddress = serverHostAddress
  sendEvent(name: "serverHostAddress", value: state.serverHostAddress, displayed: false, isStateChange: true)

  state.playerMAC = playerMAC
  sendEvent(name: "playerMAC", value: state.playerMAC, displayed: false, isStateChange: true)
}

def processJsonMessage(msg) {

  log.debug "Squeezebox Player Message [${device.name}]: ${msg}"

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
  log.debug "Executing 'setPlaybackStatus'"
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
  log.debug "Executing 'setTrack'"
  // TODO: handle 'setTrack' command
}
def resumeTrack(trackToResume) {
  log.debug "Executing 'resumeTrack'"
  // TODO: handle 'resumeTrack' command
}
def restoreTrack(trackToRestore) {
  log.debug "Executing 'restoreTrack'"
  // TODO: handle 'restoreTrack' command
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
def playTrackAndResume(uri, duration) {
  log.debug "Executing 'playTrackAndResume'"
  playUri(uri)
}
def playTrackAndResume(uri, duration, volume) {
  log.debug "Executing 'playTrackAndResume'"
  setVolume(volume)
  playUri(uri)
}
def playTrackAndRestore(uri, duration) {
  log.debug "Executing 'playTrackAndRestore"
  playUri(uri)
}
def playTrackAndRestore(uri, duration, volume) {
  log.debug "Executing 'playTrackAndRestore"
  setVolume(volume)
  playUri(uri)
}
def playUri(uri) {
  executeCommand(["playlist", "play", uri])
  refresh()  
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