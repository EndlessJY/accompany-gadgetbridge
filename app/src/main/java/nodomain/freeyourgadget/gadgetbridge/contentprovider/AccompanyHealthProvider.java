/*
 * Copyright (C) 2026 Accompany contributors
 *
 * This file is part of Gadgetbridge.
 *
 * Gadgetbridge is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package nodomain.freeyourgadget.gadgetbridge.contentprovider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.ActivityAnalysis;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.StepAnalysis;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.SampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityAmount;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySession;
import nodomain.freeyourgadget.gadgetbridge.model.BloodPressureSample;
import nodomain.freeyourgadget.gadgetbridge.model.BodyEnergySample;
import nodomain.freeyourgadget.gadgetbridge.model.DailyTotals;
import nodomain.freeyourgadget.gadgetbridge.model.HeartRateSample;
import nodomain.freeyourgadget.gadgetbridge.model.HrvValueSample;
import nodomain.freeyourgadget.gadgetbridge.model.PaiSample;
import nodomain.freeyourgadget.gadgetbridge.model.RespiratoryRateSample;
import nodomain.freeyourgadget.gadgetbridge.model.Spo2Sample;
import nodomain.freeyourgadget.gadgetbridge.model.StressSample;
import nodomain.freeyourgadget.gadgetbridge.model.TemperatureSample;
import nodomain.freeyourgadget.gadgetbridge.model.TimeSample;
import nodomain.freeyourgadget.gadgetbridge.model.Vo2MaxSample;

/**
 * Fixed read-only bridge for a bounded health summary.
 *
 * Android performs signature-permission enforcement before this provider is
 * entered. The API deliberately offers no caller-controlled device or range.
 */
public class AccompanyHealthProvider extends ContentProvider {
    static final String AUTHORITY = "com.example.sender.gadgetbridge.health";
    static final Uri SUMMARY_URI = Uri.parse("content://" + AUTHORITY + "/summary");
    static final String COLUMN_SNAPSHOT_JSON = "snapshot_json";

    private static final long DAY_MS = 24L * 60L * 60L * 1000L;
    private static final long RECENT_MS = DAY_MS;
    private static final long VO2_WINDOW_MS = 30L * DAY_MS;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor query(
            @NonNull final Uri uri,
            @Nullable final String[] projection,
            @Nullable final String selection,
            @Nullable final String[] selectionArgs,
            @Nullable final String sortOrder
    ) {
        requireFixedRead(uri, projection, selection, selectionArgs, sortOrder);
        final MatrixCursor cursor = new MatrixCursor(new String[]{COLUMN_SNAPSHOT_JSON}, 1);
        cursor.addRow(new Object[]{readSnapshot().toString()});
        return cursor;
    }

    private static void requireFixedRead(
            final Uri uri,
            final String[] projection,
            final String selection,
            final String[] selectionArgs,
            final String sortOrder
    ) {
        if (!SUMMARY_URI.equals(uri)) {
            throw new IllegalArgumentException("Unsupported health bridge URI");
        }
        if (projection != null || selection != null || selectionArgs != null || sortOrder != null) {
            throw new IllegalArgumentException("Health bridge does not accept query parameters");
        }
    }

    private JSONObject readSnapshot() {
        final long now = System.currentTimeMillis();
        final AccompanyHealthSnapshot output = new AccompanyHealthSnapshot(now);
        final GBDevice device = selectDevice();
        if (device == null) {
            return output.build();
        }

        try (DBHandler db = GBApplication.acquireDbReadOnly()) {
            populate(output, device, db, now);
        } catch (final Exception ignored) {
            // Do not log health values, device identity, database paths or query details.
            return AccompanyHealthSnapshot.error(now);
        }
        return output.build();
    }

    @Nullable
    private static GBDevice selectDevice() {
        final List<GBDevice> selected = GBApplication.app().getDeviceManager().getSelectedDevices();
        final List<GBDevice> candidates;
        if (!selected.isEmpty()) {
            candidates = selected;
        } else {
            final List<GBDevice> stored = GBApplication.app().getDeviceManager().getDevices();
            if (stored.size() != 1) return null;
            candidates = stored;
        }
        return candidates.stream()
                .filter(device -> device.getDeviceCoordinator().supportsActivityTracking(device))
                .sorted(Comparator.comparing(GBDevice::isInitialized).reversed())
                .findFirst()
                .orElse(null);
    }

