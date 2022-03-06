/*
 * GivEnergy Forecast Charge
 */

definition(
  name: "GivEnergy Forecast Charge",
  namespace: "xap",
  author: "Ben Deitch",
  description: "Uses Solcast to predict solar generation and adjust GivEnergy Battery target charge via local GivTCP service.",
  category: "My Apps",
  iconUrl: "http://cdn.device-icons.smartthings.com/Entertainment/entertainment2-icn.png",
  iconX2Url: "http://cdn.device-icons.smartthings.com/Entertainment/entertainment2-icn@2x.png",
  iconX3Url: "http://cdn.device-icons.smartthings.com/Entertainment/entertainment2-icn@3x.png")

preferences {
  section("<h3>Solcast API</h3>") {
    input name: "solcastResourceId", type: "string", required: true, title: "Solcast Resource ID"
    input name: "solcastApiKey", type: "string", required: true, title: "Solcast API Key"
  }
  section("<h3>GivTCP</h3>") {
    input name: "givTcpAddress", type: "string", required: true, title: "GivTCP Service IP Address"
    input name: "givTcpPort", type: "number", required: true, title: "GivTCP Service Port"
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
  queryForecast()
}

def queryForecast() {

  def uri = "https://api.solcast.com.au/rooftop_sites/${solcastResourceId}/forecasts?format=json&hours=24"
  
  log "GET forecast from ${uri}"

  asynchttpGet "handleForecast", [
    uri: uri,
    headers: ["Authorization": "Bearer ${solcastApiKey}"]
  ]
}

def handleForecast(response, data) {
  
  def json = response.json
  
  log json
  
  if (json?.forecasts) {
    def forecastEnergy = calculateDailyForecast(json.forecasts)
    log.info "Today's solar PV generation forecast is ${forecastEnergy}Wh"
    def chargeTarget = determineChargeTarget(forecastEnergy)
    log.info "Set charge target to ${chargeTarget}%"
    setChargeTarget(chargeTarget)
  } else {
    log.warn "Unable to set charge target as query for forecast returned no data"
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

def determineChargeTarget(forecastEnergy) {
  
  def highestEnergy = -1;
  def highestCharge = -1;
  for (int threshold = 1; threshold <= thresholdCount; threshold++) {
    def thresholdEnergy = getForecastEnergy(threshold)
    if (forecastEnergy >= thresholdEnergy && thresholdEnergy > highestEnergy) {
      highestEnergy = thresholdEnergy
      highestCharge = getTargetCharge(threshold)
    }
  }
  highestCharge
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
