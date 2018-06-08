# ST-Smart-Dehumidifier
Smart Dehumidifier Control

# Summary
Controls a switched dehumidifier based on relative humidity from an external sensor

The “tricky” part about this is the humidity sensor reporting. Depending on the sensor it might not report changes very frequently or it could blast out a lot of noise (aka same readings). The Halo is a bit chatty but it seems to only report a change in humidity when there actually is a change. So, fairly responsive but not annoying. I can only guess that some of the battery powered sensors, you might have fewer reports in an effort to save battery but that too could be an issue. So far for me the Halo has been working in this “unintended purpose”.

Almost goes without saying but this app depends on your dehumidifier to be able to run continuously (i.e. have a drain) and be able to restart from power off. For those that are worried about turning on and off the power on the dehumidifier, don’t. It won’t shorten the life. I’ve been controlling window A/Cs this way using solid-state relays for 20 years (up until recently, now with ST!) without issues.

# Required Devices
A switch that is controllable by SmartThngs

A humidity sensor that reports to SmartThings

# Installation

The SmartApp is installed as a custom app in the SmartThings mobile app via the "My Apps" section.  THerefore, the app must
first be installed into your SmartThings SmartApps repository.

## Installation via GitHub Integration
1. Open SmartThings IDE in your web browser and log into your account.
2. Click on the "My Device Handlers" section in the navigation bar.
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
2. Click on the "My Device Handlers" section in the navigation bar.
3. On your Device Handlers page, click on the "+ Create New Device Handler" button on the right.
4. On the "New Device Handler" page, Select the Tab "From Code" , Copy the "smart-dehumidifier.groovy" source code from GitHub and paste it into the IDE editor window.
5. Click the blue "Create" button at the bottom of the page. An IDE editor window containing device handler template should now open.
6. Click the blue "Save" button above the editor window.
7. Click the "Publish" button next to it and select "For Me". You have now self-published your Device Handler.

# App Settings
Desired Humidity Setpoint: %RH that you want to target, defaults to 50% which is a good place to start

Also allow an overshoot of +/-: %RH that you want to allow above/below the setpoint before it turns on/off, defaults to 0% and can be up to 25%. 5% is a good place to start and if combined with the default setpoint of 50% would establish a 45%-55% band. Also helps to alleviate excessive switching since the %RH can fluctuate quickly.

Require a minimum off cycle time of this many minutes: A setting that allows you to not turn on the dehumidifier too soon. Most dehumidifiers have sensors in them to not run the compressor blindly but this ensures a minimum amount of off time. The default is 0 but 5 minutes is probably adequate if you need the feature. A dehumidifier that has this protection built in will typically run the fan for a period of time when powering up (very typical unless the dehumidifier is old).

Continuous Runtime Notifications: Here you set the desired notifications for a push or push+SMS. It’s also where you set the runtime threshold “Maximum Runtime (Hours)” which defaults to 0 (no notifications ever) and up to 48 hours. This will depend on how long you expect the dehumidifier to cycle. I think something like 2 or 4 hours is probably a good place to start but it really depends on the dehumidifier and the space it runs in.

# App Logging
There is no “user” logging of routine operations in the Messages tab of the ST phone app but if you enable the alert (see above) you’ll get the push message which also will be logged there. The default push message is basically:

Warning: Dehumidifier has run continuously for more than 2 hours. Humidity is 62% 

There is plenty of debug logging that the app does and you can see from the Groovy IDE. Messages include the current %RH changes that are reported to the app and if the dehumidifier is on, and for how long, or off and when it is turned off how long it ran for. A typical message (one of them anyway) is:

humidity is above 45% +/-5%, humidifier has been running for 107 minutes 
