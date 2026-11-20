package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.example.data.Place
import com.example.data.ScriptConstraintCatalog
import com.example.data.ScriptRepository
import com.example.data.ShortcutScript
import com.example.service.bridge.BridgeController
import kotlin.math.max

/**
 * Wake-up source and state for `at_place`. "Battery friendly" places use Android's proximity alert
 * (the platform's own geofence, no Play services); "Fast (GPS)" places get fixes from
 * [PlaceTrackingService]. Either way KeySnap decides the side itself with [side], because the
 * platform geofence calls a vague fix (a 1–2 km cell estimate) "inside": an alert is only a cue to
 * take a fresh fix, and no fix means no decision. Only "in"/"out" and when it was last checked are
 * kept per place (the circle the user chose is the key); the phone's position is never stored,
 * logged or sent anywhere.
 */
object PlaceCondition {
    internal const val EXTRA_KEY = "com.example.service.PLACE_KEY"
    internal const val EXTRA_ATTEMPT = "com.example.service.PLACE_ATTEMPT"
    internal const val ACTION_RECHECK = "com.example.service.PLACE_RECHECK"
    private const val PREFS = "place_condition"

    /** Battery-friendly places with a proximity alert; an alert or recheck for any other key is stale. */
    private const val REGISTERED = "registered"
    private const val CHECKED_SUFFIX = "#checked"
    private const val REARMED_SUFFIX = "#rearmed"

    /** Set while a referenced place is not watched (paused, script off, no permission); see [commit]. */
    private const val STALE_SUFFIX = "#stale"

    // ponytail: a cached fix only seeds a place's first state; older than this it says nothing.
    private const val SEED_MAX_AGE_MS = 15 * 60_000L
    private const val FIX_TIMEOUT_MS = 20_000L

    /** A vague or missing fix is re-checked this often, this many times, then left alone. */
    private const val RECHECK_DELAY_MS = 2 * 60_000L
    internal const val MAX_RECHECKS = 3

    /** Re-arming an alert makes Android ask again at once; at most this often per place. */
    private const val REARM_MIN_GAP_MS = 30 * 60_000L

    enum class Status { NO_PERMISSION, NO_BACKGROUND, LOCATION_OFF, UNKNOWN, INSIDE, OUTSIDE }

    /** What the place row shows: the last decided side and when it was last checked (epoch ms, 0 = never). */
    data class Reading(val status: Status, val checkedAtMs: Long)

    /** Fast mode: a side one fix suggested, waiting for the next fix to agree. Process lifetime only. */
    private val pendingFast = mutableMapOf<String, Boolean>()

    /**
     * Alerts added in this process. A new process re-adds them all, since a reboot, an update or a
     * force stop drops them, unless one of them started it ([onAlert]); a warm one does not, because
     * a re-add resets Android's side to unknown.
     */
    private val armed = mutableSetOf<String>()

    /** Battery places whose first side this process already asked a fresh fix for; one ask each while watched. */
    private val seedAsked = mutableSetOf<String>()

    /** In, out, or null while unknown (no permission yet, or nothing has said either way). */
    fun knownInside(context: Context, params: Map<String, String>): Boolean? {
        if (!hasFineLocation(context)) return null
        val place = Place.parse(params) ?: return null
        return storedSide(context, place.key)
    }

    fun reading(context: Context, params: Map<String, String>): Reading {
        val place = Place.parse(params)
        val checked = place?.let { prefs(context).getLong(it.key + CHECKED_SUFFIX, 0L) } ?: 0L
        val status = when {
            !hasFineLocation(context) -> Status.NO_PERMISSION
            // Without "Allow all the time" nothing is checked while KeySnap is closed.
            !hasBackgroundLocation(context) -> Status.NO_BACKGROUND
            context.getSystemService(LocationManager::class.java)?.isLocationEnabled == false -> Status.LOCATION_OFF
            place == null -> Status.UNKNOWN
            else -> when (storedSide(context, place.key)) {
                true -> Status.INSIDE
                false -> Status.OUTSIDE
                null -> Status.UNKNOWN
            }
        }
        return Reading(status, checked)
    }

