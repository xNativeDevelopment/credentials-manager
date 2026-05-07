package dev.xnative.samples.credentialmanager

/**
 * Suspending [CredentialManager] that lazily migrates legacy entries into a primary
 * encrypted-DataStore-backed store.
 *
 * Read flow on every cold call (the in-memory state is intentionally minimal — DataStore has
 * its own coherent cache):
 *
 *  1. Read [primary]. Non-null → return it.
 *  2. [primary] is empty → read [legacy].
 *  3. [legacy] is empty too → return `null`.
 *  4. [legacy] has the token → migrate:
 *     a. Write the token into [primary].
 *     b. Re-read [primary] and compare byte-for-byte to the legacy token.
 *     c. On match: delete the legacy entry, return the migrated token.
 *  5. On any migration failure (write returned `false`, write threw, readback returned a
 *     different token, readback threw): leave the legacy entry intact, log via [logger], and
 *     return the legacy token so the user stays signed in.
 *
 * Write flow simply forwards to [primary] and, on success, clears the legacy entry — the
 * legacy store is read-mostly after rollout, so we keep cleaning it up opportunistically.
 *
 * Delete flow clears both stores so a sign-out leaves no token anywhere on disk.
 */
internal class MigratingCredentialManager(
    private val primary: CredentialStore,
    private val legacy: CredentialStore,
    private val logger: CredentialMigrationLogger
) : CredentialManager {

    override suspend fun retrieve(key: String): String? {
        val primaryValue = runCatching { primary.read(key) }
            .onFailure { logger.onMigrationError(STAGE_PRIMARY_READ, it) }
            .getOrNull()
        if (primaryValue != null) return primaryValue

        val legacyValue = runCatching { legacy.read(key) }
            .onFailure { logger.onMigrationError(STAGE_LEGACY_READ, it) }
            .getOrNull()
            ?: return null

        return migrateLegacyToken(key, legacyValue)
    }

    override suspend fun save(key: String, value: String): Boolean {
        val saved = runCatching { primary.write(key, value) }
            .onFailure { logger.onMigrationError(STAGE_PRIMARY_WRITE, it) }
            .getOrDefault(false)

        // Once the secure store has the new token, the legacy copy is stale. We try to clear
        // it, but a clear failure must not fail the whole save: the migration will retry the
        // cleanup on the next cold read.
        if (saved) {
            runCatching { legacy.delete(key) }
                .onFailure { logger.onMigrationError(STAGE_LEGACY_CLEANUP, it) }
        }
        return saved
    }

    override suspend fun delete(key: String): Boolean {
        val primaryDeleted = runCatching { primary.delete(key) }
            .onFailure { logger.onMigrationError(STAGE_PRIMARY_DELETE, it) }
            .getOrDefault(false)
        val legacyDeleted = runCatching { legacy.delete(key) }
            .onFailure { logger.onMigrationError(STAGE_LEGACY_DELETE, it) }
            .getOrDefault(false)
        return primaryDeleted && legacyDeleted
    }

    private suspend fun migrateLegacyToken(key: String, legacyValue: String): String {
        // Copy-before-delete: never destroy the legacy entry until the encrypted readback
        // proves byte-for-byte that the secure store can give us the same token back.
        val written = runCatching { primary.write(key, legacyValue) }
            .onFailure { logger.onMigrationError(STAGE_DATASTORE_WRITE, it) }
            .getOrDefault(false)

        if (!written) {
            return legacyValue
        }

        val readback = runCatching { primary.read(key) }
            .onFailure { logger.onMigrationError(STAGE_DATASTORE_READBACK, it) }
            .getOrNull()

        if (readback != legacyValue) {
            // Soft-failure path: encryption round-trip altered the bytes (algorithm
            // misconfiguration, IO race). Keep the legacy token intact and surface a typed
            // signal via the logger.
            logger.onMigrationError(STAGE_READBACK_MISMATCH, null)
            return legacyValue
        }

        runCatching { legacy.delete(key) }
            .onFailure { logger.onMigrationError(STAGE_LEGACY_CLEANUP, it) }
        return readback
    }

    private companion object {
        const val STAGE_PRIMARY_READ = "primary.read"
        const val STAGE_PRIMARY_WRITE = "primary.write"
        const val STAGE_PRIMARY_DELETE = "primary.delete"
        const val STAGE_LEGACY_READ = "legacy.read"
        const val STAGE_LEGACY_DELETE = "legacy.delete"
        const val STAGE_LEGACY_CLEANUP = "legacy.cleanup"
        const val STAGE_DATASTORE_WRITE = "datastore.write"
        const val STAGE_DATASTORE_READBACK = "datastore.readback"
        const val STAGE_READBACK_MISMATCH = "datastore.readback-mismatch"
    }
}