    private static void populate(
            final AccompanyHealthSnapshot output,
            final GBDevice device,
            final DBHandler db,
            final long now
    ) {
        final DeviceCoordinator coordinator = device.getDeviceCoordinator();
        final Calendar day = Calendar.getInstance();
        day.setTimeInMillis(now);
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        final long todayStart = day.getTimeInMillis();

        final SampleProvider<? extends ActivitySample> activityProvider = coordinator.getSampleProvider(
                device,
                db.getDaoSession()
        );
        final List<? extends ActivitySample> todaySamples = activityProvider == null
                ? Collections.emptyList()
                : activityProvider.getAllActivitySamples((int) (todayStart / 1000L), (int) (now / 1000L));

        if (!todaySamples.isEmpty()) {
            final DailyTotals totals = DailyTotals.getDailyTotalsForDevice(device, day, db);
            if (coordinator.supportsStepCounter(device)) {
                output.putLong("stepsToday", totals.getSteps(), 0L, 500_000L);
            }
            if (coordinator.supportsActivityDistance(device)) {
                output.putDouble("distanceMetersToday", totals.getDistance() / 100.0d, 0.0d, 1_000_000.0d);
            }
            if (coordinator.supportsActiveCalories(device)) {
                final double activeKcal = totals.getActiveCalories() / 1000.0d;
                final double restingKcal = totals.getRestingCalories();
                output.putDouble("activeCaloriesKcalToday", activeKcal, 0.0d, 100_000.0d)
                        .putDouble("restingCaloriesKcalToday", restingKcal, 0.0d, 100_000.0d)
                        .putDouble("totalCaloriesKcalToday", activeKcal + restingKcal, 0.0d, 200_000.0d);
            }
            populateActiveMinutes(output, todaySamples);
        }
        if (activityProvider != null) {
            final List<? extends ActivitySample> recentSamples = activityProvider.getAllActivitySamples(
                    (int) ((now - RECENT_MS) / 1000L),
                    (int) (now / 1000L)
            );
            populateLatestHeartRate(output, recentSamples, now);
        }

        if (coordinator.supportsSleepMeasurement(device) && activityProvider != null) {
            final List<? extends ActivitySample> sleepWindow = activityProvider.getAllActivitySamples(
                    (int) ((todayStart - 12L * 60L * 60L * 1000L) / 1000L),
                    (int) (now / 1000L)
            );
            populateSleep(output, sleepWindow);
        }
        if (coordinator.supportsHeartRateRestingMeasurement(device)) {
            final HeartRateSample sample = latestWithin(
                    coordinator.getHeartRateRestingSampleProvider(device, db.getDaoSession()),
                    todayStart,
                    now
            );
            if (sample != null) {
                output.putLong("restingHeartRateBpm", sample.getHeartRate(), 20L, 300L)
                        .putTimestamp("restingHeartRateAt", sample.getTimestamp(), now);
            }
        }
        if (coordinator.supportsSpo2(device)) {
            final Spo2Sample sample = latestWithin(
                    coordinator.getSpo2SampleProvider(device, db.getDaoSession()),
                    now - RECENT_MS,
                    now
            );
            if (sample != null) {
                output.putDouble("latestOxygenSaturationPercent", sample.getSpo2(), 50.0d, 100.0d)
                        .putTimestamp("latestOxygenSaturationAt", sample.getTimestamp(), now);
            }
        }
        if (coordinator.supportsStressMeasurement(device)) {
            populateStress(
                    output,
                    coordinator.getStressSampleProvider(device, db.getDaoSession()),
                    coordinator.getStressRanges(),
                    todayStart,
                    now
            );
        }
        if (coordinator.supportsBodyEnergy(device)) {
            final BodyEnergySample sample = latestWithin(
                    coordinator.getBodyEnergySampleProvider(device, db.getDaoSession()),
                    now - RECENT_MS,
                    now
            );
            if (sample != null) {
                output.putLong("latestBodyEnergy", sample.getEnergy(), 0L, 100L)
                        .putTimestamp("latestBodyEnergyAt", sample.getTimestamp(), now);
            }
        }
        if (coordinator.supportsPai(device)) {
            final PaiSample sample = latestWithin(
                    coordinator.getPaiSampleProvider(device, db.getDaoSession()),
                    todayStart,
                    now
            );
            if (sample != null) {
                output.putDouble("vitalityScoreToday", sample.getPaiToday(), 0.0d, 1_000.0d)
                        .putDouble("vitalityScoreTotal", sample.getPaiTotal(), 0.0d, 10_000.0d)
                        .putTimestamp("vitalityScoreAt", sample.getTimestamp(), now);
            }
        }
        if (coordinator.supportsTemperatureMeasurement(device)) {
            final TemperatureSample sample = latestWithin(
                    coordinator.getTemperatureSampleProvider(device, db.getDaoSession()),
                    now - RECENT_MS,
                    now
            );
            if (sample != null && sample.getTemperatureType() == TemperatureSample.TYPE_SKIN) {
                output.putDouble("latestSkinTemperatureCelsius", sample.getTemperature(), 0.0d, 60.0d)
                        .putTimestamp("latestSkinTemperatureAt", sample.getTimestamp(), now);
            }
        }
        if (coordinator.supportsHrvMeasurement(device)) {
            final HrvValueSample sample = latestWithin(
                    coordinator.getHrvValueSampleProvider(device, db.getDaoSession()),
                    now - RECENT_MS,
                    now
            );
            if (sample != null) {
                output.putLong("latestHrvMs", sample.getValue(), 1L, 1_000L)
                        .putTimestamp("latestHrvAt", sample.getTimestamp(), now);
            }
        }
        if (coordinator.supportsRespiratoryRate(device)) {
            final RespiratoryRateSample sample = latestWithin(
                    coordinator.getRespiratoryRateSampleProvider(device, db.getDaoSession()),
                    now - RECENT_MS,
                    now
            );
            if (sample != null) {
                output.putDouble("latestRespiratoryRate", sample.getRespiratoryRate(), 1.0d, 100.0d)
                        .putTimestamp("latestRespiratoryRateAt", sample.getTimestamp(), now);
            }
        }
        if (coordinator.supportsVO2Max(device)) {
            final Vo2MaxSample sample = latestWithin(
                    coordinator.getVo2MaxSampleProvider(device, db.getDaoSession()),
                    now - VO2_WINDOW_MS,
                    now
            );
            if (sample != null) {
                output.putDouble("latestVo2Max", sample.getValue(), 1.0d, 100.0d)
                        .putTimestamp("latestVo2MaxAt", sample.getTimestamp(), now);
            }
        }
        if (coordinator.supportsBloodPressureMeasurement(device)) {
            final BloodPressureSample sample = latestWithin(
                    coordinator.getBloodPressureSampleProvider(device, db.getDaoSession()),
                    now - RECENT_MS,
                    now
            );
            if (sample != null) {
                output.putLong("latestSystolicPressure", sample.getBpSystolic(), 20L, 300L)
                        .putLong("latestDiastolicPressure", sample.getBpDiastolic(), 20L, 200L)
                        .putTimestamp("latestBloodPressureAt", sample.getTimestamp(), now);
            }
        }
    }

