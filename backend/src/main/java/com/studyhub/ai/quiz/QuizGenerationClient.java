package com.studyhub.ai.quiz;

import static com.studyhub.ai.quiz.QuizGenerationModels.*;
import static com.studyhub.ai.quiz.QuizGenerationEvidenceValidator.invalid;
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
public class QuizGenerationClient {
    private final String url,token,model;private final Duration timeout;
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public QuizGenerationClient(@Value("${studyhub.ai.url:http://localhost:8000}") String url,@Value("${studyhub.ai.token:}") String token,
        @Value("${studyhub.ai.chat-timeout-seconds:75}") long timeout,@Value("${studyhub.ai.llm-model:gpt-4.1-mini}") String model) {
        if(timeout<=0)throw new IllegalArgumentException("Invalid quiz timeout");
        this.url=url.replaceAll("/+$","");this.token=token;this.timeout=Duration.ofSeconds(timeout);this.model=model;
    }
    public GeneratedResult generate(SourceWindow window,int count) {
        if(url.isBlank() || token.isBlank() || model.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"AI is not configured.");
        var body=new BoundedBody();
        try {
            var request=HttpRequest.newBuilder(URI.create(url+"/internal/quizzes/generate")).timeout(timeout)
                .header("X-AI-Service-Token",token).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(Map.of("questionCount",count,"contexts",window.contexts())))).build();
            var response=http.send(request,info->body);
            if(response.statusCode()!=200) {
                boolean meaningful=false;
                if(response.statusCode()==422)try {meaningful=Set.of("TOKEN_BUDGET_EXCEEDED","INSUFFICIENT_CONTENT").contains(json.readTree(response.body()).at("/detail/code").asText());}catch(IOException | NullPointerException ignored) {}
                var status=switch(response.statusCode()) {case 401,403,429,503->HttpStatus.SERVICE_UNAVAILABLE;case 504->HttpStatus.GATEWAY_TIMEOUT;
                    case 422->meaningful?HttpStatus.UNPROCESSABLE_ENTITY:HttpStatus.BAD_GATEWAY;default->HttpStatus.BAD_GATEWAY;};
                throw new ResponseStatusException(status,"AI quiz generation failed.");
            }
            try {return parse(json.readTree(response.body()));}catch(IOException error) {throw invalid();}
        }catch(HttpTimeoutException error) {throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,"AI quiz timed out.");}
        catch(InterruptedException error) {Thread.currentThread().interrupt();throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"AI quiz interrupted.");}
        catch(IOException error) {
            // HttpClient may replace a cancelled stream's original exception with a transport error.
            if(body.limitExceeded)throw invalid();
            for(Throwable cause=error;cause!=null;cause=cause.getCause())if(cause instanceof BodyLimitException)throw invalid();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"AI quiz unavailable.",error);
        }
    }
    GeneratedResult parse(JsonNode root) {
        fields(root,"model","promptVersion","questions");
        if(!root.path("model").isTextual() || !model.equals(root.path("model").asText()) || !root.path("promptVersion").isTextual()
            || !PROMPT_VERSION.equals(root.path("promptVersion").asText()) || !root.path("questions").isArray() || root.path("questions").isEmpty() || root.path("questions").size()>10)throw invalid();
        var questions=new ArrayList<GeneratedQuestion>();
        for(var q:root.path("questions")) {
            fields(q,"text","options","evidence");
            if(!q.path("text").isTextual() || !q.path("options").isArray() || q.path("options").size()!=4
                || !q.path("evidence").isArray() || q.path("evidence").isEmpty() || q.path("evidence").size()>3)throw invalid();
            var options=new ArrayList<Option>();var evidence=new ArrayList<Evidence>();
            for(var o:q.path("options")) {fields(o,"text","correct");if(!o.path("text").isTextual() || !o.path("correct").isBoolean())throw invalid();options.add(new Option(o.path("text").asText(),o.path("correct").booleanValue()));}
            for(var e:q.path("evidence")) {
                fields(e,"chunkId","quote");var id=e.path("chunkId");
                if(!id.isIntegralNumber() || !id.canConvertToLong() || id.longValue()<=0 || !e.path("quote").isTextual())throw invalid();
                evidence.add(new Evidence(id.longValue(),e.path("quote").asText()));
            }
            questions.add(new GeneratedQuestion(q.path("text").asText(),List.copyOf(options),List.copyOf(evidence)));
        }
        return new GeneratedResult(model,PROMPT_VERSION,List.copyOf(questions));
    }
    private void fields(JsonNode node,String... names) {
        if(node==null || !node.isObject() || node.size()!=names.length || !Arrays.stream(names).allMatch(node::has))throw invalid();
    }
    private static class BodyLimitException extends IOException {}
    private static class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private volatile boolean limitExceeded;
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();private Subscription subscription;
        public CompletionStage<byte[]> getBody() {return result;}
        public void onSubscribe(Subscription value) {subscription=value;value.request(1);}
        public void onNext(List<ByteBuffer> buffers) {
            for(var buffer:buffers) {
                if(buffer.remaining()>1048576-bytes.size()) {limitExceeded=true;result.completeExceptionally(new BodyLimitException());subscription.cancel();return;}
                byte[] chunk=new byte[buffer.remaining()];buffer.get(chunk);bytes.writeBytes(chunk);
            }subscription.request(1);
        }
        public void onError(Throwable error) {result.completeExceptionally(error);}
        public void onComplete() {result.complete(bytes.toByteArray());}
    }
}
