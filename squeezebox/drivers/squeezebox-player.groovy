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
 * 03/02/2023 - v2.4 - Add activity attribute for use with Google Home Community integration
 * 12/01/2023 - v2.3 - Add parameter to delay TTS playback
 * 01/06/2022 - v2.2.1 - Refresh synchronized players when playlist is cleared.
 * 01/06/2022 - v2.2 - Update commands to better match Hubitat capability specifications. Fix bug where slave players not updating status correctly.
 * 25/10/2021 - v2.1 - Add support for AudioVolume capability
 * 27/09/2021 - v2.0.6 - Handle 'playlist newsong' and 'newmetadata' to refresh track details
 * 26/09/2021 - v2.0.5 - Use state rather than attribute for syncGroup
 * 26/09/2021 - v2.0.4 - Remove unused attributes
 * 26/09/2021 - v2.0.3 - Correct log message
 * 26/09/2021 - v2.0.2 - Fix bugs where power, syncGroup not updated
 * 26/09/2021 - v2.0.1 - Fix bug where All Alarms child switch was not updating
 * 26/09/2021 - v2.0 - Replace player HTTP commands and polling with LMS CLI commands and subscription
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
    capability "AudioNotification"
    capability "AudioVolume"
    capability "MusicPlayer"
    capability "Refresh"
    capability "Sensor"
    capability "SpeechSynthesis"
    capability "Switch"

    attribute "repeat", "ENUM", REPEAT_MODE
    attribute "shuffle", "ENUM", SHUFFLE_MODE
    attribute "activity", "ENUM", ["active", "inactive", "standby"]
    
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
    command "repeat", [REPEAT_MODE]
    command "shuffle", [SHUFFLE_MOE]
    command "speakCurrentTrack"
    command "sync", ["STRING"]
    command "transferPlaylist", ["STRING"]
    command "unsync"
    command "unsyncAll"
     // explicitly added for the AudioNotification capability as the MusicPlayer capability hides the commands that take volume
    command "playText", ["STRING", "NUMBER"]
    command "playTrack", ["STRING", "NUMBER"]
  }
	preferences {
		input name: "ttsDelay", title: "TTS delay enable", description: "(Turn on if the beginning of the message is being dropped.)", type: "bool"
	}
}

import groovy.transform.Field

// define constants for repeat mode (order must match LMS modes)
@Field static final List REPEAT_MODE = ["off", "song", "playlist"]

// define constants for shuffle mode (order must match LMS modes)
@Field static final List SHUFFLE_MODE = ["off", "song", "album"]

def log(message) {
  if (parent.debugLogging) {
    log.debug message
  }
}

def installed() {
  initialize()
}

def initialize() {
  refresh()
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

    case "power":
      processPower(msg)
      break

    case "status":
      processStatus(msg)
      break

    case "time":
      processTime(msg)
      break

    case "playlist":
      processPlaylist(msg)
      break

    case "playerpref":
      processPlayerpref(msg)
      break

    case "prefset":
      processPrefset(msg)
      break

    case "sync":
    case "newmetadata":
      refreshStatus()
      break
      
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

private processPower(msg) {
  updatePower msg[2]
}

private processStatus(msg) {

  values = tagValues(msg)
  
  ifPresent(values, "power", "updatePower")
  ifPresent(values, "mixer volume", "updateVolume")
  ifPresent(values, "mode", "updatePlayPause")
  ifPresent(values, "playlist repeat", "updateRepeat")
  ifPresent(values, "playlist shuffle", "updateShuffle")

  updateTrackData(values.get("title"), values.get("artist"), values.get("album"), values.get("url"))

  updateTrackDescription(values.get("title"), values.get("artist"))

  updateSyncGroup(values.get("sync_master"), values.get("sync_slaves"))
}

private processTime(msg) {
  state.trackTime = msg[2]
}

private processPlaylist(msg) {
  
  switch (msg[2]) {

    case "pause":
      def playPause = msg[3] == "1" ? "pause" : "play"
      updatePlayPause(playPause)
      updateSlavesplayPauseIfMaster(playPause)
      break

    case "stop":
      updatePlayPause("stop")
      updateSlavesplayPauseIfMaster("stop")
      break

    case "newsong":
      refreshStatus()
      refreshSlavesStatusIfMaster()
      break
    
    case "sync":
      refreshStatus()
      break
    
    case "clear":
      updateTrackData(null, null, null, null)
      updateTrackDescription(null, null)
      refreshOthersStatus()
      break
      
    default:
      log "Ignored playlist command: ${msg}"
  } 
}

private processPlayerpref(msg) {

  if (msg[2] == "alarmsEnabled") {
    updateAlarms(msg[3])
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

      case "mute":
        updateMuted(msg[4])
        break

      case "syncgroupid":
        refreshStatus()
        break

      case "alarmsEnabled":
        updateAlarms(msg[4])
        break
    }
  }
}

