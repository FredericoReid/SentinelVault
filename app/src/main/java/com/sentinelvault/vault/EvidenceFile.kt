package com.sentinelvault.vault

/**
 * Catalog entry surfaced by an [EvidenceLister]. Decouples the FIFO retention algorithm from
 * any specific storage backend (file system today; encrypted-blob table tomorrow). The
 * algorithm only needs a stable identity, an age proxy (`timestampMs`) and a size in bytes.
 *
 * @property eventId        primary key of the parent `event_log` row, so retention can clear
 *                          [com.sentinelvault.data.db.entity.EventLogEntity.evidencePath] in
 *                          lock-step with the file deletion.
 * @property absolutePath   absolute path of the on-disk artefact.
 * @property sizeBytes      file size in bytes, queried at scan time. Implementations should
 *                          cache `length()` rather than `stat()` per access.
 * @property timestampMs    wall-clock millis used to define the FIFO order (oldest first).
 */
data class EvidenceFile(
    val eventId: Long,
    val absolutePath: String,
    val sizeBytes: Long,
    val timestampMs: Long
)
