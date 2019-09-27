/*
 * Copyright (C) 1997-2001 Id Software, Inc.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.
 * 
 * See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place - Suite 330, Boston, MA 02111-1307, USA.
 *  
 */

// Created on 28.12.2003 by RST.

package jake2.game;

import jake2.game.monsters.M_Player;
import jake2.qcommon.*;
import jake2.qcommon.network.MulticastTypes;
import jake2.qcommon.network.NetworkCommands;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;

public class PlayerClient {

    public static int player_die_i = 0;
    
    /**
     * player_die. 
     */
    static EntDieAdapter player_die = new EntDieAdapter() {
    	public String getID() { return "player_die"; }
        public void die(SubgameEntity self, SubgameEntity inflictor, SubgameEntity attacker,
                        int damage, float[] point) {
            int n;
    
            Math3D.VectorClear(self.avelocity);
    
            self.takedamage = Defines.DAMAGE_YES;
            self.movetype = GameDefines.MOVETYPE_TOSS;
    
            self.s.modelindex2 = 0; // remove linked weapon model
    
            self.s.angles[0] = 0;
            self.s.angles[2] = 0;
    
            self.s.sound = 0;
            gclient_t client = (gclient_t) self.client;
            client.weapon_sound = 0;
    
            self.maxs[2] = -8;
    
            // self.solid = SOLID_NOT;
            self.svflags |= Defines.SVF_DEADMONSTER;
    
            if (self.deadflag == 0) {
                client.respawn_time = GameBase.level.time + 1.0f;
                PlayerClient.LookAtKiller(self, inflictor, attacker);
                client.getPlayerState().pmove.pm_type = Defines.PM_DEAD;
                ClientObituary(self, inflictor, attacker);
                PlayerClient.TossClientWeapon(self);
                if (GameBase.deathmatch.value != 0) {
                    Com.Printf("NOT IMPLEMENTED!");
                    //Cmd.Help_f(self); // show scores
                }
    
                // clear inventory
                // this is kind of ugly, but it's how we want to handle keys in
                // coop
                for (n = 0; n < GameBase.game.num_items; n++) {
                    if (GameBase.coop.value != 0
                            && (GameItemList.itemlist[n].flags & GameDefines.IT_KEY) != 0)
                        client.resp.coop_respawn.inventory[n] = client.pers.inventory[n];
                    client.pers.inventory[n] = 0;
                }
            }
    
            // remove powerups
            client.quad_framenum = 0;
            client.invincible_framenum = 0;
            client.breather_framenum = 0;
            client.enviro_framenum = 0;
            self.flags &= ~GameDefines.FL_POWER_ARMOR;
    
            if (self.health < -40) { // gib
                GameBase.gi
                        .sound(self, Defines.CHAN_BODY, GameBase.gi
                                .soundindex("misc/udeath.wav"), 1,
                                Defines.ATTN_NORM, 0);
                for (n = 0; n < 4; n++)
                    GameMisc.ThrowGib(self, "models/objects/gibs/sm_meat/tris.md2",
                            damage, GameDefines.GIB_ORGANIC);
                GameMisc.ThrowClientHead(self, damage);
    
                self.takedamage = Defines.DAMAGE_NO;
            } else { // normal death
                if (self.deadflag == 0) {
    
                    player_die_i = (player_die_i + 1) % 3;
                    // start a death animation
                    client.anim_priority = Defines.ANIM_DEATH;
                    if ((client.getPlayerState().pmove.pm_flags & Defines.PMF_DUCKED) != 0) {
                        self.s.frame = M_Player.FRAME_crdeath1 - 1;
                        client.anim_end = M_Player.FRAME_crdeath5;
                    } else
                        switch (player_die_i) {
                        case 0:
                            self.s.frame = M_Player.FRAME_death101 - 1;
                            client.anim_end = M_Player.FRAME_death106;
                            break;
                        case 1:
                            self.s.frame = M_Player.FRAME_death201 - 1;
                            client.anim_end = M_Player.FRAME_death206;
                            break;
                        case 2:
                            self.s.frame = M_Player.FRAME_death301 - 1;
                            client.anim_end = M_Player.FRAME_death308;
                            break;
                        }
    
                    GameBase.gi.sound(self, Defines.CHAN_VOICE, GameBase.gi
                            .soundindex("*death" + ((Lib.rand() % 4) + 1)
                                    + ".wav"), 1, Defines.ATTN_NORM, 0);
                }
            }
    
            self.deadflag = GameDefines.DEAD_DEAD;
    
            GameBase.gi.linkentity(self);
        }
    };
    static EntThinkAdapter SP_FixCoopSpots = new EntThinkAdapter() {
    	public String getID() { return "SP_FixCoopSpots"; }
        public boolean think(SubgameEntity self) {

            float[] d = { 0, 0, 0 };

            SubgameEntity spot;
            EdictIterator es = null;
    
            while (true) {
                es = GameBase.G_Find(es, GameBase.findByClass,
                        "info_player_start");
    
                if (es == null)
                    return true;
                
                spot = es.o;
                
                if (spot.targetname == null)
                    continue;
                Math3D.VectorSubtract(self.s.origin, spot.s.origin, d);
                if (Math3D.VectorLength(d) < 384) {
                    if ((self.targetname == null)
                            || Lib.Q_stricmp(self.targetname, spot.targetname) != 0) {
                        // gi.dprintf("FixCoopSpots changed %s at %s targetname
                        // from %s to %s\n", self.classname,
                        // vtos(self.s.origin), self.targetname,
                        // spot.targetname);
                        self.targetname = spot.targetname;
                    }
                    return true;
                }
            }
        }
    };
    static EntThinkAdapter SP_CreateCoopSpots = new EntThinkAdapter() {
    	public String getID() { return "SP_CreateCoopSpots"; }
        public boolean think(SubgameEntity self) {

            if (Lib.Q_stricmp(GameBase.level.mapname, "security") == 0) {
                SubgameEntity spot = GameUtil.G_Spawn();
                spot.classname = "info_player_coop";
                spot.s.origin[0] = 188 - 64;
                spot.s.origin[1] = -164;
                spot.s.origin[2] = 80;
                spot.targetname = "jail3";
                spot.s.angles[1] = 90;
    
                spot = GameUtil.G_Spawn();
                spot.classname = "info_player_coop";
                spot.s.origin[0] = 188 + 64;
                spot.s.origin[1] = -164;
                spot.s.origin[2] = 80;
                spot.targetname = "jail3";
                spot.s.angles[1] = 90;
    
                spot = GameUtil.G_Spawn();
                spot.classname = "info_player_coop";
                spot.s.origin[0] = 188 + 128;
                spot.s.origin[1] = -164;
                spot.s.origin[2] = 80;
                spot.targetname = "jail3";
                spot.s.angles[1] = 90;
            }
            return true;
        }
    };
    // player pain is handled at the end of the frame in P_DamageFeedback
    static EntPainAdapter player_pain = new EntPainAdapter() {
    	public String getID() { return "player_pain"; }
        public void pain(SubgameEntity self, SubgameEntity other, float kick, int damage) {
        }
    };
    static EntDieAdapter body_die = new EntDieAdapter() {
    	public String getID() { return "body_die"; }
        public void die(SubgameEntity self, SubgameEntity inflictor, SubgameEntity attacker,
                int damage, float[] point) {
    
            int n;
    
            if (self.health < -40) {
                GameBase.gi.sound(self, Defines.CHAN_BODY, 
                		GameBase.gi.soundindex("misc/udeath.wav"), 1, Defines.ATTN_NORM, 0);
                for (n = 0; n < 4; n++)
                    GameMisc.ThrowGib(self, "models/objects/gibs/sm_meat/tris.md2", damage,
                            GameDefines.GIB_ORGANIC);
                self.s.origin[2] -= 48;
                GameMisc.ThrowClientHead(self, damage);
                self.takedamage = Defines.DAMAGE_NO;
            }
        }
    };

