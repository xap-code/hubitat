Squeezebox Integration for Hubitat

Installation
============

Installation of Squeezebox Integration can now be easily performed via the Hubitat Package Manager

Debug Logging
=============
You can enable/disable debug logging in the Squeezebox Integration app preferences. If you have multiple players then they can generate quite a lot of log data so it is strongly recommended to deactivate the debug logging unless you need it to troubleshoot something for example.

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
