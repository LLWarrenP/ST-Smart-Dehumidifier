# ST-Smart-Dehumidifier
Smart Dehumidifier Control

# Summary
Controls a switched dehumidifier based on relative humidity from an external sensor

The dehumidifier is plugged into a smart switch which simply turns the power on and off depending on the relative humidity reading from the external humidity sensor of some sort.  Depending on the sensor it might not report changes very frequently or it could blast out a lot of noise (aka same readings).  Some of the battery powered sensors might have fewer reports in an effort to save battery while others may report lots of data.

This app depends on your dehumidifier to be able to run continuously (i.e. have a drain) and be able to auto-restart from power off. For those that are worried about turning on and off the power on the dehumidifier, don’t. It won’t shorten the life but there is a feature to ensure a minimum off cycle time if so desired.  The app also can optionally monitor doors and windows equipped with contact open/close sensors to avoid running the dehumidifier.

# Required Devices
1. A switch that is controllable by SmartThngs
2. A humidity sensor that reports to SmartThings
3. (optional) A door or window equipped with a contact open/close sensor

# Installation

The SmartApp is installed as a custom app in the SmartThings mobile app via the "My Apps" section.  THerefore, the app must
first be installed into your SmartThings SmartApps repository.

## Installation via GitHub Integration
1. Open SmartThings IDE in your web browser and log into your account.
2. Click on the "My SmartApps" section in the navigation bar.
3. Click on "Settings"
4. Click "Add New Repository"
5. Enter "LLWarrenP" as the namespace
6. Enter "ST-Smart-Dehumidifier" as the repository
7. Hit "Save"
8. Select "Update from Repo" and select "ST-Smart-Dehumidifier"
9. Select "smartapps/smart-dehumidifier.src/smart-dehumidifier.groovy"
10. Check "Publish" and hit "Execute Update"

## Manual Installation
1. Open SmartThings IDE in your web browser and log into your account.
2. Click on the "My SmartApps" section in the navigation bar.
3. On your Device Handlers page, click on the "+ Create New SmartApp" button on the right.
4. On the "New SmartApp" page, Select the Tab "From Code", Copy the "smart-dehumidifier.groovy" source code from GitHub and paste it into the IDE editor window.
5. Click the blue "Create" button at the bottom of the page. An IDE editor window containing the SmartApp code should now open.
6. Click the blue "Save" button above the editor window.
7. Click the "Publish" button next to it and select "For Me". You have now self-published your SmartApp.

# App Settings
Desired Humidity Setpoint: %RH that you want to target, defaults to 50% which is a good place to start

**Also allow an overshoot of +/-:** %RH that you want to allow above/below the setpoint before it turns on/off, defaults to 0% and can be up to 25%. 5% is a good place to start and if combined with the default setpoint of 50% would establish a 45%-55% band. Also helps to alleviate excessive switching since the %RH can fluctuate quickly.

**Require a minimum off cycle time of this many minutes:** A setting that allows you to not turn on the dehumidifier too soon. Most dehumidifiers have sensors in them to not run the compressor blindly but this ensures a minimum amount of off time. The default is 0 but 5 minutes is probably adequate if you need the feature. A dehumidifier that already has this protection built in will typically run the fan for a period of time when powering up (very typical unless the dehumidifier is old).

**Continuous Runtime Notifications:** Here you set the desired notifications for a push or push+SMS. It’s also where you set the runtime threshold “Maximum Runtime (Hours)” which defaults to 0 (no notifications ever) and up to 48 hours. This will depend on how long you expect the dehumidifier to cycle. I think something like 2 or 4 hours is probably a good place to start but it really depends on the dehumidifier and the space it runs in.

**Pause dehumidification while any of these doors or windows are open:** Allows the dehumidifier to pause its operation while any door or window equipped with a contact (open/close) sensor is open so as to not waste electricity dehumdifying the earth.  Also allows you to specify a maximum time for the door/window to be opened before pausing so as to not trigger during routine use.  Allows you to monitor any number of doors or windows and won't resume until all are closed again.

# App Logging
There is no “user” logging of routine operations in the Messages tab of the ST phone app but if you enable the alert (see above) you’ll get the push message which also will be logged there. The default push message is basically:

*Warning: Dehumidifier has run continuously for more than 2 hours. Humidity is 62%*

There is plenty of debug logging that the app does and you can see from the Groovy IDE. Messages include the current %RH changes that are reported to the app and if the dehumidifier is on, and for how long, or off and when it is turned off how long it ran for. A typical message (one of them anyway) is:

*humidity is above 45% +/-5%, humidifier has been running for 107 minutes*