    private static SubgameEntity pm_passent;
    // pmove doesn't need to know about passent and contentmask
    public static pmove_t.TraceAdapter PM_trace = new pmove_t.TraceAdapter() {
    
        public trace_t trace(float[] start, float[] mins, float[] maxs,
                float[] end) {
            if (pm_passent.health > 0)
                return GameBase.gi.trace(start, mins, maxs, end, pm_passent,
                        Defines.MASK_PLAYERSOLID);
            else
                return GameBase.gi.trace(start, mins, maxs, end, pm_passent,
                        Defines.MASK_DEADSOLID);
        }
    
    };

    /**
     * QUAKED info_player_start (1 0 0) (-16 -16 -24) (16 16 32) The normal
     * starting point for a level.
     */
    public static void SP_info_player_start(SubgameEntity self) {
        if (GameBase.coop.value == 0)
            return;
        if (Lib.Q_stricmp(GameBase.level.mapname, "security") == 0) {
            // invoke one of our gross, ugly, disgusting hacks
            self.think = PlayerClient.SP_CreateCoopSpots;
            self.nextthink = GameBase.level.time + Defines.FRAMETIME;
        }
    }

    /**
     * QUAKED info_player_deathmatch (1 0 1) (-16 -16 -24) (16 16 32) potential
     * spawning position for deathmatch games.
     */
    public static void SP_info_player_deathmatch(SubgameEntity self) {
        if (0 == GameBase.deathmatch.value) {
            GameUtil.G_FreeEdict(self);
            return;
        }
        GameMisc.SP_misc_teleporter_dest.think(self);
    }

    /**
     * QUAKED info_player_coop (1 0 1) (-16 -16 -24) (16 16 32) potential
     * spawning position for coop games.
     */

    public static void SP_info_player_coop(SubgameEntity self) {
        if (0 == GameBase.coop.value) {
            GameUtil.G_FreeEdict(self);
            return;
        }

        if ((Lib.Q_stricmp(GameBase.level.mapname, "jail2") == 0)
                || (Lib.Q_stricmp(GameBase.level.mapname, "jail4") == 0)
                || (Lib.Q_stricmp(GameBase.level.mapname, "mine1") == 0)
                || (Lib.Q_stricmp(GameBase.level.mapname, "mine2") == 0)
                || (Lib.Q_stricmp(GameBase.level.mapname, "mine3") == 0)
                || (Lib.Q_stricmp(GameBase.level.mapname, "mine4") == 0)
                || (Lib.Q_stricmp(GameBase.level.mapname, "lab") == 0)
                || (Lib.Q_stricmp(GameBase.level.mapname, "boss1") == 0)
                || (Lib.Q_stricmp(GameBase.level.mapname, "fact3") == 0)
                || (Lib.Q_stricmp(GameBase.level.mapname, "biggun") == 0)
                || (Lib.Q_stricmp(GameBase.level.mapname, "space") == 0)
                || (Lib.Q_stricmp(GameBase.level.mapname, "command") == 0)
                || (Lib.Q_stricmp(GameBase.level.mapname, "power2") == 0)
                || (Lib.Q_stricmp(GameBase.level.mapname, "strike") == 0)) {
            // invoke one of our gross, ugly, disgusting hacks
            self.think = PlayerClient.SP_FixCoopSpots;
            self.nextthink = GameBase.level.time + Defines.FRAMETIME;
        }
    }

    /**
     * QUAKED info_player_intermission (1 0 1) (-16 -16 -24) (16 16 32) The
     * deathmatch intermission point will be at one of these Use 'angles'
     * instead of 'angle', so you can set pitch or roll as well as yaw. 'pitch
     * yaw roll'
     */
    public static void SP_info_player_intermission() {
    }

