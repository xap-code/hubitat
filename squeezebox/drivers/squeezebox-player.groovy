/**
 *  Squeezebox Player
 *
 *  Git Hub Raw Link - Use for Import into Hubitat
 *  https://raw.githubusercontent.com/xap-code/hubitat/master/squeezebox/drivers/squeezebox-player.groovy
 *
 *  Copyright 2017 Ben Deitch
 *
 */

/* ChangeLog:
 * 13/10/2018 - Added support for password protection
 * 14/10/2018 - Added support for player synchronization
 * 14/10/2018 - Bugfix - Track resume not taking into account previous track time position
 * 14/10/2018 - Added transferPlaylist
 * 15/10/2018 - Add child switch device for Enable/Disable All Alarms
 * 16/10/2018 - Add methods to play albums, artists and songs by name
 * 16/10/2018 - Add methods to control repeat and shuffle mode
 * 16/10/2018 - Speak error message if search by name fails
 * 17/10/2018 - Add method to speak the names of an artist's albums
 * 18/10/2018 - Adjust spoken error messages to be more useful and less specific to voice control
 * 18/10/2018 - Replace '&' in TTS input with ' and '
 * 03/06/2019 - Resume playing track (instead of restore) after speaking
 * 03/06/2019 - Add speakCurrentTrack() command
 * 03/06/2019 - Change type of playFavorite argument NUMBER -> INTEGER
 * 05/04/2020 - Support Audio Notification capability
 * 13/04/2020 - merge PR to include git hub link in header
 * 13/04/2020 - Use async http method for player commands
 */
metadata {
  definition (name: "Squeezebox Player", namespace: "xap", author: "Ben Deitch") {
    capability "Actuator"
    capability "Audio Notification"
    capability "MusicPlayer"
    capability "Refresh"
    capability "Sensor"
    capability "Speech Synthesis"
    capability "Switch"

    attribute "playerMAC", "STRING"
    attribute "repeat", "ENUM", repeatModes
    attribute "serverHostAddress", "STRING"
    attribute "shuffle", "ENUM", shuffleModes
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
    command "repeat", [repeatModes]
    command "shuffle", [shuffleModes]
    command "speakArtistAlbums", ["STRING"]
    command "speakCurrentTrack"
    command "sync", ["STRING"]
    command "transferPlaylist", ["STRING"]
    command "unsync"
    command "unsyncAll"
  }
}

// define constants for repeat mode (order must match LMS modes)
def getRepeatModes() {
  ["off", "song", "playlist"]
}

// define constants for shuffle mode (order must match LMS modes)
def getShuffleModes() {
  ["off", "song", "album"]
}

def log(message) {
  if (getParent().debugLogging) {
    log.debug message
  }
}

def getAlarmsSwitchDni() {
  "${state.playerMAC}-alarms"
}

def configureAlarmsSwitch(createAlarmsSwitch) {
    
  def alarmsSwitchDni = getAlarmsSwitchDni()

  if (createAlarmsSwitch) {
    if (!getChildDevice(alarmsSwitchDni)) {
        def alarmsSwitch = addChildDevice("Squeezebox Player Alarms Switch", alarmsSwitchDni)
        alarmsSwitch.name = "${device.name} - All Alarms"
    }
  } else if (getChildDevice(alarmsSwitchDni)) {
    deleteChildDevice(alarmsSwitchDni)
  }
}

def configure(serverHostAddress, playerMAC, auth, createAlarmsSwitch) {
    
  state.serverHostAddress = serverHostAddress
  sendEvent(name: "serverHostAddress", value: state.serverHostAddress, displayed: false, isStateChange: true)

  state.playerMAC = playerMAC
  sendEvent(name: "playerMAC", value: state.playerMAC, displayed: false, isStateChange: true)
    
  state.auth = auth
    
  configureAlarmsSwitch(createAlarmsSwitch)
      
  log "Configured with [serviceHostAddress: ${serverHostAddress}, playerMAC: ${playerMAC}, auth: ${auth}, createAlarmsSwitch: ${createAlarmsSwitch}]"
}

def processJsonMessage(msg) {

  log "Squeezebox Player Received [${device.name}]: ${msg}"

  def command = msg.params[1][0]

  switch (command) {
    case "status":
      processStatus(msg)
      break
    case "time":
      processTime(msg)
      break
    case "playerpref":
      processPlayerPref(msg)
      break
  }
}