    /**
     * Keeps one proximity alert per "Battery friendly" place an enabled shortcut uses, runs
     * [PlaceTrackingService] while any "Fast (GPS)" place is in use, and drops the rest. Runs on
     * every script change, on pause or resume of everything, on app resume (a permission may have
     * changed) and at boot. Paused, nothing is watched and no notification stays up, but the sides
     * are kept (a side is forgotten only with its circle) and marked stale, so the first side decided
     * after resuming is recorded silently: resuming is never an arrival or a departure.
     */
    fun sync(context: Context, scripts: List<ShortcutScript>) {
        val locations = context.getSystemService(LocationManager::class.java) ?: return
        val running = ScriptRepository.getInstance(context).globallyEnabled.value
        val permitted = hasFineLocation(context)
        val inUse = if (running && permitted) placesInUse(scripts) else emptyList()
        val fast = inUse.filter { it.second }.map { it.first }.distinctBy { it.key }
        // The same circle in both modes is simply watched fast.
        val battery = inUse.filter { !it.second }.map { it.first }.distinctBy { it.key }.filter { p -> fast.none { it.key == p.key } }
        val watched = (fast + battery).map { it.key }.toSet()
        val referenced = scripts.flatMap { it.constraints }.filter { it.type == ScriptConstraintCatalog.AT_PLACE }
            .mapNotNull { Place.parse(it.params)?.key }.toSet()
        val prefs = prefs(context)
        val edit = prefs.edit()
        registered(context).filter { key -> battery.none { it.key == key } }.forEach { key ->
            locations.removeProximityAlert(alertIntent(context, key))
            armed -= key
            cancelRecheck(context, key)
        }
        prefs.all.keys.map { it.substringBefore('#') }.filter { it != REGISTERED && it !in referenced }.toSet().forEach { key ->
            edit.remove(key).remove(key + CHECKED_SUFFIX).remove(key + REARMED_SUFFIX).remove(key + STALE_SUFFIX)
        }
        (referenced - watched).forEach { edit.putBoolean(it + STALE_SUFFIX, true) }
        edit.putStringSet(REGISTERED, battery.map { it.key }.toSet()).apply()
        // Whatever was half-confirmed belongs to the previous set of places.
        pendingFast.clear()
        seedAsked.retainAll(watched)
        val tracker = Intent(context, PlaceTrackingService::class.java)
        if (!permitted) {
            context.stopService(tracker)
            // The bridge must stop believing the last side too: without permission it is unknown.
            publishForKeys(context, scripts)
            return
        }
        (fast + battery).filter { storedSide(context, it.key) == null || isStale(context, it.key) }.forEach { place ->
            val cached = seedState(locations, place)
            when {
                cached != null -> commit(context, place.key, cached)
                // Nothing will say where a Battery place is until the phone crosses it; ask once.
                place in battery && seedAsked.add(place.key) -> freshFix(context) { fix -> onSeedFix(context, place, fix) }
            }
        }
        battery.filter { it.key !in armed }.forEach { place ->
            if (!addAlert(locations, place, alertIntent(context, place.key))) return@forEach
            armed += place.key
            // A fresh alert starts from "unknown", from which Android never reports a departure.
            if (storedSide(context, place.key) == true) scheduleRecheck(context, place.key, null, 1)
        }
        if (fast.isEmpty()) {
            context.stopService(tracker)
        } else {
            // Refused when started from the background without an exemption; the next resume retries.
            runCatching { context.startForegroundService(tracker) }
        }
    }

    /** Every place an enabled shortcut (automation or key) uses, paired with whether it asked for "Fast (GPS)". */
    private fun placesInUse(scripts: List<ShortcutScript>): List<Pair<Place, Boolean>> =
        scripts.filter { it.enabled }.flatMap { it.constraints }
            .filter { it.type == ScriptConstraintCatalog.AT_PLACE }
            .mapNotNull { c -> Place.parse(c.params)?.let { it to (c.params["mode"] == Place.MODE_FAST) } }
            .distinct()

