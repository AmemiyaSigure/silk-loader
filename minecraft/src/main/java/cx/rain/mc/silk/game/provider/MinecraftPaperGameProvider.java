package cx.rain.mc.silk.game.provider;

import cx.rain.mc.silk.Silk;
import cx.rain.mc.silk.logging.SilkLogHandler;
import cx.rain.mc.silk.patch.SilkBrandingPatch;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.game.GameProviderHelper;
import net.fabricmc.loader.impl.game.minecraft.McVersion;
import net.fabricmc.loader.impl.game.minecraft.McVersionLookup;
import net.fabricmc.loader.impl.game.minecraft.patch.EntrypointPatch;
import net.fabricmc.loader.impl.game.patch.GameTransformer;
import net.fabricmc.loader.impl.metadata.BuiltinModMetadata;
import net.fabricmc.loader.impl.metadata.ModDependencyImpl;
import net.fabricmc.loader.impl.util.Arguments;
import net.fabricmc.loader.impl.util.SystemProperties;
import net.fabricmc.loader.impl.util.log.Log;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class MinecraftPaperGameProvider implements GameProvider {
	public static final GameTransformer TRANSFORMER = new GameTransformer(transormer ->
			Arrays.asList(new EntrypointPatch(transormer), new SilkBrandingPatch(transormer)));

	private EnvType environment;
	private String entrypoint;
	private McVersion version;
	private Path gameJar;
	private Path realmsJar;
	private boolean hasModLoader = false;
	private Arguments arguments;

	@Override
	public String getGameId() {
		return "minecraft";
	}

	@Override
	public String getGameName() {
		return "Minecraft";
	}

	@Override
	public String getRawGameVersion() {
		return version.getRaw();
	}

	@Override
	public String getNormalizedGameVersion() {
		return version.getNormalized();
	}

	@Override
	public Collection<BuiltinMod> getBuiltinMods() {
		List<BuiltinMod> builtinMods = new ArrayList<>();

		// Make builtin mod: silk, and its dependency: paper.
		List<ModDependency> silkDependencies = new ArrayList<>();

		silkDependencies.add(makeModDependency("paper", ">=1.17.1")); // Todo: Show paper build id.

		if (version.getClassVersion().isPresent()) {
			int classVer = version.getClassVersion().getAsInt() - 44; // Class version to java version.

			silkDependencies.add(makeModDependency("java", String.format(">=%d", classVer)));
		}

		builtinMods.add(makeBuiltinMod("silk", "Silk", Silk.VERSION,
				getGameJar(), silkDependencies));

		return builtinMods;
	}

	private BuiltinMod makeBuiltinMod(String id, String name, String version, Path jar,
									  List<ModDependency> dependencies) {
		BuiltinModMetadata.Builder metadataBuilder = new BuiltinModMetadata.Builder(id, version).setName(name);

		for (ModDependency d : dependencies) {
			metadataBuilder.addDependency(d);
		}

		return new BuiltinMod(jar, metadataBuilder.build());
	}

	private ModDependency makeModDependency(String modid, String version) {
		try {
			return new ModDependencyImpl(ModDependency.Kind.DEPENDS, modid, Collections.singletonList(version));
		} catch (VersionParsingException ex) {
			throw new RuntimeException(ex);
		}
	}

	public Path getGameJar() {
		return gameJar;
	}

	@Override
	public String getEntrypoint() {
		return entrypoint;
	}

	@Override
	public Path getLaunchDirectory() {
		if (getArguments() == null) {
			return new File(".").toPath();
		}

		return getLaunchDirectory(getArguments()).toPath();
	}

	private static File getLaunchDirectory(Arguments args) {
		return new File(args.getOrDefault("gameDir", "."));
	}

	@Override
	public boolean isObfuscated() {
		return true; // Maybe?
	}

	@Override
	public boolean requiresUrlClassLoader() {
		return hasModLoader;
	}

	@Override
	public List<Path> getGameContextJars() {
		List<Path> list = new ArrayList<>();
		list.add(gameJar);

		if (realmsJar != null) {
			list.add(realmsJar);
		}
		return list;
	}

	@Override
	public boolean isEnabled() {
		// Why use silk if you do not want to enable bukkit?
		return true;
	}

	@Override
	public boolean locateGame(EnvType envType, String[] args, ClassLoader loader) {
		environment = envType;
		arguments = new Arguments();
		arguments.parse(args);

		String paperEntrypoint;
		if (envType == EnvType.CLIENT) {
			throw new RuntimeException("Do you want to run paper on your client? No way.");
		} else {
			paperEntrypoint = "org.bukkit.craftbukkit.Main";
		}

		List<String> entrypoints = Collections.singletonList(paperEntrypoint);

		Optional<GameProviderHelper.EntrypointResult> entrypointResult =
				GameProviderHelper.findFirstClass(loader, entrypoints);

		if (!entrypointResult.isPresent()) {
			return false;
		}

		Log.init(new SilkLogHandler(), true);

		entrypoint = entrypointResult.get().entrypointName;
		gameJar = entrypointResult.get().entrypointPath;
		realmsJar = GameProviderHelper.getSource(loader, "realmsVersion").orElse(null);
		hasModLoader = GameProviderHelper.getSource(loader, "ModLoader.class").isPresent();

		String versionString = arguments.remove(Arguments.GAME_VERSION);
		if (versionString == null) {
			versionString = System.getProperty(SystemProperties.GAME_VERSION);
		}
		version = McVersionLookup.getVersion(gameJar, entrypoints, versionString);

		processArgumentsMap(arguments);

		return true;
	}

	private static void processArgumentsMap(Arguments args) {
		args.remove("version");
		args.remove("gameDir");
		args.remove("assetsDir");
	}

	@Override
	public GameTransformer getEntrypointTransformer() {
		return TRANSFORMER;
	}

	@Override
	public void launch(ClassLoader loader) {
		String target = entrypoint;
		try {
			Class<?> clazz = loader.loadClass(target);
			Method method = clazz.getMethod("main", String[].class);
			method.invoke(null, (Object) arguments.toArray());
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	@Override
	public Arguments getArguments() {
		return arguments;
	}

	@Override
	public String[] getLaunchArguments(boolean sanitize) {
		if (arguments == null) {
			return new String[0];
		}

		if (!sanitize) {
			return arguments.toArray();
		}

		List<String> list = new ArrayList<>(Arrays.asList(arguments.toArray()));

		for (int i = 0; i < list.toArray().length; i++) {
			if (list.get(i).equals("--accessToken")) {
				list.remove(list.get(i));
				list.remove(list.get(i + 1)); // I hope people are okay. 但愿人没事。
			}
		}

		return list.toArray(new String[0]);
	}

	@Override
	public boolean canOpenErrorGui() {
		if (System.getProperty("os.name").equals("Mac OS X")) {
			return false;
		}

		if (arguments == null || environment == EnvType.CLIENT) {
			return true;
		}

		List<String> extras = arguments.getExtraArgs();
		return !extras.contains("nogui") && !extras.contains("--nogui");
	}
}
