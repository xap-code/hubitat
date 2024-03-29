# Miele Integration for Hubitat

The Miele Integration app integrates Miele@home devices with your Hubitat hub. It can create a Hubitat device for each of your Miele@home enabled devices.

Currently this integration is read-only in that the device states are shown (e.g. status, programPhase, etc.) but device actions are not yet supported. It is intended that device actions will be supported in a future release.

Whilst the app has to interact with the Miele cloud-based 3rd Party API, it is designed to efficiently use a single event stream from Miele to receive device state updates rather than polling for changes.

## Installation

### Pre-Installation
In order to use the Miele Integration app with your hub you first need to request client credentials from Miele. You can do this by visiting https://developer.miele.com and clicking on "Get involved" at the top of the page. *(Automatic registration for client credentials is not currently available but you can request access via email - details are provided on the page.)*

### Hubitat Package Manager
Installation of Miele Integration is most easily performed via the Hubitat Package Manager (HPM). This is the preferred method for installation as it easily allows updates to be discovered and applied. Information on installing and using HPM can be found at https://community.hubitat.com/t/release-hubitat-package-manager-hubitatcommunity.

Once HPM is installed on your hub you can install Miele Integration by opening the "Hubitat Package Manager" app and selecting "Install" from the main menu, then "Search by Keywords", and then enter "Miele" as your search criteria. Select "Miele Integration" and then "Next" (twice) to install it.

### Authorization
Once Miele Integration is installed you first need to authorize it to access your Miele devices. Open the "Miele Integration" app and select "Authorization". Enter the Client ID and Client Secret provided by Miele when you registered for access (see **Pre-installation** above). The "Authorize" button will appear once values have been provided, select "Authorize" to open a popup window with the Miele login prompt. Once you have successfully logged in you should see a message confirming success. You can now select which of your Miele@home devices you want to integrate.

At any point after authorizing the Miele Integration you can return to the app and de-authorize or re-authorize via the Authorization app page. NB: If you de-authorize the app the devices it creates are not removed but they will stop working until you re-authorize the app. If you wish to remove all devices then you can remove the app and the devices will be removed with it.

## Configuration
If the app has been successfully authorized then you will see a prompt to select the Miele devices that you want to integrate with Hubitat and a drop down list of the available devices. Once you have selected the devices you want to integrate the app will display a list of the Hubitat devices that will be created when you select "Done". 

### Miele Event Stream
The first time the app is configured you will also see the "Miele Event Stream" device listed under "Create Hubitat devices". This device is required by the integration and is used to connect to the event stream provided by the Miele 3rd Party API in order to get device updates.

