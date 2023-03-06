/**
 *  Met Office Forecast
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
 * 28/02/2023 - Initial Version
 * 06/03/2023 - Use three hourly forecast for weather warnings
 */
metadata {
  definition (
    name: "Met Office Forecast",
    namespace: "xap",
    author: "Ben Deitch",
    importUrl: ""
  ) {
    capability "Sensor"

    attribute "warnCold", "enum", ["ok", "warn"]
    attribute "warnHot", "enum", ["ok", "warn"]
    attribute "warnRain", "enum", ["ok", "warn"]
    attribute "warnSnow", "enum", ["ok", "warn"]
    attribute "warnWind", "enum", ["ok", "warn"]
    attribute "warnUV", "enum", ["ok", "warn"]
    
    attribute "forecast1Time", "string"
    attribute "forecast1WeatherCode", "number"
    attribute "forecast1WeatherDescription", "string"
    attribute "forecast1Temperature", "number"
    
    attribute "forecast2Time", "string"
    attribute "forecast2WeatherCode", "number"
    attribute "forecast2WeatherDescription", "string"
    attribute "forecast2Temperature", "number"
    
    attribute "forecast3Time", "string"
    attribute "forecast3WeatherCode", "number"
    attribute "forecast3WeatherDescription", "string"
    attribute "forecast3Temperature", "number"
    
    attribute "forecast4Time", "string"
    attribute "forecast4WeatherCode", "number"
    attribute "forecast4WeatherDescription", "string"
    attribute "forecast4Temperature", "number"
    
    command "testWarning", ["string"]
  }
	preferences {
		input name: "clientId", title: "Client ID", type: "string", required: true
		input name: "clientSecret", title: "Client Secret", type: "string", required: true
    input name: "coldThreshold", title: "Warn Cold Threshold (<= °C)", type: "number", required: true
    input name: "hotThreshold", title: "Warn Hot Threshold (>= °C)", type: "number", required: true
    input name: "rainThreshold", title: "Warn Heavy Rain Threshold (>= %prob.)", type: "number", required: true
    input name: "snowThreshold", title: "Warn Heavy Snow Threshold (>= %prob.)", type: "number", required: true
    input name: "windThreshold", title: "Warn Wind Gust Threshold (>= MPH)", type: "number", required: true
    input name: "uvThreshold", title: "Warn UV Index Threshold (>= UVI)", type: "number", required: true
		input name: "debugEnabled", title: "Enable debug logging", type: "bool"
	}
}

import groovy.transform.Field
import java.text.SimpleDateFormat

@Field static final String URL_THREE_HOURLY = "https://api-metoffice.apiconnect.ibmcloud.com/v0/forecasts/point/three-hourly"
@Field static final String HEADER_CLIENT_ID = "X-IBM-Client-Id"
@Field static final String HEADER_CLIENT_SECRET = "X-IBM-Client-Secret"
@Field static final SimpleDateFormat JSON_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'")
@Field static final SimpleDateFormat ATTRIBUTE_TIME_FORMAT = new SimpleDateFormat("HH:mm")
@Field static final String[] WEATHER_DESCRIPTIONS = [
  "Clear night",
  "Sunny day",
  "Partly cloudy",
  "Partly cloudy",
  "Not used",
  "Mist",
  "Fog",
  "Cloudy",
  "Overcast",
  "Light rain",
  "Light rain",
  "Drizzle",
  "Light rain",
  "Heavy rain",
  "Heavy rain",
  "Heavy rain",
  "Sleet",
  "Sleet",
  "Sleet",
  "Hail",
  "Hail",
  "Hail",
  "Light snow",
  "Light snow",
  "Light snow",
  "Heavy snow",
  "Heavy snow",
  "Heavy snow",
  "Thunder",
  "Thunder",
  "Thunder"
]

def log(message) {
  if (debugEnabled) {
    log.debug message
  }
}

def installed() {
  initialize()
}

def updated() {
  initialize()
}

def initialize() {
  unschedule()
  queryThreeHourlyForecast()
  schedule("0 1/30  * ? * *", queryThreeHourlyForecast)
}