    internal fun fastWatchNeeded(context: Context): Boolean =
        hasFineLocation(context) && ScriptRepository.getInstance(context).globallyEnabled.value && fastPlaces(context).isNotEmpty()

    internal fun fastPlaces(context: Context): List<Place> =
        placesInUse(ScriptRepository.getInstance(context).scripts.value).filter { it.second }.map { it.first }.distinctBy { it.key }

    /** Params of every "Fast (GPS)" place an enabled shortcut uses, in script order. */
    internal fun fastParams(context: Context): List<Map<String, String>> =
        ScriptRepository.getInstance(context).scripts.value.filter { it.enabled }.flatMap { it.constraints }
            .filter { it.type == ScriptConstraintCatalog.AT_PLACE && it.params["mode"] == Place.MODE_FAST }
            .map { it.params }

    /**
     * Inside, outside, or null for "keep what we had". Inside needs the fix within the circle and
     * no vaguer than the circle; outside needs it beyond the circle plus [exitMargin], which is the
     * band that stops a phone parked on the edge from flapping in and out. A fix whose error alone
     * could not reach back into that band is outside however vague it is.
     */
    internal fun side(distance: Float, accuracy: Float, radius: Int): Boolean? {
        val out = radius + exitMargin(radius)
        return when {
            distance - accuracy > out -> false
            accuracy > radius -> null
            distance <= radius -> true
            distance > out -> false
            else -> null
        }
    }

    internal fun exitMargin(radius: Int): Float = max(50f, radius * 0.25f)

    /** A fix that does not say how good it is says nothing. */
    private fun verdictFor(fix: Location, place: Place): Boolean? =
        if (fix.hasAccuracy()) side(distanceTo(fix, place), fix.accuracy, place.radius) else null

    /** Fast mode's fix rate from how far the nearest circle's edge is: slow when far, every 30 s when close. */
    internal fun fastIntervalMs(distanceToNearestEdge: Float): Long = when {
        distanceToNearestEdge > 5_000f -> 5 * 60_000L
        distanceToNearestEdge > 1_000f -> 2 * 60_000L
        else -> 30_000L
    }

    /** Distance from [fix] to the nearest Fast circle's edge (0 inside), or null with none in use. */
    internal fun nearestFastEdge(context: Context, fix: Location): Float? =
        fastPlaces(context).minOfOrNull { max(0f, distanceTo(fix, it) - it.radius) }

    internal fun awaitingConfirmation(): Boolean = pendingFast.isNotEmpty()

    /** The tracking service stopped: a half-confirmed change must not be confirmed by a fix from a later run. */
    internal fun forgetPendingFixes() = pendingFast.clear()

    /**
     * A fix from [PlaceTrackingService]. A side change is committed only when two fixes in a row
     * agree, so one stray GPS jump never runs a shortcut.
     */
    internal fun onFix(context: Context, fix: Location) {
        val now = System.currentTimeMillis()
        val places = fastPlaces(context)
        pendingFast.keys.retainAll(places.map { it.key }.toSet())
        places.forEach { place ->
            touch(context, place.key, now)
            val verdict = verdictFor(fix, place)
            val stored = storedSide(context, place.key)
            when {
                // A vague fix (on Android 11 often the network estimate between GPS fixes) neither confirms nor cancels.
                verdict == null -> Unit
                verdict == stored -> {
                    pendingFast.remove(place.key)
                    takeStale(context, place.key)
                }
                pendingFast[place.key] == verdict -> {
                    pendingFast.remove(place.key)
                    commit(context, place.key, verdict)
                }
                else -> pendingFast[place.key] = verdict
            }
        }
    }

    /** What an alert or recheck does with the side its fix gave ([verdict], null = vague or no fix). */
    internal enum class Step { COMMIT, RECHECK, GIVE_UP }

    internal data class AlertDecision(val step: Step, val rearm: Boolean)