    private static void populateActiveMinutes(
            final AccompanyHealthSnapshot output,
            final List<? extends ActivitySample> samples
    ) {
        final List<ActivitySession> sessions = new StepAnalysis().calculateStepSessions(
                samples,
                Collections.emptyList()
        );
        final long activeMs = sessions.stream()
                .mapToLong(session -> Math.max(0L, session.getEndTime().getTime() - session.getStartTime().getTime()))
                .sum();
        output.putLong("activeMinutesToday", activeMs / 60_000L, 0L, 1_440L);
    }

    private static void populateLatestHeartRate(
            final AccompanyHealthSnapshot output,
            final List<? extends ActivitySample> samples,
            final long now
    ) {
        ActivitySample latest = null;
        for (final ActivitySample sample : samples) {
            if (sample.getHeartRate() >= 20 && sample.getHeartRate() <= 300 &&
                    (latest == null || sample.getTimestamp() > latest.getTimestamp())) {
                latest = sample;
            }
        }
        if (latest != null) {
            output.putLong("latestHeartRateBpm", latest.getHeartRate(), 20L, 300L)
                    .putTimestamp("latestHeartRateAt", latest.getTimestamp() * 1000L, now);
        }
    }

    private static void populateSleep(
            final AccompanyHealthSnapshot output,
            final List<? extends ActivitySample> samples
    ) {
        long light = 0L;
        long deep = 0L;
        long rem = 0L;
        long awake = 0L;
        for (final ActivityAmount amount : new ActivityAnalysis().calculateActivityAmounts(samples).getAmounts()) {
            final long minutes = amount.getTotalSeconds() / 60L;
            if (amount.getActivityKind() == ActivityKind.LIGHT_SLEEP) light += minutes;
            if (amount.getActivityKind() == ActivityKind.DEEP_SLEEP) deep += minutes;
            if (amount.getActivityKind() == ActivityKind.REM_SLEEP) rem += minutes;
            if (amount.getActivityKind() == ActivityKind.AWAKE_SLEEP) awake += minutes;
        }
        if (light + deep + rem + awake > 0L) {
            output.putLong("sleepMinutes", light + deep + rem, 0L, 2_160L)
                    .putLong("sleepLightMinutes", light, 0L, 2_160L)
                    .putLong("sleepDeepMinutes", deep, 0L, 2_160L)
                    .putLong("sleepRemMinutes", rem, 0L, 2_160L)
                    .putLong("sleepAwakeMinutes", awake, 0L, 2_160L);
        }
    }

