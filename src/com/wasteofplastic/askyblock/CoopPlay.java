/*******************************************************************************
 * This file is part of ASkyBlock.
 *
 *     ASkyBlock is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     ASkyBlock is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with ASkyBlock.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/

package com.wasteofplastic.askyblock;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.wasteofplastic.askyblock.events.CoopJoinEvent;
import com.wasteofplastic.askyblock.events.CoopLeaveEvent;
import com.wasteofplastic.askyblock.util.Util;

/**
 * Handles coop play interactions
 * 
 * @author tastybento
 * 
 */
public class CoopPlay {
    private static CoopPlay instance = new CoopPlay(ASkyBlock.getPlugin());
    // Stores all the coop islands, the coop player, the location and the
    // inviter
    private HashMap<UUID, HashMap<Location, UUID>> coopPlayers = new HashMap<UUID, HashMap<Location, UUID>>();
    // Defines whether a player is on a coop island or not
    // private HashMap<UUID, Location> onCoopIsland = new HashMap<UUID,
    // Location>();
    private ASkyBlock plugin;

    /**
     * @param instance
     */
    private CoopPlay(ASkyBlock plugin) {
        this.plugin = plugin;
    }

    /**
     * Adds a player to an island as a coop player.
     * 
     * @param requester
     * @param newPlayer
     */
    public void addCoopPlayer(Player requester, Player newPlayer) {
        // plugin.getLogger().info("DEBUG: adding coop player");
        // Find out which island this coop player is being requested to join
        Location island = null;
        if (plugin.getPlayers().inTeam(requester.getUniqueId())) {
            island = plugin.getPlayers().getTeamIslandLocation(requester.getUniqueId());
            // Tell the team owner
            UUID leaderUUID = plugin.getPlayers().getTeamLeader(requester.getUniqueId());
            // Tell all the team members
            for (UUID member : plugin.getPlayers().getMembers(leaderUUID)) {
                // plugin.getLogger().info("DEBUG: " + member.toString());
                if (!member.equals(requester.getUniqueId())) {
                    Player player = plugin.getServer().getPlayer(member);
                    if (player != null) {
                        player.sendMessage(ChatColor.GOLD
                                + plugin.myLocale(player.getUniqueId()).coopInvited.replace("[name]", requester.getDisplayName()).replace("[player]", newPlayer.getDisplayName()));
                        player.sendMessage(ChatColor.GOLD + plugin.myLocale(player.getUniqueId()).coopUseExpel);
                    } else {
                        if (member.equals(leaderUUID)) {
                            // offline - tell leader
                            plugin.getMessages().setMessage(leaderUUID,
                                    plugin.myLocale(leaderUUID).coopInvited.replace("[name]", requester.getDisplayName()).replace("[player]", newPlayer.getDisplayName()));
                        }
                    }
                }
            }
        } else {
            island = plugin.getPlayers().getIslandLocation(requester.getUniqueId());
        }
        // Add the coop to the list. If the location already exists then the new
        // requester will replace the old
        if (coopPlayers.containsKey(newPlayer.getUniqueId())) {
            // This is an existing player in the list
            // Add this island to the set
            coopPlayers.get(newPlayer.getUniqueId()).put(island, requester.getUniqueId());
        } else {
            // First time. Create the hashmap
            HashMap<Location, UUID> loc = new HashMap<Location, UUID>();
            loc.put(island, requester.getUniqueId());
            coopPlayers.put(newPlayer.getUniqueId(), loc);
        }
        // Fire event
        Island coopIsland = plugin.getGrid().getIslandAt(island);
        final CoopJoinEvent event = new CoopJoinEvent(newPlayer.getUniqueId(), coopIsland, requester.getUniqueId());
        plugin.getServer().getPluginManager().callEvent(event);
    }

    /**
     * Removes a coop player
     * 
     * @param requester
     * @param targetPlayer
     * @return true if the player was a coop player, and false if not
     */
    public boolean removeCoopPlayer(Player requester, Player targetPlayer) {
        return removeCoopPlayer(requester, targetPlayer.getUniqueId());
    }

