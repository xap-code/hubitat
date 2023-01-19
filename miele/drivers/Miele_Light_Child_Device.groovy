/**
 *  Miele Light Child Device
 *
 *  Copyright 2023 Ben Deitch
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
 * 19/01/2023 - v1.0.0 - Initial implementation
 */
metadata {
  definition (name: "Miele Light Child Device", namespace: "xap", author: "Ben Deitch") {
    capability "Light"
    capability "Sensor"
    capability "Switch"
  }
}

import groovy.transform.Field

// define constants
@Field static final int ENABLE = 1
@Field static final int DISABLE = 2


def uninstalled() {
  parent.disableDeletedChildDevice(device.deviceNetworkId)
}

def eventReceived(light) {
  switch (light) {
    case ENABLE:
      value = "on"
      break
    case DISABLE:
      value = "off"
      break
    default:
      logWarn("Unrecognised event value: ${light}")
      return
  }
  sendEvent name: "switch", value: value
}

def on() {
  logWarn("cannot switch light as device actions are not yet supported")
}

def off() {
  logWarn("cannot switch light as device actions are not yet supported")
}

// ** Logging **

private logWarn(message) {
  log.warn buildLogMessage(message)
}

private buildLogMessage(message) {
  "[${device.name}] ${message}"
}
