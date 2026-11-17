# Zeekr 001 — windshield HUD, cluster, music and wireless changes

Summary of the Zeekr 001 adaptation carried on top of the public source
(`main`). Grouped by area; the short hashes point at the commit that landed each
item for traceability. Everything here is clean-room: implemented from Android
platform facts and observed behaviour, with no code copied from proprietary
receivers.

## Instrument cluster (centre of the dashboard)

The existing, working path — the CarPlay map on the centre of the instrument
cluster. Left untouched by the HUD work below.

- Show the CarPlay map on the instrument cluster (`053499bf`).

## Windshield head-up display (HUD)

CarPlay navigation on the 001's windshield HUD, drawn from the secondary
CarPlay display streams the iPhone offers.

- Port the windshield HUD subsystem from DiPlay: map and turn-card streams plus
  the AR-HUD route feed (`b5a9ea9d`).
- Expose the Apple turn card on the windshield (`120f8530`).
- Drive HUD and cluster turn-by-turn via the ECARX adaptapi, announce the
  navigation-guidance status to the HUD/ZID, and push turns over ICarFunction
  (`aa519a9b`, `d43a63d3`, `fd425c1d`).
- Track HUD guidance on the iPhone's `modesChanged` command and follow iAP2
  route guidance as well, so the HUD opens whenever the phone is actually
  guiding (`49a39c61`, `a8759541`).
- Render the stream aspect-correct and placeable on an otherwise black panel,
  with a dark-HUD option, and keep size and position per mode (turn card vs
  map) (`9d48bc35`, `45d76d6f`).
- Add a third content mode — a compact arrow drawn by ZeePlay into the small
  free square — and advertise the HUD map at the size it is actually shown, so
  the iPhone renders a small map 1:1 instead of a full-size map scaled down
  (`60193046`).
- Stop flooding the iPhone with a HUD `stopUI` every 2 s (`4342503b`).
- Default placement matches the stock Zeekr navigation (`com.zeekr.automap`), which draws its HUD map in a 753×188 band at (135, 193) of the 1024×576 panel; every mode starts inside that band, and one button resets to it.

## Music (wireless CarPlay audio)

Wireless CarPlay audio kept playing on the 001 instead of pausing every few
seconds.

- Turn the car's Bluetooth off during wireless CarPlay: the car's A2DP sink has
  no audio focus while audio goes over CarPlay, so it was sending the iPhone a
  pause roughly every four seconds (`5cecd51b`).
- Hold the media-center focus on a steady tick so CarPlay audio keeps routing,
  without overriding a source the driver selected (`9236f38e`, `79fb36ab`,
  `134c7694`).
- Report the real track playout position in the audio feedback and anchor the
  buffered audio to real playout, so the media stream stops tearing down
  (`41156f1e`, `ce197199`).

## Wireless connection / car hotspot

- Read the car hotspot credentials directly on Zeekr (platform-signed builds),
  and fall back to manual entry when the build is not platform-signed on that
  car — no ADB, and the car hotspot is never written to (`fd3c9be0`). See
  [zeekr-wireless-hotspot.md](zeekr-wireless-hotspot.md) for the mechanism.

## Zeekr edition defaults

Only in the `diplay.edition=zeekr` build (standard/BYD defaults are unchanged); settings a driver already
saved are kept.

- HUD content: compact arrow; cluster map, media integration, HUD and dark HUD on.
- App theme follows the car's day/night (Auto); CarPlay itself already follows the system by default.
- Car Bluetooth off during wireless CarPlay.
- The car's status bar and dock stay visible over CarPlay.
- Battery and wheel speed / gear sent to the iPhone.
- Car hotspot read automatically before connecting and when the app returns (direct read, platform-signed
  builds); the car's hotspot is never written to on Zeekr, and without the signature the hand-entered
  details are used.

## Diagnostics and build

- Carry HUD, media and the modes/resources layout into the diagnostic report
  (`a8759541`, `ccf0892a`).
- Notes on reading the head-unit hotspot credentials; pin the Gradle daemon JVM
  vendor to Adoptium (`fb59fd3d`).

---

*Clean-room notes for interoperability. Vehicle acceptance is not claimed;
capability depends on the specific model, firmware, Android user and the
signature the build is installed with.*