    /**
     * Returns the list of islands that this player is coop on or empty if none
     * 
     * @param player
     * @return Set of locations
     */
    public Set<Location> getCoopIslands(Player player) {
        if (coopPlayers.containsKey(player.getUniqueId())) {
            return coopPlayers.get(player.getUniqueId()).keySet();
        }
        return new HashSet<Location>();
    }

    /**
     * Gets a list of all the players that are currently coop on this island
     * 
     * @param island
     * @return List of UUID's of players that have coop rights to the island
     */
    public List<UUID> getCoopPlayers(Location island) {
        List<UUID> result = new ArrayList<UUID>();
        for (UUID player : coopPlayers.keySet()) {
            if (coopPlayers.get(player).containsKey(island)) {
                result.add(player);
            }
        }
        return result;
    }

    /**
     * Removes all coop players from an island - used when doing an island reset
     * 
     * @param player
     */
    public void clearAllIslandCoops(UUID player) {
        // Remove any and all islands related to requester
        Island island = plugin.getGrid().getIsland(player);
        if (island == null) {
            return;
        }
        for (HashMap<Location, UUID> coopPlayer : coopPlayers.values()) {
            for (UUID inviter : coopPlayer.values()) {
                // Fire event
                final CoopLeaveEvent event = new CoopLeaveEvent(player, inviter, island);
                plugin.getServer().getPluginManager().callEvent(event);
            }
            coopPlayer.remove(island.getCenter());
        }
    }

    /**
     * Deletes all coops from player.
     * Used when player logs out.
     * 
     * @param player
     */
    public void clearMyCoops(Player player) {
        //plugin.getLogger().info("DEBUG: clear my coops - clearing coops memberships of " + player.getName());
        Island coopIsland = plugin.getGrid().getIsland(player.getUniqueId());
        if (coopPlayers.get(player.getUniqueId()) != null) {
            //plugin.getLogger().info("DEBUG: " + player.getName() + " is a member of a coop");
            for (UUID inviter : coopPlayers.get(player.getUniqueId()).values()) {
                // Fire event
                //plugin.getLogger().info("DEBUG: removing invite from " + plugin.getServer().getPlayer(inviter).getName());
                final CoopLeaveEvent event = new CoopLeaveEvent(player.getUniqueId(), inviter, coopIsland);
                plugin.getServer().getPluginManager().callEvent(event);
            }
            coopPlayers.remove(player.getUniqueId());
        }
    }

