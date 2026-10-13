# Yandex MapKit / campus map

## Setup

1. Obtain a **MapKit Mobile SDK** API key, using the appropriate Yandex license.
2. Add `YANDEX_MAPKIT_API_KEY` to your local Gradle properties / environment.
   For CI, add a GitHub Actions repository secret with the same name.
   The dev build ID is `com.rteats.mpeineo.dev`, while stable uses
   `com.rteats.mpeineo`. Apply Yandex key restrictions where available.
3. The fifth tab contains MapKit Lite and local MPEI building markers.
   It does not request location permission or use routing/search/geocoding.
4. `config/mapkit.json` is the **remote operator kill switch**.
   To disable SDK usage for newly opened maps, change `enabled` to `false`
   and commit the change. No APK update is required.

## Why there is no device-side request quota

MapKit licensing commonly measures **unique map users (DAU/MAU)** rather
than tile requests. One device cannot determine how many distinct people
opened the map using the same key. The public kill switch is only a manual
admission gate; it cannot predict aggregate usage. Check usage statistics
in the Yandex Developer Dashboard, disable the map before the applicable
limit, or build a trusted server-side quota controller with aggregate
usage data for automatic account-wide admission decisions.

The admission check fails closed on network/config errors, so MapKit is
not initialized while access is uncertain. The built-in address list
continues to work. GitHub's raw file CDN can cache responses; this is
not an instantaneous or guaranteed stop for already-running SDK sessions.

API keys embedded in APKs are extractable. Do not commit them as plaintext
and restrict them to app identities/signatures where supported.

## Attribution, licensing, coordinates

The MapKit native map keeps the bottom Yandex branding/Open in Maps UI
unobstructed. The app About section links to Yandex Maps terms.
The local address list is derived from MPEI's official campus directory:
https://www.mpei.ru/AboutUniverse/Contact/Pages/Sheme.aspx
The corresponding map marker coordinates are verified against
address-level entries on Yandex Maps (not obtained via MapKit Geocoder).

MapKit Lite includes native libraries and will increase APK size.