    public static void ClientObituary(SubgameEntity self, edict_t inflictor,
                                      SubgameEntity attacker) {
        int mod;
        String message;
        String message2;
        boolean ff;

        gclient_t attackerClient = (gclient_t) attacker.client;
        if (GameBase.coop.value != 0 && attackerClient != null)
            GameBase.meansOfDeath |= GameDefines.MOD_FRIENDLY_FIRE;

        gclient_t client = (gclient_t) self.client;
        if (GameBase.deathmatch.value != 0 || GameBase.coop.value != 0) {
            ff = (GameBase.meansOfDeath & GameDefines.MOD_FRIENDLY_FIRE) != 0;
            mod = GameBase.meansOfDeath & ~GameDefines.MOD_FRIENDLY_FIRE;
            message = null;
            message2 = "";

            switch (mod) {
            case GameDefines.MOD_SUICIDE:
                message = "suicides";
                break;
            case GameDefines.MOD_FALLING:
                message = "cratered";
                break;
            case GameDefines.MOD_CRUSH:
                message = "was squished";
                break;
            case GameDefines.MOD_WATER:
                message = "sank like a rock";
                break;
            case GameDefines.MOD_SLIME:
                message = "melted";
                break;
            case GameDefines.MOD_LAVA:
                message = "does a back flip into the lava";
                break;
            case GameDefines.MOD_EXPLOSIVE:
            case GameDefines.MOD_BARREL:
                message = "blew up";
                break;
            case GameDefines.MOD_EXIT:
                message = "found a way out";
                break;
            case GameDefines.MOD_TARGET_LASER:
                message = "saw the light";
                break;
            case GameDefines.MOD_TARGET_BLASTER:
                message = "got blasted";
                break;
            case GameDefines.MOD_BOMB:
            case GameDefines.MOD_SPLASH:
            case GameDefines.MOD_TRIGGER_HURT:
                message = "was in the wrong place";
                break;
            }
            if (attacker == self) {
                switch (mod) {
                case GameDefines.MOD_HELD_GRENADE:
                    message = "tried to put the pin back in";
                    break;
                case GameDefines.MOD_HG_SPLASH:
                case GameDefines.MOD_G_SPLASH:
                    if (PlayerClient.IsNeutral(self))
                        message = "tripped on its own grenade";
                    else if (PlayerClient.IsFemale(self))
                        message = "tripped on her own grenade";
                    else
                        message = "tripped on his own grenade";
                    break;
                case GameDefines.MOD_R_SPLASH:
                    if (PlayerClient.IsNeutral(self))
                        message = "blew itself up";
                    else if (PlayerClient.IsFemale(self))
                        message = "blew herself up";
                    else
                        message = "blew himself up";
                    break;
                case GameDefines.MOD_BFG_BLAST:
                    message = "should have used a smaller gun";
                    break;
                default:
                    if (PlayerClient.IsNeutral(self))
                        message = "killed itself";
                    else if (PlayerClient.IsFemale(self))
                        message = "killed herself";
                    else
                        message = "killed himself";
                    break;
                }
            }
            if (message != null) {
                GameBase.gi.bprintf(Defines.PRINT_MEDIUM,
                        client.pers.netname + " " + message + ".\n");
                if (GameBase.deathmatch.value != 0)
                    client.resp.score--;
                self.enemy = null;
                return;
            }

            self.enemy = attacker;
            if (attacker != null && attackerClient != null) {
                switch (mod) {
                case GameDefines.MOD_BLASTER:
                    message = "was blasted by";
                    break;
                case GameDefines.MOD_SHOTGUN:
                    message = "was gunned down by";
                    break;
                case GameDefines.MOD_SSHOTGUN:
                    message = "was blown away by";
                    message2 = "'s super shotgun";
                    break;
                case GameDefines.MOD_MACHINEGUN:
                    message = "was machinegunned by";
                    break;
                case GameDefines.MOD_CHAINGUN:
                    message = "was cut in half by";
                    message2 = "'s chaingun";
                    break;
                case GameDefines.MOD_GRENADE:
                    message = "was popped by";
                    message2 = "'s grenade";
                    break;
                case GameDefines.MOD_G_SPLASH:
                    message = "was shredded by";
                    message2 = "'s shrapnel";
                    break;
                case GameDefines.MOD_ROCKET:
                    message = "ate";
                    message2 = "'s rocket";
                    break;
                case GameDefines.MOD_R_SPLASH:
                    message = "almost dodged";
                    message2 = "'s rocket";
                    break;
                case GameDefines.MOD_HYPERBLASTER:
                    message = "was melted by";
                    message2 = "'s hyperblaster";
                    break;
                case GameDefines.MOD_RAILGUN:
                    message = "was railed by";
                    break;
                case GameDefines.MOD_BFG_LASER:
                    message = "saw the pretty lights from";
                    message2 = "'s BFG";
                    break;
                case GameDefines.MOD_BFG_BLAST:
                    message = "was disintegrated by";
                    message2 = "'s BFG blast";
                    break;
                case GameDefines.MOD_BFG_EFFECT:
                    message = "couldn't hide from";
                    message2 = "'s BFG";
                    break;
                case GameDefines.MOD_HANDGRENADE:
                    message = "caught";
                    message2 = "'s handgrenade";
                    break;
                case GameDefines.MOD_HG_SPLASH:
                    message = "didn't see";
                    message2 = "'s handgrenade";
                    break;
                case GameDefines.MOD_HELD_GRENADE:
                    message = "feels";
                    message2 = "'s pain";
                    break;
                case GameDefines.MOD_TELEFRAG:
                    message = "tried to invade";
                    message2 = "'s personal space";
                    break;
                }
                if (message != null) {
                    GameBase.gi.bprintf(Defines.PRINT_MEDIUM,
                            client.pers.netname + " " + message + " "
                                    + attackerClient.pers.netname + " "
                                    + message2 + "\n");
                    if (GameBase.deathmatch.value != 0) {
                        if (ff)
                            attackerClient.resp.score--;
                        else
                            attackerClient.resp.score++;
                    }
                    return;
                }
            }
        }

        GameBase.gi.bprintf(Defines.PRINT_MEDIUM, client.pers.netname
                + " died.\n");
        if (GameBase.deathmatch.value != 0)
            client.resp.score--;
    }

    /**
     * This is only called when the game first initializes in single player, but
     * is called after each death and level change in deathmatch. 
     */
    public static void InitClientPersistant(gclient_t client) {
        gitem_t item;

        client.pers = new client_persistant_t();

        item = GameItems.FindItem("Blaster");
        client.pers.selected_item = GameItems.ITEM_INDEX(item);
        client.pers.inventory[client.pers.selected_item] = 1;

        /*
         * Give shotgun. item = FindItem("Shotgun"); client.pers.selected_item =
         * ITEM_INDEX(item); client.pers.inventory[client.pers.selected_item] =
         * 1;
         */

        client.pers.weapon = item;

        client.pers.health = 100;
        client.pers.max_health = 100;

        client.pers.max_bullets = 200;
        client.pers.max_shells = 100;
        client.pers.max_rockets = 50;
        client.pers.max_grenades = 50;
        client.pers.max_cells = 200;
        client.pers.max_slugs = 50;

        client.pers.connected = true;
    }

    public static void InitClientResp(gclient_t client) {
        //memset(& client.resp, 0, sizeof(client.resp));
        client.resp.clear(); //  ok.
        client.resp.enterframe = GameBase.level.framenum;
        client.resp.coop_respawn.set(client.pers);
    }

    /**
     * Some information that should be persistant, like health, is still stored
     * in the edict structure, so it needs to be mirrored out to the client
     * structure before all the edicts are wiped. 
     */
    public static void SaveClientData() {

        for (int i = 0; i < GameBase.game.maxclients; i++) {
            SubgameEntity ent = GameBase.g_edicts[1 + i];
            if (!ent.inuse)
                continue;

            GameBase.game.clients[i].pers.health = ent.health;
            GameBase.game.clients[i].pers.max_health = ent.max_health;
            GameBase.game.clients[i].pers.savedFlags = (ent.flags & (GameDefines.FL_GODMODE
                    | GameDefines.FL_NOTARGET | GameDefines.FL_POWER_ARMOR));

            if (GameBase.coop.value != 0) {
                gclient_t client = (gclient_t) ent.client;
                GameBase.game.clients[i].pers.score = client.resp.score;
            }
        }
    }

    public static void FetchClientEntData(SubgameEntity ent) {
        gclient_t client = (gclient_t) ent.client;
        ent.health = client.pers.health;
        ent.max_health = client.pers.max_health;
        ent.flags |= client.pers.savedFlags;
        if (GameBase.coop.value != 0)
            client.resp.score = client.pers.score;
    }

    /**
     * Returns the distance to the nearest player from the given spot.
     */
    static float PlayersRangeFromSpot(SubgameEntity spot) {
        SubgameEntity player;
        float bestplayerdistance;
        float[] v = { 0, 0, 0 };
        int n;
        float playerdistance;

        bestplayerdistance = 9999999;

        for (n = 1; n <= GameBase.maxclients.value; n++) {
            player = GameBase.g_edicts[n];

            if (!player.inuse)
                continue;

            if (player.health <= 0)
                continue;

            Math3D.VectorSubtract(spot.s.origin, player.s.origin, v);
            playerdistance = Math3D.VectorLength(v);

            if (playerdistance < bestplayerdistance)
                bestplayerdistance = playerdistance;
        }

        return bestplayerdistance;
    }

    /**
     * Go to a random point, but NOT the two points closest to other players.
     */
    public static SubgameEntity SelectRandomDeathmatchSpawnPoint() {
        int count = 0;
        float range2;

        SubgameEntity spot;

        float range1 = range2 = 99999;
        SubgameEntity spot2;
        SubgameEntity spot1 = spot2 = null;

        EdictIterator es = null;

        while ((es = GameBase.G_Find(es, GameBase.findByClass,
                "info_player_deathmatch")) != null) {
            spot = es.o;
            count++;
            float range = PlayersRangeFromSpot(spot);
            if (range < range1) {
                range1 = range;
                spot1 = spot;
            } else if (range < range2) {
                range2 = range;
                spot2 = spot;
            }
        }

        if (count == 0)
            return null;

        if (count <= 2) {
            spot1 = spot2 = null;
        } else
            count -= 2;

        int selection = Lib.rand() % count;

        spot = null;
        es = null;
        do {
            es = GameBase.G_Find(es, GameBase.findByClass,
                    "info_player_deathmatch");
            
            if (es == null) 
                break;
            
            spot = es.o;
            if (spot == spot1 || spot == spot2)
                selection++;
        } while (selection-- > 0);

        return spot;
    }

