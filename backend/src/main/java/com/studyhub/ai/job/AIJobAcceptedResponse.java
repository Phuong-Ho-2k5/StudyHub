package com.studyhub.ai.job;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import java.net.URI;
public record AIJobAcceptedResponse(UUID jobId,String statusUrl) {
 public static AIJobAcceptedResponse of(UUID id) {return new AIJobAcceptedResponse(id,"/api/ai/jobs/"+id);}
 public ResponseEntity<AIJobAcceptedResponse> accepted() {return ResponseEntity.accepted().location(URI.create(statusUrl)).body(this);}
}