    private static void populateStress(
            final AccompanyHealthSnapshot output,
            final TimeSampleProvider<? extends StressSample> provider,
            final int[] ranges,
            final long from,
            final long now
    ) {
        if (provider == null || ranges == null || ranges.length < 4) return;
        final List<? extends StressSample> samples = provider.getAllSamples(from, now);
        if (samples.isEmpty()) return;

        long sum = 0L;
        final long[] categoryMinutes = new long[4];
        StressSample latest = null;
        for (final StressSample sample : samples) {
            final int value = sample.getStress();
            if (value < 1 || value > 100) continue;
            sum += value;
            if (latest == null || sample.getTimestamp() > latest.getTimestamp()) latest = sample;
            if (value >= ranges[3]) categoryMinutes[3]++;
            else if (value >= ranges[2]) categoryMinutes[2]++;
            else if (value >= ranges[1]) categoryMinutes[1]++;
            else if (value >= ranges[0]) categoryMinutes[0]++;
        }
        final long validCount = categoryMinutes[0] + categoryMinutes[1] + categoryMinutes[2] + categoryMinutes[3];
        if (validCount == 0L || latest == null) return;
        output.putDouble("stressAverageToday", sum / (double) validCount, 1.0d, 100.0d)
                .putLong("stressRelaxedMinutes", categoryMinutes[0], 0L, 1_440L)
                .putLong("stressMildMinutes", categoryMinutes[1], 0L, 1_440L)
                .putLong("stressModerateMinutes", categoryMinutes[2], 0L, 1_440L)
                .putLong("stressHighMinutes", categoryMinutes[3], 0L, 1_440L)
                .putLong("latestStressScore", latest.getStress(), 1L, 100L)
                .putTimestamp("latestStressAt", latest.getTimestamp(), now);
    }

    @Nullable
    private static <T extends TimeSample> T latestWithin(
            final TimeSampleProvider<? extends T> provider,
            final long from,
            final long to
    ) {
        if (provider == null) return null;
        final List<? extends T> samples = provider.getAllSamples(from, to);
        T latest = null;
        for (final T sample : samples) {
            if (latest == null || sample.getTimestamp() > latest.getTimestamp()) latest = sample;
        }
        return latest;
    }

    @Nullable
    @Override
    public String getType(@NonNull final Uri uri) {
        return SUMMARY_URI.equals(uri) ? "application/vnd.accompany.health-summary+json" : null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull final Uri uri, @Nullable final ContentValues values) {
        throw new UnsupportedOperationException("Health bridge is read-only");
    }

    @Override
    public int delete(@NonNull final Uri uri, @Nullable final String selection, @Nullable final String[] selectionArgs) {
        throw new UnsupportedOperationException("Health bridge is read-only");
    }

    @Override
    public int update(
            @NonNull final Uri uri,
            @Nullable final ContentValues values,
            @Nullable final String selection,
            @Nullable final String[] selectionArgs
    ) {
        throw new UnsupportedOperationException("Health bridge is read-only");
    }
}
