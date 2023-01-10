# Miele Integration for Hubitat

The Miele Integration app integrates Miele@home devices with your Hubitat hub. It can create a Hubitat device for each of your Miele@home enabled devices.

Currently this integration is read-only in that the device states are shown (e.g. status, programPhase, etc.) but device actions are not yet supported. It is intended that device actions will be supported in a future release.

Whilst the app has to interact with the Miele cloud-based 3rd Party API, it is designed to efficiently use a single event stream from Miele to receive device state updates rather than polling for changes.

## Installation

### Pre-Installation
In order to use the Miele Integration app with your hub you first need to request client
 credentials from Miele. You can do this by visiting https://developer.miele.com and 
clicking on "Get involved" at the top of the page. *(Automatic registration for client 
credentials is not currently available but you can request access via email - details 
are provided on the page.)*

### Hubitat Package Manager
*(The Miele Integration app is not yet available - waiting on approval from Miele)*
Installation of Miele Integration is most easily performed via the Hubitat Package Manager (HPM). This is the preferred method for installation as it easily allows updates to be discovered and applied. Information on installing and using HPM can be found at https://community.hubitat.com/t/release-hubitat-package-manager-hubitatcommunity.

Once HPM is installed on your hub you can install Miele Integration by opening the "Hubitat Package Manager" app and selecting "Install" from the main menu, then "Search by Keywords", and then enter "Miele" as your search criteria. Select "Miele Integration" and then "Next" (twice) to install it.

### Authorization
Once Miele Integration is installed you first need to authorize it to access your Miele devices. Open the "Miele Integration" app and select "Authorization". Enter the Client ID and Client Secret provided by Miele when you registered for access (see **Pre-installation** above). The "Authorize" button will appear once values have been provided, select "Authorize" to open a popup window with the Miele login prompt. Once you have successfully logged in you should see a message confirming success. You can now select which of your Miele@home devices you want to integrate.

At any point after authorizing the Miele Integration you can return to the app and de-authorize or re-authorize via the Authorization app page. NB: If you de-authorize the app the devices it creates are not removed but they will stop working until you re-authorize the app. If you wish to remove all devices then you can remove the app and the devices will be removed with it.

## Configuration
If the app has been successfully authorized then you will see a prompt to select the Miele devices that you want to integrate with Hubitat and a drop down list of the available devices. Once you have selected the devices you want to integrate the app will display a list of the Hubitat devices that will be created when you select "Done". 

### Miele Event Stream
The first time the app is configured you will also see the "Miele Event Stream" device listed under "Create Hubitat devices". This device is required by the integration and is used to connect to the event stream provided by the Miele 3rd Party API in order to get device updates. Should you accidentally remove this device you can come back to the app configuration and select "Done" again which will recreate the event stream device.

## Using Miele Integration devices
Each Miele@home device that is integrated with Hubitat is created as a "Miele Generic Device". This reflects the design approach used for the Miele 3rd Party API where a single generic device model is used for all device types. (More details on this design approach are provided at https://www.miele.com/developer/concept.html.)

### Miele Generic Device
In v1 of Miele Integration the Miele Generic Device is fairly simple. It only advertises the "Sensor" device capability and some attributes which expose a subset of the Miele device state values. In future this will be extended with more state information and eventually the ability to invoke device actions.

Attribute values are provided depending on if the associated device state information is available for a particular device received from the Miele 3rd Party API.
#### Attributes:
- **`status`** Indicates the current device status
  - e.g. `"Off"`, `"In Use"`
- **`program`** The currently selected program
  - e.g. `"Eco"`, `"Auto"`
  - (set to a blank string " " if no program is selected) 
- **`programPhase`** The current phase of a program in progress
  - e.g. `"Pre-wash"`, `"Main wash"`
  - (set to a blank string " " if no program is in progress or value not provided by Miele) 
- **`programDescription`** A string describing the program and current phase
  - e.g. `"Auto (Cooling down)"`
  - (set to a blank string " " if no program is selected) 
- **`startTime`** A Date value indicating when the selected program is set to start
  - e.g. `Tue Jan 10 00:30:00 GMT 2023`
  - (set to zero if no start time is set, i.e. Thu Jan 01 01:00:00 GMT 1970) 
- **`startTimeText`** A string with the start time in the format HH:mm
  - e.g. `"00:30"`, `"13:40"`
  - (set to a blank string " " if no start time is set) 
- **`startTimeDescription`** A string describing the start time
  - e.g. `"Start at 00:30"`, `"Start at 13:40"`
  - (set to a blank string " " if no start time is set) 
- **`finishTime`** A Date value indicating when the selected program is expected to finish
  - e.g. `Tue Jan 10 04:25:00 GMT 2023`
  - (set to zero if no program is selected, i.e. Thu Jan 01 01:00:00 GMT 1970) 
- **`finishTimeText`** A string with the finish time in the format HH:mm
  - e.g. `"04:25"`, `"15:39"`
  - (set to a blank string " " if no program is selected) 
- **`finishTimeDescription`** A string describing the finish time
  - e.g. `"Finish at 04:25"`, `"Finish at 15:39"`
  - (set to a blank string " " if no program is selected)
- **`elapsedTime`** The number of minutes elapsed for the program currently in progress
  - e.g. `121`, `53`
  - (set to zero if no program is in progress)
- **`elapsedTimeText`** A string with the elapsed time in the format HH:mm
  - e.g. `"02:01"`, `"00:53"`
  - (set to a blank string " " if no program is in progress)
- **`elapsedTimeDescription`**  A string describing the elapsed time
  - e.g. `"2 hours 1 minute elapsed"`, `"53 minutes elapsed"`
  - (set to a blank string " " if no program is in progress)
- **`remainingTime`** The number of minutes remaining for the selected program
  - e.g. `180`, `26`
  - (set to zero if no program is selected)
- **`remainingTimeText`** A string with the remaining time in the format HH:mm
  - e.g. `"03:00"`, `"00:26"`
  - (set to a blank string " " if no program is selected)
- **`remainingTimeDescription`**  A string describing the remaining time
  - e.g. `"3 hours 0 minutes remaining"`, `"26 minutes remaining"`
  - (set to a blank string " " if no program is selected)
  
## Future Releases
Further releases are planned (no dates specified as this is implemented in my spare time). Functionality to be added:
- Further device states to be included, as well as Hubitat child devices to allow easier use of properties related to lights and temperatures.
- Device command to allow the full device JSON to be read and used in other apps. e.g. webCoRE
- Device commands to allow device actions available in the Miele 3rd Party API to be invoked.
- Custom Hubitat device for Miele robot vacuums to expose state and actions specific to those devices only. e.g. battery, rooms

## Contributions
Bug reports are welcome! Feature requests also.

Pull requests will be considered but code consistency is important as well as maintaining the generic nature of the implementation aligned with the Miele design approach.

Hope this is useful!