private processStatus(msg) {

  updatePower(msg.result?.get("power"))
  updateVolume(msg.result?.get("mixer volume"))
  updatePlayPause(msg.result?.get("mode"))
  updateRepeat(msg.result?.get("playlist repeat"))
  updateShuffle(msg.result?.get("playlist shuffle"))
  updateSyncGroup(msg.result?.get("sync_master"), msg.result?.get("sync_slaves"))
    
  def trackDetails = msg.result?.playlist_loop?.get(0)
  updateTrackUri(trackDetails?.url)
  String trackDescription
  if (trackDetails) {
    trackDescription = trackDetails.artist ? "${trackDetails.title} by ${trackDetails.artist}" : trackDetails.title
  }
  updateTrackDescription(trackDescription)
}

private processTime(msg) {
  state.trackTime = msg.result?.get("_time")
}

private processPlayerPref(msg) {

  if (msg.params[1][1] == "alarmsEnabled") {
    def alarmsSwitch = getChildDevice(getAlarmsSwitchDni())
    alarmsSwitch?.update(msg.result?.get("_p2") == "1")    
  }
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
  sendEvent(name: "repeat", value: repeatModes[Integer.valueOf(repeat)], displayed: true)
}

private updateShuffle(shuffle) {
  sendEvent(name: "shuffle", value: shuffleModes[Integer.valueOf(shuffle)], displayed: true)
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

def refresh() {
  executeCommand(["status", "-", 1, "tags:abclsu"]) 
  if (getChildDevice(getAlarmsSwitchDni())) {
    executeCommand(["playerpref", "alarmsEnabled", "?"]) 
  }
}

//--- Power
def on() {
  log "on()"
  executeCommand(["power", 1])
  refresh()
}

def off() {
  log "off()"
  executeCommand(["power", 0])
  refresh()  
}

//--- Volume
private setVolume(volume) {
  executeCommand(["mixer", "volume", volume])
}

def setLevel(level) {
  log "setLevel(${level})"
  setVolume(level)
  refresh()
}

def mute() {
  log "mute()"
  executeCommand(["mixer", "muting", 1])
  refresh() 
}

def unmute() {
  log "unmute()"
  executeCommand(["mixer", "muting", 0])
  refresh() 
}

//--- Playback
private executePlayAndRefresh(uri) {
  executeCommand(["playlist", "play", uri])
  refresh()  
}

def play() {
  log "play()"
  executeCommand(["play"])
  refresh()
}

def pause() {
  log "pause()"
  executeCommand(["pause"])
  refresh() 
}

def stop() {
  log "stop()"
  executeCommand(["stop"])
  refresh() 
}

def nextTrack() {
  log "nextTrack()"
  executeCommand(["playlist", "jump", "+1"])
  refresh()  
}

def previousTrack() {
  log "previousTrack()"
  executeCommand(["playlist", "jump", "-1"])
  refresh() 
}

def setTrack(trackToSet) {
  log "setTrack(\"${trackToSet}\")"
  executeCommand(["playlist", "stop", trackToSet])
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
  executeCommand(["time", "?"])
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
  executeCommand(["playlist", "preview", "url:${uri}", "silent:1"])
  captureAndChangeVolume(volume)    
  return 2 + duration as int
}

private restoreVolumeAndRefresh() {
  if (state.previousVolume) {
    setVolume(state.previousVolume)
    clearCapturedVolume()
  }
  refresh()
}

// this method is also used by the server when sending a playlist to this player
def resumeTempPlaylistAtTime(tempPlaylist, time=null) {
  executeCommand(["playlist", "resume", tempPlaylist, "wipePlaylist:1"])
  if (time) {
    executeCommand(["time", time])
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
  executeCommand(["playlist", "preview", "cmd:stop"])
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
  executeCommand(["favorites", "playlist", "play", "item_id:${intIndex - 1}"])
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
private getPlayerMacs(players) {
  players?.collect { parent.getChildDeviceMac(it) }
    .findAll { it != null }
}

def sync(slaves) {
  log "sync(\"${slaves}\")"
  def parent = getParent()
  def slaveMacs = getPlayerMacs(slaves.tokenize(","))
  if (slaveMacs) {
    slaveMacs.each { executeCommand(["sync", it]) }
    refresh()
  }
}

def unsync() {
  log "unsync()"
  executeCommand(["sync", "-"])
  refresh()
}

def unsyncAll() {
  log "unsyncAll()"
  def slaves = state.syncGroup?.findAll { it != device.name }
  def syncGroupMacs = getPlayerMacs(slaves)
  if (syncGroupMacs) {
    getParent().unsyncAll(syncGroupMacs)
  }
}

//--- Playlist
def transferPlaylist(destination) {
  log "transferPlaylist(\"${destination}\")"
  def tempPlaylist = "tempplaylist_from_" + state.playerMAC.replace(":", "")
  executeCommand(["playlist", "save", tempPlaylist])
  captureTime()
  if (getParent().transferPlaylist(destination, tempPlaylist, state.trackTime)) {
    executeCommand(["playlist", "clear"])
  }
  clearCapturedTime()
  refresh()
}

def clearPlaylist() {
  log "clearPlaylist()"
  executeCommand(["playlist", "clear"])
  refresh()
}
//--- Alarms
def disableAlarms() {
  log "disableAlarms()"
  executeCommand(["alarm", "disableall"])
  refresh()
}

def enableAlarms() {
  log "enableAlarms()"
  executeCommand(["alarm", "enableall"])
  refresh()
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
  executeCommand(["playlist", "loadtracks", "album.titlesearch=${search}"])
  refresh()
  runIn(3, checkAlbumSuccess)
}

def checkArtistSuccess() {
  checkSuccess("artist")
}

def playArtist(search) {
  log "playAlbum(\"${search}\")"
  executeCommand(["playlist", "loadtracks", "contributor.namesearch=${search}"])
  refresh()
  runIn(3, checkArtistSuccess)
}

def checkSongSuccess() {
  checkSuccess("song")
}

def playSong(search) {
  log "playSong(\"${search}\")"
  executeCommand(["playlist", "loadtracks", "track.titlesearch=${search}"])
  refresh()
  runIn(3, checkSongSuccess)
}

def speakArtistAlbums(artist) {
  log "speakArtistAlbums(\"${artist}\")"
  executeQuery(["search", 0, 2, "term:${artist}"], { resp -> getArtistForListAlbums(artist, resp) })
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
          break
    }
}

private listArtistAlbums(artist) {
   def artistName = artist.contributor
   def artistId = artist.contributor_id
   executeQuery(["albums", 0, -1, "artist_id:${artistId}"], { resp -> listAlbums(artistName, resp) })
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
  def mode = tryConvertToIndex(repeat, repeatModes)
  executeCommand(["playlist", "repeat", mode])
  refresh()
}

def shuffle(shuffle=null) {
  log "shuffle(\"${shuffle}\")"
  def mode = tryConvertToIndex(shuffle, shuffleModes)
  executeCommand(["playlist", "shuffle", mode])
  refresh()
}

/*******************
 * Utility Methods *
 *******************/

private tryConvertToIndex(value, lowerCaseValues) {
  def lowerCaseValue = String.valueOf(value).toLowerCase()
  def index = lowerCaseValues.indexOf(lowerCaseValue)
  index < 0 ? value : index
}

private buildPlayerRequest(params) {
    
  def request = [
    id: 1,
    method: "slim.request",
    params: [state.playerMAC, params]
  ]
    
  new groovy.json.JsonBuilder(request)
}

private buildParams(json) {

  def params = [
    uri: "http://${state.serverHostAddress}",
		path: "jsonrpc.js",
		requestContentType: 'application/json',
		contentType: 'application/json',
		body: json.toString()
	]
  
	if (state.auth) {
		params.headers = ["Authorization": "Basic ${state.auth}"]
	}
  
  params
}

private executeQuery(params, callback) {
  
  def json = buildPlayerRequest(params)
  
  log "Squeezebox Player Send Query [${device.name}]: ${json}"

  def postParams = buildParams(json)
     
  httpPost postParams, callback
}

private executeCommand(params) {

  log "Squeezebox Player Send Command: ${params}"

  def json = buildPlayerRequest(params)

  def postParams = buildParams(json)

  asynchttpPost "receiveCommandResponse", postParams
}

def receiveCommandResponse(response, data) {

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
