package io.github.headlesshq.web.api;

import io.github.headlesshq.web.console.Job;
import io.github.headlesshq.web.console.JobDto;
import io.github.headlesshq.web.console.JobService;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Executes HeadlessMc command lines.
 */
@Path("/api/jobs")
public class JobResource {
    private final JobService jobService;

    @Inject
    public JobResource(JobService jobService) {
        this.jobService = jobService;
    }

    @GET
    public List<JobDto> list(@QueryParam("output") boolean output) {
        return jobService.list().stream().map(job -> JobDto.of(job, output)).toList();
    }

    @POST
    public Response submit(SubmitRequest request) {
        if (request == null || request.line() == null) {
            throw new BadRequestException("line is required");
        }

        String origin = request.origin() == null ? "gui" : request.origin();
        Job job = jobService.submit(request.line(), origin);
        return Response.status(Response.Status.CREATED).entity(JobDto.of(job, true)).build();
    }

    @GET
    @Path("/{id}")
    public JobDto get(@PathParam("id") String id) {
        return JobDto.of(job(id), true);
    }

    @POST
    @Path("/{id}/input")
    public JobDto input(@PathParam("id") String id, InputRequest request) {
        if (request == null || request.promptId() == null) {
            throw new BadRequestException("promptId is required");
        }

        Job job = job(id);
        String value = request.cancel() ? null : (request.value() == null ? "" : request.value());
        if (!jobService.answer(id, request.promptId(), value)) {
            throw new ClientErrorException("Prompt " + request.promptId() + " is not pending", Response.Status.CONFLICT);
        }

        return JobDto.of(job, false);
    }

    @POST
    @Path("/{id}/cancel")
    public JobDto cancel(@PathParam("id") String id) {
        Job job = job(id);
        jobService.cancel(id);
        return JobDto.of(job, false);
    }

    private Job job(String id) {
        return jobService.get(id).orElseThrow(() -> new NotFoundException("No job " + id));
    }

    public record SubmitRequest(@Nullable String line, @Nullable String origin) {
    }

    public record InputRequest(@Nullable String promptId, @Nullable String value, boolean cancel) {
    }

}
