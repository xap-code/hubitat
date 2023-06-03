/**
 *  Battery Power Meter Child Device
 *
 *  Copyright 2021 Ben Deitch
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
 * 03/06/2023 - v0.1 - Initial implementation
 */

metadata {
    definition (name: "Battery Power Meter Child Device", namespace: "xap", author: "Ben Deitch") {
        capability "Actuator"
        capability "Sensor"
        capability "Battery"
        capability "Power Meter"
        capability "Refresh"
    }
}

void refresh() {
    parent.refresh()
}