private updatePower(onOff) {

  boolean isOn = String.valueOf(onOff) == "1";
  String onOffString = isOn ? "on" : "off"
  String current = attribute("switch")

  def powerSwitch = getChildDevice(powerSwitchDni)
  powerSwitch?.update(isOn)

  if (current != onOffString) {

    sendEvent(name: "switch", value: onOffString, displayed: true)
    updateActivityFromPower(isOn)
    return true
 
  } else {
    return false
  }
}

private updateVolume(volume) {
  String absVolume = Math.abs(Integer.valueOf(volume)).toString()
  sendEvent(name: "level", value: absVolume, displayed: true)
  sendEvent(name: "mute", value: volume.startsWith("-") ? "muted" : "unmuted")
}

private updateMuted(muted) {
  sendEvent(name: "mute", value: muted == "1" ? "muted" : "unmuted")
}

private updatePlayPause(playPause) {

  switch (playPause) {
    
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
      status = playPause
  }

  sendEvent(name: "status", value: status, displayed: true)
  updateActivityFromStatus(status)
}

private updateRepeat(repeat) {
  sendEvent(name: "repeat", value: REPEAT_MODE[Integer.valueOf(repeat)], displayed: true)
}

private updateShuffle(shuffle) {
  sendEvent(name: "shuffle", value: SHUFFLE_MODE[Integer.valueOf(shuffle)], displayed: true)
}

private updateTrackData(title, artist, album, uri) {

  trackData = uri ? json([
    "title": title,
    "artist": artist,
    "album": album,
    "image": "http://${parent.serverIP}:${parent.serverPort}/music/current/cover.jpg?player=${device.deviceNetworkId}",
    "uri": uri
  ]) : " "

  sendEvent(name: "trackData", value: trackData, displayed: true)
}

private updateTrackDescription(title, artist) {
  trackDescription = artist ? "${title} by ${artist}" : (title ?: " ")
  sendEvent(name: "trackDescription", value: trackDescription, displayed: true)
}

private updateSyncGroup(syncMaster, syncSlaves) {
  if (syncMaster && syncSlaves) {
    state.syncGroup = "${syncMaster},${syncSlaves}"
      .tokenize(",")
      .collect { parent.getChildDeviceName(it) ?: "Unlinked Player" }
  } else {
    state.remove("syncGroup")
  }
}

private updateAlarms(alarms) {
  def alarmsSwitch = getChildDevice(alarmsSwitchDni)
  alarmsSwitch?.update(alarms == "1")
}

private updateActivityFromPower(isOn) {
  sendEvent(name: "activity", value: isOn ? "standby" : "inactive", displayed: true)
}

private updateActivityFromStatus(status) {
  if (attribute("switch") == "on") {
    sendEvent(name: "activity", value: status == "stopped" ? "standby" : "active", displayed: true)
  }
}

/************
 * Commands *
 ************/

def refreshStatus() {
  sendCommand(["status", "-", 1, "tags:abclsu"]) 
}

def refreshAlarms() {
  if (getChildDevice(alarmsSwitchDni)) {
    sendCommand(["playerpref", "alarmsEnabled", "?"]) 
  }
}

