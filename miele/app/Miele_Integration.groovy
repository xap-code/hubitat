/**
 *  Miele Integration
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
 * 08/01/2023 - Initial read-only implementation without actions
 */

definition(
  name: "Miele Integration",
  namespace: "xap",
  author: "Ben Deitch",
  description: "Integrates Miele@home devices into Hubitat via Miele cloud.",
	singleInstance: true,
  category: "", iconUrl: "", iconX2Url: "", iconX3Url: ""
)

preferences {
	page(name: "configure")
	page(name: "authorize")
}

mappings {
    path("/handleAuth") {
        action: [
            GET: "handleAuthRedirect"
        ]
    }
}

import groovy.transform.Field
  
// define constants
@Field static final String MIELE_AUTH_BASE = "https://api.mcs3.miele.com/thirdparty"
@Field static final String MIELE_LOGIN_PATH = "/login"
@Field static final String MIELE_LOGOUT_PATH = "/logout"
@Field static final String MIELE_TOKEN_PATH = "/token"

@Field static final String MIELE_API_BASE = "https://api.mcs3.miele.com/v1"
@Field static final String DEVICES_PATH = "/short/devices"
@Field static final String EVENTS_PATH = "/devices/all/events"

@Field static final String EVENTSTREAM_DNI = "miele:eventstream"
@Field static final String CHILD_DEVICE_DNI_PREFIX = "miele:"

@Field static final int REFRESH_TOKEN_EARLY_SECONDS = 5 * 60 // refresh OAuth token 5 minutes before it expires
@Field static final int REFRESH_TOKEN_MINIMUM_SECONDS = 60 // don't reduce refresh interval to less than 1 minute

// variable to hold transient state during app configuration
@Field static final Map context = [:]

private logInfo(message) {
  log.info buildLogMessage(message)
}

private logError(message) {
  log.error buildLogMessage(message)
}

private buildLogMessage(message) {
  "[${app.name}] ${message}"
}

def configure() {
  dynamicPage(name: "configure", title: "", install: true, uninstall: true) {
    header()

    section {
      if (state.clientAccessToken) {
        paragraph """${app.name} is authorized."""
        authDescription = "Edit authorization details"
      } else {
        paragraph """<h2>Welcome</h2>To integrate your Miele@home devices with Hubitat please authorize this app to access the Miele 3rd Party API."""
        authDescription = "Authorize access to your Miele@home devices"
      }
      href title: "Authorization", required: false, page: "authorize", description: authDescription
    }

    if (state.clientAccessToken) {
      generateDeviceActions()
      section {
        if (context.devicesError) {
          paragraph """<span style="color: darkred; font-weight: bold">${state.devicesError}</span>"""
        } else {
          paragraph """Select the Miele devices that you want to integrate with Hubitat (${context.deviceActions?.mieleDevices?.size()?:0} devices discovered):"""
          input name: "selectedDevices", title: "", type: "enum", multiple: true, options: getDeviceOptions(), submitOnChange: true
          input name: "refreshDevices", type: "button", title: "Refresh Devices", submitOnChange: true
        }
      }
      if (context.deviceActions?.createDevices || context.deviceActions?.deleteDevices) {
        section {
          paragraph """Press 'Done' to perform the following actions:"""
          if (context.deviceActions.createDevices) {
            paragraph """<span style="font-weight: bold">Create Hubitat devices:</span>"""
            context.deviceActions.createDevices.each { paragraph "<li>${it.name} [${it.type}]</li>" }
          }
          if (context.deviceActions.deleteDevices) {
            paragraph """<span style="font-weight: bold">Delete Hubitat devices:</span>"""
            context.deviceActions.deleteDevices.each { paragraph "<li>${it.name} [${it.type}]</li>" }
          }
        }
      }
    }
  }
}

def authorize(params) {

  if (!state.accessToken) {
    createAccessToken()
  }
  
  if (params?.logout) {
    if (params.logout > (context.logout ?: 0)) {
      logout()
      context.logout = params.logout
    }
  }

  dynamicPage(name: "authorize", title: "", install: false, uninstall: false, nextPage: "configure") {
    header()
    section {
      paragraph """To authorize ${app.name} please enter your Client ID and Client Secret provided by Miele for use with the Miele 3rd Party API."""
      input "clientId", "string", title: "Client ID", required: false, submitOnChange: true
      input "clientSecret", "string", title: "Client Secret", required: false, submitOnChange: true
      if (state.clientAccessToken) {
        href title: "Deauthorize", description: "Logout from Miele (NB: This will disable any integrated devices)", required: false, page: "authorize", params: ["logout": (context.logout ?: 0) + 1 ]
      } else if (clientId && clientSecret) {
        href title: "Authorize", description: "Login to Miele", required: false, url: buildLoginUrl(), style: "external"
      }
      if (context.authError) {
        paragraph """<span style="color: darkred; font-weight: bold">${context.authError}</span>"""
        context.remove("authError")
      }
      if (context.authSuccess) {
        paragraph """<span style="color: darkgreen; font-weight: bold">${context.authSuccess}</span>"""
        context.remove("authSuccess")
      }
      paragraph """<small>If you don't already have your Client ID and Client Secret then you need to register with Miele to get your personal client credentials. Further information on how to register with Miele can be found at <a href="https://developer.miele.com" target="_new">https://developer.miele.com</a> (click on "Get involved" at the top of the page).</small>"""
    }
  }
}

