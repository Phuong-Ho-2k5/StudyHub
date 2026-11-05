package com.studyhub.ai.advice;

import static com.studyhub.ai.advice.LearningAdviceModels.*;
import static com.studyhub.ai.advice.LearningAdviceService.invalid;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.studyhub.ai.RagAnswerClient.Evidence;
import com.studyhub.search.dto.CourseSearchResponse.SearchHitResponse;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow.Subscription;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class LearningAdviceClient {
    private record Context(Long chunkId,Long documentId,String documentTitle,int chunkIndex,String content,Integer pageStart,Integer pageEnd) {}
    private final String url,token,model;
    private final Duration timeout;
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public LearningAdviceClient(@Value("${studyhub.ai.url:http://localhost:8000}") String url,
                               @Value("${studyhub.ai.token:}") String token,
                               @Value("${studyhub.ai.chat-timeout-seconds:75}") long timeout,
                               @Value("${studyhub.ai.llm-model:gpt-4.1-mini}") String model) {
        if(timeout<=0)throw new IllegalArgumentException("Invalid advice timeout");
        this.url=url.replaceAll("/+$","");this.token=token;this.model=model;this.timeout=Duration.ofSeconds(timeout);
    }

    public AdviceGenerationResult generate(String question,List<LearningFact> facts,List<SearchHitResponse> contexts) {
        if(url.isBlank() || token.isBlank() || model.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"AI is not configured.");
        var body=new BoundedBody();
        try {
            var sources=contexts.stream().map(c->new Context(c.chunkId(),c.documentId(),c.documentTitle(),c.chunkIndex(),c.content(),c.pageStart(),c.pageEnd())).toList();
            var request=HttpRequest.newBuilder(URI.create(url+"/internal/advice/generate")).timeout(timeout)
                .header("X-AI-Service-Token",token).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(Map.of("question",question,"learningEvidence",facts,"contexts",sources)))).build();
            var response=http.send(request,info->body);
            if(response.statusCode()!=200) {
                boolean budget=false;
                if(response.statusCode()==422)try {budget="TOKEN_BUDGET_EXCEEDED".equals(json.readTree(response.body()).at("/detail/code").asText());}catch(IOException|NullPointerException ignored) {}
                var status=switch(response.statusCode()) {
                    case 401,403,429,503 -> HttpStatus.SERVICE_UNAVAILABLE;
                    case 504 -> HttpStatus.GATEWAY_TIMEOUT;
                    case 422 -> budget?HttpStatus.UNPROCESSABLE_ENTITY:HttpStatus.BAD_GATEWAY;
                    default -> HttpStatus.BAD_GATEWAY;
                };
                throw new ResponseStatusException(status,"AI learning advice request failed.");
            }
            try {return parse(json.readTree(response.body()));}catch(IOException error){throw invalid();}
        }catch(HttpTimeoutException error){throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,"AI learning advice timed out.");}
        catch(InterruptedException error){Thread.currentThread().interrupt();throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"AI request interrupted.");}
        catch(IOException error){if(body.limitExceeded)throw invalid();throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"AI learning advice unavailable.");}
    }

    private AdviceGenerationResult parse(JsonNode root) {
        fields(root,"status","answer","usedLearningEvidenceIds","evidence","model","promptVersion");
        if(!root.path("status").isTextual() || !Set.of("ANSWERED","LEARNING_DATA_ONLY","INSUFFICIENT_CONTEXT").contains(root.path("status").asText())
            || !root.path("model").isTextual() || !model.equals(root.path("model").asText())
            || !root.path("promptVersion").isTextual() || !PROMPT_VERSION.equals(root.path("promptVersion").asText())
            || !text(root.path("answer"),32768) || !root.path("usedLearningEvidenceIds").isArray() || root.path("usedLearningEvidenceIds").size()>37
            || !root.path("evidence").isArray() || root.path("evidence").size()>10) throw invalid();
        var ids=new ArrayList<String>();var evidence=new ArrayList<Evidence>();
        for(var id:root.path("usedLearningEvidenceIds")){if(!text(id,100))throw invalid();ids.add(id.asText());}
        for(var item:root.path("evidence")) {
            fields(item,"chunkId","quote");var id=item.path("chunkId");
            if(!id.isIntegralNumber() || !id.canConvertToLong() || id.longValue()<=0 || !text(item.path("quote"),1000))throw invalid();
            evidence.add(new Evidence(id.longValue(),item.path("quote").asText()));
        }
        return new AdviceGenerationResult(root.path("status").asText(),root.path("answer").asText(),List.copyOf(ids),List.copyOf(evidence),model,PROMPT_VERSION);
    }

    private static boolean text(JsonNode value,int max) {
        return value.isTextual() && value.asText().codePointCount(0,value.asText().length())<=max
            && value.asText().codePoints().anyMatch(c -> !com.studyhub.ai.concept.ConceptSuggestionModels.whitespace(c));
    }
    private static void fields(JsonNode node,String...names) {
        if(node==null || !node.isObject() || node.size()!=names.length || !Arrays.stream(names).allMatch(node::has))throw invalid();
    }

    private static class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private volatile boolean limitExceeded;
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();
        private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();private Subscription subscription;
        public CompletionStage<byte[]> getBody(){return result;}
        public void onSubscribe(Subscription value){subscription=value;value.request(1);}
        public void onNext(List<ByteBuffer> buffers){
            for(var buffer:buffers){
                if(buffer.remaining()>1048576-bytes.size()){
                    limitExceeded=true;result.completeExceptionally(new IOException("Response limit exceeded"));subscription.cancel();return;
                }
                byte[] chunk=new byte[buffer.remaining()];buffer.get(chunk);bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable error){result.completeExceptionally(error);}
        public void onComplete(){result.complete(bytes.toByteArray());}
    }
}
