Squeezebox Integration for Hubitat

Installation
============

A: Create App
1. View app/squeezebox-connect.groovy in raw mode (just the source code text)
2. In Hubitat click on "Apps Code" on the left and then "New App" on the top right.
3. Copy and paste the app source code into the blank section on the Hubitat page and click Save.

B: Create Driver
1. View driver/squeezebox-player.groovy in raw mode
2. In Hubitat click on "Drivers Code" on the left and then "New Driver" on the top right.
3. Copy and paste the device source code into the blank section on the Hubitat page and click Save.

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

Extra Commands
==============
This integration exposes some extra Player device commands specific to Squeezebox.

sync("{players}")
- This command takes a string which is a comma separated list of either player MACs (the device network ID) or player names and will then synchronise them all as a group with the master being the player the command is called on.

unsync()
- Removes the player from a sync group if it is a member, remaining members remain in the sync group.

unsyncAll()
- If the player is in a sync group, calls unsync() on all members of the group, removing the sync group.

Example: create a virtual switch to allow you to synchronise and unsynchronise your players
-------------------------------------------------------------------------------------------

A: Create the virtual switch

1. Select "Devices"
2. Click "Add Virtual Device"
3. Enter a Device Name for the new device e.g. "Synchronise Downstairs Players"
4. Enter a Device Network Id for the new device e.g. "switch-sync-downstairs-players"
5. Select "Virtual Switch" as the Type
6. Click "Save"

B: Create Custom Commands to call sync(...) and unsync() methods

1. Open Rule Machine app (you may need to install it via "Load New App" if you haven't already)
2. Click "Create Custom Commands"
3. Select "Music player" as capability for test device
4. Select one of you Squeezebox player devices (this is only used to get available commands and test the function)
5. Click "New custom command..."
6. Select "sync" as custom command
7. Click "Parameters"
8. Select "string" as parameter type
9. Enter the name of one or more of your other Squeezebox player devices (comma separated)
NB: these will be the slave devices (i.e. don't include the name of the player that will be the sync master)
10. Click "Done"
11. Click "Save command now" (NB: The command doesn't seem to get saved at all if you don't do this)
12. Click "Done"
13. Click "Done" again
14. Click "New custom command..."
15. Select "unsyncAll" as custom command
16. Click "Save command now"
17. Click "Done"
18. Click "Done" again
19. Click "Done" again

You now have custom commands that will synchronise and unsynchronise players that can be used in Rule Machine Rules.

C: Create Rule to synchronise and unsynchronise players based on the virtual switch

1. Open Rule Machine app
2. Click "Create New Rule..."
3. Click "Define a Triggered Rule..."
4. Enter a name for the Triggered Rule e.g. "Synchronise Downstairs Players"
5. Click "Select Trigger Events"
6. Select "Switch" for capability
7. Select the virtual switch created in stage A
8. Select "on" for "Switch turns" (this is usually already selected by default)
9. Select "Switch" for capability for Event Trigger #2
10. Select the virtual switch again
11. Select "off" for "Switch turns"
12. Click "Done"
13. Click "Select Conditions"
14. Select "Switch" for capability
15. Select the virtual switch again
16. Select "on" for "Switch state" (this is usually already selected by default)
17. Click "Done"
18. Click "Select Actions for True"
19. Click "Run custom commands"
20. Select the sync(...) command you created in stage B
21. Select the device that will be the sync master for "On these devices"
22. Click "Done"
23. Click "Done" again
24. Click "Select Actions for False"
25. Click "Run custom commands"
26. Select the unsyncAll() command you created in stage B
27. Select the sync master device again
28. Click "Done"
29. Click "Done" again
30. Click "Done" again

D: Test your synchronise switch

1. Select one of your player devices from the "Devices" page
2. In another tab, select your virtual switch device
3. Switch the virtual switch on/off and check that the "syncGroup" state shown on the player device page changes.

Now you have a switch that activates and deactivates a sync group, you could add this to your dashboard :-)