def header() {
  section {
    paragraph """<div style="width: 100%; background-color: lightgray; text-align: center; "><img src="https://www.miele.com/developer/wmedia/svg/logo.svg" onerror="this.src=this.src.replace(".svg",".png"); this.onerror=null;" height="64" /></div>"""
  }
}

def appButtonHandler(String button) {
  
  switch(button) {
    case "refreshDevices":
      getMieleDevices()
      break
  }
}

def handleAuthRedirect() {

  def authCode = params.code
    
  login(authCode)

  def html = "<script>window.close()</script>"
  render contentType: "text/html", data: html, status: 200
}

def installed() {
  initialize()
  context.clear()
}

def uninstalled() {
  unschedule()
  if (state.clientAccessToken) {
    logout()
  }
  context.clear()
}

def updated() {
  initialize()
  context.clear()
}

def initialize() {
  updateChildren()
  eventStreamDevice?.initialize()
}

def updateChildren() {
  context.deviceActions?.createDevices?.each { 
    if (it.id == EVENTSTREAM_DNI) {
      createEventStreamDevice()
    } else {
      createGenericDevice(it)
    }
  }
  context.deviceActions?.deleteDevices?.each {
    deleteChildDevice(it.dni)
    logInfo "Device '${it.name}' deleted [${it.dni}]"
  }
}

def createEventStreamDevice() {
  eventStreamDevice = addChildDevice(
      "xap",
      "Miele Event Stream",
      EVENTSTREAM_DNI,
      ["name": "Miele Event Stream"]
    )
  eventStreamDevice.configure(MIELE_API_BASE + EVENTS_PATH, decrypt(state.clientAccessToken))
  logInfo "Device '${genericDevice.name}' created [${genericDevice.deviceNetworkId}]"
}

def createGenericDevice(mieleDevice) {
  genericDevice = addChildDevice(
    "xap",
    "Miele Generic Device",
    CHILD_DEVICE_DNI_PREFIX + mieleDevice.id,
    ["name": mieleDevice.name]
  )
  genericDevice.configure(mieleDevice.type)
  logInfo "Device '${genericDevice.name}' created [${genericDevice.deviceNetworkId}]"
}

def eventReceived(message) {
  
  data = parseJson(message)
  
  // only pass on state update events
  data
  .findAll { id, json -> json.state }
  .each { id, json -> getChildDeviceByMieleId(id)?.eventReceived(json.state)}
}

private getAuthHeaders() {
  return [
    Authorization : "Bearer ${decrypt(state.clientAccessToken)}"
  ]
}

// ** DEVICES **

private generateDeviceActions() {
  if (!context.deviceActions) {
    context.deviceActions = [:]
  }
  if (!context.deviceActions.mieleDevices) {
    getMieleDevices();
  }
  getHubitatDevices();
  calculateCreateDevices();
  calculateDeleteDevices();
}

private getDeviceOptions() {
  context.deviceActions.mieleDevices?.sort { "${it.name},${it.id}" }
  .collectEntries { [ (it.id): "${it.name ? it.name + ' ' : ''}[${it.type}]" ] }
}

private getMieleDevices() {

  def params = [uri: MIELE_API_BASE + DEVICES_PATH, headers: getAuthHeaders()]
  
  try {
    httpGet(params) { response -> handleMieleDevicesResponse(response) }
  } catch (groovyx.net.http.HttpResponseException e) {
    context.devicesError = "Unable to get Miele devices -- ${e.getLocalizedMessage()}: ${e.response.data}"
  }
}

private handleMieleDevicesResponse(response) {
  context.deviceActions.mieleDevices = response.getData()
  .collect { it -> [ id: it.fabNumber, type: it.type, name: it.deviceName ] }
}

