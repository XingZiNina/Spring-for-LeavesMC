/*
 * DH Support, server-side support for Distant Horizons.
 * Copyright (C) 2024 Jim C K Flaten
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package no.jckf.dhsupport.core.handler;

import no.jckf.dhsupport.core.DhSupport;
import no.jckf.dhsupport.core.configuration.Configuration;
import no.jckf.dhsupport.core.configuration.DhsConfig;
import no.jckf.dhsupport.core.message.plugin.LevelInitMessage;
import no.jckf.dhsupport.core.message.plugin.RemotePlayerConfigMessage;
import no.jckf.dhsupport.core.world.WorldInterface;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PlayerConfigHandler
{
    public final static String USAGE_PERMISSION = "distant_horizons.worlds.%s";

    protected DhSupport dhSupport;

    protected PluginMessageHandler pluginMessageHandler;

    public PlayerConfigHandler(DhSupport dhSupport, PluginMessageHandler pluginMessageHandler)
    {
        this.dhSupport = dhSupport;
        this.pluginMessageHandler = pluginMessageHandler;
    }

    public void register()
    {
        this.pluginMessageHandler.getEventBus().registerHandler(RemotePlayerConfigMessage.class, (configMessage) -> {
            // TODO: Some sort of Player wrapper or interface object. Bukkit classes should not be imported here.
            Player player = Bukkit.getPlayer(configMessage.getSender());
            if (player == null) {
                return;
            }

            WorldInterface world = this.dhSupport.getWorldInterface(player.getWorld().getUID());
            if (world == null) {
                return;
            }

            String permission = USAGE_PERMISSION.formatted(world.getName());
            boolean dhUseIsAllowed = !player.isPermissionSet(permission) || player.hasPermission(permission);

            Configuration dhsConfig = world.getConfig();

            String levelKeyPrefix = dhsConfig.getString(DhsConfig.LEVEL_KEY_PREFIX);
            String levelKey = world.getKey();

            if (levelKeyPrefix != null) {
                levelKey = levelKeyPrefix + levelKey;
            }

            //this.dhSupport.info("Received DH config for " + player.getName() + " in " + levelKey);

            LevelInitMessage levelInitResponse = new LevelInitMessage();
            levelInitResponse.setServerKey(dhsConfig.getString(DhsConfig.SERVER_KEY, ""));
            levelInitResponse.setLevelKey(levelKey);
            levelInitResponse.setTime(System.currentTimeMillis());
            this.pluginMessageHandler.sendPluginMessage(configMessage.getSender(), levelInitResponse);

            Configuration clientConfig = configMessage.toConfiguration();

            // This is not very flexible, but will do for now.
            for (String key : RemotePlayerConfigMessage.KEYS) {
                //this.dhSupport.getLogger().info("Config key " + key + ":");

                Object dhsValue = dhsConfig.get(key);
                Object clientValue = clientConfig.get(key);
                Object keepValue = null;

                if (key.equals(DhsConfig.DISTANT_GENERATION_ENABLED) && !dhUseIsAllowed) {
                    this.dhSupport.info("Player " + player.getName() + " has been denied the use of Distant Horizons through permissions.");
                    keepValue = false;
                }

                // TODO: This is ugly. Move to comparator closures like in DH.
                if (key.equals(DhsConfig.BORDER_CENTER_X)) {
                    keepValue = world.getWorldBorderX();
                } else if (key.equals(DhsConfig.BORDER_CENTER_Z)) {
                    keepValue = world.getWorldBorderZ();
                } else if(key.equals(DhsConfig.BORDER_RADIUS)) {
                    keepValue = world.getWorldBorderRadius();
                }

                if (keepValue == null) {
                    if (dhsValue instanceof Boolean dhsBool && clientValue instanceof Boolean clientBool) {
                        keepValue = dhsBool && clientBool;

                        //this.dhSupport.getLogger().info("    Server " + (dhsBool ? "Y" : "N") + " or client " + (clientBool ? "Y" : "N") + " = " + ((boolean) keepValue ? "Y" : "N"));
                    } else if (dhsValue instanceof Integer dhsInt && clientValue instanceof Integer clientInt) {
                        keepValue = dhsInt < clientInt ? dhsInt : clientInt;

                        //this.dhSupport.getLogger().info("    Server " + dhsInt + " or client " + clientInt + " = " + keepValue);
                    } else {
                        //this.dhSupport.getLogger().info("    Uhh... 😵‍💫");
                    }
                }

                clientConfig.set(key, keepValue == null ? -1 : keepValue);
            }

            this.dhSupport.setPlayerConfiguration(configMessage.getSender(), clientConfig);

            RemotePlayerConfigMessage configResponse = new RemotePlayerConfigMessage();
            configResponse.fromConfiguration(clientConfig);

            this.pluginMessageHandler.sendPluginMessage(configMessage.getSender(), configResponse);
        });
    }
}