    /**
     * A decided side is committed. An undecided one is checked again, a few times, then left
     * alone; there is no fallback to Android's word. When Android said the opposite of the decision,
     * or nothing was decided, Android's own side is now wrong and it would stay silent on the real
     * crossing, so its alert is re-armed ([alertSaid] is null for KeySnap's own checks).
     */
    internal fun decide(alertSaid: Boolean?, verdict: Boolean?, attempt: Int): AlertDecision {
        val step = when {
            verdict != null -> Step.COMMIT
            attempt < MAX_RECHECKS -> Step.RECHECK
            else -> Step.GIVE_UP
        }
        val rearm = alertSaid != null && when (step) {
            Step.COMMIT -> verdict != alertSaid
            Step.GIVE_UP -> true
            Step.RECHECK -> false
        }
        return AlertDecision(step, rearm)
    }

    /**
     * Android's proximity alert, or a recheck, in a process that may have just started. Takes a
     * fresh fix and acts on [decide]. [done] gets whether a shortcut fired.
     */
    internal fun onAlert(context: Context, key: String, alertSaid: Boolean?, attempt: Int, done: (Boolean) -> Unit) {
        val place = Place.parse(keyParams(key))
        // A recheck that outlived its place (deleted, switched to Fast, paused, permission gone) decides nothing.
        if (place == null || !hasFineLocation(context) || key !in registered(context)) return done(false)
        // Alerts are added together and dropped together: one arriving in a fresh process means all are still there.
        if (alertSaid != null && armed.isEmpty()) armed += registered(context)
        freshFix(context) { fix ->
            // The place can be deleted, switched or paused while the fix comes.
            if (key !in registered(context)) return@freshFix done(false)
            if (fix != null) touch(context, key, System.currentTimeMillis())
            val verdict = fix?.let { verdictFor(it, place) }
            val decision = decide(alertSaid, verdict, attempt)
            val rearmed = !decision.rearm || rearm(context, place)
            val fired = when (decision.step) {
                Step.COMMIT -> commit(context, key, verdict!!)
                Step.RECHECK -> {
                    scheduleRecheck(context, key, alertSaid, attempt + 1)
                    false
                }
                Step.GIVE_UP -> false
            }
            // ponytail: where Android's vague fix keeps calling this place "inside", this repeats every 30 min.
            // Android's side is still wrong and re-arming must wait: check again once it may.
            if (!rearmed) scheduleRecheck(context, key, alertSaid, MAX_RECHECKS, rearmWaitMs(context, key))
            done(fired)
        }
    }

    /** A seed's fresh fix: decided, it is the place's side; vague or missing, it is checked again like an alert. */
    private fun onSeedFix(context: Context, place: Place, fix: Location?) {
        // Gone, switched or paused meanwhile, or an alert decided first: nothing left to seed.
        if (place.key !in registered(context) || (storedSide(context, place.key) != null && !isStale(context, place.key))) return
        val verdict = fix?.let { verdictFor(it, place) }
        if (verdict != null) commit(context, place.key, verdict) else scheduleRecheck(context, place.key, null, 1)
    }

    /**
     * Records [inside] and returns whether a shortcut fired. A change re-evaluates automations,
     * recorded with the old side first so a fresh process sees the edge. The first side ever known,
     * and the first decided after the place was unwatched (its kept side may be out of date), is
     * recorded silently.
     */
    private fun commit(context: Context, key: String, inside: Boolean): Boolean {
        cancelRecheck(context, key)
        touch(context, key, System.currentTimeMillis())
        val stored = storedSide(context, key)
        val stale = takeStale(context, key)
        if (stored == inside) return false
        HardwareKeyTriggerCoordinator.ensureInitialized(context)
        val fired = if (stored == null || stale) {
            save(context, key, inside)
            HardwareKeyTriggerCoordinator.reseedAutomationsSilently { usesPlace(it, key) }
            false
        } else {
            HardwareKeyTriggerCoordinator.seedUnknownAutomations()
            save(context, key, inside)
            HardwareKeyTriggerCoordinator.reevaluateAutomationsFired()
        }
        publishForKeys(context, ScriptRepository.getInstance(context).scripts.value)
        return fired
    }

