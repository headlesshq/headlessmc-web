package io.github.headlesshq.web.mods;

import io.github.headlesshq.headlessmc.commands.mod.ModFile;
import io.github.headlesshq.headlessmc.commands.mod.ModListingService;
import io.github.headlesshq.headlessmc.launcher.profile.Profile;
import io.github.headlesshq.headlessmc.launcher.profile.ProfileService;
import io.github.headlesshq.headlessmc.launcher.server.ServerService;
import io.github.headlesshq.headlessmc.mods.ModType;
import io.github.headlesshq.headlessmc.mods.distribution.ModDistributionPlatformService;
import io.github.headlesshq.headlessmc.mods.distribution.RemoteMod;
import io.github.headlesshq.headlessmc.platform.Platform;
import io.github.headlesshq.headlessmc.platform.PlatformService;
import io.github.headlesshq.headlessmc.platform.mods.ModSupport;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * The mod files (mods, plugins, resource packs, shaders, data packs) installed in a profile or server.
 * Metadata comes from HeadlessMc's {@link ModListingService}, logos and versions from {@link ModArchiveInfo}.
 */
@ApplicationScoped
public class ModFilesService {
    static final String DISABLED = ".disabled";

    private static final Logger LOG = Logger.getLogger(ModFilesService.class);
    private static final Set<String> EXTENSIONS = Set.of(".jar", ".zip");

    private final Map<String, CachedInfo> infoCache = new ConcurrentHashMap<>();
    private final ModDistributionPlatformService distributionService;
    private final ModListingService modListingService;
    private final PlatformService platformService;
    private final ProfileService profileService;
    private final ServerService serverService;

    @Inject
    public ModFilesService(
        ModDistributionPlatformService distributionService,
        ModListingService modListingService,
        PlatformService platformService,
        ProfileService profileService,
        ServerService serverService
    ) {
        this.distributionService = distributionService;
        this.modListingService = modListingService;
        this.platformService = platformService;
        this.profileService = profileService;
        this.serverService = serverService;
    }

    public Profile getProfile(String name) {
        return profileService.getProfile(name)
            .or(() -> serverService.getServer(name))
            .orElseThrow(() -> new NoSuchElementException("No profile or server called " + name));
    }

    /**
     * @return the types of mods that can be installed in the given profile, e.g. mods, resource packs.
     */
    public List<ModType> getTypes(Profile profile) {
        List<ModType> types = new ArrayList<>();
        platformService.getPlatform(profile.version().platform())
            .flatMap(Platform::getModSupport)
            .map(ModSupport::modTypes)
            .ifPresent(types::addAll);
        if (profile.side().isClient()) {
            types.add(ModType.RESOURCE_PACK);
            types.add(ModType.SHADER);
        }

        types.add(ModType.DATA_PACK);
        return types.stream().distinct().toList();
    }

    public ModType getType(Profile profile, String name) {
        return getTypes(profile).stream()
            .filter(type -> type.name().equalsIgnoreCase(name))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Mod type " + name + " is not supported by " + profile.name()));
    }

    /**
     * @return the worlds of the profile, which data packs can be installed to.
     */
    public List<String> getWorlds(Profile profile) {
        return worldDirs(profile).stream().map(dir -> dir.getFileName().toString()).toList();
    }

