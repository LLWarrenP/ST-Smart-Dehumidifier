/*
 *  Smart Dehumidifier
 *
 *  Copyright 2018 Warren Poschman
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
 
def appVersion() {
	return "1.1"
}

/**
* Change Log:
* 2018-6-7 - (1.1) Added the option to have minimum cycle off time in case the dehumidifier does not have cycle protection (uncommon unless old)
* 2018-6-2 - (1.0) Initial release
**/

definition(
    name: "Smart Dehumidifier Control v${appVersion()}",
    namespace: "LLWarrenP",
    author: "Warren Poschman",
    description: "Control dehumidifier based on relative humidity from an external sensor",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/water_moisture.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/water_moisture@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Meta/water_moisture@2x.png")

preferences {
	section("Smart Dehumidifier Control v${appVersion()}")
   	section("Control which Dehumidifier:") {
		input "dehumidifier", "capability.switch", required:true
	}
	section("Use the following humidity sensor(s):") {
		input "humiditySensor", "capability.relativeHumidityMeasurement", required:true
	}
	section("Desired Humidity Setpoint") {
		input "humiditySetpoint", "number", title: "Setpoint % RH", defaultValue: 50, required:true
	}
	section("Also allow an overshoot of +/-:") {
		input "humidityOvershoot", "number", title: "Overshoot % RH", range: 0..25, defaultValue: 0, required:true
	}
	section("Require a minimum off cycle time of this many minutes:") {
		input "minCycleTime", "number", title: "Minimum Off Cycle (Minutes)", defaultValue: 0, required:true
	}
	section( "Continuous Runtime Notifications" ) {
		input "maxRuntime", "number", title: "Maximum Runtime (Hours)", range: 0..48, defaultValue: 0, required:true
		input "messageText", "text", title: "Custom Runtime Alert Text (optional)", required: false
		input "phone", "phone", title: "Phone Number (for SMS, optional)", required: false
		input "pushAndPhone", "enum", title: "Both Push and SMS?", required: false, options: ["Yes","No"]
	}
}

def installed() {
	log.debug "Installed with settings: ${settings}"
	subscribe(humiditySensor, "humidity", humidityHandler)
}

def updated() {
	log.debug "Updated with settings: ${settings}"
	unsubscribe()
	subscribe(humiditySensor, "humidity", humidityHandler)
}

def humidityHandler(evt) {
	log.debug "humidity changed to: ${evt.value}%"
	def currentHumidity = Integer.parseInt(evt.value.replace("%", ""))

	def overshoot = humidityOvershoot.toInteger()
	def runtime = maxRuntime.toInteger()
	def cycleTime = minCycleTime.toInteger()
	def timePassedHours = 0
	def timePassedRoundHours = 0
	def timePassedMinutes = 0
	def timePassedRoundMinutes = 0
	def timeCycleMinutes = 0
	def timeCycleRoundMinutes = 0
	if (overshoot == null) overshoot = 0
	if (runtime == null) runtime = 0
	if (cycleTime == null) cycleTime = 0

	// Calculate the setpoint band (setpoint +/-overshoot)
	def adjustedMinThreshold = (humiditySetpoint.toInteger() - overshoot.toInteger())
	def adjustedMaxThreshold = (humiditySetpoint.toInteger() + overshoot.toInteger())

	// If state machine status is undefined, make it match the switch or if that isn't reporting, set it to off
	// Note that this could be done all the time but shouldn't be necessary when the state is known and allows some user flexibility
	if (state[frequencyStatus(evt)] == null) {
		if ((dehumidifier.currentValue("switch") == "on") || (dehumidifier.currentValue("switch") == "off")) state[frequencyStatus(evt)] = dehumidifier.currentValue("switch")
		else state[frequencyStatus(evt)] = "off"
	}

	// Calculate runtime and cycletime for use later
	if (state[frequencyStatus(evt)] == "on") {
		timePassedHours = (now() - state[frequencyLastOn(evt)]) / 3600000
		timePassedRoundHours = Math.round(timePassedHours.toDouble()) + (unit ?: "")
		timePassedMinutes = (now() - state[frequencyLastOn(evt)]) / 60000
		timePassedRoundMinutes = Math.round(timePassedMinutes.toDouble()) + (unit ?: "")
	}
	else {
		if (state[frequencyLastOff(evt)] == null) state[frequencyLastOff(evt)] = 0
		timeCycleMinutes = (now() - state[frequencyLastOff(evt)]) / 60000
		timeCycleRoundMinutes = Math.round(timeCycleMinutes.toDouble()) + (unit ?: "")
	}
   
	// Based on the event (change in humidity), turn the humidifier on, off, or leave it unchanged

	// Humidity is below the setpoint plus any allowable overshoot, turn off dehumidifier if required
	if (currentHumidity <= adjustedMinThreshold) {
    	// Log current status and relevant runtime
		if (state[frequencyStatus(evt)] == "on") log.debug "humidity is below ${humiditySetpoint}% +/-${overshoot}%, turning off dehumidifier after running for ${timePassedRoundMinutes} minutes"
		else log.debug "humidity is below ${humiditySetpoint}% +/-${overshoot}%, dehumidifier off"
		dehumidifier.off()			// Always, just in case it was on manually
		state[frequencyStatus(evt)] = "off"
		state[frequencyLastOff(evt)] = now()
		state[frequencyAlert(evt)] = 0		// Reset alert interval timer
	}
	// Humidity is between the +/-setpoints, keep it as-is until we reach the lower overshoot
	else if ((currentHumidity > adjustedMinThreshold) && (currentHumidity <= adjustedMaxThreshold)) {
    	// Log the current status, assumption is that state machine matches dehumidifier state
		if (state[frequencyStatus(evt)] == "on") log.debug "dehumidifier is on, within setpoint band (running for ${timePassedRoundMinutes} minutes)"
		if (state[frequencyStatus(evt)] == "off") log.debug "dehumidifier is off, within setpoint band"
	}
	// Humidity has risen above the setpoint plus any allowable overshoot, turn on dehumidifier as long as the minimum off cycle time has passed
	else if (currentHumidity > adjustedMaxThreshold) {
		// If dehumidifier is off according to the state machine, log whether we are turning it on or waiting for the minimum off cycle
		// or if it is already on, log that it is running and for how long
		if (state[frequencyStatus(evt)] == "off") {
			if (timeCycleRoundMinutes.toInteger() >= cycleTime.toInteger()) log.debug "humidity is above ${humiditySetpoint}% +/-${overshoot}%, turning on dehumidifier"
			// Dehumidifier is off but hasn't been off long enough - only really necessary if dehumidifier does not have cycle protection built in (uncommon today)
			else log.debug "humidity is above ${humiditySetpoint}% +/-${overshoot}%, waiting for a minimum cycle time of ${cycleTime} minutes"
		}
		else log.debug "humidity is above ${humiditySetpoint}% +/-${overshoot}%, humidifier has been running for ${timePassedRoundMinutes} minutes"
		// If the minimum off cycle time has passed, turn the dehumidifier on
		if (timeCycleRoundMinutes.toInteger() >= cycleTime.toInteger()) {
			// If the dehumidifier was off according to the state machine, begin to track when it was turned on
			if ((state[frequencyStatus(evt)] == "off") || (state[frequencyStatus(evt)] == null)) state[frequencyLastOn(evt)] = now()
			dehumidifier.on()			// Always, just in case it was off manually
			state[frequencyStatus(evt)] = "on"
		}
		// Check to see that the humidifier hasn't been running too long - a sign of either failure or excessive humidity
		if ((runtime.toInteger() != 0) && (state[frequencyStatus(evt)] == "on")) {
			if (state[frequencyAlert(evt)] == null) state[frequencyAlert(evt)] = 0
			if ((now() - state[frequencyLastOn(evt)]) >= (runtime.toInteger() * 3600000)) {
				// Only alert once per period
				if ((now() - state[frequencyAlert(evt)]) >= (runtime.toInteger() * 3600000)) {
					log.debug "dehumidifier excessive runtime of ${timePassedRoundHours} hours - sending alerts"
					state[frequencyAlert(evt)] = now()
					def msg = messageText ?: "Warning: ${dehumidifier} has run continuously for more than ${timePassedRoundHours} hours.  Humidity is ${currentHumidity}%."

					if (!phone || pushAndPhone != "No") {
						log.debug "sending push"
						sendPush(msg)
					}

					if (phone) {
						log.debug "sending SMS"
						sendSms(phone, msg)
					}
				}
			}
		}

	}

}

private frequencyLastOn(evt) {
	"lastOnTimeStamp"
}

private frequencyLastOff(evt) {
	"lastOffTimeStamp"
}

private frequencyStatus(evt) {
	"lastStatus"
}

private frequencyAlert(evt) {
	"lastAlertTimeStamp"
}

// END OF FILE
