/**
 *  RESTful Music Players
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
definition(
  name: "RESTful Music Players",
  namespace: "xap",
  author: "Ben Deitch",
  description: "Exposes a REST API that can be used to remotely control the main functions of music players.",
  category: "My Apps",
  iconUrl: "http://cdn.device-icons.smartthings.com/Entertainment/entertainment2-icn.png",
  iconX2Url: "http://cdn.device-icons.smartthings.com/Entertainment/entertainment2-icn@2x.png",
  iconX3Url: "http://cdn.device-icons.smartthings.com/Entertainment/entertainment2-icn@3x.png")

preferences {
	page(name: "Select Players", install: false, uninstall: true, nextPage: "viewURL") {
		section {
			input(name: "players", type: "capability.musicPlayer", title: "Select players to control", multiple: true)
		}
	}
	page(name: "viewURL", install: false, uninstall: true)
}

def viewURL() {
	dynamicPage(name: "viewURL", title: "Example Endpoint URL", install:true, nextPage: null) {
		section() {
			paragraph "Copy the URL below as an example for your IFTTT Webhook Applet (you will need to adjust the '/players' part)"
			href url:"${generateURL("link").join()}", style:"embedded", required:false, title:"URL", description:"Tap to view, then click \"Done\""
		}
	}
}

def oauthError() {[error: "OAuth token is invalid or access has been revoked"]}

def installed() {
	log.debug "Installed with settings: ${settings}"
	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"
	unsubscribe()
	unschedule()
	initialize()
}

def initialize() {
  // nothing yet
}

def generateURL(path) {

	if (!state.accessToken) {
		try {
			createAccessToken()
			log.debug "Creating new Access Token: $state.accessToken"
		} catch (ex) {
			log.error "Did you forget to enable OAuth for the App?"
			log.error ex
		}
	}

	//Hubitat cloud endpoint
	["${fullApiServerUrl(path)}?access_token=${state.accessToken}"]
}
   
def link() {
	if (!params.access_token) return ["You are not authorized to view OAuth access token"]
	render contentType: "text/html", data: """<!DOCTYPE html>
<html>
<body style="margin: 0;">
<div style="padding:10px">${title ?: location.name} Example URL:</div>
<textarea rows="9" cols="30" style="font-size:14px; width: 100%">${generateURL("players").join()}</textarea>
<div style="padding:10px">Copy the URL above, then go back and tap Done.</div></body>
</html>"""
}

mappings {
  // simple GET action to list players (typically used for debugging service)
  path("/players") {
    action: [
      GET: "listPlayers"
    ]
  }
  // path to capture player command without a parameter
  path("/player/:playerName/:command") {
    action: [
      POST: "playerCommand"
    ]
  }
  // path to capture player command with a parameter
  path("/player/:playerName/:command/:value") {
    action: [
      POST: "playerCommand"
    ]
  }
  // path to capture multiple player names to synchronize together
  path("/synchronise/:playerNames") {
    action: [
      POST: "synchronisePlayers"
    ]
  }
  // path to capture multiple player names to transfer playlist between
  path("/transfer/:playerNames") {
    action: [
      POST: "transferPlaylist"
    ]
  }
  // path to capture search and player name to play album
  path("/album/:searchAndPlayer") {
    action: [
      POST: "searchToPlayAlbum"
    ]
  }
  // path to capture search and player name to play artist
  path("/artist/:searchAndPlayer") {
    action: [
      POST: "searchToPlayArtist"
    ]
  }
  // path to capture search and player name to play album
  path("/song/:searchAndPlayer") {
    action: [
      POST: "searchToPlaySong"
    ]
  }
  // path to capture search and player name to list an artist's albums
  path("/artistalbums/:searchAndPlayer") {
    action: [
      POST: "searchToSpeakArtistAlbums"
    ]
  }
  // path used to get oauth token during setup
  path("/link") {
    action: [
      GET: "link"
    ]
  }
}

// return a list of players' display names
def listPlayers() {
  def resp = []
  players.each {
    resp << [name: it.displayName]
  }
  return resp
}

private getParam(param) {
  param?.replace("%20", " ")?.trim()
}

// Simple fuzzy matching algorithm (far from perfect but seems to work quite well with Google Home voice recog.):
// returns a score indicating the quality of the match between device name and player name passed to REST service
// performs case insensitive matching across multiple words split by a single space
// if the submitted name part is fully contained within the device name part then a higher score is gained
// if the device name part is contained within the submitted name part then a score is still gained but not as high
def fuzzyMatch(deviceName, playerName) {

  // upper-case and split into parts
  def deviceParts = deviceName.toUpperCase().split(" ")
  def playerParts = playerName.toUpperCase().split(" ")

  // iterate over the parts to sum the scores
  int matches = deviceParts.collect({
    devicePart -> playerParts.collect({
      playerPart ->
        if (devicePart.trim().equalsIgnoreCase(playerPart.trim())) {
          3
        } else if (devicePart.contains(playerPart.trim())) {
          2
        } else if (playerPart.contains(devicePart.trim())) {
          1
        } else {
          0
        }
      }).max()
    }).sum()

  return matches
}

// uses the fuzzy matching defined above to find a player with a matching name
// NB: Only returns a player if that player gains a higher score than any other player,
// i.e. If two players both score the same highest value then it is not possible to choose between them so no player is returned
def findPlayer(playerName) {

  // build a list of players with their match scores
  def matches = players.collect({[ score: (fuzzyMatch(it.displayName, playerName)), player: it ]})

  // iterate over the matches to find a single player with the highest score
  def highScore = -1
  def player
  for (match in matches) {
    if (match.score > highScore) {
      player = match.player
      highScore = match.score
    } else if (match.score == highScore) {
      player = null
    }
  }
  
  log.debug("Player selected: ${player}, input:\"${playerName}\",  matches: ${matches}")
  return player
}

def playerCommand() {

  // extract variables from REST URI
  def playerName = getParam(params.playerName)
  def command = getParam(params.command)
  def value = getParam(params.value)
    
  log.debug "Command received: \"${command}\", playerName: \"${playerName}\", value: ${value}"
  
  // attempt to match a single player based on the provided player name
  def player = findPlayer(playerName)

  // if a player was found then invoke the relevant method
  if (player) {
    switch (command) {
      case "pause":
        player.pause()
        break
      case "play":
        player.play()
        break
      case "stop":
        player.stop()
        player.shuffle("off")
        player.repeat("off")
        player.clearPlaylist()
        break
      case "mute":
        player.mute()
        break
      case "unmute":
        player.unmute()
        break
      case "next":
        player.nextTrack()
        break
      case "previous":
        player.previousTrack()
        break
      case "volume":
        player.setLevel(value)
        break
      case "quieter":
        player.setLevel("-" + value)
        break
      case "louder":
        player.setLevel("+" + value)
        break
      case "favorite":
        player.playFavorite(value)
        break
      case "unsynchronise-all":
        player.unsyncAll()
        break
      case "reset":
        
      default:
        log.debug "command not found: \"${command}\""
    }
  } else {
    log.debug "player not found: \"${playerName}\""
  }
    
  render contentType: "text/plain", data: "OK"
}

def synchronisePlayers() {
  
  // extract variables from REST URI
  def playerNames = getParam(params.playerNames)
    
  def splitPlayerNames = playerNames.split("and")

  def players = splitPlayerNames
    .collect { findPlayer(it.trim()) }
    .findAll { it != null }
    
  if (players.size() > 1) {
      def master = players.head()
      def slaves = players.tail().collect({ it.name }).join(",")
      
      log.debug "Synchronising ${master.name} > ${slaves}"
      
      master.sync(slaves)
  }
   
  render contentType: "text/plain", data: "OK"
}

def transferPlaylist() {
  // extract variables from REST URI
  def playerNames = getParam(params.playerNames)
    
  def splitPlayerNames = playerNames.split("to")

  def players = splitPlayerNames
    .collect { findPlayer(it.trim()) }
    .findAll { it != null }
    
  if (players.size() == 2) {
      
      def source = players[0]
      def destination = players[1]
      
      log.debug "Transferring playlist from ${source} to ${destination}"
      
      source?.transferPlaylist(destination?.deviceNetworkId)
  }
   
  render contentType: "text/plain", data: "OK"
}

def delayedSpeakArtistAlbums(data) {
  def player = players.find { it.deviceNetworkId == data.playerId }
  player?.speakArtistAlbums(data.search)
}

private searchTo(playerMethod) {
    def searchAndPlayer = getParam(params.searchAndPlayer)
    log.debug searchAndPlayer
    def splitIndex = searchAndPlayer.lastIndexOf(" on ")
    if (splitIndex < 0) {
      return
    }
    
    def search = searchAndPlayer.substring(0, splitIndex)
    def playerName = searchAndPlayer.substring(splitIndex + " on ".length())
    
    def player = findPlayer(playerName)
    
    switch (playerMethod) {
      case "playAlbum":
        if (player) {
          player.shuffle("off")
          player.playAlbum(search)
        }
        break
      case "playArtist":
        if (player) {
          player.playArtist(search)
          player.shuffle("song")
        }
        break
      case "playSong":
        if (player) {
          player.shuffle("off")
          player.playSong(search)
        }
        break
      case "speakArtistAlbums":
        def data = [playerId: player.deviceNetworkId, search: search]
        runIn(2, delayedSpeakArtistAlbums, [data: data])
    }
}

def searchToPlayAlbum() {
  searchTo("playAlbum")    
}

def searchToPlayArtist() {
  searchTo("playArtist")    
}

def searchToPlaySong() {
  searchTo("playSong")    
}

def searchToSpeakArtistAlbums() {
  searchTo("speakArtistAlbums")
}
