# Accompany integration

This repository preserves Gadgetbridge's upstream history while maintaining a
small Accompany-specific compatibility layer. It is not the official
Gadgetbridge repository and no change from this repository is pushed to the
upstream project.

## Source baseline

- Upstream release: `0.93.0`
- Upstream commit: `a09e037374d0013ffc31978fc1d1ada2943280e4`
- Compatible build identity: `0.93.0-accompany.1`
- Android version code: `253`
- License: GNU Affero General Public License version 3 or later

The `upstream` remote points to the official Codeberg repository for fetches.
The writable `origin` remote is the independent GitHub repository
`EndlessJY/accompany-gadgetbridge`. Maintainers must never push Accompany
branches or tags to the official Codeberg remote.

## Functional delta

The Accompany variant adds one read-only Android `ContentProvider`:

- URI: `content://com.example.sender.gadgetbridge.health/summary`
- Read permission: `com.example.sender.permission.READ_GADGETBRIDGE_HEALTH`
- Permission protection: Android signature level, declared by the Accompany
  sender application
- Result: one `snapshot_json` column containing a bounded JSON summary

The provider accepts no caller-controlled projection, selection, sort order,
device address or time range. Insert, update and delete operations are not
supported. It exposes no workout routes, raw sample history, account data,
notifications, contacts, pairing secrets or device-control configuration.

The provider first uses Gadgetbridge's selected activity-capable wearable. If
none is selected and exactly one stored activity-capable wearable exists, that
sole device is used so restored data remains readable while disconnected. More
than one ambiguous stored device fails closed; summaries from several devices
are never merged.

Supported optional summary values include daily activity and calories, sleep
stages, latest and resting heart rate, blood oxygen, wearable stress,
vitality/PAI, body energy, wrist temperature, HRV, respiratory rate, VO2 max
and blood pressure. Missing or invalid samples are omitted. Wearable stress is
not interpreted as mood or a medical conclusion.

## Validation

Use Java 21 and the Android SDK, then run:

```bash
./gradlew \
  :app:testMainlineDebugUnitTest \
  --tests nodomain.freeyourgadget.gadgetbridge.contentprovider.AccompanyHealthSnapshotTest \
  :app:compileMainlineDebugJavaWithJavac
```

For a local release candidate, build `:app:assembleMainlineRelease`. The output
is unsigned by default. Signing credentials and production certificates are
private Accompany release inputs and must never be stored in this repository.

## Upstream updates

1. Fetch the official Codeberg `upstream` remote without pushing to it.
2. Review upstream database, coordinator and device-capability changes before
   rebasing the Accompany branch.
3. Re-run the focused provider test and mainline Java compilation.
4. Revalidate the exact provider permission, package/version identity, backup
   migration and a connected plus disconnected wearable on a test device.
5. Assign a new distinguishable `-accompany.N` version before distribution.

Modified APK distribution must keep the corresponding source available under
the repository's AGPLv3-or-later terms.
