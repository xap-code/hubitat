/*
 * GivEnergy Forecast Charge
 *
 *  Copyright 2022 Ben Deitch
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
 * 06/03/2022 - v0.1 - Initial implementation using forecast from Solcast
 * 11/03/2022 - v0.2 - Add support for forecast from Forecast.Solar
 */
definition(
  name: "GivEnergy Forecast Charge",
  namespace: "xap",
  author: "Ben Deitch",
  description: "Uses solar forecast API to predict solar generation and adjust GivEnergy Battery target charge via local GivTCP service.",
  category: "", iconUrl: "", iconX2Url: "", iconX3Url: "")

preferences {
  section("<h3>GivEnergy Battery</h3>") {
    input name: "givTcpAddress", type: "string", required: true, title: "GivTCP Service IP Address"
    input name: "givTcpPort", type: "number", required: true, title: "GivTCP Service Port"
  }
  section("<h3>Solar Forecast</h3>") {
    input name: "forecastProvider", type: "enum", options: ["Solcast", "Forecast.Solar"], required: true, title: "Forecast Provider", submitOnChange: true
    if (forecastProvider == 'Solcast') { 
      input name: "solcastResourceId", type: "string", required: true, title: "Solcast Resource ID"
      input name: "solcastApiKey", type: "string", required: true, title: "Solcast API Key"
    }
    if (forecastProvider == 'Forecast.Solar') {
      input name: "latitude", type: "string", required: true, title: "Latitude"
      input name: "longtitude", type: "string", required: true, title: "Longtitude"
      input name: "declination", type: "string", required: true, title: "Declination (angle of roof)"
      input name: "azimuth", type: "string", required: true, title: "Azimuth (direction of roof face)"
      input name: "power", type: "string", required: true, title: "System Power (kW)"
    }
  }
  section("<h3>Charge Thresholds</h3>") {
    input name: "setTime", type: "time", required: true, title: "Daily time to get forecast and set charge target"
    paragraph("Define Charge Thresholds")
    (1..thresholdCount).each { 
      input name: getForecastEnergyName(it), type: "number", required: true, title: "Daily Forecast (Wh)", width: 20
      input name: getTargetChargeName(it), type: "number", range: "1..100", required: true, title: "Target SoC%", width: 20
      paragraph("")
    }
    input name: "addThreshold", type: "button", title: "Add", width: 2
    input name: "removeThreshold", type: "button", title: "Remove", width: 1
    input name: "defaultTargetCharge", type: "number", range: "1..100", required: true, title: "Default Target SoC% (used if no thresholds met)"
  }
  section("<h3>Other Settings</h3>") {
    input name: "debugLogging", type: "bool", title: "Enable/disable debug logging", defaultValue: false, required: false
  }
}

import java.time.Duration 

def getThresholdCount() {
  state.thresholdCount ?: 1
}

def appButtonHandler(btn) {

  def count = thresholdCount

  state.maxThresholdCount = Math.max(state.maxThresholdCount?:0, count)
  
  switch (btn) {
    case "addThreshold":
      count ++
      state.thresholdCount = count
      break;
    case "removeThreshold":
      if (count > 1) {
        state.thresholdCount = count - 1
      }
      break;
  }
}

def pruneThresholdSettings() {
  if (state.maxThresholdCount) {
    for (int remove = thresholdCount + 1; remove <= state.maxThresholdCount; remove++) {
      app.removeSetting getForecastEnergyName(remove)
      app.removeSetting getTargetChargeName(remove)
    }
    state.remove("maxThresholdCount")
  }
}
                     
def getForecastEnergyName(index) {
  "forecastEnergy_${index}"
}

def getTargetChargeName(index) {
  "targetCharge_${index}"
}

def getForecastEnergy(index) {
  app.getSetting(getForecastEnergyName(index))
}

def getTargetCharge(index) {
  app.getSetting(getTargetChargeName(index))
}

def log(message) {
  if (debugLogging) {
    log.debug message
  }
}

def installed() {
  initialize()
}

def uninstalled() {
}

def updated() {
  initialize()
}

