package com.studyhub.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DocumentIngestionWorker {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(DocumentIngestionWorker.class);
    private final DocumentIngestionRepository queue;
    private final DocumentIngestionService service;
    private final boolean enabled;

    public DocumentIngestionWorker(DocumentIngestionRepository queue,DocumentIngestionService service,
            @Value("${studyhub.ingestion.worker-enabled:true}") boolean enabled) {
        this.queue=queue;this.service=service;this.enabled=enabled;
    }

    @Scheduled(fixedDelayString="${studyhub.ingestion.poll-delay-ms:1000}",initialDelayString="${studyhub.ingestion.poll-delay-ms:1000}")
    public void tick(){
        if(!enabled)return;
        try{processNext();}
        catch(RuntimeException ignored){log.warn("Ingestion worker deferred work; queue state retained for retry or lease recovery");}
    }

    public boolean processNext(){
        var claim=queue.claimNext();
        if(claim==null)return false;
        service.process(claim);
        return true;
    }
}
