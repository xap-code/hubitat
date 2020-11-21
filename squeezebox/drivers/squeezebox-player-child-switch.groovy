/**
 *  Squeezebox Player Child Switch
 *
 *  Git Hub Raw Link - Use for Import into Hubitat
 *  https://raw.githubusercontent.com/xap-code/hubitat/master/squeezebox/drivers/squeezebox-player-alarms-switch.groovy
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
 * 15/10/2018 - Add child switch device for Enable/Disable All Alarms
 * 13/04/2020 - Include git link
 * 21/11/2020 - Change to generic child switch to allow use as separate player power switch as well
 */
metadata {
  definition (name: "Squeezebox Player Child Switch", namespace: "xap", author: "Ben Deitch") {
    capability "Actuator"
    capability "Sensor"
    capability "Switch"
  }
}

def on() {
  getParent().childSwitchedOn(device.deviceNetworkId)
}

def off() {
  getParent().childSwitchedOff(device.deviceNetworkId)
}

def update(isOn) {
  sendEvent(name: "switch", value: isOn ? "on" : "off", display: true)
}
