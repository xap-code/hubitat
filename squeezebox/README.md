Squeezebox Integration for Hubitat

Installation
============

A: Create App

1. View app/squeezebox-connect.groovy in raw mode (just the source code text)
2. In Hubitat click on "Apps Code" on the left and then "New App" on the top right.
3. Copy and paste the app source code into the blank section on the Hubitat page and click Save.

B: Create Drivers

(Steps 4-6 are optional, they are only required if you want to enable/disable player alarms via a player child switch or use the extra player power switch)
1. View driver/squeezebox-player.groovy in raw mode
2. In Hubitat click on "Drivers Code" on the left and then "New Driver" on the top right.
3. Copy and paste the device source code into the blank section on the Hubitat page and click Save.
4. View driver/squeezebox-player-child-switch.groovy in raw mode
5. In Hubitat click on "Drivers Code" on the left and then "New Driver" on the top right.
6. Copy and paste the device source code into the blank section on the Hubitat page and click Save.

C: Install App

1. In Hubitat click on "Apps" on the left and then "Load New App" on the top right.
2. Scroll to the bottom of the list that pops up, under "User Apps" find Squeezebox Connect and click on it.
3. Enter the IP Address of the machine running your Squeezebox Server (LMS) software (this needs to have a fixed IP address).
4. Enter the port number for LMS (usually 9000 or 9002)
5. Click Next
6. Choose the refresh interval (the number of seconds) between each call the app makes to LMS to get the current status of the players. 
7. If you are using password protection on LMS then select "Password Protection" and enter the username and password.
8. Click Next
9. The app will query the server for available players, select the players you want to integrate into Hubitat.
10. (optional) Add a prefix and/or suffix that the app will add to each player's device name when creating them (e.g. suffix: "&nbsp;Player")
11. Click Done

NB: The app only gets detailed information for players that are switched on but it does make a call to LMS after each interval. If you are displaying current player status based on the information in Hubitat (e.g. on a dashboard) or if you want to trigger rules from changes in player state then you should choose a low number of seconds for the refresh interval (2 is recommended). However, if you are just using Hubitat to control the players and don't need quick updates from external changes then a higher number is fine and will generate less network traffic. The state of the player device is immediately updated when you make a change via Hubitat regardless of this setting.

Debug Logging
=============
You can enable/disable debug logging in the Squeezebox Connect app preferences. If you have multiple players then they can generate quite a lot of log data so it is strongly recommended to deactivate the debug logging unless you need it to troubleshoot something for example.

Extra Player Commands
=====================
This integration exposes some extra Player device commands specific to Squeezebox.

sync("{players}")
- This command takes a string which is a comma separated list of either player MACs (the device network ID) or player names and will then synchronise them all as a group with the master being the player the command is called on.

unsync()
- Removes the player from a sync group if it is a member, remaining members remain in the sync group.

unsyncAll()
- If the player is in a sync group, calls unsync() on all members of the group, removing the sync group.

transferPlaylist("{destination player}")
- Transfers the current playlist from the player the command is called on to the destination player (specified by either name or MAC).

clearPlaylist()
- Clears the current playlist from the player

playAlbum(search)
- Searches for albums based on the provided search text and plays them on the player

playArtist(search)
- Searches for artists based on the provided search text and plays all their tracks on the player. This works well used in conjuction with shuffle("song")

playSong(search)
- Searches for songs based on the provided search text and plays them on the player

repeat(repeat)
- Sets the player repeat mode if parameter is specified, otherwise toggles the repeat mode if no parameter is specified.
	valid values are: "off", "song", "playlist"
	
shuffle(shuffle)
- Sets the player shuffle mode if parameter is specified, otherwise toggles the shuffle mode if no parameter is specified.
	valid values are: "off", "song", "album"
	
speakArtistAlbums(artist)
- Searches for an artist based on the provided search text and lists all their albums in the library via text to speech on the player.