def initialize() {
  unschedule()
  scheduleSetChargeTarget()
  pruneThresholdSettings()
}

def scheduleSetChargeTarget() {
  def time = toDateTime(setTime)
  schedule("0 ${time.minutes} ${time.hours} ? * *", updateChargeTarget)
}

def updateChargeTarget() {
  log "Update charge target"
  if (forecastProvider == 'Solcast') {
    querySolcast()
  }
  if (forecastProvider == 'Forecast.Solar') {
    queryForecastSolar()
  }
}

def querySolcast() {

  def uri = "https://api.solcast.com.au/rooftop_sites/${solcastResourceId}/forecasts?format=json&hours=24"
  
  log "GET forecast from Solcast: ${uri}"

  asynchttpGet "handleSolcast", [
    uri: uri,
    headers: ["Authorization": "Bearer ${solcastApiKey}"]
  ]
}

def handleSolcast(response, data) {
  
  def json = response.json
  
  log json
  
  if (json?.forecasts) {
    def forecastEnergy = calculateDailyForecast(json.forecasts)
    adjustToForecast(forecastEnergy)
  } else {
    log.warn "Unable to set charge target as query for forecast returned no data"
  }
}

def queryForecastSolar() {

  def uri = "https://api.forecast.solar/estimate/${latitude}/${longtitude}/${declination}/${azimuth}/${power}"
  
  log "GET forecast from Forecast.Solar: ${uri}"

  asynchttpGet "handleForecastSolar", [ uri: uri ]
}

def handleForecastSolar(response, data) {
  
  def json = response.json
  
  log json
  
  if (json?.result) {
    def forecastEnergy = chooseDailyForecast(json.result.watt_hours_day)
    adjustToForecast(forecastEnergy)
  } else {
    log.warn "Unable to set charge target as query for forecast returned no data"
  }
}

def adjustToForecast(forecastEnergy) {
  log.info "Today's solar PV generation forecast is ${forecastEnergy}Wh"
  def chargeTarget = determineChargeTarget(forecastEnergy)
  if (chargeTarget >= 0) {
    log.info "Set charge target to ${chargeTarget}%"
    setChargeTarget(chargeTarget)
  }
}

def getForecastDate(forecast) {
  toDateTime(forecast.period_end.replace(".0000000Z", "Z")).date
}

def getForecastPeriodHours(forecast) {
  Duration.parse(forecast.period).toMinutes() / 60.0
}

def calculateDailyForecast(forecasts) {

  def today = new Date().date

  forecasts
  .findAll { getForecastDate(it) == today }
  .collect { getForecastPeriodHours(it) * it.pv_estimate }
  .sum { it } * 1000
}

def chooseDailyForecast(days) {
  
  def today = new Date().format("yyyy-MM-dd")
  
  days[today]
}

def determineChargeTarget(forecastEnergy) {
  
  def highestEnergy = -1;
  def targetCharge = defaultTargetCharge;
  for (int threshold = 1; threshold <= thresholdCount; threshold++) {
    def thresholdEnergy = getForecastEnergy(threshold)
    if (forecastEnergy >= thresholdEnergy && thresholdEnergy > highestEnergy) {
      highestEnergy = thresholdEnergy
      targetCharge = getTargetCharge(threshold)
    }
  }
  targetCharge
}

def setChargeTarget(chargeToPercent) {

  def uri = "http://${givTcpAddress}:${givTcpPort}/setChargeTarget"
  def body = "{\"chargeToPercent\": \"${chargeToPercent}\"}"

  log "POST to ${uri}: ${body}"
  
  def postParams = [
    uri: uri,
    requestContentType: 'application/json',
    contentType: 'application/json',
    timeout: 60,
    body: body
  ]

  asynchttpPost "receiveChargeTargetResponse", postParams
}


def receiveChargeTargetResponse(response, data) {

  if (response.status == 200) {

    def json = response.json
    if (json) {
      log.info json.result
    } else {
      log.warn "Received response that didn't contain any JSON"
    }

  } else {
    log.warn "Received error response [${response.status}] : ${response.errorMessage}"
  }
}