    /** A key shortcut on a place is decided by the bridge from the side its config carries. */
    private fun publishForKeys(context: Context, scripts: List<ShortcutScript>) {
        if (scripts.any { s -> !s.automation && s.constraints.any { it.type == ScriptConstraintCatalog.AT_PLACE } }) {
            BridgeController.publishConfigIfInstalled(context)
        }
    }

    @SuppressLint("MissingPermission") // a missing or revoked grant surfaces as the SecurityException below
    private fun freshFix(context: Context, onResult: (Location?) -> Unit) {
        val locations = context.getSystemService(LocationManager::class.java)
        val provider = locations?.let { bestProvider(it) }
        if (locations == null || provider == null || !locations.isLocationEnabled) return onResult(null)
        val cancel = CancellationSignal()
        var answered = false
        val main = Handler(Looper.getMainLooper())
        fun answer(fix: Location?) {
            if (answered) return
            answered = true
            onResult(fix ?: recentFix(locations))
        }
        try {
            locations.getCurrentLocation(provider, cancel, context.mainExecutor) { answer(it) }
            main.postDelayed({ cancel.cancel(); answer(null) }, FIX_TIMEOUT_MS)
        } catch (e: SecurityException) {
            answer(null)
        } catch (e: IllegalArgumentException) {
            answer(null)
        }
    }