def testWarning(name) {
  sendEvent name: "warn" + name, value: "warn"
  runIn(5, resetWarning, [data: [name: name]])
}

def resetWarning(data) {
  sendEvent name: "warn" + data.name, value: "ok"
}

def queryThreeHourlyForecast() {
  queryForLocation URL_THREE_HOURLY, { response -> handleThreeHourlyForecast(response.data) }
}

private handleThreeHourlyForecast(data) {
  def forecasts = data?.features[0].properties.timeSeries
  if (!forecasts) {
    log.error "Three hourly forecasts not found in data!"
    return
  }
  int skip = JSON_TIME_FORMAT.parse(forecasts[1].time).before(new Date()) ? 1 : 0
  def minTemp = null
  def maxTemp = null
  def rainProb = null
  def snowProb = null
  def windSpeed = null
  def uvi = null
  for (int i = 0; i <= 3; i++) {
    def current = forecasts[i + skip]
    if (minTemp == null || minTemp > current.minScreenAirTemp) {
      minTemp = current.minScreenAirTemp
    }
    if (maxTemp == null || maxTemp < current.maxScreenAirTemp) {
      maxTemp = current.maxScreenAirTemp
    }
    if (rainProb == null || rainProb < current.probOfHeavyRain) {
      rainProb = current.probOfHeavyRain
    }
    if (snowProb == null || snowProb < current.probOfHeavySnow) {
      snowProb = current.probOfHeavySnow
    }
    if (windSpeed == null || windSpeed < current.windGustSpeed10m) {
      windSpeed = current.windGustSpeed10m
    }
    if (uvi == null || uvi < current.uvIndex) {
      uvi = current.uvIndex
    }
    def previous = i > 0 ? forecasts[(i + skip) -1] : null
    def showDescription = previous == null || previous.significantWeatherCode != current.significantWeatherCode
    sendThreeHourlyForecastEvents(i + 1, current, showDescription)
  }
  sendEvent name: "warnCold", value: minTemp > coldThreshold ? "ok" : "warn"
  sendEvent name: "warnHot", value: minTemp < hotThreshold ? "ok" : "warn"
  sendEvent name: "warnRain", value: rainProb < rainThreshold ? "ok" : "warn"
  sendEvent name: "warnSnow", value: snowProb < snowThreshold ? "ok" : "warn"
  sendEvent name: "warnWind", value: windSpeed < windThreshold ? "ok" : "warn"
  sendEvent name: "warnUV", value: uvi < uvThreshold ? "ok" : "warn"
}

private sendThreeHourlyForecastEvents(forecastNumber, forecast, showDescription) {
  log "Forecast: ${forecast}"
  def time = ATTRIBUTE_TIME_FORMAT.format(JSON_TIME_FORMAT.parse(forecast.time))
  def weatherCode = forecast.significantWeatherCode
  def weatherDescription = showDescription ? WEATHER_DESCRIPTIONS[weatherCode] : " "
  def temperature = roundToHalf((forecast.minScreenAirTemp + forecast.maxScreenAirTemp) / 2)
  sendEvent name: "forecast${forecastNumber}Time", value: time
  sendEvent name: "forecast${forecastNumber}WeatherCode", value: weatherCode
  sendEvent name: "forecast${forecastNumber}WeatherDescription", value: weatherDescription
  sendEvent name: "forecast${forecastNumber}Temperature", value: temperature
}

private roundToHalf(value) {
  Math.round(value * 2) / 2
}

private queryForLocation(uri, responseHandler) {
  def params = [
    uri: "${uri}?latitude=${location.latitude}&longitude=${location.longitude}",
    headers: [ "${HEADER_CLIENT_ID}": clientId, "${HEADER_CLIENT_SECRET}": clientSecret ]
  ]
  log "Query: ${params}"
  try {
    httpGet(params, responseHandler)
  } catch (groovyx.net.http.HttpResponseException e) {
    log.error "Request Failed -- ${e.getLocalizedMessage()}: ${e.response.data}"
  }
}