private getHubitatDevices() {
  context.deviceActions.hubitatDevices = childDevices
  .findAll { it.getDeviceNetworkId() != EVENTSTREAM_DNI }
  .collect { it -> [ mieleId: it.getMieleDeviceId(), name: it.name, label: it.label, type: it.getMieleDeviceType(), dni: it.getDeviceNetworkId() ] }
}

private getEventStreamDevice() {
  getChildDevice(EVENTSTREAM_DNI)
}

private getChildDeviceByMieleId(mieleId) {
  getChildDevice(CHILD_DEVICE_DNI_PREFIX + mieleId)
}

private calculateCreateDevices() {
  context.deviceActions.createDevices = selectedDevices
  .findAll { sd -> !context.deviceActions.hubitatDevices.any { hd -> hd.mieleId == sd } }
  .collect { sd -> context.deviceActions.mieleDevices.find { md -> md.id == sd } }
  if (!getEventStreamDevice()) {
    context.deviceActions.createDevices.add([ id: EVENTSTREAM_DNI, name: "Miele Event Stream", type: "${app.name} events receiver <i>*required device*</i> " ])
  }
}

private calculateDeleteDevices() {
  context.deviceActions.deleteDevices = context.deviceActions.hubitatDevices
  .findAll { hd -> !selectedDevices.any { sd -> sd == hd.mieleId } }
}

// ** LOGIN/LOGOUT **

private buildLoginUrl() {
  MIELE_AUTH_BASE + MIELE_LOGIN_PATH + 
    '?client_id=' + clientId + 
    '&response_type=code' +
    '&redirect_uri=https://cloud.hubitat.com/oauth/stateredirect' +
    '&state=' + getHubUID() + '/apps/' + app.id + '/handleAuth?access_token=' + state.accessToken
}

private login(authCode) {

  def body = [
    client_id    : clientId,
    client_secret: clientSecret,
    code         : authCode,
    grant_type   : 'authorization_code',
    redirect_uri : 'https://cloud.hubitat.com/oauth/stateredirect'
  ]
  
  def params = [uri: MIELE_AUTH_BASE + MIELE_TOKEN_PATH, body: body]

  try {
    httpPost(params) { response -> handleLoginResponse(response) }
  } catch (groovyx.net.http.HttpResponseException e) {
    context.authError = "Request Failed -- ${e.getLocalizedMessage()}: ${e.response.data}"
  }
}

private refreshToken() {

  def body = [
    client_id    : clientId,
    client_secret: clientSecret,
    grant_type   : 'refresh_token',
    refresh_token: decrypt(state.clientRefreshToken)
  ]
  
  def params = [uri: MIELE_AUTH_BASE + MIELE_TOKEN_PATH, body: body]

  try {
    httpPost(params) { response -> handleLoginResponse(response) }
  } catch (groovyx.net.http.HttpResponseException e) {
    logError "Refresh Token Request Failed! Please re-authorize to use the integration -- ${e.getLocalizedMessage()}: ${e.response.data}"
  }
}

private handleLoginResponse(response) {
  def data = response.getData()
  if (data.access_token && data.refresh_token) {
    updateAccessToken(data)
    context.authSuccess = "Login succeeded; Press 'Next' to configure devices."
    logInfo "Login succeeded"
  } else {
    logError(context.authError = "Login failed; No tokens found in response: ${data}")
  }
}

private logout() {

  def params = [uri: MIELE_AUTH_BASE + MIELE_LOGOUT_PATH, headers: getAuthHeaders()]
  
  try {
    httpPost(params) { response -> handleLogoutResponse(response) }
  } catch (groovyx.net.http.HttpResponseException e) {
    logError(context.authError = "Request Failed -- ${e.getLocalizedMessage()}: ${e.response.data}")
  }
}

private handleLogoutResponse(response) {
  if (response.getStatus() == 204) {
    unschedule()
    state.remove("clientAccessToken")
    state.remove("clientRefreshToken")
    context.authSuccess = "Logout succeeded; Please re-authorize to use the integration."
    logInfo "Logout succeeded"
  } else {
    logError(context.authError = "Logout failed; response status ${response.getStatus()}: ${response.getData()}")
  }
}

private updateAccessToken(data) {
  state.clientAccessToken = encrypt(data.access_token)
  state.clientRefreshToken = encrypt(data.refresh_token)
  scheduleRefreshToken(data.expires_in)
  eventStreamDevice?.configure(MIELE_API_BASE + EVENTS_PATH, data.access_token)
}

private scheduleRefreshToken(interval) {
  refreshInterval = Math.max(REFRESH_TOKEN_MINIMUM_SECONDS, interval - REFRESH_TOKEN_EARLY_SECONDS)
  runIn(refreshInterval, "refreshToken")
}