    /** 
	 * If turned on in the dmflags, select a spawn point far away from other players.
     */
    static SubgameEntity SelectFarthestDeathmatchSpawnPoint() {

        SubgameEntity spot;
        SubgameEntity bestspot = null;
        float bestdistance = 0;

        EdictIterator es = null;
        while ((es = GameBase.G_Find(es, GameBase.findByClass,
                "info_player_deathmatch")) != null) {
            spot = es.o;
            float bestplayerdistance = PlayersRangeFromSpot(spot);

            if (bestplayerdistance > bestdistance) {
                bestspot = spot;
                bestdistance = bestplayerdistance;
            }
        }

        if (bestspot != null) {
            return bestspot;
        }

        // if there is a player just spawned on each and every start spot
        // we have no choice to turn one into a telefrag meltdown
        EdictIterator edit = GameBase.G_Find(null, GameBase.findByClass,
                "info_player_deathmatch");
        if (edit == null)
            return null;
        
        return edit.o;
    }

    
    public static SubgameEntity SelectDeathmatchSpawnPoint() {
        if (0 != ((int) (GameBase.dmflags.value) & Defines.DF_SPAWN_FARTHEST))
            return SelectFarthestDeathmatchSpawnPoint();
        else
            return SelectRandomDeathmatchSpawnPoint();
    }

    public static SubgameEntity SelectCoopSpawnPoint(edict_t ent) {

        //index = ent.client - game.clients;
        int index = ent.client.getIndex();

        // player 0 starts in normal player spawn point
        if (index == 0)
            return null;

        SubgameEntity spot;
        EdictIterator es = null;

        // assume there are four coop spots at each spawnpoint
        while (true) {

            es = GameBase.G_Find(es, GameBase.findByClass,
                    "info_player_coop");
                    
            if (es == null)
                return null;
            
            spot = es.o;
                
            if (spot == null)
                return null; // we didn't have enough...

            String target = spot.targetname;
            if (target == null)
                target = "";
            if (Lib.Q_stricmp(GameBase.game.spawnpoint, target) == 0) { 
                // this is a coop spawn point for one of the clients here
                index--;
                if (0 == index)
                    return spot; // this is it
            }
        }

    }

    /**
     * Chooses a player start, deathmatch start, coop start, etc.
     */
    public static void SelectSpawnPoint(edict_t ent, float[] origin,
            float[] angles) {
        SubgameEntity spot = null;

        if (GameBase.deathmatch.value != 0)
            spot = SelectDeathmatchSpawnPoint();
        else if (GameBase.coop.value != 0)
            spot = SelectCoopSpawnPoint(ent);

        EdictIterator es = null;
        // find a single player start spot
        if (null == spot) {
            while ((es = GameBase.G_Find(es, GameBase.findByClass,
                    "info_player_start")) != null) {
                spot = es.o;

                if (GameBase.game.spawnpoint.length() == 0
                        && spot.targetname == null)
                    break;

                if (GameBase.game.spawnpoint.length() == 0
                        || spot.targetname == null)
                    continue;

                if (Lib.Q_stricmp(GameBase.game.spawnpoint, spot.targetname) == 0)
                    break;
            }

            if (null == spot) {
                if (GameBase.game.spawnpoint.length() == 0) { 
                    // there wasn't a spawnpoint without a
                    // target, so use any
                    es = GameBase.G_Find(es, GameBase.findByClass,
                            "info_player_start");
                    
                    if (es != null)
                        spot = es.o;
                }
                if (null == spot)
                {
                    GameBase.gi.error("Couldn't find spawn point "
                            + GameBase.game.spawnpoint + "\n");
                    return;
                }
            }
        }

        Math3D.VectorCopy(spot.s.origin, origin);
        origin[2] += 9;
        Math3D.VectorCopy(spot.s.angles, angles);
    }


    public static void InitBodyQue() {

        GameBase.level.body_que = 0;
        for (int i = 0; i < GameDefines.BODY_QUEUE_SIZE; i++) {
            SubgameEntity ent = GameUtil.G_Spawn();
            ent.classname = "bodyque";
        }
    }

    public static void CopyToBodyQue(SubgameEntity ent) {

        // grab a body que and cycle to the next one
        int i = (int) GameBase.maxclients.value + GameBase.level.body_que + 1;
        SubgameEntity body = GameBase.g_edicts[i];
        GameBase.level.body_que = (GameBase.level.body_que + 1)
                % GameDefines.BODY_QUEUE_SIZE;

        // FIXME: send an effect on the removed body

        GameBase.gi.unlinkentity(ent);

        GameBase.gi.unlinkentity(body);
        body.s = ent.s.getClone();

        body.s.number = body.index;

        body.svflags = ent.svflags;
        Math3D.VectorCopy(ent.mins, body.mins);
        Math3D.VectorCopy(ent.maxs, body.maxs);
        Math3D.VectorCopy(ent.absmin, body.absmin);
        Math3D.VectorCopy(ent.absmax, body.absmax);
        Math3D.VectorCopy(ent.size, body.size);
        body.solid = ent.solid;
        body.clipmask = ent.clipmask;
        body.setOwner(ent.getOwner());
        body.movetype = ent.movetype;

        body.die = PlayerClient.body_die;
        body.takedamage = Defines.DAMAGE_YES;

        GameBase.gi.linkentity(body);
    }

    public static void respawn(SubgameEntity self) {
        if (GameBase.deathmatch.value != 0 || GameBase.coop.value != 0) {
            // spectator's don't leave bodies
            if (self.movetype != GameDefines.MOVETYPE_NOCLIP)
                CopyToBodyQue(self);
            self.svflags &= ~Defines.SVF_NOCLIENT;
            PutClientInServer(self);

            // add a teleportation effect
            self.s.event = Defines.EV_PLAYER_TELEPORT;

            // hold in place briefly
            gclient_t client = (gclient_t) self.client;
            client.getPlayerState().pmove.pm_flags = Defines.PMF_TIME_TELEPORT;
            client.getPlayerState().pmove.pm_time = 14;

            client.respawn_time = GameBase.level.time;

            return;
        }

        // restart the entire server
        GameBase.gi.AddCommandString("menu_loadgame\n");
    }

    private static boolean passwdOK(String i1, String i2) {
        if (i1.length() != 0 && !i1.equals("none") && !i1.equals(i2))
            return false;
        return true;
    }

