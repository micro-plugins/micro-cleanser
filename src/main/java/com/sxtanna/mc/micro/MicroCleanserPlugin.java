package com.sxtanna.mc.micro;

import org.jetbrains.annotations.NotNull;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.sxtanna.mc.micro.flags.MicroFlags;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class MicroCleanserPlugin extends JavaPlugin implements Listener {


    @NotNull
    private final AtomicInteger            taskID = new AtomicInteger(-1);
    @NotNull
    private final Map<UUID, CleansedWorld> worlds = new HashMap<>();


    @Override
    public void onLoad() {
        final var flags = WorldGuard.getInstance().getFlagRegistry();

        flags.register(MicroFlags.CLEANSER_TYPES);
        flags.register(MicroFlags.CLEANSER_DELAY);
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        final var task = this.taskID.getAndSet(getServer().getScheduler().runTaskTimer(this, this::pollWorlds, 0L, 20L).getTaskId());
        if (task != -1) {
            getServer().getScheduler().cancelTask(task);
        }
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(((Plugin) this));

        final var task = this.taskID.getAndSet(-1);
        if (task != -1) {
            getServer().getScheduler().cancelTask(task);
        }

        this.worlds.values().forEach(CleansedWorld::restoreInstant);
        this.worlds.clear();
    }


    private void pollWorlds() {
        this.worlds.values().forEach(CleansedWorld::restoreDelayed);
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onPlace(@NotNull final BlockPlaceEvent event) {
        final var rc = WorldGuard.getInstance().getPlatform().getRegionContainer();

        final var lp = WorldGuardPlugin.inst().wrapPlayer(event.getPlayer());
        final var lc = BukkitAdapter.adapt(event.getBlockPlaced().getLocation());

        final var types = rc.createQuery().queryValue(lc, lp, MicroFlags.CLEANSER_TYPES);
        if (types == null || !types.contains(event.getBlockPlaced().getType())) {
            return;
        }

        final var delay = rc.createQuery().queryValue(lc, lp, MicroFlags.CLEANSER_DELAY);
        if (delay == null || delay <= 0) {
            return;
        }


        final var world = this.worlds.computeIfAbsent(event.getBlockPlaced().getWorld().getUID(), CleansedWorld::new);

        final var now = System.currentTimeMillis();
        //noinspection deprecation
        final var key = event.getBlockPlaced().getBlockKey();

        world.delays.put(key, now + (delay * 1000));

        final var state = event.getBlockReplacedState();
        if (state.getType() != Material.AIR) {
            world.states.put(key, state);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onBreak(@NotNull final BlockBreakEvent event) {

    }


    private static final class CleansedWorld {

        @NotNull
        private final UUID world;

        @NotNull
        private final Long2LongMap               delays = new Long2LongOpenHashMap();
        @NotNull
        private final Long2ObjectMap<BlockState> states = new Long2ObjectOpenHashMap<>();


        public CleansedWorld(@NotNull UUID world) {
            this.world = world;
        }


        public void restoreInstant() {
            final var world = Bukkit.getWorld(this.world);
            if (world == null) {
                return;
            }

            for (final var entry : this.delays.long2LongEntrySet()) {
                final var state = this.states.remove(entry.getLongKey());
                if (state != null) {
                    state.update(true, false);
                } else {
                    //noinspection deprecation
                    world.getBlockAtKey(entry.getLongKey()).setType(Material.AIR, false);
                }
            }

            this.delays.clear();
            this.states.clear();
        }

        public void restoreDelayed() {
            final var world = Bukkit.getWorld(this.world);
            if (world == null) {
                return;
            }

            final var time = System.currentTimeMillis();
            final var iter = this.delays.long2LongEntrySet().iterator();

            while (iter.hasNext()) {
                final var entry = iter.next();

                if (entry.getLongValue() > time) {
                    continue;
                }

                final var state = this.states.remove(entry.getLongKey());
                if (state != null) {
                    state.update(true, false);
                } else {
                    //noinspection deprecation
                    world.getBlockAtKey(entry.getLongKey()).setType(Material.AIR, false);
                }

                iter.remove();
            }

            if (this.delays.isEmpty()) {
                this.states.clear(); // sanity
            }
        }

    }

}
