package com.studyhub.ai.job;

import com.studyhub.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/jobs")
@Tag(name="AI Jobs")
@SecurityRequirement(name="bearerAuth")
public class AIJobController {
    private final AIJobService service;
    public AIJobController(AIJobService service) {this.service=service;}
    @GetMapping("/{jobId}")
    @Operation(summary="Read a durable AI job",description="Requester and current Course ownership are both required. Read only; never claims work or calls AI. Results contain resource references only.")
    @ApiResponses({@ApiResponse(responseCode="200",description="Current job snapshot"),
        @ApiResponse(responseCode="400",description="Malformed UUID"),
        @ApiResponse(responseCode="401",description="Missing or invalid JWT"),
        @ApiResponse(responseCode="404",description="Job missing or outside current ownership")})
    public AIJobResponse get(@PathVariable UUID jobId,@AuthenticationPrincipal CustomUserDetails user) {
        return service.get(jobId,user.getId());
    }
}