    /**
     * Only called when pers.spectator changes note that resp.spectator should
     * be the opposite of pers.spectator here
     */
    public static void spectator_respawn(SubgameEntity ent) {
        int i, numspec;

        // if the user wants to become a spectator, make sure he doesn't
        // exceed max_spectators

        gclient_t client = (gclient_t) ent.client;
        if (client.pers.spectator) {
            String value = Info.Info_ValueForKey(client.pers.userinfo,
                    "spectator");

            if (!passwdOK(GameBase.spectator_password.string, value)) {
                GameBase.gi.cprintf(ent, Defines.PRINT_HIGH,
                        "Spectator password incorrect.\n");
                client.pers.spectator = false;
                GameBase.gi.WriteByte(NetworkCommands.svc_stufftext);
                GameBase.gi.WriteString("spectator 0\n");
                GameBase.gi.unicast(ent, true);
                return;
            }

            // count spectators
            for (i = 1, numspec = 0; i <= GameBase.maxclients.value; i++) {
                gclient_t other = (gclient_t) GameBase.g_edicts[i].client;
                if (GameBase.g_edicts[i].inuse && other.pers.spectator)
                    numspec++;
            }

            if (numspec >= GameBase.maxspectators.value) {
                GameBase.gi.cprintf(ent, Defines.PRINT_HIGH,
                        "Server spectator limit is full.");
                client.pers.spectator = false;
                // reset his spectator var
                GameBase.gi.WriteByte(NetworkCommands.svc_stufftext);
                GameBase.gi.WriteString("spectator 0\n");
                GameBase.gi.unicast(ent, true);
                return;
            }
        } else {
            // he was a spectator and wants to join the game
            // he must have the right password
            String value = Info.Info_ValueForKey(client.pers.userinfo,
                    "password");
            if (!passwdOK(GameBase.spectator_password.string, value)) {
                GameBase.gi.cprintf(ent, Defines.PRINT_HIGH,
                        "Password incorrect.\n");
                client.pers.spectator = true;
                GameBase.gi.WriteByte(NetworkCommands.svc_stufftext);
                GameBase.gi.WriteString("spectator 1\n");
                GameBase.gi.unicast(ent, true);
                return;
            }
        }

        // clear client on respawn
        client.resp.score = client.pers.score = 0;

        ent.svflags &= ~Defines.SVF_NOCLIENT;
        PutClientInServer(ent);

        // add a teleportation effect
        if (!client.pers.spectator) {
            // send effect
            GameBase.gi.WriteByte(NetworkCommands.svc_muzzleflash);
            //gi.WriteShort(ent - g_edicts);
            GameBase.gi.WriteShort(ent.index);

            GameBase.gi.WriteByte(Defines.MZ_LOGIN);
            GameBase.gi.multicast(ent.s.origin, MulticastTypes.MULTICAST_PVS);

            // hold in place briefly
            client.getPlayerState().pmove.pm_flags = Defines.PMF_TIME_TELEPORT;
            client.getPlayerState().pmove.pm_time = 14;
        }

        client.respawn_time = GameBase.level.time;

        if (client.pers.spectator)
            GameBase.gi.bprintf(Defines.PRINT_HIGH, client.pers.netname
                    + " has moved to the sidelines\n");
        else
            GameBase.gi.bprintf(Defines.PRINT_HIGH, client.pers.netname
                    + " joined the game\n");
    }

    /**
     * Called when a player connects to a server or respawns in a deathmatch.
     */
    public static void PutClientInServer(SubgameEntity ent) {
        float[] mins = { -16, -16, -24 };
        float[] maxs = { 16, 16, 32 };
        int index;
        float[] spawn_origin = { 0, 0, 0 }, spawn_angles = { 0, 0, 0 };
        gclient_t client;
        int i;
        client_persistant_t saved = new client_persistant_t();
        client_respawn_t resp = new client_respawn_t();

        // find a spawn point
        // do it before setting health back up, so farthest
        // ranging doesn't count this client
        SelectSpawnPoint(ent, spawn_origin, spawn_angles);

        index = ent.index - 1;
        client = (gclient_t) ent.client;

        // deathmatch wipes most client data every spawn
        if (GameBase.deathmatch.value != 0) {

            resp.set(client.resp);
            String userinfo = client.pers.userinfo;
            InitClientPersistant(client);

            userinfo = ClientUserinfoChanged(ent, userinfo);

        } else if (GameBase.coop.value != 0) {

            resp.set(client.resp);

            String userinfo = client.pers.userinfo;

            resp.coop_respawn.game_helpchanged = client.pers.game_helpchanged;
            resp.coop_respawn.helpchanged = client.pers.helpchanged;
            client.pers.set(resp.coop_respawn);
            userinfo = ClientUserinfoChanged(ent, userinfo);
            if (resp.score > client.pers.score)
                client.pers.score = resp.score;
        } else {
            resp.clear();
        }

        // clear everything but the persistant data
        saved.set(client.pers);
        client.clear();
        client.pers.set(saved);
        if (client.pers.health <= 0)
            InitClientPersistant(client);

        client.resp.set(resp);

        // copy some data from the client to the entity
        FetchClientEntData(ent);

        // clear entity values
        ent.groundentity = null;
        ent.client = GameBase.game.clients[index];
        ent.takedamage = Defines.DAMAGE_AIM;
        ent.movetype = GameDefines.MOVETYPE_WALK;
        ent.viewheight = 22;
        ent.inuse = true;
        ent.classname = "player";
        ent.mass = 200;
        ent.solid = Defines.SOLID_BBOX;
        ent.deadflag = GameDefines.DEAD_NO;
        ent.air_finished = GameBase.level.time + 12;
        ent.clipmask = Defines.MASK_PLAYERSOLID;
        ent.model = "players/male/tris.md2";
        ent.pain = PlayerClient.player_pain;
        ent.die = PlayerClient.player_die;
        ent.waterlevel = 0;
        ent.watertype = 0;
        ent.flags &= ~GameDefines.FL_NO_KNOCKBACK;
        ent.svflags &= ~Defines.SVF_DEADMONSTER;

        Math3D.VectorCopy(mins, ent.mins);
        Math3D.VectorCopy(maxs, ent.maxs);
        Math3D.VectorClear(ent.velocity);

        // clear playerstate values
        ent.client.getPlayerState().clear();

        client.getPlayerState().pmove.origin[0] = (short) (spawn_origin[0] * 8);
        client.getPlayerState().pmove.origin[1] = (short) (spawn_origin[1] * 8);
        client.getPlayerState().pmove.origin[2] = (short) (spawn_origin[2] * 8);

        if (GameBase.deathmatch.value != 0
                && 0 != ((int) GameBase.dmflags.value & Defines.DF_FIXED_FOV)) {
            client.getPlayerState().fov = 90;
        } else {
            client.getPlayerState().fov = Lib.atoi(Info.Info_ValueForKey(
                    client.pers.userinfo, "fov"));
            if (client.getPlayerState().fov < 1)
                client.getPlayerState().fov = 90;
            else if (client.getPlayerState().fov > 160)
                client.getPlayerState().fov = 160;
        }

        client.getPlayerState().gunindex = GameBase.gi
                .modelindex(client.pers.weapon.view_model);

        // clear entity state values
        ent.s.effects = 0;
        ent.s.modelindex = 255; // will use the skin specified model
        ent.s.modelindex2 = 255; // custom gun model
        // sknum is player num and weapon number
        // weapon number will be added in changeweapon
        ent.s.skinnum = ent.index - 1;

        ent.s.frame = 0;
        Math3D.VectorCopy(spawn_origin, ent.s.origin);
        ent.s.origin[2] += 1; // make sure off ground
        Math3D.VectorCopy(ent.s.origin, ent.s.old_origin);

        // set the delta angle
        for (i = 0; i < 3; i++) {
            client.getPlayerState().pmove.delta_angles[i] = (short) Math3D
                    .ANGLE2SHORT(spawn_angles[i] - client.resp.cmd_angles[i]);
        }

        ent.s.angles[Defines.PITCH] = 0;
        ent.s.angles[Defines.YAW] = spawn_angles[Defines.YAW];
        ent.s.angles[Defines.ROLL] = 0;
        Math3D.VectorCopy(ent.s.angles, client.getPlayerState().viewangles);
        Math3D.VectorCopy(ent.s.angles, client.v_angle);

        // spawn a spectator
        if (client.pers.spectator) {
            client.chase_target = null;

            client.resp.spectator = true;

            ent.movetype = GameDefines.MOVETYPE_NOCLIP;
            ent.solid = Defines.SOLID_NOT;
            ent.svflags |= Defines.SVF_NOCLIENT;
            ent.client.getPlayerState().gunindex = 0;
            GameBase.gi.linkentity(ent);
            return;
        } else
            client.resp.spectator = false;

        if (!GameUtil.KillBox(ent)) { // could't spawn in?
        }

        GameBase.gi.linkentity(ent);

        // force the current weapon up
        client.newweapon = client.pers.weapon;
        PlayerWeapon.ChangeWeapon(ent);
    }