## Using Miele Integration devices
Each Miele@home device that is integrated with Hubitat is created as a "Miele Generic Device". This reflects the design approach used for the Miele 3rd Party API where a single generic device model is used for all device types. (More details on this design approach are provided at https://www.miele.com/developer/concept.html.)

### Miele Generic Device
Currently the Miele Generic Device is read-only and just advertises the "Sensor" device capability and some attributes which expose a subset of the Miele device state values. In future this will be extended with the ability to invoke device actions.

Attribute values are provided depending on if the associated device state information is available for a particular device received from the Miele 3rd Party API.
#### Generic Device Attributes:
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
- **`signalInfo`**  A boolean indicating if a notification is active for the device
- **`signalFailure`**  A boolean indicating if a failure is active for the device
- **`signalDoor`**  A boolean indicating if a door-open message is active for the device
- **`fullRemoteControlEnabled`**  A boolean indicating if the device can be controlled from remote
- **`mobileStartEnabled`**  A boolean indicating if the device supports the Mobile Start option
- **`smartGridEnabled`**  A boolean indicating if the device is set to Smart Grid mode

#### Generic Device Child Devices:
Most attributes are on the main device created for each Miele appliance. Some attributes are either repeated in the Miele data model (e.g. temperature) or occur on a small subset of the Miele device types. These attributes are not on the main device but instead are on child devices that are automatically created under the appliance's main device. Child devices are only created if the associated data is received for the appliance. The creation of child devices can also be enabled or disabled via the main device preferences. The following devices can be created:
- **Eco Feedback** *[Miele Eco Feedback Child Device]*
  - The Eco Feedback device is created if the Miele API returns ecoFeedback data for the device
    - It is named after its parent device
	  - e.g. `Dishwasher Eco Feedback`
- **Light** *[Miele Light Child Device]*
- **Ambient Light** *[Miele Light Child Device]*
  - The Light and Ambient Light devices are created if the Miele API returns light, ambientLight data for the device
    - They are named after their parent device
	  - e.g. `Dishwasher Light`, `Cooker Hood Ambient Light`
- **Temperature 1** *[Miele Temperature Child Device]*
- **Temperature 2** *[Miele Temperature Child Device]*
- **Temperature 3** *[Miele Temperature Child Device]*
- **Core Temperature** *[Miele Temperature Child Device]*
  - The temperature devices are created if the Miele API returns temperature, coreTemperature data for the device
    - They are named after their parent device
	  - e.g. `Oven Temperature 1`, `Oven Temperature 2`, `Oven Core Temperature`

#### Generic Device Child Device Types:
Child devices are created as one of the following device types:

- **Miele Eco Feedback Child Device**
  - (supports EnergyMonitor, Sensor capabilities)
  - Attributes:
    - **`energy`** The current energy consumption (this is a duplicate of `currentEnergyConsumption` in order to support the EnergyMonitor capability)
      - e.g. `1.5`, `2.0`
    - **`currentEnergyConsumption`** The current energy consumption
      - e.g. `1.5`, `2.0`
    - **`currentEnergyConsumptionText`** A string with the current energy consumption in the format "#.# kWh"
      - e.g. `"1.5 kWh"`, `"2 kWh"`
    - **`currentEnergyConsumptionDescription`** A string describing the current energy consumption
      - e.g. `"current energy consumption is 1.5 kWh"`
    - **`currentWaterConsumption`** The current water consumption
      - e.g. `2.0`, `0.8`
    - **`currentWaterConsumptionText`** A string with the current water consumption in the format "#.# l"
      - e.g. `"2 l"`, `"0.8 l"`
    - **`currentWaterConsumptionDescription`** A string describing the current water consumption
      - e.g. `"current water consumption is 2 l"`
    - **`energyForecast`** The relative energy forecast as a value between 0 and 1
      - e.g. `0.1`, `0.5 `
    - **`energyForecastText`** A string with the relative energy forecast as a percentage
      - e.g. `"10%"`, `"50%"`
    - **`energyForecastDescription`** A string describing the relative energy forecast
      - e.g. `"10% energy usage forecast"`
    - **`waterForecast`** The relative water forecast as a value between 0 and 1
      - e.g. `0.1`, `0.5 `
    - **`waterForecastText`** A string with the relative water forecast as a percentage
    - e.g. `"10%"`, `"50%"`
    - **`waterForecastDescription`** A string describing the relative water forecast
      - e.g. `"10% water usage forecast"`
  
- **Miele Light Child Device**
  - (supports Light, Sensor, Switch capabilities)
  - **NB: The on() and off() commands currently do nothing on this device type**
  - Attributes:
    - **`switch`** Indicates if the light is `on` or `off`
- **Miele Temperature Child Device**
  - (supports Sensor, TemperatureMeasurement capabilities)
  - Attributes:
    - **`temperature`** The temperature in units determined by the Miele device locale (°C or °F)
    - **`targetTemperature`** The target temperature in units determined by the Miele device locale (°C or °F)

#### Generic Device Preferences:
The following preferences can be set for each Miele appliance device:
- Enable text events
   - Raise events for attributes that present values as simple text (attributes ending `...Text`) (defaults to _true_)
- Enable description events
   - Raise events for attributes that present values as descriptive text (attributes ending `...Description`) (defaults to _true_)
- Enable Eco Feedback child device
   - Allows the Eco Feedback child device to be created if data is received for it. If created, the child device can be deleted by setting this preference to false. (defaults to _true_)
- Enable Light child device
   - Allows the Light child device to be created if data is received for it. If created, the child device can be deleted by setting this preference to false. (defaults to _true_)
- Enable Ambient Light child device
   - Allows the Ambient Light child device to be created if data is received for it. If created, the child device can be deleted by setting this preference to false. (defaults to _true_)
- Enable Temperature child devices
   - Allows up to three Temperature child devices to be created if data is received for them. If created, the child devices can be deleted by setting this preference to false. (defaults to _true_)
- Enable Core Temperature child device
   - Allows the Core Temperature child device to be created if data is received for it. If created, the child device can be deleted by setting this preference to false. (defaults to _true_)
- Enable debug logging
   - Output debug logs for the device (defaults to _false_)

  
## Future Releases
Further releases are planned (no dates specified as this is implemented in my spare time). Functionality to be added:
- Further device states to be included.
- Device command to allow the full device JSON to be read and used in other apps. e.g. webCoRE
- Device commands to allow device actions available in the Miele 3rd Party API to be invoked.
- Custom Hubitat device for Miele robot vacuums to expose state and actions specific to those devices only. e.g. battery, rooms

## Contributions
Bug reports are welcome! Feature requests also.

Pull requests will be considered but code consistency is important as well as maintaining the generic nature of the implementation aligned with the Miele design approach.

Hope this is useful!
