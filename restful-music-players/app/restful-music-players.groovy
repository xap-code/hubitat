/**
 *  RESTful Music Players
 *
 *  Copyright 2017 Ben Deitch
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
        if (devicePart.contains(playerPart)) {
          2
        } else if (playerPart.contains(devicePart)) {
          1
        } else {
          0
        }
      }).sum()
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
  
  log.debug("Player selected: ${player}, matches: ${matches}")
  return player
}

def playerCommand() {

  // extract variables from REST URI
  def playerName = params.playerName?.trim()
  def command = params.command?.trim()
  def value = params.value?.trim()
    
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
      default:
        log.debug "command not found: \"${command}\""
    }
  } else {
    log.debug "player not found: \"${playerName}\""
  }
    
  render contentType: "text/plain", data: "OK"
}