    /**
     * A client has just connected to the server in deathmatch mode, so clear
     * everything out before starting them.
     */
    public static void ClientBeginDeathmatch(SubgameEntity ent) {
        GameUtil.G_InitEdict(ent, ent.index);

        gclient_t client = (gclient_t) ent.client;
        InitClientResp(client);

        // locate ent at a spawn point
        PutClientInServer(ent);

        if (GameBase.level.intermissiontime != 0) {
            PlayerHud.MoveClientToIntermission(ent);
        } else {
            // send effect
            GameBase.gi.WriteByte(NetworkCommands.svc_muzzleflash);
            //gi.WriteShort(ent - g_edicts);
            GameBase.gi.WriteShort(ent.index);
            GameBase.gi.WriteByte(Defines.MZ_LOGIN);
            GameBase.gi.multicast(ent.s.origin, MulticastTypes.MULTICAST_PVS);
        }

        GameBase.gi.bprintf(Defines.PRINT_HIGH, client.pers.netname
                + " entered the game\n");

        // make sure all view stuff is valid
        PlayerView.ClientEndServerFrame(ent);
    }

    static void ClientBegin(SubgameEntity ent) {
        int i;

        //ent.client = game.clients + (ent - g_edicts - 1);
        ent.client = GameBase.game.clients[ent.index - 1];

        if (GameBase.deathmatch.value != 0) {
            ClientBeginDeathmatch(ent);
            return;
        }

        // if there is already a body waiting for us (a loadgame), just
        // take it, otherwise spawn one from scratch
        gclient_t client = (gclient_t) ent.client;
        if (ent.inuse == true) {
            // the client has cleared the client side viewangles upon
            // connecting to the server, which is different than the
            // state when the game is saved, so we need to compensate
            // with deltaangles
            for (i = 0; i < 3; i++)
                client.getPlayerState().pmove.delta_angles[i] = (short) Math3D
                        .ANGLE2SHORT(client.getPlayerState().viewangles[i]);
        } else {
            // a spawn point will completely reinitialize the entity
            // except for the persistant data that was initialized at
            // ClientConnect() time
            GameUtil.G_InitEdict(ent, ent.index);
            ent.classname = "player";
            InitClientResp(client);
            PutClientInServer(ent);
        }

        if (GameBase.level.intermissiontime != 0) {
            PlayerHud.MoveClientToIntermission(ent);
        } else {
            // send effect if in a multiplayer game
            if (GameBase.game.maxclients > 1) {
                GameBase.gi.WriteByte(NetworkCommands.svc_muzzleflash);
                GameBase.gi.WriteShort(ent.index);
                GameBase.gi.WriteByte(Defines.MZ_LOGIN);
                GameBase.gi.multicast(ent.s.origin, MulticastTypes.MULTICAST_PVS);

                GameBase.gi.bprintf(Defines.PRINT_HIGH, client.pers.netname
                        + " entered the game\n");
            }
        }

        // make sure all view stuff is valid
        PlayerView.ClientEndServerFrame(ent);
    }

    static String ClientUserinfoChanged(edict_t ent, String userinfo) {
        String s;
        int playernum;

        // check for malformed or illegal info strings
        if (!Info.Info_Validate(userinfo)) {
            return "\\name\\badinfo\\skin\\male/grunt";
        }

        // set name
        s = Info.Info_ValueForKey(userinfo, "name");

        gclient_t client = (gclient_t) ent.client;
        client.pers.netname = s;

        // set spectator
        s = Info.Info_ValueForKey(userinfo, "spectator");
        // spectators are only supported in deathmatch
        if (GameBase.deathmatch.value != 0 && !s.equals("0"))
            client.pers.spectator = true;
        else
            client.pers.spectator = false;

        // set skin
        s = Info.Info_ValueForKey(userinfo, "skin");

        playernum = ent.index - 1;

        // combine name and skin into a configstring
        GameBase.gi.configstring(Defines.CS_PLAYERSKINS + playernum,
                client.pers.netname + "\\" + s);

        // fov
        if (GameBase.deathmatch.value != 0
                && 0 != ((int) GameBase.dmflags.value & Defines.DF_FIXED_FOV)) {
            client.getPlayerState().fov = 90;
        } else {
            client.getPlayerState().fov = Lib
                    .atoi(Info.Info_ValueForKey(userinfo, "fov"));
            if (client.getPlayerState().fov < 1)
                client.getPlayerState().fov = 90;
            else if (client.getPlayerState().fov > 160)
                client.getPlayerState().fov = 160;
        }

        // handedness
        s = Info.Info_ValueForKey(userinfo, "hand");
        if (s.length() > 0) {
            client.pers.hand = Lib.atoi(s);
        }

        // save off the userinfo in case we want to check something later
        client.pers.userinfo = userinfo;

        return userinfo;
    }

    static boolean ClientConnect(edict_t ent, String userinfo) {
        String value;

        // check to see if they are on the banned IP list
        value = Info.Info_ValueForKey(userinfo, "ip");
        if (GameSVCmds.SV_FilterPacket(value)) {
            userinfo = Info.Info_SetValueForKey(userinfo, "rejmsg", "Banned.");
            return false;
        }

        // check for a spectator
        value = Info.Info_ValueForKey(userinfo, "spectator");
        if (GameBase.deathmatch.value != 0 && value.length() != 0 && !"0".equals(value)) {
            int i, numspec;

            if (!passwdOK(GameBase.spectator_password.string, value)) {
                userinfo = Info.Info_SetValueForKey(userinfo, "rejmsg",
                        "Spectator password required or incorrect.");
                return false;
            }

            // count spectators
            for (i = numspec = 0; i < GameBase.maxclients.value; i++) {
                gclient_t other = (gclient_t) GameBase.g_edicts[i + 1].client;
                if (GameBase.g_edicts[i + 1].inuse && other.pers.spectator)
                    numspec++;
            }

            if (numspec >= GameBase.maxspectators.value) {
                userinfo = Info.Info_SetValueForKey(userinfo, "rejmsg",
                        "Server spectator limit is full.");
                return false;
            }
        } else {
            // check for a password
            value = Info.Info_ValueForKey(userinfo, "password");
            if (!passwdOK(GameBase.spectator_password.string, value)) {
                userinfo = Info.Info_SetValueForKey(userinfo, "rejmsg",
                        "Password required or incorrect.");
                return false;
            }
        }

        // they can connect
        ent.client = GameBase.game.clients[ent.index - 1];

        // if there is already a body waiting for us (a loadgame), just
        // take it, otherwise spawn one from scratch
        gclient_t client = (gclient_t) ent.client;
        if (ent.inuse == false) {
            // clear the respawning variables
            InitClientResp(client);
            if (!GameBase.game.autosaved || null == client.pers.weapon)
                InitClientPersistant(client);
        }

        userinfo = ClientUserinfoChanged(ent, userinfo);

        if (GameBase.game.maxclients > 1)
            GameBase.gi.dprintf(client.pers.netname + " connected\n");

        ent.svflags = 0; // make sure we start with known default
        client.pers.connected = true;
        return true;
    }

