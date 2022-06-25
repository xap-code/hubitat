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
 * 12/03/2022 - v0.3 - Improve error logging for failed requests and use default target if unable to get forecast
 * 15/03/2022 - v0.4 - Allow scenario weighting with Solcast to select between p10-p-p90 estimates
 * 16/03/2022 - v0.5 - Round Solcast forecast
 * 22/03/2022 - v0.6 - Adjust charge start time based on charge target and set schedule based on earliest start time
 * 24/03/2022 - v0.7 - Include charge times in log message
 * 27/03/2022 - v0.8 - Use LocalDateTime to account for time zone offsets
 * 24/06/2022 - v0.9 - Retry failed requests to battery
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
    input name: "chargeStartTime", type: "time", required: true, title: "Earliest Charge Start Time", width: 5
    input name: "chargeEndTime", type: "time", required: true, title: "Charge End Time", width: 4
    input name: "batteryCapacity", type: "decimal", required: true, title: "Battery Capacity (kWh)", width: 5
    input name: "batteryChargeRate", type: "decimal", required: true, title: "Battery Charge Rate (kWh)", width: 5
  }
  section("<h3>Solar Forecast</h3>") {
    input name: "forecastProvider", type: "enum", options: ["Solcast", "Forecast.Solar"], required: true, title: "Forecast Provider", submitOnChange: true
    if (forecastProvider == 'Solcast') { 
      input name: "solcastResourceId", type: "string", required: true, title: "Solcast Resource ID"
      input name: "solcastApiKey", type: "string", required: true, title: "Solcast API Key"
      input name: "weighting", type: "decimal", required: true, range: "-1..1", defaultValue: 0, title: "Scenario Weighting (from -1.0 to use low estimates, up to 1.0 to use high estimates; defaults to 0 to use normal estimates)"
    }
    if (forecastProvider == 'Forecast.Solar') {
      input name: "latitude", type: "string", required: true, title: "Latitude"
      input name: "longtitude", type: "string", required: true, title: "Longtitude"
      input name: "declination", type: "string", required: true, title: "Declination (angle of roof)"
      input name: "azimuth", type: "string", required: true, title: "Azimuth (direction of roof face)"
      input name: "power", type: "string", required: true, title: "System Power (kWh)"
    }
  }
  section("<h3>Charge Thresholds</h3>") {
    paragraph("Define Charge Thresholds")
    (1..thresholdCount).each { 
      input name: getForecastEnergyName(it), type: "decimal", required: true, title: "Daily Forecast (kWh)", width: 20
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

import groovy.transform.Field
import java.time.*
  
// define constants
@Field static final int LEAD_TIME_MINUTES = 2
@Field static final int MIN_CHARGE_MINUTES = 10
@Field static final int MAX_ATTEMPTS = 5

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

def getForecastEnergyWh(index) {
  app.getSetting(getForecastEnergyName(index)) * 1000
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
  scheduleUpdateChargeTarget()
  pruneThresholdSettings()
}

def scheduleUpdateChargeTarget() {
  def time = toLocalDateTime(chargeStartTime).minusMinutes(LEAD_TIME_MINUTES)
  schedule("0 ${time.minute} ${time.hour} ? * *", updateChargeTarget)
}

def toLocalDateTime(inputDateTime) {

  def inputTime=inputDateTime.substring(inputDateTime.indexOf("T"))
  int offsetIndex=Math.max(inputTime.indexOf("+"), inputTime.indexOf("-"))

  ZoneId zone = offsetIndex > -1
  ? ZoneId.of(inputTime.substring(offsetIndex))
  : ZoneId.systemDefault()
  
  toDateTime(inputDateTime).toInstant().atZone(zone).toLocalDateTime()
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
  
  log.info "Getting forecast data from Solcast"
  log "GET: ${uri}"

  asynchttpGet "handleSolcast", [
    uri: uri,
    headers: ["Authorization": "Bearer ${solcastApiKey}"],
    timeout: 60
  ]
}

def handleSolcast(response, data) {
  
  def json = getResponseJson(response)
  
  log "Received JSON: ${json}"
  
  if (json?.forecasts) {
    def forecastEnergy = calculateDailyForecast(json.forecasts)
    adjustToForecast(forecastEnergy)
  } else {
    setDefaultTarget()
  }
}

def queryForecastSolar() {

  def uri = "https://api.forecast.solar/estimate/${latitude}/${longtitude}/${declination}/${azimuth}/${power}"
  
  log.info "Getting forecast data from Forecast.Solar"
  log "GET: ${uri}"

  asynchttpGet "handleForecastSolar", [
    uri: uri,
    timeout: 60
  ]
}

def handleForecastSolar(response, data) {
  
  def json = getResponseJson(response)
  
  log "Received JSON: ${json}"
  
  if (json?.result?.watt_hours_day) {
    def forecastEnergy = chooseDailyForecast(json.result.watt_hours_day)
    adjustToForecast(forecastEnergy)
  } else {
    setDefaultTarget()
  }
}

def adjustToForecast(forecastEnergy) {

  log.info "Today's solar PV generation forecast is ${forecastEnergy}Wh"
  def chargeTarget = determineChargeTarget(forecastEnergy)
  setForChargeTarget(chargeTarget)
}

def setDefaultTarget() {

  log.warn "Unable to get solar forecast data. Using default charge target of ${defaultTargetCharge}%"
  setForChargeTarget(defaultTargetCharge)
}

def toSlotTime(time) {
  String.format("%02d:%02d", time.hour, time.minute)
}

def setForChargeTarget(chargeTarget) {

  def startTime = toLocalDateTime(chargeStartTime)
  def endTime = toLocalDateTime(chargeEndTime)
  def calculatedStartTime = calculateChargeStartTime(startTime, endTime, chargeTarget)
  
  log.info "Set charge target to ${chargeTarget}%, charging from ${calculatedStartTime} until ${endTime}"

  def chargeStart = toSlotTime(calculatedStartTime)
  def chargeFinish = toSlotTime(endTime)

  setChargeSlot(chargeTarget, chargeStart, chargeFinish)
}

def getForecastDate(forecast) {
  toDateTime(forecast.period_end.replace(".0000000Z", "Z")).date
}

def getForecastPeriodHours(forecast) {
  Duration.parse(forecast.period).toMinutes() / 60.0
}

def getPeriodEstimate(forecast) {

  if (weighting < 0) {
    forecast.pv_estimate + weighting * (forecast.pv_estimate - forecast.pv_estimate10)
  } else if (weighting > 0) {
    forecast.pv_estimate + weighting * (forecast.pv_estimate90 - forecast.pv_estimate)
  } else {
    forecast.pv_estimate
  }
}

def calculateDailyForecast(forecasts) {

  def today = new Date().date

  Math.round(
    forecasts
    .findAll { getForecastDate(it) == today }
    .collect { getForecastPeriodHours(it) * getPeriodEstimate(it) }
    .sum { it } * 1000
  )
}

def chooseDailyForecast(days) {

  def today = new Date().format("yyyy-MM-dd")
  days[today]
}

def determineChargeTarget(forecastEnergy) {
  
  def highestEnergy = -1;
  def targetCharge = defaultTargetCharge;
  for (int threshold = 1; threshold <= thresholdCount; threshold++) {
    def thresholdEnergy = getForecastEnergyWh(threshold)
    if (forecastEnergy >= thresholdEnergy && thresholdEnergy > highestEnergy) {
      highestEnergy = thresholdEnergy
      targetCharge = getTargetCharge(threshold)
    }
  }
  targetCharge
}

def calculateChargeStartTime(startTime, endTime, chargePercent) {

  def chargeTargetkWh = batteryCapacity * chargePercent/100.0
  def chargeTimeMinutes = (int) Math.ceil(chargeTargetkWh / batteryChargeRate*60)
  def calculatedStartTime = endTime.minusMinutes(chargeTimeMinutes)

  log "chargePercent=${chargePercent}, chargeTargetkWh=${chargeTargetkWh}, chargeTimeMinutes=${chargeTimeMinutes}, calculatedStartTime=${calculatedStartTime}"
  
  startTime.isAfter(calculatedStartTime) ? startTime : calculatedStartTime
}

def setChargeSlot(chargeToPercent, chargeStart, chargeFinish, attempts = 0) {

  def uri = "http://${givTcpAddress}:${givTcpPort}/setChargeSlot1"
  def body = "{\"start\": \"${chargeStart}\", \"finish\": \"${chargeFinish}\", \"chargeToPercent\": \"${chargeToPercent}\"}"

  log "POST: ${uri} < ${body}"
  
  def postParams = [
    uri: uri,
    contentType: 'application/json',
    timeout: 60,
    body: body
  ]

  def data = [
    chargeToPercent: chargeToPercent,
    chargeStart: chargeStart,
    chargeFinish: chargeFinish,
    attempts: ++attempts
  ]
  
  asynchttpPost "receiveChargeSlotResponse", postParams, data
}

def retrySetChargeSlot(data) {
  
  setChargeSlot(
    data.chargeToPercent,
    data.chargeStart,
    data.chargeFinish,
    data.attempts
  )
}

def receiveChargeSlotResponse(response, data) {

  def json = getResponseJson(response)

  if (json.result.contains("failed")) {
    log.warn json.result
    if (data.attempts < MAX_ATTEMPTS) {
      def delayInSeconds = data.attempts
      runIn(delayInSeconds, "retrySetChargeSlot", [data: data])
    } else {
      log.error "Failed to set charge target after ${data.attempts} attempts"
    }
  } else {
    log.info json.result
  }
}

private getResponseJson(response) {
  
  if (response.status == 200) {
    def json = response.json
    if (json) {
      json
    } else {
      log.warn "Received response that didn't contain any JSON"
    }
  } else {
    log.warn "Received error response [${response.status}] : ${response.errorMessage}"
  }
}