    public List<ModFileDto> list(Profile profile, ModType type) {
        Map<Path, List<ModFile>> metadata = new HashMap<>();
        try {
            for (ModFile modFile : modListingService.list(profile, null, null, type.name())) {
                metadata.computeIfAbsent(modFile.file().toAbsolutePath().normalize(), k -> new ArrayList<>()).add(modFile);
            }
        } catch (RuntimeException e) {
            LOG.debugf(e, "Failed to read %s metadata of %s", type.name(), profile.name());
        }

        List<ModFileDto> result = new ArrayList<>();
        for (Map.Entry<Path, @Nullable String> dir : directories(profile, type).entrySet()) {
            for (Path file : listFiles(dir.getKey())) {
                result.add(describe(profile, type, file, dir.getValue(), metadata.getOrDefault(file, List.of())));
            }
        }

        result.sort(Comparator.comparing(ModFileDto::displayName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    /**
     * Resolves a mod file of the profile by its path relative to the game directory.
     * Only files directly inside one of the mod directories of the profile are allowed.
     */
    public ModLocation resolve(Profile profile, String relativePath) {
        Path gameDir = gameDir(profile);
        Path file = gameDir.resolve(relativePath).normalize();
        for (ModType type : getTypes(profile)) {
            for (Map.Entry<Path, @Nullable String> dir : directories(profile, type).entrySet()) {
                if (dir.getKey().equals(file.getParent())) {
                    if (!Files.exists(file)) {
                        throw new NoSuchElementException("No file " + relativePath + " in " + profile.name());
                    }

                    return new ModLocation(type, file, dir.getValue());
                }
            }
        }

        throw new IllegalArgumentException(relativePath + " is not a mod file of " + profile.name());
    }

    public Optional<byte[]> logo(Profile profile, String relativePath) throws IOException {
        ModLocation location = resolve(profile, relativePath);
        ModArchiveInfo info = info(location.file());
        if (info.logo() == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(ModArchiveInfo.readLogo(location.file(), info.logo()));
    }

    /**
     * Copies an uploaded file into the directory of the given mod type.
     *
     * @param world the world for data packs.
     */
    public ModFileDto add(Profile profile, ModType type, @Nullable String world, String fileName, Path upload, boolean overwrite)
        throws IOException {
        String name = Path.of(fileName.replace('\\', '/')).getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        if (name.isBlank() || name.startsWith(".") || EXTENSIONS.stream().noneMatch(lower::endsWith)) {
            throw new IllegalArgumentException("Only .jar and .zip files can be added, got " + fileName);
        }

        Path dir = directory(profile, type, world);
        Files.createDirectories(dir);
        Path target = dir.resolve(name);
        if (!overwrite && (Files.exists(target) || Files.exists(dir.resolve(name + DISABLED)))) {
            throw new FileExistsException(name + " already exists");
        }

        Files.copy(upload, target, StandardCopyOption.REPLACE_EXISTING);
        return describe(profile, type, target, worldName(type, dir), List.of());
    }

    public void delete(Profile profile, String relativePath) throws IOException {
        Path file = resolve(profile, relativePath).file();
        if (Files.isDirectory(file)) {
            try (Stream<Path> walk = Files.walk(file)) {
                for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        } else {
            Files.delete(file);
        }
    }

    /**
     * Enables or disables a mod file by adding/removing the {@code .disabled} suffix, like most launchers do.
     */
    public ModFileDto setEnabled(Profile profile, String relativePath, boolean enabled) throws IOException {
        ModLocation location = resolve(profile, relativePath);
        Path file = location.file();
        String name = file.getFileName().toString();
        boolean disabled = name.endsWith(DISABLED);
        Path target = file;
        if (enabled && disabled) {
            target = file.resolveSibling(name.substring(0, name.length() - DISABLED.length()));
        } else if (!enabled && !disabled) {
            target = file.resolveSibling(name + DISABLED);
        }

        if (!target.equals(file)) {
            if (Files.exists(target)) {
                throw new FileExistsException(target.getFileName() + " already exists");
            }

            Files.move(file, target);
        }

        return describe(profile, location.type(), target, location.world(), List.of());
    }

    public List<RemoteMod> search(Profile profile, ModType type, String query, @Nullable String platform) {
        return distributionService.getByArg(platform).search(query, profile.version(), Set.of(type));
    }

    private ModFileDto describe(Profile profile, ModType type, Path file, @Nullable String world, List<ModFile> mods) {
        String fileName = file.getFileName().toString();
        long size = 0;
        long modified = 0;
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            size = attributes.isDirectory() ? 0 : attributes.size();
            modified = attributes.lastModifiedTime().toMillis();
        } catch (IOException e) {
            LOG.debugf(e, "Failed to read attributes of %s", file);
        }

        ModArchiveInfo info = info(file);
        ModFile first = mods.isEmpty() ? null : mods.getFirst();
        String displayName = first != null && !first.name().equals(fileName) ? first.name() : prettyFileName(fileName);
        // HeadlessMc's toml readers may return null authors/description for mods that do not declare them
        List<String> authors = mods.stream()
            .flatMap(mod -> mod.authors() == null ? Stream.<String>empty() : mod.authors().stream())
            .filter(author -> author != null && !author.isBlank())
            .distinct()
            .toList();
        String description = first == null || first.description() == null
            ? null
            : first.description().filter(d -> !"?".equals(d) && !d.isBlank()).orElse(null);
        Map<String, String> contained = new LinkedHashMap<>();
        mods.forEach(mod -> contained.putIfAbsent(mod.id(), mod.name()));

        return new ModFileDto(
            gameDir(profile).relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/'),
            fileName,
            type.name(),
            world,
            Files.isDirectory(file),
            size,
            modified,
            !fileName.endsWith(DISABLED),
            displayName,
            info.version(),
            description,
            authors,
            contained.entrySet().stream().map(e -> new ModFileDto.ContainedMod(e.getKey(), e.getValue())).toList(),
            info.logo() != null
        );
    }

    private ModArchiveInfo info(Path file) {
        long modified;
        long size;
        try {
            modified = Files.getLastModifiedTime(file).toMillis();
            size = Files.isDirectory(file) ? 0 : Files.size(file);
        } catch (IOException e) {
            return ModArchiveInfo.NONE;
        }

        String key = file.toAbsolutePath().toString();
        CachedInfo cached = infoCache.get(key);
        if (cached != null && cached.modified() == modified && cached.size() == size) {
            return cached.info();
        }

        ModArchiveInfo info = ModArchiveInfo.read(file);
        infoCache.put(key, new CachedInfo(modified, size, info));
        return info;
    }

    /**
     * @return the directories mods of the type are installed to, with the world for data packs.
     */
    private Map<Path, @Nullable String> directories(Profile profile, ModType type) {
        Map<Path, @Nullable String> result = new LinkedHashMap<>();
        if (ModType.DATA_PACK.equals(type)) {
            for (Path world : worldDirs(profile)) {
                result.put(type.directory().getDir(world).toAbsolutePath().normalize(), world.getFileName().toString());
            }
        } else if (!ModType.MOD_PACK.equals(type)) {
            result.put(type.directory().getDir(gameDir(profile)).toAbsolutePath().normalize(), null);
        }

        return result;
    }

    private Path directory(Profile profile, ModType type, @Nullable String world) {
        if (ModType.DATA_PACK.equals(type)) {
            List<Path> worlds = worldDirs(profile);
            Path worldDir = worlds.stream()
                .filter(dir -> world == null ? worlds.size() == 1 : dir.getFileName().toString().equals(world))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                    world == null ? "Choose the world to add the data pack to" : "No world called " + world
                ));
            return type.directory().getDir(worldDir).toAbsolutePath().normalize();
        } else if (ModType.MOD_PACK.equals(type)) {
            throw new IllegalArgumentException("Mod packs cannot be added as files");
        }

        return type.directory().getDir(gameDir(profile)).toAbsolutePath().normalize();
    }

    private @Nullable String worldName(ModType type, Path dir) {
        return ModType.DATA_PACK.equals(type) ? dir.getParent().getFileName().toString() : null;
    }

    /**
     * Mirrors HeadlessMc's world lookup: saves/* for clients, directories with a datapacks folder for servers.
     */
    private List<Path> worldDirs(Profile profile) {
        Path gameDir = gameDir(profile);
        Path dir = profile.side().isClient() ? gameDir.resolve("saves") : gameDir;
        if (!Files.isDirectory(dir)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isDirectory)
                .filter(world -> profile.side().isClient() || Files.isDirectory(world.resolve("datapacks")))
                .sorted()
                .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static List<Path> listFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.list(dir)) {
            return stream.map(path -> path.toAbsolutePath().normalize())
                .filter(path -> !path.getFileName().toString().startsWith("."))
                .filter(path -> Files.isDirectory(path) || isModFileName(path.getFileName().toString()))
                .toList();
        } catch (IOException e) {
            LOG.debugf(e, "Failed to list %s", dir);
            return List.of();
        }
    }

    private static boolean isModFileName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(DISABLED)) {
            lower = lower.substring(0, lower.length() - DISABLED.length());
        }

        String finalLower = lower;
        return EXTENSIONS.stream().anyMatch(finalLower::endsWith);
    }

    private static String prettyFileName(String fileName) {
        String name = fileName.endsWith(DISABLED) ? fileName.substring(0, fileName.length() - DISABLED.length()) : fileName;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static Path gameDir(Profile profile) {
        return profile.path().toAbsolutePath().normalize();
    }

    public record ModLocation(ModType type, Path file, @Nullable String world) {
    }

    private record CachedInfo(long modified, long size, ModArchiveInfo info) {
    }

    public static final class FileExistsException extends RuntimeException {
        FileExistsException(String message) {
            super(message);
        }
    }

}
