package io.github.headlesshq.web.api;

import io.github.headlesshq.headlessmc.HeadlessMc;
import io.github.headlesshq.headlessmc.auth.Account;
import io.github.headlesshq.headlessmc.auth.AuthProvider;
import io.github.headlesshq.headlessmc.auth.AuthService;
import io.github.headlesshq.headlessmc.auth.LastUsedAccountService;
import io.github.headlesshq.headlessmc.config.ConfigDescriptionService;
import io.github.headlesshq.headlessmc.config.ConfigService;
import io.github.headlesshq.headlessmc.files.AppFiles;
import io.github.headlesshq.headlessmc.files.McFiles;
import io.github.headlesshq.headlessmc.java.Java;
import io.github.headlesshq.headlessmc.java.JavaService;
import io.github.headlesshq.headlessmc.launcher.profile.Profile;
import io.github.headlesshq.headlessmc.launcher.profile.ProfileService;
import io.github.headlesshq.headlessmc.launcher.server.ServerService;
import io.github.headlesshq.headlessmc.version.Version;
import io.github.headlesshq.headlessmc.version.service.VersionJsonService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.jboss.logging.Logger;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Read only, structured views of HeadlessMc's state for the GUI.
 * Everything that changes state is done by executing HeadlessMc commands via {@link JobResource}.
 */
@Path("/api")
public class StateResource {
    private static final Logger LOG = Logger.getLogger(StateResource.class);

    private final ConfigDescriptionService configDescriptionService;
    private final LastUsedAccountService lastUsedAccountService;
    private final VersionJsonService versionJsonService;
    private final ProfileService profileService;
    private final ServerService serverService;
    private final ConfigService configService;
    private final AuthService authService;
    private final JavaService javaService;
    private final AppFiles appFiles;
    private final McFiles mcFiles;

    @Inject
    public StateResource(
        ConfigDescriptionService configDescriptionService,
        LastUsedAccountService lastUsedAccountService,
        VersionJsonService versionJsonService,
        ProfileService profileService,
        ServerService serverService,
        ConfigService configService,
        AuthService authService,
        JavaService javaService,
        AppFiles appFiles,
        McFiles mcFiles
    ) {
        this.configDescriptionService = configDescriptionService;
        this.lastUsedAccountService = lastUsedAccountService;
        this.versionJsonService = versionJsonService;
        this.profileService = profileService;
        this.serverService = serverService;
        this.configService = configService;
        this.authService = authService;
        this.javaService = javaService;
        this.appFiles = appFiles;
        this.mcFiles = mcFiles;
    }

    @GET
    @Path("/info")
    public Info info() {
        Runtime runtime = Runtime.getRuntime();
        return new Info(
            HeadlessMc.NAME,
            HeadlessMc.VERSION,
            Map.of(
                "data", appFiles.getDataDir().toAbsolutePath().toString(),
                "config", appFiles.getConfigDir().toAbsolutePath().toString(),
                "state", appFiles.getStateDir().toAbsolutePath().toString(),
                "cache", appFiles.getCacheDir().toAbsolutePath().toString(),
                "mc", mcFiles.getMcDir().toAbsolutePath().toString()
            ),
            runtime.maxMemory(),
            runtime.totalMemory() - runtime.freeMemory(),
            System.getProperty("java.version")
        );
    }

    @GET
    @Path("/accounts")
    public Accounts accounts() {
        List<Provider> providers = new ArrayList<>();
        for (AuthProvider provider : authService.getProviders()) {
            List<AccountDto> accounts = List.of();
            try {
                accounts = provider.getAccounts().stream().map(AccountDto::of).toList();
            } catch (RuntimeException e) {
                LOG.warnf(e, "Failed to read accounts of provider %s", provider.getName());
            }

            providers.add(new Provider(
                provider.getName(),
                new ArrayList<>(provider.getAuthenticator().getMethods().keySet()),
                accounts
            ));
        }

        AccountDto selected = null;
        try {
            selected = lastUsedAccountService.getLastUsedAccount().map(AccountDto::of).orElse(null);
        } catch (RuntimeException e) {
            LOG.warn("Failed to read last used account", e);
        }

        return new Accounts(providers, selected);
    }

    @GET
    @Path("/profiles")
    public List<ProfileDto> profiles() {
        return profileService.getProfiles().stream()
            .map(ProfileDto::of)
            .sorted(Comparator.comparing(ProfileDto::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    @GET
    @Path("/servers")
    public List<ProfileDto> servers() {
        return serverService.listServers().stream()
            .map(ProfileDto::of)
            .sorted(Comparator.comparing(ProfileDto::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    @GET
    @Path("/versions")
    public List<VersionDto> versions() {
        return versionJsonService.getInstalledVersions().stream()
            .map(version -> new VersionDto(version.getId(), version.getInheritsFrom(), version.getType()))
            .sorted(Comparator.comparing(VersionDto::id, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    @GET
    @Path("/java")
    public List<JavaDto> java() {
        return javaService.getJavaVersions().stream()
            .map(java -> new JavaDto(java.name(), java.version(), java.home().toString(), java.current()))
            .toList();
    }

    @GET
    @Path("/config")
    public List<ConfigProperty> config(@QueryParam("all") boolean all) {
        return configService.getPropertyNames().stream()
            .filter(name -> all || name.startsWith("hmc."))
            .map(name -> new ConfigProperty(
                name,
                configService.getConfig().getOptionalValue(name, String.class).orElse(null),
                configDescriptionService.getDescription(name).orElse(null)
            ))
            .toList();
    }

    public record Info(
        String name,
        String version,
        Map<String, String> directories,
        long maxMemory,
        long usedMemory,
        String javaVersion
    ) {
    }

    public record Accounts(List<Provider> providers, @Nullable AccountDto selected) {
    }

    public record Provider(String name, List<String> methods, List<AccountDto> accounts) {
    }

    /**
     * An account, without its token.
     */
    public record AccountDto(String name, String uuid, String type, String provider) {
        static AccountDto of(Account account) {
            return new AccountDto(account.getName(), account.getUuid(), account.getType(), account.getProvider());
        }
    }

    public record ProfileDto(
        String name,
        String side,
        String version,
        String currentVersion,
        String path,
        @Nullable Integer javaVersion,
        List<String> patchers,
        List<String> vmArgs,
        @Nullable List<String> gameArgs,
        Map<String, @Nullable String> systemProperties,
        String eulaStatus
    ) {
        static ProfileDto of(Profile profile) {
            return new ProfileDto(
                profile.name(),
                profile.side().name().toLowerCase(),
                profile.version().toString(" "),
                profile.currentVersion().toString(" "),
                profile.path().toAbsolutePath().toString(),
                profile.javaVersion(),
                profile.patchers(),
                profile.vmArgs(),
                profile.gameArgs(),
                profile.systemProperties(),
                profile.eulaStatus().name()
            );
        }
    }

    public record VersionDto(String id, @Nullable String inheritsFrom, @Nullable String type) {
    }

    public record JavaDto(String name, int version, String home, boolean current) {
    }

    public record ConfigProperty(String name, @Nullable String value, @Nullable String description) {
    }

}
