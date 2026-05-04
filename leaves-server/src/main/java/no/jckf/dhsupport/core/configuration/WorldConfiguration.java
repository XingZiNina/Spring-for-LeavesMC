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

package no.jckf.dhsupport.core.configuration;

import no.jckf.dhsupport.core.world.WorldInterface;
import org.checkerframework.checker.nullness.qual.PolyNull;

import javax.annotation.Nullable;

public class WorldConfiguration extends Configuration
{
    protected static String WORLD_PREFIX = "worlds.%s.";

    protected WorldInterface world;

    protected Configuration config;

    public WorldConfiguration(WorldInterface world, Configuration config)
    {
        this.world = world;
        this.config = config;
    }

    @Override
    public void set(String key, @Nullable Object value)
    {
        this.config.set(WORLD_PREFIX.formatted(this.world.getName()) + key, value);
    }

    @Override
    public void unset(String key)
    {
        this.config.unset(WORLD_PREFIX.formatted(this.world.getName()) + key);
    }

    @Override
    public @PolyNull Object get(String key, @PolyNull Object defaultValue)
    {
        Object specific = this.config.get(WORLD_PREFIX.formatted(this.world.getName()) + key);

        return specific == null ? this.config.get(key, defaultValue) : specific;
    }
}