    public void saveCoops() {
        File coopFile = new File(plugin.getDataFolder(), "coops.yml");
        YamlConfiguration coopConfig = new YamlConfiguration();
        for (UUID playerUUID : coopPlayers.keySet()) {
            coopConfig.set(playerUUID.toString(), getMyCoops(playerUUID));
        }
        try {
            coopConfig.save(coopFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save coop.yml file!");
        }
    }
    
    public void loadCoops() {
        File coopFile = new File(plugin.getDataFolder(), "coops.yml");
        if (!coopFile.exists()) {
            return;
        }
        YamlConfiguration coopConfig = new YamlConfiguration();
        try {
            coopConfig.load(coopFile);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().severe("Could not load coop.yml file!");
        }
        // Run through players
        for (String playerUUID : coopConfig.getValues(false).keySet()) {
          try {
              setMyCoops(UUID.fromString(playerUUID), coopConfig.getStringList(playerUUID));
          } catch (Exception e) {
              plugin.getLogger().severe("Could not load coops for player UUID " + playerUUID + " skipping...");
          }
        }
    }
    
    /**
     * Gets a serialize list of all the coops for this player. Used when saving the player
     * @param playerUUID
     * @return List of island location | uuid of invitee
     */
    private List<String> getMyCoops(UUID playerUUID) {
        List<String> result = new ArrayList<String>();
        if (coopPlayers.containsKey(playerUUID)) {
            for (Entry<Location, UUID> entry : coopPlayers.get(playerUUID).entrySet()) {
                result.add(Util.getStringLocation(entry.getKey()) + "|" + entry.getValue().toString());
            }
        }
        return result;
    }  

    /**
     * Sets a player's coops from string. Used when loading a player.
     * @param playerUUID
     * @param coops
     */
    private void setMyCoops(UUID playerUUID, List<String> coops) {
        try {
            HashMap<Location, UUID> temp = new HashMap<Location, UUID>();
            for (String coop : coops) {
                String[] split = coop.split("\\|");
                if (split.length == 2) {
                    temp.put(Util.getLocationString(split[0]), UUID.fromString(split[1])); 
                }
            }
            coopPlayers.put(playerUUID, temp);
        } catch (Exception e) {
            plugin.getLogger().severe("Could not load coops for UUID " + playerUUID);
            e.printStackTrace();
        }
    }  

    /**
     * Goes through all the known coops and removes any that were invited by
     * clearer. Returns any inventory
     * Can be used when clearer logs out or when they are kicked or leave a team
     * 
     * @param clearer
     * @param target
     */
    public void clearMyInvitedCoops(Player clearer) {
        //plugin.getLogger().info("DEBUG: clear my invited coops - clearing coops that were invited by " + clearer.getName());
        Island coopIsland = plugin.getGrid().getIsland(clearer.getUniqueId());
        for (UUID players : coopPlayers.keySet()) {
            Iterator<Entry<Location, UUID>> en = coopPlayers.get(players).entrySet().iterator();
            while (en.hasNext()) {
                Entry<Location, UUID> entry = en.next();
                // Check if this invite was sent by clearer
                if (entry.getValue().equals(clearer.getUniqueId())) {
                    // Yes, so get the invitee (target)
                    Player target = plugin.getServer().getPlayer(players);
                    if (target != null) {
                        target.sendMessage(ChatColor.RED + "You are no longer a coop player with " + clearer.getDisplayName() + ".");
                    }
                    // Fire event
                    final CoopLeaveEvent event = new CoopLeaveEvent(players, clearer.getUniqueId(), coopIsland);
                    plugin.getServer().getPluginManager().callEvent(event);
                    // Mark them as no longer on a coop island
                    // setOnCoopIsland(players, null);
                    // Remove this entry
                    en.remove();
                }
            }
        }
    }

    /**
     * Removes all coop players from an island - used when doing an island reset
     * 
     * @param player
     */
    public void clearAllIslandCoops(Location island) {
        if (island == null) {
            return;
        }
        Island coopIsland = plugin.getGrid().getIslandAt(island);
        // Remove any and all islands related to requester
        for (HashMap<Location, UUID> coopPlayer : coopPlayers.values()) {
            // Fire event
            final CoopLeaveEvent event = new CoopLeaveEvent(coopPlayer.get(island), coopIsland.getOwner(), coopIsland);
            plugin.getServer().getPluginManager().callEvent(event);
            coopPlayer.remove(island);
        }
    }

    /**
     * @return the instance
     */
    public static CoopPlay getInstance() {
        return instance;
    }

    public boolean removeCoopPlayer(Player requester, UUID targetPlayerUUID) {
        boolean removed = false;
        // Only bother if the player is in the list
        if (coopPlayers.containsKey(targetPlayerUUID)) {
            // Remove any and all islands related to requester
            /*
        if (plugin.getPlayers().getTeamIslandLocation(requester.getUniqueId()) != null) {
        removed = coopPlayers.get(targetPlayer.getUniqueId()).remove(plugin.getPlayers().getTeamIslandLocation(requester.getUniqueId())) != null ? true
            : false;
        }*/
            if (plugin.getPlayers().getIslandLocation(requester.getUniqueId()) != null) {
                removed = coopPlayers.get(targetPlayerUUID).remove(plugin.getPlayers().getIslandLocation(requester.getUniqueId())) != null ? true
                        : false;
                // Fire event
                Island coopIsland = plugin.getGrid().getIsland(requester.getUniqueId());
                final CoopLeaveEvent event = new CoopLeaveEvent(targetPlayerUUID, requester.getUniqueId(), coopIsland);
                plugin.getServer().getPluginManager().callEvent(event);
            }
        }
        return removed;
    }
}