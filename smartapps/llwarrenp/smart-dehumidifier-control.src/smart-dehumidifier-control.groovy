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
	return "1.7"
}

/*
* Change Log:
* 2018-9-30 - (1.7) Tweak to allow for decimal % RH by rounding to nearest whole value
* 2018-6-24 - (1.6) Fixed bug that prevented app from loading in certain circumstances due to range preference
* 2018-6-23 - (1.5) Improved logic for resuming after doors/windows close and also accounting for off cycle time delays
* 2018-6-19 - (1.4) Added feature to pause dehumidification while a door or window is open for longer than a set time
* 2018-6-14 - (1.3) Added display for last/current RH and status in app settings
* 2018-6-8  - (1.2) Tweaked for GitHub and uploaded
* 2018-6-7  - (1.1) Added the option to have minimum cycle off time in case the dehumidifier does not have cycle protection (uncommon unless old)
* 2018-6-2  - (1.0) Initial release
*/

definition(
    name: "Smart Dehumidifier Control",
    namespace: "LLWarrenP",
    author: "Warren Poschman",
    description: "Control dehumidifier based on relative humidity from an external sensor",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/water_moisture.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/water_moisture@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Meta/water_moisture@2x.png")

def showLast(last) {
	if (last == null) {
		last = "Updating..."
	}
	return last
}


preferences {
	section("Smart Dehumidifier Control v${appVersion()}\n\n   Last Equipment Status: ${showLast(atomicState.lastStatus)}\n   Last Humidity Reading: ${showLast(atomicState.lastRH)}%")
   	section("Control which Dehumidifier:") {
		input "dehumidifier", "capability.switch", required:true
	}
	section("Use the following humidity sensor(s):") {
		input "humiditySensor", "capability.relativeHumidityMeasurement", required:true
	}
	section("Desired Humidity Setpoint") {
		input "humiditySetpoint", "number", title: "Setpoint % RH", range: "0..100", defaultValue: 50, required:true
	}
	section("Also allow an overshoot of +/-:") {
		input "humidityOvershoot", "number", title: "Overshoot % RH", range: "0..25", defaultValue: 0, required:true
	}
	section("Require a minimum off cycle time of this many minutes:") {
		input "minCycleTime", "number", title: "Minimum Off Cycle (Minutes)", range: "0..*", defaultValue: 0, required:true
	}
    section("Pause dehumidification while any of these doors or windows are open:") {
		input "doorwindowSensors", "capability.contactSensor", title: "Which Doors and Windows?", multiple: true, required: false
        input "openTime", "number", title: "For how many minutes?", range: "0..*", defaultValue: 5, required: false
	}
	section( "Continuous Runtime Notifications" ) {
		input "maxRuntime", "number", title: "Maximum Runtime (Hours)", range: "0..48", defaultValue: 0, required:true
		input "messageText", "text", title: "Custom Runtime Alert Text (optional)", required: false
		input "phone", "phone", title: "Phone Number (for SMS, optional)", required: false
		input "pushAndPhone", "enum", title: "Both Push and SMS?", required: false, options: ["Yes","No"]
	}
}

def installed() {
	log.debug "Installed with settings: ${settings}"
	subscribe(humiditySensor, "humidity", humidityHandler)
    if (doorwindowSensors) subscribe(doorwindowSensors, "contact", doorwindowHandler)
}

def updated() {
	log.debug "Updated with settings: ${settings}"
	unsubscribe()
	subscribe(humiditySensor, "humidity", humidityHandler)
    if (doorwindowSensors) subscribe(doorwindowSensors, "contact", doorwindowHandler)
}

def humidityHandler(evt) {
	def rawHumidity = Math.round(Double.parseDouble(evt.value.replace("%", "")))
    def currentHumidity = rawHumidity.toInteger();
	log.debug "humidity changed to: ${currentHumidity}% (${evt.value}%)"
    state[frequencyLastRH(evt)] = currentHumidity

	def overshoot = humidityOvershoot.toInteger()
	def runtime = maxRuntime.toInteger()
	def cycleTime = minCycleTime.toInteger()
	if (overshoot == null) overshoot = 0
	if (runtime == null) runtime = 0
	if (cycleTime == null) cycleTime = 0

	def timePassedHours = 0
	def timePassedRoundHours = 0
	def timePassedMinutes = 0
	def timePassedRoundMinutes = 0
	def timeCycleMinutes = 0
	def timeCycleRoundMinutes = 0

	// Calculate the setpoint band (setpoint +/-overshoot)
	def adjustedMinThreshold = (humiditySetpoint.toInteger() - overshoot.toInteger())
	def adjustedMaxThreshold = (humiditySetpoint.toInteger() + overshoot.toInteger())

	// If state machine status is undefined, make it match the switch or if that isn't reporting, set it to off
	// Note that this could be done all the time but shouldn't be necessary when the state is known and allows some user flexibility
	if ((state[frequencyStatus(evt)] == null) || (state[frequencyStatus(evt)] == "paused")) {
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
		if (state[frequencyStatus(evt)] == "on") log.debug "humidity (${currentHumidity}%) is below ${humiditySetpoint}% +/-${overshoot}%, turning off dehumidifier after running for ${timePassedRoundMinutes} minutes"
		else log.debug "humidity (${currentHumidity}%) is below ${humiditySetpoint}% +/-${overshoot}%, dehumidifier off"
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
			if (timeCycleRoundMinutes.toInteger() >= cycleTime.toInteger()) log.debug "humidity (${currentHumidity}%) is above ${humiditySetpoint}% +/-${overshoot}%, turning on dehumidifier"
			// Dehumidifier is off but hasn't been off long enough - only really necessary if dehumidifier does not have cycle protection built in (uncommon today)
			else log.debug "humidity (${currentHumidity}%) is above ${humiditySetpoint}% +/-${overshoot}%, waiting for a minimum cycle time of ${cycleTime} minutes"
		}
		else log.debug "humidity (${currentHumidity}%) is above ${humiditySetpoint}% +/-${overshoot}%, humidifier has been running for ${timePassedRoundMinutes} minutes"
		// If the minimum off cycle time has passed and no doors or windows are open, turn the dehumidifier on
		if ((timeCycleRoundMinutes.toInteger() >= cycleTime.toInteger()) && (!doorwindowSensors || !doorwindowSensors.latestValue("contact").contains("open"))) {
			// If the dehumidifier was off according to the state machine, begin to track when it was turned on
			if ((state[frequencyStatus(evt)] == "off") || (state[frequencyStatus(evt)] == null)) state[frequencyLastOn(evt)] = now()
			dehumidifier.on()			// Always, just in case it was off manually
			state[frequencyStatus(evt)] = "on"
		}
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

def doorwindowHandler(evt) {
	// If any of the open/close contact sensors open, pause the dehumidifier until they are all closed
	if (evt.value == "open") {
    	// One of the selected contact sensors was opened, start a timer to pause the dehumidifier after the window elapses
		log.debug "dehumidifier notified that a door or window was opened"
        def pauseTimer = 0
        if (openTime == null) pauseTimer = 0
        else pauseTimer = openTime.toInteger() * 60
    	runIn(pauseTimer, pauseDehumidification)
    }
    else if (evt.value == "closed") {
    	// One of the selected contact sensors was closed, check to see if all are closed and resume after any required restart delay
        if (!doorwindowSensors || !doorwindowSensors.latestValue("contact").contains("open")) {
	        log.debug "dehumdifier notified that all doors and windows were closed"
			unschedule(pauseDehumidification)
        	def resumeDelay = 0
            def timeCycleSeconds = 0
            def timeCycleRoundSeconds
            if (minCycleTime == null) resumeDelay = 0
            else resumeDelay = minCycleTime.toInteger() * 60
  			timeCycleSeconds = (now() - state[frequencyLastOff(evt)]) / 1000
            timeCycleRoundSeconds = Math.round(timeCycleSeconds.toDouble()) + (unit ?: "")
			resumeDelay = resumeDelay - timeCycleRoundSeconds.toInteger()
            if (resumeDelay < 0) resumeDelay = 0
            else log.debug "dehumidifier delaying ${resumeDelay} seconds before resuming"
        	runIn(resumeDelay, resumeDehumidification)
        }
    }
}

def pauseDehumidification() {
	unschedule(pauseDehumidification)
    unschedule(resumeDehumidification)
	unsubscribe(humiditySensor)
	log.debug "dehumidifier paused while doors and windows are open"
    if (state[frequencyStatus(evt)] == "on") {
		dehumidifier.off()
		state[frequencyLastOff(evt)] = now()
        state[frequencyStatus(evt)] = "paused"
		state[frequencyAlert(evt)] = 0		// Reset alert interval timer
    }
}

def resumeDehumidification() {
	unschedule(resumeDehumidification)
	unschedule(pauseDehumidification)
    if (state[frequencyStatus(evt)] == "paused") {
	    log.debug "dehumidifier resuming since doors and windows are closed"
    	state[frequencyLastOn(evt)] = now()
		dehumidifier.on()
        state[frequencyStatus(evt)] = "on"
		subscribe(humiditySensor, "humidity", humidityHandler)
    }
    else if (state[frequencyStatus(evt)] == "off") subscribe(humiditySensor, "humidity", humidityHandler)
}

private frequencyLastOn(evt) {
	"lastOnTimeStamp"
}

private frequencyLastOff(evt) {
	"lastOffTimeStamp"
}

private frequencyLastRH(evt) {
	"lastRH"
}

private frequencyStatus(evt) {
	"lastStatus"
}

private frequencyAlert(evt) {
	"lastAlertTimeStamp"
}

// END OF FILE