package dev.xnative.samples.credentialmanager

/**
 * Hook for surfacing legacy → DataStore migration failures.
 *
 * The migration path swallows nothing: when a write or readback fails, [onMigrationError] is
 * invoked and the legacy token is returned to keep the user signed in. Apps that already have
 * a logging stack (Timber, Crashlytics, Sentry, structured logging) plug their adapter here;
 * the default is the no-op so library consumers are never forced to depend on a logger.
 *
 * @param stage Coarse-grained location where the failure occurred (e.g. "datastore.write",
 *   "datastore.readback", "datastore.readback-mismatch"). Stable strings, useful for grouping
 *   in dashboards.
 * @param throwable Optional underlying exception. `null` for soft failures such as a readback
 *   that simply did not match the original token (encryption misconfiguration, IO race, etc.).
 */
fun interface CredentialMigrationLogger {
    fun onMigrationError(stage: String, throwable: Throwable?)
}

/** Default no-op logger — callers can swap this for their own without wrapping a heavy lib. */
object NoOpCredentialMigrationLogger : CredentialMigrationLogger {
    override fun onMigrationError(stage: String, throwable: Throwable?) {
        // Intentional: keep the migration silent for consumers that have not wired a logger.
    }
}
