/*
 * This file is part of Hawk Anticheat.
 * Copyright (C) 2018 Hawk Development Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.islandscout.hawk.module;

import me.islandscout.hawk.Hawk;
import me.islandscout.hawk.util.ConfigHelper;
import me.islandscout.hawk.util.Pair;
import me.islandscout.hawk.util.ServerUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.DayOfWeek;
import java.util.*;

public class PunishmentScheduler {

    //Allows hourly, daily, and weekly schedule setup for punishing players.

    //TODO load from file & save to file

    private Map<UUID, Pair<String, Boolean>> convicts; //UUID mapped to reason and whether they're authorized for punishment or not
    private int taskId;
    private Hawk hawk;
    private Schedule schedule;
    private boolean justExecuted;
    private String cmd;
    private boolean enabled;
    private boolean ignoreIfServerOverloaded;
    private int pingThreshold;
    private boolean requireAuthorization;

    private final String DEFAULT_REASON;
    private final String USER_ADDED;
    private final String USER_REMOVED;
    private final String USER_AUTHORIZED;

    public PunishmentScheduler(Hawk hawk) {
        convicts = new HashMap<>();
        this.hawk = hawk;

        USER_ADDED = ConfigHelper.getOrSetDefault("&6%player% has been added to punishment system.", hawk.getMessages(), "punishmentScheduler.userAdded");
        USER_REMOVED = ConfigHelper.getOrSetDefault("&6%player% has been removed from punishment system.", hawk.getMessages(), "punishmentScheduler.userRemoved");
        USER_AUTHORIZED = ConfigHelper.getOrSetDefault("&6%player% has been authorized for punishment.", hawk.getMessages(), "punishmentScheduler.userAuthorized");

        enabled = ConfigHelper.getOrSetDefault(false, hawk.getConfig(), "punishmentScheduler.enabled");
        cmd = ConfigHelper.getOrSetDefault("ban %player% %reason%", hawk.getConfig(), "punishmentScheduler.command");
        DEFAULT_REASON = ConfigHelper.getOrSetDefault("Illegal game modification", hawk.getConfig(), "punishmentScheduler.defaultReason");
        String rawSchedule = ConfigHelper.getOrSetDefault("SUNDAY 0 0", hawk.getConfig(), "punishmentScheduler.schedule");
        ignoreIfServerOverloaded = ConfigHelper.getOrSetDefault(true, hawk.getConfig(), "punishmentScheduler.ignoreIfServerOverloaded");
        pingThreshold = ConfigHelper.getOrSetDefault(-1, hawk.getConfig(), "punishmentScheduler.pingThreshold");
        requireAuthorization = ConfigHelper.getOrSetDefault(false, hawk.getConfig(), "punishmentScheduler.requireAuthorization");

        String[] schedule = rawSchedule.split(" ");
        int sDayOfWeek = schedule[0].equals("*") ? -1 : DayOfWeek.valueOf(schedule[0].toUpperCase()).getValue();
        int sHour = schedule[1].equals("*") ? -1 : Integer.parseInt(schedule[1]);
        int sMinute = schedule[2].equals("*") ? -1 : Integer.parseInt(schedule[2]);
        this.schedule = new Schedule(sDayOfWeek, sHour, sMinute);
    }

    public void start() {
        if(!enabled)
            return;
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(hawk, new Runnable() {
            @Override
            public void run() {

                if(schedule.isNow()) {
                    if(!justExecuted) {
                        punishTime();
                    }
                    justExecuted = true;
                }
                else {
                    justExecuted = false;
                }

            }
        }, 0L, 20L);
    }

    public void stop() {
        if(!enabled)
            return;
        Bukkit.getScheduler().cancelTask(taskId);
    }

    private void punishTime() {
        for (UUID currUuid : convicts.keySet()) {
            Pair<String, Boolean> pair = convicts.get(currUuid);
            if (pair.getValue()) {
                OfflinePlayer p = Bukkit.getOfflinePlayer(currUuid);
                //TODO dont forget to parse prefix. should there be a utility class for this?
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
                convicts.remove(currUuid);
            }

        }
    }

    public Result add(Player p, String reason) {
        if(!enabled)
            return Result.DISABLED;
        if(ignoreIfServerOverloaded && ServerUtils.getStress() > 1)
            return Result.SERVER_OVERLOADED;
        if(pingThreshold > -1 && ServerUtils.getPing(p) > pingThreshold)
            return Result.PING_LIMIT_EXCEEDED;
        boolean authorized = !requireAuthorization;
        String processedReason = reason == null ? DEFAULT_REASON : reason;
        convicts.put(p.getUniqueId(), new Pair<>(processedReason, authorized));
        return Result.PASSED;
    }

    public Result remove(Player p) {
        if(!enabled)
            return Result.DISABLED;
        convicts.remove(p.getUniqueId());
        return Result.PASSED;
    }

    public void authorize(Player p) {
        UUID uuid = p.getUniqueId();
        if(convicts.containsKey(uuid))
            convicts.get(uuid).setValue(true);
    }

    public void load() {

    }

    public void save() {

    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean status) {
        if(!status)
            stop();
        enabled = status;
    }

    public Map<UUID, Pair<String, Boolean>> getConvicts() {
        return convicts;
    }

    private class Schedule {

        private int dayOfWeek;
        private int hour;
        private int minute;

        Schedule(int dayOfWeek, int hour, int minute) {
            this.dayOfWeek = dayOfWeek;
            this.hour = hour;
            this.minute = minute;
        }

        boolean isNow() {
            Calendar now = Calendar.getInstance();
            int compareDayOfWeek = now.get(Calendar.DAY_OF_WEEK);
            int compareHour = now.get(Calendar.HOUR_OF_DAY);
            int compareMinute = now.get(Calendar.MINUTE);
            if(dayOfWeek != -1 && compareDayOfWeek != dayOfWeek) {
                return false;
            }
            if(hour != -1 && compareHour != hour) {
                return false;
            }
            return minute == -1 || compareMinute == minute;
        }
    }

    public enum Result {
        PASSED,
        DISABLED,
        SERVER_OVERLOADED,
        PING_LIMIT_EXCEEDED
    }
}
