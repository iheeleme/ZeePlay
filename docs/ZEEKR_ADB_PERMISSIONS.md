# ZeePlay on the Zeekr 001: permissions to grant over ADB

For a Zeekr build **not** signed with the car's platform key. Package of the Zeekr release build:
`com.shihab.diplay.hudtest`.

ADB on the Zeekr works only from a laptop, on a specific USB port, with debugging opened. Run the commands
below from the laptop with `-d`.

## Grant

```sh
P=com.shihab.diplay.hudtest

# Runtime permissions (ZeePlay also asks for them on first start)
adb -d shell pm grant $P android.permission.RECORD_AUDIO          # microphone: calls, Siri
adb -d shell pm grant $P android.permission.ACCESS_FINE_LOCATION   # Wi-Fi details and location for the iPhone
adb -d shell pm grant $P android.permission.ACCESS_COARSE_LOCATION
adb -d shell pm grant $P android.permission.BLUETOOTH_CONNECT      # turn the car Bluetooth off during CarPlay
adb -d shell pm grant $P android.car.permission.CAR_SPEED          # speed to the iPhone
adb -d shell pm grant $P android.car.permission.CAR_ENERGY         # charge and range to the iPhone

# App-ops
adb -d shell appops set $P WRITE_SETTINGS allow             # start the car hotspot when the Aptiv service is not there
adb -d shell appops set $P SYSTEM_ALERT_WINDOW allow        # draw over other apps, start after boot
adb -d shell appops set $P GET_USAGE_STATS allow            # see which app is in front
adb -d shell appops set $P ACTIVATE_VPN allow               # no VPN consent dialog
adb -d shell appops set $P REQUEST_INSTALL_PACKAGES allow   # install ZeePlay updates
```

`CAR_SPEED` and `CAR_ENERGY` are `dangerous` in the 001's CarService manifest, so `pm grant` accepts them.

## Granted automatically

These are `normal` permissions; Android grants them at install:

- Wi-Fi and network: `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `ACCESS_NETWORK_STATE`,
  `CHANGE_WIFI_MULTICAST_STATE` (Bonjour), `INTERNET`;
- foreground services;
- `android.car.permission.CAR_INFO`, `android.car.permission.CAR_POWERTRAIN`.

## Car hotspot

**Turning it on.** ZeePlay first asks the system service `com.aptiv.wifi` to start the hotspot with the car's
saved configuration. That needs no permission. Without the service, or if it fails, ZeePlay uses Android
tethering, which needs `WRITE_SETTINGS` (above).

**Reading its name and password** needs the platform signature and cannot be granted over ADB, not even as root:

| permission | protection (Android 12L) | `pm grant` |
|---|---|---|
| `NETWORK_SETTINGS` | `signature` | no |
| `OVERRIDE_WIFI_CONFIG` | `signature\|privileged` | no |

`getSoftApConfiguration` is a hidden API as well. The built-in TV cast app shows the login and password the same
way (it is platform-signed) and does not share them.

Without the platform signature, enter the hotspot name and password once in **Settings → Connection → Built-in
car hotspot**. The car hotspot does not change afterwards, so this is a one-time step. Where to find them:

- **The TV cast app** shows the login and password next to its QR code.
- **Temporary ADB root.** The file is only read; nothing in the car changes:

  ```sh
  adb -d shell setprop service.adb.root 1
  adb -d usb
  adb -d wait-for-device
  adb -d shell "grep -E 'name=\"(SSID|WifiSsid|Passphrase)\"' /data/misc/apexdata/com.android.wifi/WifiConfigStoreSoftAp.xml"
  adb -d shell setprop service.adb.root 0
  adb -d usb
  ```

  The password is on the `Passphrase` line, the name on `SSID` or `WifiSsid`. A name stored as hex decodes
  with `echo <hex> | xxd -r -p`. If the file is elsewhere on a firmware:
  `adb -d shell "find /data/misc -name '*SoftAp*' 2>/dev/null"`.

ZeePlay never writes the hotspot configuration on a Zeekr; it only reads it.

## Without the platform signature

Checked on the car:

- the Zeekr media center (track and artwork on the car screens);
- the call card on the instrument cluster (answer, hang up, mute from the steering wheel).

Not yet checked without the signature: the turn arrows on the instrument cluster and the HUD through ECARX. If
they do not work, export a report (**Settings → Diagnostics**) and look for `Ecarx HUD: connect failed …`.
