package io.github.headlesshq.web.api;

import io.github.headlesshq.web.process.GameProcessManager;
import io.github.headlesshq.web.process.ManagedProcess;
import io.github.headlesshq.web.process.ProcessDto;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * The Minecraft clients and servers launched from the web ui.
 */
@Path("/api/processes")
public class ProcessResource {
    private final GameProcessManager manager;

    @Inject
    public ProcessResource(GameProcessManager manager) {
        this.manager = manager;
    }

    @GET
    public List<ProcessDto> list() {
        return manager.list().stream().map(ProcessDto::of).toList();
    }

    @GET
    @Path("/{id}")
    public ProcessDto get(@PathParam("id") String id) {
        return ProcessDto.of(process(id));
    }

    @GET
    @Path("/{id}/output")
    public List<ManagedProcess.Line> output(@PathParam("id") String id) {
        return process(id).getHistory();
    }

    @POST
    @Path("/{id}/input")
    public ProcessDto input(@PathParam("id") String id, InputRequest request) throws IOException {
        if (request == null || request.line() == null) {
            throw new BadRequestException("line is required");
        }

        ManagedProcess process = process(id);
        try {
            process.sendInput(request.line());
        } catch (IllegalStateException e) {
            throw new ClientErrorException(e.getMessage(), Response.Status.CONFLICT);
        }

        return ProcessDto.of(process);
    }

    @POST
    @Path("/{id}/stop")
    public ProcessDto stop(@PathParam("id") String id) {
        ManagedProcess process = process(id);
        process.stop();
        return ProcessDto.of(process);
    }

    @POST
    @Path("/{id}/kill")
    public ProcessDto kill(@PathParam("id") String id) {
        ManagedProcess process = process(id);
        process.kill();
        return ProcessDto.of(process);
    }

    @DELETE
    @Path("/{id}")
    public Response remove(@PathParam("id") String id) {
        process(id);
        if (!manager.remove(id)) {
            throw new ClientErrorException("Process " + id + " is still running", Response.Status.CONFLICT);
        }

        return Response.noContent().build();
    }

    private ManagedProcess process(String id) {
        return manager.get(id).orElseThrow(() -> new NotFoundException("No process " + id));
    }

    public record InputRequest(@Nullable String line) {
    }

}
