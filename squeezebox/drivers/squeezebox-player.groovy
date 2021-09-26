/**
 *  Squeezebox Player
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
 * ??/??/???? - v2.0 - Replace player HTTP commands and polling with LMS CLI commands and subscription
 * 25/09/2021 - v1.0 - Integration into Hubitat Package Manager
 * 25/09/2021 - Use @Field for constant lists
 * 24/09/2021 - Reformat indentation
 * 24/09/2021 - Fix bug related to alarms switch not updating
 * 24/09/2021 - Set HTTP timeout to 60s
 * 21/11/2020 - Add optional child switch for power
 * 20/04/2020 - Add 500ms delay before post-command refresh
 * 20/04/2020 - Add excludeFromPolling preference
 * 13/04/2020 - Use async http method for player commands
 * 13/04/2020 - merge PR to include git hub link in header
 * 05/04/2020 - Support Audio Notification capability
 * 03/06/2019 - Change type of playFavorite argument NUMBER -> INTEGER
 * 03/06/2019 - Add speakCurrentTrack() command
 * 03/06/2019 - Resume playing track (instead of restore) after speaking
 * 18/10/2018 - Replace '&' in TTS input with ' and '
 * 18/10/2018 - Adjust spoken error messages to be more useful and less specific to voice control
 * 17/10/2018 - Add method to speak the names of an artist's albums
 * 16/10/2018 - Speak error message if search by name fails
 * 16/10/2018 - Add methods to control repeat and shuffle mode
 * 16/10/2018 - Add methods to play albums, artists and songs by name
 * 15/10/2018 - Add child switch device for Enable/Disable All Alarms
 * 14/10/2018 - Added transferPlaylist
 * 14/10/2018 - Bugfix - Track resume not taking into account previous track time position
 * 14/10/2018 - Added support for player synchronization
 * 13/10/2018 - Added support for password protection
*/
metadata {
  definition (
    name: "Squeezebox Player",
    namespace: "xap",
    author: "Ben Deitch",
    importUrl: "https://raw.githubusercontent.com/xap-code/hubitat/master/squeezebox/drivers/squeezebox-player.groovy"
  ) {
    capability "Actuator"
    capability "Audio Notification"
    capability "Music Player"
    capability "Refresh"
    capability "Sensor"
    capability "Speech Synthesis"
    capability "Switch"

    attribute "playerMAC", "STRING"
    attribute "repeat", "ENUM", REPEAT_MODE
    attribute "serverHostAddress", "STRING"
    attribute "shuffle", "ENUM", SHUFFLE_MODE
    attribute "syncGroup", "STRING"
    
    command "clearPlaylist"
    command "fav1"
    command "fav2"
    command "fav3"
    command "fav4"
    command "fav5"
    command "fav6"
    command "playAlbum", ["STRING"]
    command "playArtist", ["STRING"]
    command "playFavorite", ["INTEGER"]
    command "playSong", ["STRING"]
    command "playTrackAtVolume", ["STRING","NUMBER"]
    command "repeat", [REPEAT_MODE]
    command "shuffle", [SHUFFLE_MOE]
    command "speakArtistAlbums", ["STRING"]
    command "speakCurrentTrack"
    command "sync", ["STRING"]
    command "transferPlaylist", ["STRING"]
    command "unsync"
    command "unsyncAll"
  }
}

import groovy.transform.Field

// define constants for repeat mode (order must match LMS modes)
@Field static final List REPEAT_MODE = ["off", "song", "playlist"]

// define constants for shuffle mode (order must match LMS modes)
@Field static final List SHUFFLE_MODE = ["off", "song", "album"]

def log(message) {
  if (getParent().debugLogging) {
    log.debug message
  }
}

def getAlarmsSwitchDni() {
  "${device.deviceNetworkId}-alarms"
}

def getPowerSwitchDni() {
  "${device.deviceNetworkId}-power"
}

def configure(createAlarmsSwitch, createPowerSwitch) {
    
  configureChildSwitch(createAlarmsSwitch, alarmsSwitchDni, "All Alarms")
  configureChildSwitch(createPowerSwitch, powerSwitchDni, "Power")
      
  log "Configured with [createAlarmsSwitch: ${createAlarmsSwitch}, createPowerSwitch: ${createPowerSwitch}]"
}

