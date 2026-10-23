package com.studyhub.document.storage;

import org.slf4j.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class FileDeletionWorker {
    private static final Logger log = LoggerFactory.getLogger(FileDeletionWorker.class);
    private final JdbcTemplate jdbc;
    private final DocumentStorage storage;

    public FileDeletionWorker(JdbcTemplate jdbc, DocumentStorage storage) { this.jdbc = jdbc; this.storage = storage; }

    @Scheduled(fixedDelayString = "${studyhub.storage.cleanup-delay-ms:60000}", initialDelayString = "${studyhub.storage.cleanup-delay-ms:60000}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processPending() {
        var keys = jdbc.queryForList("SELECT storage_key FROM file_deletion_tasks ORDER BY id LIMIT 100", String.class);
        for (String key : keys) {
            try { storage.delete(key); }
            catch (RuntimeException error) {
                log.warn("Document file cleanup failed; retained for retry", error);
                continue;
            }
            jdbc.update("DELETE FROM file_deletion_tasks WHERE storage_key = ?", key);
        }
    }
}