    static void ClientDisconnect(SubgameEntity ent) {

        gclient_t client = (gclient_t) ent.client;
        if (client == null)
            return;

        GameBase.gi.bprintf(Defines.PRINT_HIGH, client.pers.netname
                + " disconnected\n");

        // send effect
        GameBase.gi.WriteByte(NetworkCommands.svc_muzzleflash);
        GameBase.gi.WriteShort(ent.index);
        GameBase.gi.WriteByte(Defines.MZ_LOGOUT);
        GameBase.gi.multicast(ent.s.origin, MulticastTypes.MULTICAST_PVS);

        GameBase.gi.unlinkentity(ent);
        ent.s.modelindex = 0;
        ent.solid = Defines.SOLID_NOT;
        ent.inuse = false;
        ent.classname = "disconnected";
        client.pers.connected = false;

        int playernum = ent.index - 1;
        GameBase.gi.configstring(Defines.CS_PLAYERSKINS + playernum, "");
    }

    /*
     * static int CheckBlock(int c) 
     * { 
     * 		int v, i; 
     * 		v = 0; 
     * 		for (i = 0; i < c; i++)
     *			v += ((byte *) b)[i]; 
     *		return v; 
     * }
     * 
     * public static void PrintPmove(pmove_t * pm) 
     * { 
     *		unsigned c1, c2;
     * 
     * 		c1 = CheckBlock(&pm.s, sizeof(pm.s));
     * 		c2 = CheckBlock(&pm.cmd, sizeof(pm.cmd)); 
     *      Com_Printf("sv %3i:%i %i\n", pm.cmd.impulse, c1, c2); 
     * }
     */

    static void ClientThink(SubgameEntity ent, usercmd_t ucmd) {

        GameBase.level.current_entity = ent;
        gclient_t client = (gclient_t) ent.client;

        if (GameBase.level.intermissiontime != 0) {
            client.getPlayerState().pmove.pm_type = Defines.PM_FREEZE;
            // can exit intermission after five seconds
            if (GameBase.level.time > GameBase.level.intermissiontime + 5.0f
                    && 0 != (ucmd.buttons & Defines.BUTTON_ANY))
                GameBase.level.exitintermission = true;
            return;
        }

        PlayerClient.pm_passent = ent;

        SubgameEntity other;
        int i;
        if (client.chase_target != null) {

            client.resp.cmd_angles[0] = Math3D.SHORT2ANGLE(ucmd.angles[0]);
            client.resp.cmd_angles[1] = Math3D.SHORT2ANGLE(ucmd.angles[1]);
            client.resp.cmd_angles[2] = Math3D.SHORT2ANGLE(ucmd.angles[2]);

        } else {

            // set up for pmove
            pmove_t pm = new pmove_t();

            if (ent.movetype == GameDefines.MOVETYPE_NOCLIP)
                client.getPlayerState().pmove.pm_type = Defines.PM_SPECTATOR;
            else if (ent.s.modelindex != 255)
                client.getPlayerState().pmove.pm_type = Defines.PM_GIB;
            else if (ent.deadflag != 0)
                client.getPlayerState().pmove.pm_type = Defines.PM_DEAD;
            else
                client.getPlayerState().pmove.pm_type = Defines.PM_NORMAL;

            client.getPlayerState().pmove.gravity = (short) GameBase.sv_gravity.value;
            pm.s.set(client.getPlayerState().pmove);

            for (i = 0; i < 3; i++) {
                pm.s.origin[i] = (short) (ent.s.origin[i] * 8);
                pm.s.velocity[i] = (short) (ent.velocity[i] * 8);
            }

            if (client.old_pmove.equals(pm.s)) {
                pm.snapinitial = true;
                // gi.dprintf ("pmove changed!\n");
            }

            // this should be a copy
            pm.cmd.set(ucmd);

            pm.trace = PlayerClient.PM_trace; // adds default parms
            pm.pointcontents = (float[] p) -> GameBase.gi.getPointContents(p);

            // perform a pmove
            GameBase.gi.Pmove(pm);

            // save results of pmove
            client.getPlayerState().pmove.set(pm.s);
            client.old_pmove.set(pm.s);

            for (i = 0; i < 3; i++) {
                ent.s.origin[i] = pm.s.origin[i] * 0.125f;
                ent.velocity[i] = pm.s.velocity[i] * 0.125f;
            }

            Math3D.VectorCopy(pm.mins, ent.mins);
            Math3D.VectorCopy(pm.maxs, ent.maxs);

            client.resp.cmd_angles[0] = Math3D.SHORT2ANGLE(ucmd.angles[0]);
            client.resp.cmd_angles[1] = Math3D.SHORT2ANGLE(ucmd.angles[1]);
            client.resp.cmd_angles[2] = Math3D.SHORT2ANGLE(ucmd.angles[2]);

            if (ent.groundentity != null && null == pm.groundentity
                    && (pm.cmd.upmove >= 10) && (pm.waterlevel == 0)) {
                GameBase.gi.sound(ent, Defines.CHAN_VOICE, GameBase.gi
                        .soundindex("*jump1.wav"), 1, Defines.ATTN_NORM, 0);
                PlayerWeapon.PlayerNoise(ent, ent.s.origin, GameDefines.PNOISE_SELF);
            }

            ent.viewheight = (int) pm.viewheight;
            ent.waterlevel = (int) pm.waterlevel;
            ent.watertype = pm.watertype;
            ent.groundentity = pm.groundentity;
            if (pm.groundentity != null)
                ent.groundentity_linkcount = pm.groundentity.linkcount;

            if (ent.deadflag != 0) {
                client.getPlayerState().viewangles[Defines.ROLL] = 40;
                client.getPlayerState().viewangles[Defines.PITCH] = -15;
                client.getPlayerState().viewangles[Defines.YAW] = client.killer_yaw;
            } else {
                Math3D.VectorCopy(pm.viewangles, client.v_angle);
                Math3D.VectorCopy(pm.viewangles, client.getPlayerState().viewangles);
            }

            GameBase.gi.linkentity(ent);

            if (ent.movetype != GameDefines.MOVETYPE_NOCLIP)
                GameBase.G_TouchTriggers(ent);

            // touch other objects
            for (i = 0; i < pm.numtouch; i++) {
                other = (SubgameEntity) pm.touchents[i];
                int j;
                for (j = 0; j < i; j++)
                    if (pm.touchents[j] == other)
                        break;
                if (j != i)
                    continue; // duplicated
                if (other.touch == null)
                    continue;
                other.touch.touch(other, ent, GameBase.dummyplane, null);
            }

        }

        client.oldbuttons = client.buttons;
        client.buttons = ucmd.buttons;
        client.latched_buttons |= client.buttons & ~client.oldbuttons;

        // save light level the player is standing on for
        // monster sighting AI
        ent.light_level = ucmd.lightlevel;

        // fire weapon from final position if needed
        if ((client.latched_buttons & Defines.BUTTON_ATTACK) != 0) {
            if (client.resp.spectator) {

                client.latched_buttons = 0;

                if (client.chase_target != null) {
                    client.chase_target = null;
                    client.getPlayerState().pmove.pm_flags &= ~Defines.PMF_NO_PREDICTION;
                } else
                    GameChase.GetChaseTarget(ent);

            } else if (!client.weapon_thunk) {
                client.weapon_thunk = true;
                PlayerWeapon.Think_Weapon(ent);
            }
        }

        if (client.resp.spectator) {
            if (ucmd.upmove >= 10) {
                if (0 == (client.getPlayerState().pmove.pm_flags & Defines.PMF_JUMP_HELD)) {
                    client.getPlayerState().pmove.pm_flags |= Defines.PMF_JUMP_HELD;
                    if (client.chase_target != null)
                        GameChase.ChaseNext(ent);
                    else
                        GameChase.GetChaseTarget(ent);
                }
            } else
                client.getPlayerState().pmove.pm_flags &= ~Defines.PMF_JUMP_HELD;
        }

        // update chase cam if being followed
        for (i = 1; i <= GameBase.maxclients.value; i++) {
            other = GameBase.g_edicts[i];
            gclient_t otherClient = (gclient_t) other.client;
            if (other.inuse && otherClient.chase_target == ent)
                GameChase.UpdateChaseCam(other);
        }
    }

