package com.studyhub.ai.concept;

import static com.studyhub.ai.concept.ConceptSuggestionModels.*;
import static com.studyhub.ai.concept.ConceptEvidenceValidator.invalid;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
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
public class ConceptExtractionClient {
    private final String url,token,model;
    private final Duration timeout;
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public ConceptExtractionClient(@Value("${studyhub.ai.url:http://localhost:8000}") String url,
        @Value("${studyhub.ai.token:}") String token,@Value("${studyhub.ai.chat-timeout-seconds:75}") long timeout,
        @Value("${studyhub.ai.llm-model:gpt-4.1-mini}") String model) {
        if(timeout<=0) throw new IllegalArgumentException("Invalid Concept extraction timeout");
        this.url=url.replaceAll("/+$","");this.token=token;this.model=model;this.timeout=Duration.ofSeconds(timeout);
    }
    public ExtractionResult extract(SourceWindow window,int maxSuggestions) {
        if(url.isBlank() || token.isBlank() || model.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"AI is not configured.");
        try {
            var request=HttpRequest.newBuilder(URI.create(url+"/internal/concepts/extract")).timeout(timeout)
                .header("X-AI-Service-Token",token).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(Map.of("maxSuggestions",maxSuggestions,"contexts",window.contexts())))).build();
            var response=http.send(request,info->new BoundedBody());
            if(response.statusCode()!=200) {
                boolean budget=false;
                if(response.statusCode()==422) {
                    try { budget="TOKEN_BUDGET_EXCEEDED".equals(json.readTree(response.body()).at("/detail/code").asText()); }
                    catch(IOException | NullPointerException ignored) {}
                }
                var status=switch(response.statusCode()) {
                    case 401,403,429,503->HttpStatus.SERVICE_UNAVAILABLE;
                    case 504->HttpStatus.GATEWAY_TIMEOUT;
                    case 422->budget?HttpStatus.UNPROCESSABLE_ENTITY:HttpStatus.BAD_GATEWAY;
                    default->HttpStatus.BAD_GATEWAY;
                };
                throw new ResponseStatusException(status,"AI Concept extraction failed.");
            }
            try { return parse(json.readTree(response.body()),maxSuggestions); }
            catch(IOException error) { throw invalid(); }
        } catch(HttpTimeoutException error) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,"AI extraction timed out.");
        } catch(InterruptedException error) {
            Thread.currentThread().interrupt();throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"AI extraction interrupted.");
        } catch(IOException error) {
            for(Throwable cause=error;cause!=null;cause=cause.getCause()) if(cause instanceof BodyLimitException) throw invalid();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"AI extraction unavailable.");
        }
    }
    ExtractionResult parse(JsonNode root,int maximum) {
        if(root==null || !root.isObject() || root.size()!=3 || !root.path("model").isTextual()
            || !model.equals(root.path("model").asText()) || !root.path("promptVersion").isTextual()
            || !PROMPT_VERSION.equals(root.path("promptVersion").asText()) || !root.path("suggestions").isArray()
            || root.path("suggestions").size()>maximum) throw invalid();
        var suggestions=new ArrayList<GeneratedSuggestion>();
        for(var item:root.path("suggestions")) {
            if(!item.isObject() || item.size()!=3 || !item.path("name").isTextual() || !item.path("description").isTextual()
                || !item.path("evidence").isArray() || item.path("evidence").isEmpty() || item.path("evidence").size()>3) throw invalid();
            var evidence=new ArrayList<Evidence>();
            for(var e:item.path("evidence")) {
                var id=e.path("chunkId");
                if(!e.isObject() || e.size()!=2 || !id.isIntegralNumber() || !id.canConvertToLong() || id.longValue()<=0
                    || !e.path("quote").isTextual()) throw invalid();
                evidence.add(new Evidence(id.longValue(),e.path("quote").asText()));
            }
            suggestions.add(new GeneratedSuggestion(item.path("name").asText(),item.path("description").asText(),List.copyOf(evidence)));
        }
        return new ExtractionResult(model,PROMPT_VERSION,List.copyOf(suggestions));
    }
    private static class BodyLimitException extends IOException {}
    /** Bound memory even if a malicious upstream omits or lies about Content-Length. */
    private static class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();
        private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        private Subscription subscription;
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Subscription value) { subscription=value;value.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for(var buffer:buffers) {
                if(buffer.remaining()>1048576-bytes.size()) {
                    subscription.cancel();result.completeExceptionally(new BodyLimitException());return;
                }
                byte[] chunk=new byte[buffer.remaining()];buffer.get(chunk);bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { result.completeExceptionally(error); }
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
