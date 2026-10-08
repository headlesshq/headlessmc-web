package io.github.headlesshq.web.api;

import io.github.headlesshq.headlessmc.launcher.profile.Profile;
import io.github.headlesshq.headlessmc.mods.ModType;
import io.github.headlesshq.web.mods.ModFileDto;
import io.github.headlesshq.web.mods.ModFilesService;
import io.github.headlesshq.web.mods.RemoteModDto;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

/**
 * The mod files installed in a profile or server: listing with logos, adding (drag and drop), removing, toggling.
 * Downloading from mod distribution platforms is done with HeadlessMc's {@code mod add} command.
 */
@Path("/api/mods/{profile}")
public class ModsResource {
    private final ModFilesService service;

    @Inject
    public ModsResource(ModFilesService service) {
        this.service = service;
    }

    @GET
    public Mods list(@PathParam("profile") String profileName, @QueryParam("type") @Nullable String typeName) {
        Profile profile = service.getProfile(profileName);
        List<ModType> types = service.getTypes(profile);
        ModType type = typeName == null ? types.getFirst() : service.getType(profile, typeName);
        return new Mods(
            profile.name(),
            profile.side().name().toLowerCase(Locale.ROOT),
            profile.version().platform(),
            type.name(),
            types.stream().map(ModType::name).toList(),
            service.getWorlds(profile),
            service.list(profile, type)
        );
    }

    @GET
    @Path("/logo")
    public Response logo(@PathParam("profile") String profileName, @QueryParam("path") @Nullable String path) throws IOException {
        Profile profile = service.getProfile(profileName);
        byte[] logo = service.logo(profile, require(path, "path")).orElse(null);
        if (logo == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(logo, imageType(logo)).header("Cache-Control", "private, max-age=300").build();
    }

    @POST
    @Path("/files")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public List<ModFileDto> add(
        @PathParam("profile") String profileName,
        @QueryParam("type") @Nullable String typeName,
        @QueryParam("world") @Nullable String world,
        @QueryParam("overwrite") boolean overwrite,
        @RestForm("files") List<FileUpload> files
    ) throws IOException {
        if (files == null || files.isEmpty()) {
            throw new BadRequestException("No files");
        }

        Profile profile = service.getProfile(profileName);
        ModType type = service.getType(profile, require(typeName, "type"));
        List<ModFileDto> result = new ArrayList<>();
        for (FileUpload file : files) {
            result.add(service.add(profile, type, world, file.fileName(), file.uploadedFile(), overwrite));
        }

        return result;
    }

    @DELETE
    @Path("/files")
    public Response delete(@PathParam("profile") String profileName, @QueryParam("path") @Nullable String path) throws IOException {
        service.delete(service.getProfile(profileName), require(path, "path"));
        return Response.noContent().build();
    }

    @POST
    @Path("/files/enabled")
    public ModFileDto setEnabled(
        @PathParam("profile") String profileName,
        @QueryParam("path") @Nullable String path,
        @QueryParam("enabled") boolean enabled
    ) throws IOException {
        return service.setEnabled(service.getProfile(profileName), require(path, "path"), enabled);
    }

    @GET
    @Path("/search")
    public List<RemoteModDto> search(
        @PathParam("profile") String profileName,
        @QueryParam("query") @Nullable String query,
        @QueryParam("type") @Nullable String typeName,
        @QueryParam("platform") @Nullable String platform
    ) {
        Profile profile = service.getProfile(profileName);
        return service.search(profile, service.getType(profile, require(typeName, "type")), require(query, "query"), platform);
    }

    @ServerExceptionMapper
    public Response notFound(NoSuchElementException e) {
        return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).type(MediaType.TEXT_PLAIN).build();
    }

    @ServerExceptionMapper
    public Response badRequest(IllegalArgumentException e) {
        return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).type(MediaType.TEXT_PLAIN).build();
    }

    @ServerExceptionMapper
    public Response conflict(ModFilesService.FileExistsException e) {
        return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).type(MediaType.TEXT_PLAIN).build();
    }

    private static String require(@Nullable String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(name + " is required");
        }

        return value;
    }

    private static String imageType(byte[] data) {
        if (data.length > 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
            return "image/jpeg";
        } else if (data.length > 3 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F') {
            return "image/gif";
        } else if (data.length > 12 && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return "image/webp";
        }

        return "image/png";
    }

    public record Mods(
        String profile,
        String side,
        String platform,
        String type,
        List<String> types,
        List<String> worlds,
        List<ModFileDto> files
    ) {
    }

}