def processMessage(String[] msg) {

  log "Squeezebox Player Received [${device.name}]: ${msg}"
  
  def command = msg[1]

  switch (command) {
    case "status":
      processStatus(msg)
      break
    case "time":
      processTime(msg)
      break
    case "playerpref":
      processPlayerpref(msg)
      break
    case "playlist":
      processPlaylist(msg)
      break
    case "prefset":
      processPrefset(msg)
    default:
      log "Ignored player message: ${msg}"
  }
}
   
private configureChildSwitch(createSwitch, switchDni, switchNameSuffix) {

  if (createSwitch) {
    if (!getChildDevice(switchDni)) {
      def childSwitch = addChildDevice("Squeezebox Player Child Switch", switchDni)
      childSwitch.name = "${device.name} - ${switchNameSuffix}"
    }
  } else if (getChildDevice(switchDni)) {
    deleteChildDevice(switchDni)
  }
}

private processStatus(msg) {

  values = tagValues(msg)
  
  ifPresent(values, "power", "updatePower")
  ifPresent(values, "mixer volume", "updateVolume")
  ifPresent(values, "mode", "updatePlayPause")
  ifPresent(values, "playlist repeat", "updateRepeat")
  ifPresent(values, "playlist shuffle", "updateShuffle")

  updateTrackUri(values.get("url"))
  updateSyncGroup(values.get("sync_master"), values.get("sync_slaves"))

  String trackDescription = values.get("artist") ? "${values.get("title")} by ${values.get("artist")}" : values.get("title")
  updateTrackDescription(trackDescription)
}

private processTime(msg) {
  state.trackTime = msg[2]
}

private processPlayerpref(msg) {

  if (msg[2] == "alarmsEnabled") {
    def alarmsSwitch = getChildDevice(alarmsSwitchDni)
    alarmsSwitch?.update(msg[3] == "1")    
  }
}

private processPlaylist(msg) {
  
  switch (msg[2]) {

    case "pause":
      updatePlayPause(msg[3] == "1" ? "pause" : "play")
      break

    case "stop":
      updatePlayPause("stop")
      break
    
    case "open":
      updatePlayPause("play")
      statusRefresh()
      break
    
    case "clear":
      updateTrackUri(null)
      updateTrackDescription(null)
      break
  } 
}

private processPrefset(msg) {
  
  if (msg[2] == "server") {
    switch (msg[3]) {
      
      case "volume":
        updateVolume(msg[4])
        break
      
      case "power":
        updatePower(msg[4])
        break
    }
  }
}

private updatePower(onOff) {

  boolean isOn = String.valueOf(onOff) == "1";
  String onOffString = isOn ? "on" : "off"
  String current = device.currentValue("switch")

  def powerSwitch = getChildDevice(powerSwitchDni)
  powerSwitch?.update(isOn)

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

  switch (playpause) {
    case "play":
      state.status = "playing"
      break
    case "pause":
      state.status = "paused"
      break
    case "stop":
      state.status = "stopped"
      break
    default:
      state.status = playpause
  }

  sendEvent(name: "status", value: state.status, displayed: true)
}

private updateRepeat(repeat) {
  sendEvent(name: "repeat", value: REPEAT_MODE[Integer.valueOf(repeat)], displayed: true)
}

private updateShuffle(shuffle) {
  sendEvent(name: "shuffle", value: SHUFFLE_MODE[Integer.valueOf(shuffle)], displayed: true)
}

private updateTrackUri(trackUri) {
  sendEvent(name: "trackUri", value: trackUri, displayed: true)
}

private updateTrackDescription(trackDescription) {
  state.trackDescription = trackDescription
  sendEvent(name: "trackDescription", value: trackDescription, displayed: true)
}

