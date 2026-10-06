package io.github.headlesshq.web.api;

import io.github.headlesshq.web.command.CommandModel;
import io.github.headlesshq.web.command.CommandModelService;
import io.github.headlesshq.web.command.CompletionService;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.jspecify.annotations.Nullable;

@Path("/api")
public class CommandResource {
    private final CommandModelService modelService;
    private final CompletionService completionService;

    @Inject
    public CommandResource(CommandModelService modelService, CompletionService completionService) {
        this.modelService = modelService;
        this.completionService = completionService;
    }

    /**
     * @return the HeadlessMc command tree.
     */
    @GET
    @Path("/commands")
    public CommandModel.Command commands() {
        return modelService.getModel();
    }

    /**
     * Tab completion for a command line.
     */
    @POST
    @Path("/complete")
    public CompletionService.Result complete(CompletionRequest request) {
        if (request == null || request.line() == null) {
            throw new BadRequestException("line is required");
        }

        int cursor = request.cursor() == null ? request.line().length() : request.cursor();
        return completionService.complete(request.line(), cursor);
    }

    public record CompletionRequest(@Nullable String line, @Nullable Integer cursor) {
    }

}
