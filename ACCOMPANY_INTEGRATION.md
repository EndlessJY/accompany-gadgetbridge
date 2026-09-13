# Accompany integration

This repository preserves Gadgetbridge's upstream history while maintaining a
small Accompany-specific compatibility layer. It is not the official
Gadgetbridge repository and no change from this repository is pushed to the
upstream project.

## Source baseline

- Upstream release: `0.93.0`
- Upstream commit: `a09e037374d0013ffc31978fc1d1ada2943280e4`
- Compatible build identity: `0.93.0-accompany.12`
- Android version code: `264`
- License: GNU Affero General Public License version 3 or later

The `upstream` remote points to the official Codeberg repository for fetches.
The writable `origin` remote is the independent GitHub repository
`EndlessJY/accompany-gadgetbridge`. Maintainers must never push Accompany
branches or tags to the official Codeberg remote.

## Functional delta

The Accompany variant adds three bounded Android bridge paths:

- Primary Provider URI:
  `content://nodomain.freeyourgadget.gadgetbridge.accompany.health/summary`
- Legacy Provider URI:
  `content://com.example.sender.gadgetbridge.health/summary`
- Explicit AIDL service returning the same bounded summary and sync status
- Explicit summary broadcast to the fixed Accompany sender receiver after
  Gadgetbridge's central new-data event
- Permission: `com.example.sender.permission.READ_GADGETBRIDGE_HEALTH`, an
  Android signature-level permission declared by the Accompany sender
- Provider result: one `snapshot_json` column containing a bounded JSON summary
- Sync method: `request_sync`, returning only a bounded status string

Provider query/call and AIDL methods enforce the signature permission before
processing. The explicit broadcast is sent with that permission to a receiver
that also requires it. This method-level enforcement is deliberate: on the
target ColorOS device, attaching the permission only to the exported component
made background component resolution fail before Gadgetbridge was entered.

The read paths accept no caller-controlled projection, selection, sort order,
device address or time range. Provider insert, update and delete operations are
not supported. The bridge exposes no workout routes, raw sample history,
account data, notifications, contacts, pairing secrets or device-control
configuration.

The fixed sync method is protected by the same signature permission, accepts
no argument or extras, and can only request Gadgetbridge's normal `TYPE_SYNC`
operation for the selected connected, supported and idle wearable. Accepted
starts are limited to once per two minutes. The result contains only
`started`, `busy`, `not_connected`, `unsupported` or `rate_limited`; it never
returns health data, sample times or wearable identity.

Gadgetbridge's foreground device service invokes that same gated normal sync
every 15 minutes while a supported wearable is connected and five seconds
after the wearable reconnects. It never forces an intentionally disconnected
wearable to connect. After Gadgetbridge announces that newly synchronized data
was written, summary publication is debounced by ten seconds and sent to the
fixed sender receiver. Repeated reads or deliveries do not alter source
freshness.

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

`readAt` is only the time at which the provider query ran. A ready summary also
includes `dataUpdatedAt` when an underlying source timestamp is available. It
is the newest valid wearable-record timestamp represented by the returned
aggregate or latest-value fields, so querying unchanged records does not make
them appear fresh. If no contributing source timestamp is available,
`dataUpdatedAt` is omitted rather than copied from `readAt`.

For Xiaomi Smart Band 9 Pro, a normal recorded-data sync also takes one bounded realtime
snapshot. The band can expose its current day total before it finalizes the matching activity
file. Gadgetbridge overlays only the difference that is still missing from the detailed database,
keeps the overlay for at most 20 minutes, and never persists it as a second activity record. This
keeps Gadgetbridge and Accompany current without double-counting when the detailed file arrives.

## Validation

Use Java 21 and the Android SDK, then run:

```bash
./gradlew \
  :app:testBanglejsDebugUnitTest \
  --tests 'nodomain.freeyourgadget.gadgetbridge.contentprovider.AccompanyHealth*Test' \
  :app:compileBanglejsDebugJavaWithJavac
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