private updateSyncGroup(syncMaster, syncSlaves) {

  def parent = getParent()

  def syncGroup = syncMaster && syncSlaves
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

private statusRefresh() {
  sendCommand(["status", "-", 1, "tags:abclsu"]) 
}

private alarmRefresh() {
  if (getChildDevice(alarmsSwitchDni)) {
    sendCommand(["playerpref", "alarmsEnabled", "?"]) 
  }
}

def refresh() {
  statusRefresh()
  alarmRefresh()
}

//--- Power
def on() {
  log "on()"
  sendCommand(["power", 1])
}

def off() {
  log "off()"
  sendCommand(["power", 0])
}

//--- Volume
private setVolume(volume) {
  sendCommand(["mixer", "volume", volume])
}

def setLevel(level) {
  log "setLevel(${level})"
  setVolume(level)
}

def mute() {
  log "mute()"
  sendCommand(["mixer", "muting", 1])
}

def unmute() {
  log "unmute()"
  sendCommand(["mixer", "muting", 0])
}

//--- Playback
private executePlayAndRefresh(uri) {
  sendCommand(["playlist", "play", uri])
}

def play() {
  log "play()"
  sendCommand(["play"])
}

def pause() {
  log "pause()"
  sendCommand(["pause"])
}

def stop() {
  log "stop()"
  sendCommand(["stop"])
}

def nextTrack() {
  log "nextTrack()"
  sendCommand(["playlist", "jump", "+1"])
}

def previousTrack() {
  log "previousTrack()"
  sendCommand(["playlist", "jump", "-1"])
}

def setTrack(trackToSet) {
  log "setTrack(\"${trackToSet}\")"
  sendCommand(["playlist", "stop", trackToSet])
  stop()  
}

def resumeTrack(trackToResume) {
  log "resumeTrack(\"${trackToResume}\")"
  executePlayAndRefresh(trackToResume)
}

def restoreTrack(trackToRestore) {
  log "restoreTrack(\"${trackToRestore}\")"
  executePlayAndRefresh(trackToRestore)
}

def playTrack(trackToPlay) {
  log "playTrack(\"${trackToPlay}\")"
  executePlayAndRefresh(trackToPlay)
}

def playTrackAtVolume(uri, volume) {
  log "playTrackAtVolume(\"${uri}\", ${volume})"
  setVolume(volume)
  executePlayAndRefresh(uri)
}

def playUri(uri) {
  log "playUri(\"${uri}\")"
  executePlayAndRefresh(uri)
}

//--- resume/restore methods
private captureTime() {
  sendCommand(["time", "?"])
}

private clearCapturedTime() {
  state.remove("trackTime")
}

private captureAndChangeVolume(volume) {
  if (volume != null) {
    state.previousVolume = device.currentValue("level");
    setVolume(volume)
  }
}

private clearCapturedVolume() {
  state.remove("previousVolume")
}

private previewAndGetDelay(uri, duration, volume=null) {
  captureTime()
  sendCommand(["playlist", "preview", "url:${uri}", "silent:1"])
  captureAndChangeVolume(volume)    
  return 2 + duration as int
}

private restoreVolumeAndRefresh() {
  if (state.previousVolume) {
    setVolume(state.previousVolume)
    clearCapturedVolume()
  }
}

// this method is also used by the server when sending a playlist to this player
def resumeTempPlaylistAtTime(tempPlaylist, time=null) {
  sendCommand(["playlist", "resume", tempPlaylist, "wipePlaylist:1"])
  if (time) {
    sendCommand(["time", time])
  }
}

def resume() {
  log "resume()"
  def tempPlaylist = "tempplaylist_" + state.playerMAC.replace(":", "")
  resumeTempPlaylistAtTime(tempPlaylist, state.trackTime)
  clearCapturedTime()
  restoreVolumeAndRefresh()
}

def restore() {
  log "restore()"
  def tempPlaylist = "tempplaylist_" + state.playerMAC.replace(":", "")
  sendCommand(["playlist", "preview", "cmd:stop"])
  restoreVolumeAndRefresh()
}

def playTrackAndResume(uri, duration, volume=null) {
  log "playTrackAndResume(\"${uri}\", ${duration}, ${volume})"
  def wasPlaying = (state.status == 'playing')
  def delay = previewAndGetDelay(uri, duration, volume)
  if (wasPlaying) runIn(delay, resume)
}

def playTrackAndRestore(uri, duration, volume=null) {
  log "playTrackAndRestore(\"${uri}\", ${duration}, ${volume})"
  def delay = previewAndGetDelay(uri, duration, volume)
  runIn(delay, restore)
}

//--- Favorites
def playFavorite(index) {
  log "playFavorite(${index})"
  int intIndex = Integer.valueOf(index)
  sendCommand(["favorites", "playlist", "play", "item_id:${intIndex - 1}"])
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
    text = text.replace("&", "and")
    // add a break to the end of the generated file, prevents text being repeated if LMS decides to loop?!?
    def result = textToSpeech("${text}<break time='2s'/>")
    // reduce the duration to account for the added break
    if (result) {
      result.duration -= 2
      result
    } else {
      log.error "textToSpeech returned null"
    }
  } else {
    log.error "No text provided for speak() method"
  }
}

