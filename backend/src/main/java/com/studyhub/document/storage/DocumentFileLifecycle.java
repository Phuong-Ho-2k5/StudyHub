package com.studyhub.document.storage;

import java.util.List;
import org.slf4j.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;
import com.studyhub.document.Document;

@Service
public class DocumentFileLifecycle {
    private static final Logger log = LoggerFactory.getLogger(DocumentFileLifecycle.class);
    private final JdbcTemplate jdbc;
    private final DocumentStorage storage;
    private final FileDeletionWorker worker;

    public DocumentFileLifecycle(JdbcTemplate jdbc, DocumentStorage storage, FileDeletionWorker worker) {
        this.jdbc = jdbc; this.storage = storage; this.worker = worker;
    }

    public void uploaded(String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try { storage.delete(key); }
                    catch (RuntimeException error) { log.error("Cannot remove rolled-back upload", error); }
                }
            }
        });
    }

    public void deleteDocument(Document document) {
        if (document.isManagedFile()) enqueue(List.of(document.getStoragePath()));
    }
    public void deleteCourse(Long courseId) {
        enqueue(jdbc.queryForList("SELECT storage_path FROM documents WHERE course_id = ? AND managed_file = TRUE", String.class, courseId));
    }
    public void deleteWorkspace(Long workspaceId) {
        enqueue(jdbc.queryForList("SELECT d.storage_path FROM documents d JOIN courses c ON c.id = d.course_id "
                + "WHERE c.workspace_id = ? AND d.managed_file = TRUE", String.class, workspaceId));
    }

    private void enqueue(List<String> keys) {
        boolean queued = false;
        for (String key : keys) {
            jdbc.update("INSERT INTO file_deletion_tasks(storage_key) VALUES (?)", key);
            queued = true;
        }
        if (queued) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try { worker.processPending(); }
                catch (RuntimeException error) { log.warn("Document cleanup deferred to retry worker", error); }
            }
        });
    }
}
