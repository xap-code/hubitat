/**
 *  Miele Temperature Child Device
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
  definition (name: "Miele Temperature Child Device", namespace: "xap", author: "Ben Deitch") {
    capability "Sensor"
    capability "TemperatureMeasurement"

    attribute "targetTemperature", "number"
  }
}

import groovy.transform.Field

// define constants
@Field static final String MIELE_UNIT_CELSIUS = "Celsius"
@Field static final String MIELE_UNIT_FAHRENHEIT = "Fahrenheit"
@Field static final String HUBITAT_UNIT_CELSIUS = "°C"
@Field static final String HUBITAT_UNIT_FAHRENHEIT = "°F"

def eventReceived(temperature, targetTemperature) {
  if (temperature) {
    sendEvent name: "temperature", value: temperature.value_localized, unit: getUnit(temperature.unit)
  }
  if (targetTemperature) {
    sendEvent name: "targetTemperature", value: targetTemperature.value_localized, unit: getUnit(targetTemperature.unit)
  }
}

private getUnit(mieleUnit) {
  switch (mieleUnit) {
    case MIELE_UNIT_CELSIUS:
      return HUBITAT_UNIT_CELSIUS
    case MIELE_UNIT_FAHRENHEIT:
      return HUBITAT_UNIT_FAHRENHEIT
    default:
      logWarn("unrecognised temperature unit: ${mieleUnit}")
  }
}

// ** Logging **

private logWarn(message) {
  log.warn buildLogMessage(message)
}

private buildLogMessage(message) {
  "[${device.name}] ${message}"
}