def refresh() {
  refreshStatus()
  refreshAlarms()
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
private sendVolume(volume) {
  sendCommand(["mixer", "volume", volume])
}

def setLevel(level) {
  log "setLevel(${level})"
  sendVolume(level)
}

def setVolume(volumeLevel) {
  log "setVolume(${volumeLevel})"
  sendVolume(volumeLevel)
}

def volumeDown() {
  log "volumeDown()"
  sendVolume("-5")
}

def volumeUp() {
  log "volumeUp()"
  sendVolume("+5")
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
private playUri(uri, volume=null) {
  if (volume) {
    setVolume(volume)
  }
  sendCommand(["playlist", "play", uri])
}

private setUriAndRefresh(uri) {
  sendCommand(["playlist", "resume", uri, "noplay:1"])
  refreshStatus()
}

private 
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
  setUriAndRefresh(trackToSet)
}

def resumeTrack(trackToResume) {
  log "resumeTrack(\"${trackToResume}\")"
  playUri(trackToResume)
}

def restoreTrack(trackToRestore) {
  log "restoreTrack(\"${trackToRestore}\")"
  setUriAndRefresh(trackToRestore)
}

def playTrack(trackUri, volume=null) {
  log "playTrack(\"${trackUri}\")"
  playUri(trackUri, volume)
}

//--- resume/restore methods
private getTempPlaylistName() {
  "tempplaylist_" + device.deviceNetworkId.replace(":", "")
}

private captureTime() {
  sendCommand(["time", "?"])
}

private clearCapturedTime() {
  state.remove("trackTime")
}

private captureAndChangeVolume(volume) {
  if (volume != null) {
    state.previousVolume = attribute("level");
    setVolume(volume)
  }
}

private clearCapturedVolume() {
  state.remove("previousVolume")
}

private previewAndGetDelay(uri, duration, volume=null) {
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
def resumeTempPlaylistAtTime(time=null) {
  sendCommand(["playlist", "resume", tempPlaylistName, "wipePlaylist:1"])
  if (time) {
    sendCommand(["time", time])
  }
}

def restoreTempPlaylist() {
  sendCommand(["playlist", "resume", tempPlaylistName, "wipePlaylist:1", "noplay:1"])
}

def resume() {
  resumeTempPlaylistAtTime(state.trackTime)
  restoreVolumeAndRefresh()
  clearCapturedTime()
}

def restore() {
  restoreTempPlaylist()
  restoreVolumeAndRefresh()
}

def playTrackAndResume(uri, duration, volume=null) {
  log "playTrackAndResume(\"${uri}\", ${duration}, ${volume})"
  def wasPlaying = (attribute("status") == 'playing')
  if (wasPlaying) captureTime()
  def delay = previewAndGetDelay(uri, duration, volume)
  if (wasPlaying) runIn(delay, resume)
}

def playTrackAndRestore(uri, duration, volume=null) {
  log "playTrackAndRestore(\"${uri}\", ${duration}, ${volume})"
  def wasPlaying = (attribute("status") == 'playing')
  def delay = previewAndGetDelay(uri, duration, volume)
  if (wasPlaying) runIn(delay, restore)
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
private getTts(text, voice=null) {
  if (text) {
    text = text.replace("&", "and")
    // always add a break to the end of the generated file, prevents text being repeated if LMS decides to loop
    def result = textToSpeech("${ttsDelay ? "<break time='2s'/>" : ""}${text}<break time='2s'/>", voice)
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

def playText(text, volume=null) {
  log "playText(\"${text}\")"
  def tts = getTts(text)
  if (tts) {
    playUri(tts.uri, volume)
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

def speak(text, volume=null, voice=null) {
  log "speak(\"${text}\", ${volume}, ${voice ? '"' + voice + '"' : null})"
  def tts = getTts(text, voice)
  if (tts) {
    playUri(tts.uri, volume)
  }
}

//--- Synchronization
private getPlayerIds(players) {
  players?.collect { parent.getChildDeviceId(it) }
    .findAll { it != null }
}

private actOnSlavesIfMaster(action) {
  if (state.syncGroup && state.syncGroup[0] == device.name) {
    def slaveIds = getPlayerIds(state.syncGroup.tail())
    if (slaveIds) {
      action(slaveIds)
    }
  }
}

private refreshSlavesStatusIfMaster() {
  actOnSlavesIfMaster(parent.&refreshStatus)
}

private updateSlavesplayPauseIfMaster(playPause) {
  actOnSlavesIfMaster({ slaveIds -> parent.updatePlayPauseIfOn(slaveIds, playPause) })
}

private refreshOthersStatus() {
  if (state.syncGroup) {
    def others = state.syncGroup?.findAll { it != device.name }
    def otherIds = getPlayerIds(others)
    if (otherIds) {
      parent.refreshStatus(otherIds)
    }
  }
}

def sync(slaves) {
  log "sync(\"${slaves}\")"
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
  if (state.syncGroup) {
    def syncGroupIds = getPlayerIds(state.syncGroup)
    if (syncGroupIds) {
      parent.unsync(syncGroupIds)
    }
  }
}

//--- Playlist
def transferPlaylist(destination) {
  log "transferPlaylist(\"${destination}\")"
  def tempPlaylist = "tempplaylist_from_" + device.deviceNetworkId.replace(":", "")
  sendCommand(["playlist", "save", tempPlaylist])
  captureTime()
  if (parent.transferPlaylist(destination, tempPlaylist, state.trackTime)) {
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
  if (attribute("status") != 'playing') {
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
  log "playArtist(\"${search}\")"
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

private announce(text) {
  if (attribute("status") == "playing") {
    playTextAndResume(text)
  } else {
    playTextAndRestore(text)
  }
}

def speakCurrentTrack() {
  log "speakCurrentTrack()"
  if (attribute("trackDescription")) {
    if (attribute("status") == "playing") {
      playTextAndResume("The track currently playing is ${attribute("trackDescription")}")
    } else {
      playTextAndRestore("The last track played was ${attribute("trackDescription")}")
    }
  } else {
    announce("There is no track.")
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

private json(obj) {
  new groovy.json.JsonBuilder(obj)
}

private attribute(name) {
  device.currentValue(name)
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
