package com.github.tobi406.aliasr;

import com.google.inject.Inject;
import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Plugin(
        id = "aliasr",
        name = "Aliasr",
        version = "1.1-BETA",
        description = "Simple Alias manager using RegEx",
        authors = {"Tobi406"}
)
@NullMarked
public class AliasrPlugin {
    private static AliasrPlugin instance;

    private final CommandManager commandManager;
    private Logger logger;
    private Toml config;

    private Path folder;

    List<String> registeredCommands = new ArrayList<>();

    @Inject
    public AliasrPlugin(final ProxyServer server, final Logger logger, @DataDirectory final Path folder) {
        this.commandManager = server.getCommandManager();
        this.logger = logger;
        this.folder = folder;
        this.config = this.loadConfig(folder);

        this.registerCommands();

        instance = this;
    }

    public static AliasrPlugin getInstance() {
        return instance;
    }

    private Toml loadConfig(final Path path) {
        final File folder = path.toFile();
        final File file = new File(folder, "config.toml");
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        if (!file.exists()) {
            try (final InputStream input = getClass().getResourceAsStream("/" + file.getName())) {
                if (input != null) {
                    Files.copy(input, file.toPath());
                } else {
                    file.createNewFile();
                }
            } catch (final IOException exception) {
                exception.printStackTrace();
                return null;
            }
        }

        return new Toml().read(file);
    }

    public void reload() {
        this.config = this.loadConfig(this.folder);

        this.unregisterCommands();
        this.registerCommands();
    }

    public void registerCommands() {
        final CommandMeta aliasrMeta = this.commandManager.metaBuilder("aliasr").plugin(this).build();
        this.commandManager.register(aliasrMeta, new AliasrCommand());
        this.registeredCommands.add("aliasr");
        this.logger.info("Registered plugin command \"aliasr\"");

        final List<HashMap<String, String>> aliases = this.config.getList("aliases");

        aliases.forEach(hashMap -> {
            final CommandMeta meta = this.commandManager.metaBuilder(hashMap.get("name")).plugin(this).build();
            this.commandManager.register(meta, new SimpleCommand() {
                private final String args = hashMap.get("args");
                private final String command = hashMap.get("command");
                private final String commandArgs = hashMap.get("commandArgs");

                @Override
                public void execute(final Invocation invocation) {
                    final String joinedArgs = String.join(" ", invocation.arguments());

                    AliasrPlugin.getInstance().commandManager.executeAsync(
                            invocation.source(),
                            this.command
                                    + (!args.isEmpty() ? (" " + joinedArgs.replaceAll(this.args, this.commandArgs)) : "")
                    );
                }
            });

            this.registeredCommands.add(hashMap.get("name"));
            this.logger.info("Registered alias command \"{}\"", hashMap.get("name"));
        });
    }

    public void unregisterCommands() {
        this.registeredCommands.forEach(this.commandManager::unregister);
    }
}