def playText(text) {
  log "playText(\"${text}\")"
  def tts = getTts(text)
  if (tts) {
    executePlayAndRefresh(tts.uri)
  }
}

def playTextAndRestore(text, volume=null) {
  log "playTextAndRestore(\"${text}\", ${volume})"
  def tts = getTts(text)
  if (tts) {
    playTrackAndRestore(tts.uri, tts.duration, volume)
  }
}

def playTextAndResume(text, volume=null) {
  log "playTextAndResume(\"${text}\", ${volume})"
  def tts = getTts(text)
  if (tts) {
    playTrackAndResume(tts.uri, tts.duration, volume)
  }
}

def speak(text) {
  log "speak(\"${text}\")"
  playText(text)
}

//--- Synchronization
private getPlayerIds(players) {
  players?.collect { parent.getChildDeviceId(it) }
    .findAll { it != null }
}

def sync(slaves) {
  log "sync(\"${slaves}\")"
  def parent = getParent()
  def slaveIds = getPlayerIds(slaves.tokenize(","))
  if (slaveIds) {
    slaveIds.each { sendCommand(["sync", it]) }
  }
}

def unsync() {
  log "unsync()"
  sendCommand(["sync", "-"])
}

def unsyncAll() {
  log "unsyncAll()"
  def slaves = state.syncGroup?.findAll { it != device.name }
  def syncGroupIds = getPlayerIds(slaves)
  if (syncGroupIds) {
    getParent().unsyncAll(syncGroupIds)
  }
}

//--- Playlist
def transferPlaylist(destination) {
  log "transferPlaylist(\"${destination}\")"
  def tempPlaylist = "tempplaylist_from_" + state.playerMAC.replace(":", "")
  sendCommand(["playlist", "save", tempPlaylist])
  captureTime()
  if (getParent().transferPlaylist(destination, tempPlaylist, state.trackTime)) {
    sendCommand(["playlist", "clear"])
  }
  clearCapturedTime()
}

def clearPlaylist() {
  log "clearPlaylist()"
  sendCommand(["playlist", "clear"])
}

//--- Alarms
def disableAlarms() {
  log "disableAlarms()"
  sendCommand(["alarm", "disableall"])
}

def enableAlarms() {
  log "enableAlarms()"
  sendCommand(["alarm", "enableall"])
}

//--- Library Methods
private checkSuccess(searchType) {
  if (state.status != 'playing') {
      playTextAndRestore("Sorry, your search didn't return anything. Please try providing less of the ${searchType} name.")
  }
}

def checkAlbumSuccess() {
  checkSuccess("album")
}

def playAlbum(search) {
  log "playAlbum(\"${search}\")"
  sendCommand(["playlist", "loadtracks", "album.titlesearch=${search}"])
  runIn(3, checkAlbumSuccess)
}

def checkArtistSuccess() {
  checkSuccess("artist")
}

def playArtist(search) {
  log "playAlbum(\"${search}\")"
  sendCommand(["playlist", "loadtracks", "contributor.namesearch=${search}"])
  runIn(3, checkArtistSuccess)
}

def checkSongSuccess() {
  checkSuccess("song")
}

def playSong(search) {
  log "playSong(\"${search}\")"
  sendCommand(["playlist", "loadtracks", "track.titlesearch=${search}"])
  runIn(3, checkSongSuccess)
}

def speakArtistAlbums(artist) {
  log "speakArtistAlbums(\"${artist}\")"
  sendCommand(["search", 0, 2, "term:${artist}"], { resp -> getArtistForListAlbums(artist, resp) })
}