    /**
     * This will be called once for each server frame, before running any other
     * entities in the world. 
     */
    public static void ClientBeginServerFrame(SubgameEntity ent) {
        gclient_t client;
        int buttonMask;

        if (GameBase.level.intermissiontime != 0)
            return;

        client = (gclient_t) ent.client;

        if (GameBase.deathmatch.value != 0
                && client.pers.spectator != client.resp.spectator
                && (GameBase.level.time - client.respawn_time) >= 5) {
            spectator_respawn(ent);
            return;
        }

        // run weapon animations if it hasn't been done by a ucmd_t
        if (!client.weapon_thunk && !client.resp.spectator)
            PlayerWeapon.Think_Weapon(ent);
        else
            client.weapon_thunk = false;

        if (ent.deadflag != 0) {
            // wait for any button just going down
            if (GameBase.level.time > client.respawn_time) {
                // in deathmatch, only wait for attack button
                if (GameBase.deathmatch.value != 0)
                    buttonMask = Defines.BUTTON_ATTACK;
                else
                    buttonMask = -1;

                if ((client.latched_buttons & buttonMask) != 0
                        || (GameBase.deathmatch.value != 0 && 0 != ((int) GameBase.dmflags.value & Defines.DF_FORCE_RESPAWN))) {
                    respawn(ent);
                    client.latched_buttons = 0;
                }
            }
            return;
        }

        // add player trail so monsters can follow
        if (GameBase.deathmatch.value != 0)
            if (!GameUtil.visible(ent, PlayerTrail.LastSpot()))
                PlayerTrail.Add(ent.s.old_origin);

        client.latched_buttons = 0;
    }

    /** 
     * Returns true, if the players gender flag was set to female. 
     */
    public static boolean IsFemale(edict_t ent) {
        char info;

        gclient_t client = (gclient_t) ent.client;
        if (client == null)
            return false;
    
        info = Info.Info_ValueForKey(client.pers.userinfo, "gender")
                .charAt(0);
        if (info == 'f' || info == 'F')
            return true;
        return false;
    }

    /**
     * Returns true, if the players gender flag was neither set to female nor to
     * male.
     */
    public static boolean IsNeutral(edict_t ent) {
        char info;

        gclient_t client = (gclient_t) ent.client;
        if (client == null)
            return false;
    
        info = Info.Info_ValueForKey(client.pers.userinfo, "gender")
                .charAt(0);
    
        if (info != 'f' && info != 'F' && info != 'm' && info != 'M')
            return true;
        return false;
    }

    /**
     * Changes the camera view to look at the killer.
     */
    public static void LookAtKiller(SubgameEntity self, edict_t inflictor,
            edict_t attacker) {
        float dir[] = { 0, 0, 0 };
    
        edict_t world = GameBase.g_edicts[0];

        gclient_t client = (gclient_t) self.client;
        if (attacker != null && attacker != world && attacker != self) {
            Math3D.VectorSubtract(attacker.s.origin, self.s.origin, dir);
        } else if (inflictor != null && inflictor != world && inflictor != self) {
            Math3D.VectorSubtract(inflictor.s.origin, self.s.origin, dir);
        } else {
            client.killer_yaw = self.s.angles[Defines.YAW];
            return;
        }
    
        if (dir[0] != 0)
            client.killer_yaw = (float) (180 / Math.PI * Math.atan2(
                    dir[1], dir[0]));
        else {
            client.killer_yaw = 0;
            if (dir[1] > 0)
                client.killer_yaw = 90;
            else if (dir[1] < 0)
                client.killer_yaw = -90;
        }
        if (client.killer_yaw < 0)
            client.killer_yaw += 360;
    
    }
    
    
    /** 
     * Drop items and weapons in deathmatch games. 
     */ 
    public static void TossClientWeapon(SubgameEntity self) {

        if (GameBase.deathmatch.value == 0)
            return;

        gclient_t client = (gclient_t) self.client;
        gitem_t item = client.pers.weapon;
        if (0 == client.pers.inventory[client.ammo_index])
            item = null;
        if (item != null && ("Blaster".equals(item.pickup_name)))
            item = null;

        boolean quad;
        if (0 == ((int) (GameBase.dmflags.value) & Defines.DF_QUAD_DROP))
            quad = false;
        else
            quad = (client.quad_framenum > (GameBase.level.framenum + 10));

        float spread;
        if (item != null && quad)
            spread = 22.5f;
        else
            spread = 0.0f;

        SubgameEntity drop;
        if (item != null) {
            client.v_angle[Defines.YAW] -= spread;
            drop = GameItems.Drop_Item(self, item);
            client.v_angle[Defines.YAW] += spread;
            drop.spawnflags = GameDefines.DROPPED_PLAYER_ITEM;
        }
    
        if (quad) {
            client.v_angle[Defines.YAW] += spread;
            drop = GameItems.Drop_Item(self, GameItems
                    .FindItemByClassname("item_quad"));
            client.v_angle[Defines.YAW] -= spread;
            drop.spawnflags |= GameDefines.DROPPED_PLAYER_ITEM;
    
            drop.touch = GameItems.Touch_Item;
            drop.nextthink = GameBase.level.time
                    + (client.quad_framenum - GameBase.level.framenum)
                    * Defines.FRAMETIME;
            drop.think = GameUtil.G_FreeEdictA;
        }
    }
}