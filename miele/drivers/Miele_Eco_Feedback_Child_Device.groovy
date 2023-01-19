/**
 *  Miele Eco Feedback Child Device
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
  definition (name: "Miele Eco Feedback Child Device", namespace: "xap", author: "Ben Deitch") {
    capability "Sensor"
    capability "EnergyMeter"

    attribute "currentEnergyConsumption", "number"
    attribute "currentEnergyConsumptionText", "string"
    attribute "currentEnergyConsumptionDescription", "string"
    attribute "currentWaterConsumption", "number"
    attribute "currentWaterConsumptionText", "string"
    attribute "currentWaterConsumptionDescription", "string"
    attribute "energyForecast", "number"
    attribute "energyForecastText", "string"
    attribute "energyForecastDescription", "string"
    attribute "waterForecast", "number"
    attribute "waterForecastText", "string"
    attribute "waterForecastDescription", "string"
  }
  preferences {
    input name: "textEventsEnabled", title: "Enable text events", type: "bool", defaultValue: true
    input name: "descriptionEventsEnabled", title: "Enable description events", type: "bool", defaultValue: true
  }
}

import groovy.transform.Field
import java.text.DecimalFormat

// define constants
@Field static final String RESET_STRING = " "
@Field static final DecimalFormat DECIMAL_FORMATTER = new DecimalFormat("#.#")
@Field static final Map<Number, String> FORECAST_LEVELS = [
  0.8: "High",
  0.6: "Medium high",
  0.4: "Medium",
  0.2: "Low medium",
  0.0: "Low"
]

def updated() {
  resetDisabledAttributes()
}

private resetDisabledAttributes() {
  if (!textEventsEnabled) {
    parent.resetDeviceAttributesEndingWith(device, "Text")
  }
  if (!descriptionEventsEnabled) {
    parent.resetDeviceAttributesEndingWith(device, "Description")
  }
}

def eventReceived(ecoFeedback) {
  sendCurrentEnergyConsumptionEvents(ecoFeedback?.currentEnergyConsumption?.value ?: 0, ecoFeedback?.currentEnergyConsumption?.unit)
  sendCurrentWaterConsumptionEvents(ecoFeedback?.currentWaterConsumption?.value ?: 0, ecoFeedback?.currentWaterConsumption?.unit)
  sendEnergyForecastEvents(ecoFeedback?.energyForecast ?: 0)
  sendWaterForecastEvents(ecoFeedback?.waterForecast ?: 0)
}

private sendCurrentEnergyConsumptionEvents(value, unit) {
  sendEvent name: "energy", value: value, unit: unit
  sendCurrentConsumptionEvents value, unit, "energy", "currentEnergyConsumption"
}

private sendCurrentWaterConsumptionEvents(value, unit) {
  sendCurrentConsumptionEvents value, unit, "water", "currentWaterConsumption"
}

private sendCurrentConsumptionEvents(value, unit, valueName, eventName) {
  
  sendEvent name: eventName, value: value, unit: unit
  
  if (value > 0) {
    valueText = "${DECIMAL_FORMATTER.format(value)} ${unit}"
    valueDescription = "current ${valueName} consumption is ${valueText}"
  } else {
    valueText = RESET_STRING
    valueDescription = RESET_STRING
  }
  
  sendTextDescriptionEvents(eventName, valueText, valueDescription)
}

private sendEnergyForecastEvents(value) {
  sendForecastEvents value, "energy", "energyForecast"
}

private sendWaterForecastEvents(value) {
  sendForecastEvents value, "water", "waterForecast"
}

private sendForecastEvents(value, valueName, eventName) {

  sendEvent name: eventName, value: value
  
  if (value > 0) {
    valueText = "${(int)(value * 100)}%"
    valueDescription = "${getForecastLevel(value)} ${valueName} usage forecast"
  } else {
    valueText = RESET_STRING
    valueDescription = RESET_STRING
  }

  sendTextDescriptionEvents(eventName, valueText, valueDescription)
}

private sendTextDescriptionEvents(eventName, valueText, valueDescription) {
  if (textEventsEnabled) {
    sendEvent name: eventName + "Text", value: valueText
  }
  if (descriptionEventsEnabled) {
    sendEvent name: eventName + "Description", value: valueDescription
  }
}

private getForecastLevel(value) {
  FORECAST_LEVELS.find({ it.key < value }).value
}