private announce(text) {
  if (state.status == "playing") {
    playTextAndResume(text)
  } else {
    playTextAndRestore(text)
  }
}

def speakCurrentTrack() {
  log "speakCurrentTrack()"
  if (state.trackDescription) {
    if (state.status == "playing") {
      playTextAndResume("The track currently playing is ${state.trackDescription}")
    } else {
      playTextAndRestore("The last track played was ${state.trackDescription}")
    }
  } else {
    announce("There is no track.")
  }
}

private getArtistForListAlbums(artistSearch, response) {
    
  def artists = response?.data?.result?.contributors_loop
    
  switch (artists?.size()) {
    case null:
    case 0:
      announce("Sorry, I couldn't find any artists matching ${artistSearch}. Please try providing less of the artist name.")
      break
    case 1:
      listArtistAlbums(artists.first())
      break
    default:
      def exactArtist = artists.find { it.contributor.equalsIgnoreCase(artistSearch.trim()) }
      if (exactArtist) {
        listArtistAlbums(exactArtist)
      } else {
        def artistNames = artists.collect({ it.contributor }).join(", ")
        announce("I found multiple matching artists: ${artistNames}. Please try providing more of the artist name.")
      }
  }
}

private listArtistAlbums(artist) {
   def artistName = artist.contributor
   def artistId = artist.contributor_id
   sendCommand(["albums", 0, -1, "artist_id:${artistId}"], { resp -> listAlbums(artistName, resp) })
}

private listAlbums(artistName, response) {
  
  def albums = response?.data?.result?.albums_loop?.collect { it.album }
    
  switch (albums?.size()) {
    case null:
    case 0:
      announce("Sorry, I couldn't find any albums for ${artistName}.")
      break
    case 1:
      announce("I found one album for ${artistName}: ${albums.first()}")
      break
    default:
      def lastAlbum = albums.pop() 
      def albumList = "${albums.toSorted().join(", ")} and ${lastAlbum}"
      announce("I found ${albums.size()} albums for ${artistName}: ${albumList}.")
  }
}

//--- Repeat and Shuffle
def repeat(repeat=null) {
  log "repeat(\"${repeat}\")"
  def mode = tryConvertToIndex(repeat, REPEAT_MODE)
  sendCommand(["playlist", "repeat", mode])
}

def shuffle(shuffle=null) {
  log "shuffle(\"${shuffle}\")"
  def mode = tryConvertToIndex(shuffle, SHUFFLE_MODE)
  sendCommand(["playlist", "shuffle", mode])
}

/************************
 * Child Switch Methods *
 ************************/

def childSwitchedOn(childDni) {
  
  if (childDni == powerSwitchDni) {
    on()
  } else if (childDni == alarmsSwitchDni) {
    enableAlarms()
  } else {
    log.warn "childSwitchedOn invoked by unrecognised device: ${childDni}"
  }
}

def childSwitchedOff(childDni) {

  if (childDni == powerSwitchDni) {
    off()
  } else if (childDni == alarmsSwitchDni) {
    disableAlarms()
  } else {
    log.warn "childSwitchedOff invoked by unrecognised device: ${childDni}"
  }
}

/*******************
 * Utility Methods *
 *******************/

private tryConvertToIndex(value, lowerCaseValues) {
  def lowerCaseValue = String.valueOf(value).toLowerCase()
  def index = lowerCaseValues.indexOf(lowerCaseValue)
  index < 0 ? value : index
}

private ifPresent(tagValues, tag, action) {
  if (tagValues.containsKey(tag)) {
    "${action}"(tagValues.get(tag))
  }
}

private tagValues(msg) {
  def values = msg.collect { tagValue it }.findAll().collectEntries { [(it.tag) : it.value] }
}

private tagValue(msgLine) {
  int tagEnd = msgLine.indexOf(":")
  tagEnd >= 0
  ? [ tag: msgLine.substring(0, tagEnd), value: msgLine.substring(tagEnd + 1) ]
  : null
}

private sendCommand(params) {

  log "Squeezebox Player Send Command: ${params}"

  parent.sendPlayerCommand(this, params)
}