    /** The fused provider when the phone has one, else GPS when it is on, else the network estimate. */
    internal fun bestProvider(locations: LocationManager): String? {
        val all = locations.allProviders
        return when {
            FUSED in all -> FUSED
            locations.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            LocationManager.NETWORK_PROVIDER in all -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
    }

    /** A cached fix from the last two minutes, or null. */
    @SuppressLint("MissingPermission")
    private fun recentFix(locations: LocationManager): Location? = latestFix(locations, 2 * 60_000L)

    @SuppressLint("MissingPermission")
    private fun latestFix(locations: LocationManager, maxAgeMs: Long): Location? = runCatching {
        locations.getProviders(true).mapNotNull { locations.getLastKnownLocation(it) }.maxByOrNull { it.elapsedRealtimeNanos }
    }.getOrNull()?.takeIf { SystemClock.elapsedRealtime() - it.elapsedRealtimeNanos / 1_000_000 <= maxAgeMs }

    private fun recheckIntent(context: Context, key: String): Intent =
        Intent(context, PlaceAlertReceiver::class.java).setAction(ACTION_RECHECK).setIdentifier(key).putExtra(EXTRA_KEY, key)

    private fun scheduleRecheck(context: Context, key: String, alertSaid: Boolean?, attempt: Int, delayMs: Long = RECHECK_DELAY_MS) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = recheckIntent(context, key).putExtra(EXTRA_ATTEMPT, attempt)
        alertSaid?.let { intent.putExtra(LocationManager.KEY_PROXIMITY_ENTERING, it) }
        val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delayMs, pending)
    }

    private fun cancelRecheck(context: Context, key: String) {
        val pending = PendingIntent.getBroadcast(
            context, 0, recheckIntent(context, key), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        ) ?: return
        context.getSystemService(AlarmManager::class.java)?.cancel(pending)
        pending.cancel()
    }

    /**
     * Drops and re-adds the alert so Android starts from "unknown" and reports the next real
     * crossing. False when the last re-arm was too recent ([rearmWaitMs]).
     */
    private fun rearm(context: Context, place: Place): Boolean {
        if (rearmWaitMs(context, place.key) > 0) return false
        val locations = context.getSystemService(LocationManager::class.java) ?: return true
        prefs(context).edit().putLong(place.key + REARMED_SUFFIX, System.currentTimeMillis()).apply()
        val intent = alertIntent(context, place.key)
        locations.removeProximityAlert(intent)
        addAlert(locations, place, intent)
        return true
    }

    /** How long until [key]'s alert may be re-armed again (0 = now). */
    private fun rearmWaitMs(context: Context, key: String): Long =
        max(0L, prefs(context).getLong(key + REARMED_SUFFIX, 0L) + REARM_MIN_GAP_MS - System.currentTimeMillis())

    @SuppressLint("MissingPermission") // checked by the caller; a revoke in between surfaces as SecurityException
    private fun addAlert(locations: LocationManager, place: Place, intent: PendingIntent): Boolean = try {
        // Android has no lower limit, but under KeySnap's 150 m the platform geofence is noise.
        locations.addProximityAlert(place.lat, place.lng, max(place.radius, Place.MIN_BATTERY_RADIUS_M).toFloat(), -1, intent)
        true
    } catch (e: SecurityException) {
        // Permission revoked between the check and the call; the next sync tries again.
        false
    }

    /** The freshest cached fix decides the first state when it can; otherwise the place stays unknown until a fix or alert. */
    private fun seedState(locations: LocationManager, place: Place): Boolean? =
        latestFix(locations, SEED_MAX_AGE_MS)?.let { verdictFor(it, place) }

    private fun distanceTo(fix: Location, place: Place): Float =
        FloatArray(1).also { Location.distanceBetween(fix.latitude, fix.longitude, place.lat, place.lng, it) }[0]

    /** The key is "lat,lng,radius", so the alert's extra is enough to rebuild the place. */
    private fun keyParams(key: String): Map<String, String> =
        key.split(',').let { if (it.size == 3) mapOf("lat" to it[0], "lng" to it[1], "radius" to it[2]) else emptyMap() }

    private fun storedSide(context: Context, key: String): Boolean? = when (prefs(context).getString(key, null)) {
        IN -> true
        OUT -> false
        else -> null
    }

    private fun save(context: Context, key: String, inside: Boolean) {
        prefs(context).edit().putString(key, if (inside) IN else OUT).apply()
    }

    private fun registered(context: Context): Set<String> = prefs(context).getStringSet(REGISTERED, emptySet()).orEmpty()

    private fun isStale(context: Context, key: String): Boolean = prefs(context).getBoolean(key + STALE_SUFFIX, false)

    /** Whether [key]'s side was marked stale, clearing the mark: a decided fix makes it current again. */
    private fun takeStale(context: Context, key: String): Boolean =
        isStale(context, key).also { if (it) prefs(context).edit().remove(key + STALE_SUFFIX).apply() }

    private fun usesPlace(script: ShortcutScript, key: String): Boolean =
        script.constraints.any { it.type == ScriptConstraintCatalog.AT_PLACE && Place.parse(it.params)?.key == key }

    private fun touch(context: Context, key: String, nowMs: Long) {
        prefs(context).edit().putLong(key + CHECKED_SUFFIX, nowMs).apply()
    }

    /** Mutable: Android adds KEY_PROXIMITY_ENTERING to it when the alert fires. One per place, told apart by identifier. */
    private fun alertIntent(context: Context, key: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, PlaceAlertReceiver::class.java).setIdentifier(key).putExtra(EXTRA_KEY, key),
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun hasFineLocation(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasBackgroundLocation(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val FUSED = "fused"
    private const val IN = "in"
    private const val OUT = "out"
}

/** A place's enter or exit, sent by Android's proximity alert, or KeySnap's own recheck. */
class PlaceAlertReceiver : BroadcastReceiver() {
    // Unexported; the only senders are the PendingIntents this app handed to LocationManager and AlarmManager.
    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(PlaceCondition.EXTRA_KEY) ?: return
        val said = if (intent.hasExtra(LocationManager.KEY_PROXIMITY_ENTERING)) intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false) else null
        if (said == null && intent.action != PlaceCondition.ACTION_RECHECK) return
        // The alert can start this process cold; keep it protected while the fix comes and a fired shortcut runs.
        val pending: PendingResult? = goAsync()
        PlaceCondition.onAlert(context.applicationContext, key, said, intent.getIntExtra(PlaceCondition.EXTRA_ATTEMPT, 0)) { fired ->
            // ponytail: a fixed budget after a fire, as for the calendar alarm; a long chain keeps running unprotected after it.
            if (fired) Handler(Looper.getMainLooper()).postDelayed({ pending?.finish() }, 9_000L) else pending?.finish()
        }
    }
}
