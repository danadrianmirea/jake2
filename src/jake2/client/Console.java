/*
 * Con.java
 * Copyright (C) 2003
 * 
 * $Id: Console.java,v 1.10 2011-07-07 21:10:18 salomo Exp $
 */
/*
 Copyright (C) 1997-2001 Id Software, Inc.

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 2
 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

 See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

 */
package jake2.client;

import jake2.game.Cmd;
import jake2.qcommon.*;
import jake2.qcommon.filesystem.FS;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Vargs;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.List;

/**
 * Console
 */
public final class Console extends Globals {

    static Command ToggleConsole_f = (List<String> args) -> {
		SCR.EndLoadingPlaque(); // get rid of loading plaque

		if (Globals.cl.attractloop) {
		Cbuf.AddText("killserver\n");
		return;
		}

		if (Globals.cls.state == Defines.ca_disconnected) {
		// start the demo loop again
		Cbuf.AddText("d1\n");
		return;
		}

		Key.ClearTyping();
		Console.ClearNotify();

		if (Globals.cls.key_dest == Defines.key_console) {
		Menu.ForceMenuOff();
		Cvar.Set("paused", "0");
		} else {
		Menu.ForceMenuOff();
		Globals.cls.key_dest = Defines.key_console;

		if (Cvar.VariableValue("maxclients") == 1
			&& Globals.server_state != 0)
			Cvar.Set("paused", "1");
		}
	};

    private static Command Clear_f = (List<String> args) -> Arrays.fill(Globals.con.text, (byte) ' ');

    private static Command Dump_f = (List<String> args) -> {

		int l, x;
		int line;
		RandomAccessFile f;
		byte[] buffer = new byte[1024];
		String name;

		if (args.size() != 2) {
			Com.Printf("usage: condump <filename>\n");
			return;
		}

		// Com_sprintf (name, sizeof(name), "%s/%s.txt", FS_Gamedir(),
		// Cmd_Argv(1));
		name = FS.Gamedir() + "/" + args.get(1) + ".txt";

		Com.Printf("Dumped console text to " + name + ".\n");
		FS.CreatePath(name);
		f = Lib.fopen(name, "rw");
		if (f == null) {
			Com.Printf("ERROR: couldn't open.\n");
			return;
		}

		// skip empty lines
		for (l = con.current - con.totallines + 1; l <= con.current; l++) {
			line = (l % con.totallines) * con.linewidth;
			for (x = 0; x < con.linewidth; x++)
				if (con.text[line + x] != ' ')
					break;
			if (x != con.linewidth)
				break;
		}

		// write the remaining lines
		buffer[con.linewidth] = 0;
		for (; l <= con.current; l++) {
			line = (l % con.totallines) * con.linewidth;
			// strncpy (buffer, line, con.linewidth);
			System.arraycopy(con.text, line, buffer, 0, con.linewidth);
			for (x = con.linewidth - 1; x >= 0; x--) {
				if (buffer[x] == ' ')
					buffer[x] = 0;
				else
					break;
			}
			for (x = 0; buffer[x] != 0; x++)
				buffer[x] &= 0x7f;

			buffer[x] = '\n';
			// fprintf (f, "%s\n", buffer);
			try {
				f.write(buffer, 0, x + 1);
			} catch (IOException e) {
			}
		}

		Lib.fclose(f);

	};

    public static void Init() {
	Globals.con.linewidth = -1;
	Globals.con.backedit = 0;

	CheckResize();

	Com.Printf("Console initialized.\n");

	//
	// register our commands
	//
	Globals.con_notifytime = Cvar.Get("con_notifytime", "3", 0);

	Cmd.AddCommand("toggleconsole", ToggleConsole_f);
	Cmd.AddCommand("togglechat", ToggleChat_f);
	Cmd.AddCommand("messagemode", MessageMode_f);
	Cmd.AddCommand("messagemode2", MessageMode2_f);
	Cmd.AddCommand("clear", Clear_f);
	Cmd.AddCommand("condump", Dump_f);
	Globals.con.initialized = true;
    }

    /**
     * If the line width has changed, reformat the buffer.
     */
    static void CheckResize() {

	int width = (Globals.viddef.getWidth() >> 3) - 2;
	if (width > Defines.MAXCMDLINE)
	    width = Defines.MAXCMDLINE;

	if (width == Globals.con.linewidth)
	    return;

	if (width < 1) { // video hasn't been initialized yet
	    width = 38;
	    Globals.con.linewidth = width;
	    Globals.con.backedit = 0; // sfranzyshen
	    Globals.con.totallines = Defines.CON_TEXTSIZE
		    / Globals.con.linewidth;
	    Arrays.fill(Globals.con.text, (byte) ' ');
	} else {
	    int oldwidth = Globals.con.linewidth;
	    Globals.con.linewidth = width;
	    Globals.con.backedit = 0; // sfranzyshen
	    int oldtotallines = Globals.con.totallines;
	    Globals.con.totallines = Defines.CON_TEXTSIZE
		    / Globals.con.linewidth;
	    int numlines = oldtotallines;

	    if (Globals.con.totallines < numlines)
		numlines = Globals.con.totallines;

	    int numchars = oldwidth;

	    if (Globals.con.linewidth < numchars)
		numchars = Globals.con.linewidth;

	    byte[] tbuf = new byte[Defines.CON_TEXTSIZE];
	    System
		    .arraycopy(Globals.con.text, 0, tbuf, 0,
			    Defines.CON_TEXTSIZE);
	    Arrays.fill(Globals.con.text, (byte) ' ');

	    for (int i = 0; i < numlines; i++) {
		for (int j = 0; j < numchars; j++) {
		    Globals.con.text[(Globals.con.totallines - 1 - i)
			    * Globals.con.linewidth + j] = tbuf[((Globals.con.current
			    - i + oldtotallines) % oldtotallines)
			    * oldwidth + j];
		}
	    }

	    Console.ClearNotify();
	}

	Globals.con.current = Globals.con.totallines - 1;
	Globals.con.display = Globals.con.current;
    }

    static void ClearNotify() {
	int i;
	for (i = 0; i < Defines.NUM_CON_TIMES; i++)
	    Globals.con.times[i] = 0;
    }

    static void DrawString(int x, int y, String s) {
	for (int i = 0; i < s.length(); i++) {
	    Globals.re.DrawChar(x, y, s.charAt(i));
	    x += 8;
	}
    }

    static void DrawAltString(int x, int y, String s) {
	for (int i = 0; i < s.length(); i++) {
	    Globals.re.DrawChar(x, y, s.charAt(i) ^ 0x80);
	    x += 8;
	}
    }

    /*
     * ================ Con_ToggleChat_f ================
     */
    private static Command ToggleChat_f = (List<String> args) -> {
		Key.ClearTyping();

		if (cls.key_dest == key_console) {
		if (cls.state == ca_active) {
			Menu.ForceMenuOff();
			cls.key_dest = key_game;
		}
		} else
		cls.key_dest = key_console;

		ClearNotify();
	};

    /*
     * ================ Con_MessageMode_f ================
     */
    private static Command MessageMode_f = (List<String> args) -> {
		chat_team = false;
		cls.key_dest = key_message;
	};

    /*
     * ================ Con_MessageMode2_f ================
     */
    private static Command MessageMode2_f = (List<String> args) -> {
		chat_team = true;
		cls.key_dest = key_message;
	};

    /*
     * =============== Con_Linefeed ===============
     */
    private static void Linefeed() {
	Globals.con.x = 0;
	if (Globals.con.display == Globals.con.current)
	    Globals.con.display++;
	Globals.con.current++;
	int i = (Globals.con.current % Globals.con.totallines)
		* Globals.con.linewidth;
	int e = i + Globals.con.linewidth;
	while (i < Globals.con.text.length && i++ < e)
	    Globals.con.text[i] = (byte) ' ';
    }

    /*
     * ================ Con_Print
     * 
     * Handles cursor positioning, line wrapping, etc All console printing must
     * go through this in order to be logged to disk If no console is visible,
     * the text will appear at the top of the game window ================
     */
    private static int cr;

    public static void Print(String txt) {
	int y;
	int c, l;
	int mask;
	int txtpos = 0;

	if (!con.initialized)
	    return;

	if (txt.charAt(0) == 1 || txt.charAt(0) == 2) {
	    mask = 128; // go to colored text
	    txtpos++;
	} else
	    mask = 0;

	while (txtpos < txt.length()) {
	    c = txt.charAt(txtpos);
	    // count word length
	    for (l = 0; l < con.linewidth && l < (txt.length() - txtpos); l++)
		if (txt.charAt(l + txtpos) <= ' ')
		    break;

	    // word wrap
	    if (l != con.linewidth && (con.x + l > con.linewidth))
		con.x = 0;

	    txtpos++;

	    if (cr != 0) {
		con.current--;
		cr = 0;
	    }

	    if (con.x == 0) {
		Console.Linefeed();
		// mark time for transparent overlay
		if (con.current >= 0)
		    con.times[con.current % NUM_CON_TIMES] = cls.realtime;
	    }

	    switch (c) {
	    case '\n':
		con.x = 0;
		break;

	    case '\r':
		con.x = 0;
		cr = 1;
		break;

	    default: // display character and advance
		y = con.current % con.totallines;
		con.text[y * con.linewidth + con.x] = (byte) (c | mask | con.ormask);
		con.x++;
		if (con.x >= con.linewidth)
		    con.x = 0;
		break;
	    }
	}
    }

    /*
     * ============== Con_CenteredPrint ==============
     */
    static void CenteredPrint(String text) {
	int l = text.length();
	l = (con.linewidth - l) / 2;
	if (l < 0)
	    l = 0;

	StringBuffer sb = new StringBuffer(1024);
	for (int i = 0; i < l; i++)
	    sb.append(' ');
	sb.append(text);
	sb.append('\n');

	sb.setLength(1024);

	Console.Print(sb.toString());
    }

    /*
     * ==============================================================================
     * 
     * DRAWING
     * 
     * ==============================================================================
     */

    /*
     * ================ Con_DrawInput
     * 
     * The input line scrolls horizontally if typing goes beyond the right edge
     * ================
     */
    private static void DrawInput() {
	int i;
	byte[] text;
	int start = 0;

	if (cls.key_dest == key_menu)
	    return;
	
	if (cls.key_dest != key_console && cls.state == ca_active)
	    return; // don't draw anything (always draw if not active)

	text = key_lines[edit_line];

	// add the cursor frame
	//text[key_linepos] = (byte) (10 + ((int) (cls.realtime >> 8) & 1)); //sfranzyshen

	// fill out remainder with spaces
	for (i = key_linepos ; i < con.linewidth; i++) // sfranzyshen
	    text[i] = ' ';

	// prestep if horizontally scrolling
	if (key_linepos >= con.linewidth)
	    start += 1 + key_linepos - con.linewidth;

	// draw it
	// y = con.vislines-16;

	// sfranzyshen --start
	for (i = 0; i < con.linewidth; i++) {
		//old:re.DrawChar((i + 1) << 3, con.vislines - 22, text[i]);
		if (con.backedit == key_linepos-i && (((int)(Globals.cls.realtime >> 8)&1) !=0))
			re.DrawChar ((i + 1) << 3, con.vislines - 22, (char)11);
		else
			re.DrawChar ((i + 1) << 3, con.vislines - 22, text[i]);
	}
	// sfranzyshen - stop
    
		
	// remove cursor
	key_lines[edit_line][key_linepos] = 0;
    }

    /*
     * ================ Con_DrawNotify
     * 
     * Draws the last few lines of output transparently over the game top
     * ================
     */
    static void DrawNotify() {
	int x, v;
	int text;
	int i;
	int time;
	String s;
	int skip;

	v = 0;
	for (i = con.current - NUM_CON_TIMES + 1; i <= con.current; i++) {
	    if (i < 0)
		continue;

	    time = (int) con.times[i % NUM_CON_TIMES];
	    if (time == 0)
		continue;

	    time = (int) (cls.realtime - time);
	    if (time > con_notifytime.value * 1000)
		continue;

	    text = (i % con.totallines) * con.linewidth;

	    for (x = 0; x < con.linewidth; x++)
		re.DrawChar((x + 1) << 3, v, con.text[text + x]);

	    v += 8;
	}

	if (cls.key_dest == key_message) {
	    if (chat_team) {
		DrawString(8, v, "say_team:");
		skip = 11;
	    } else {
		DrawString(8, v, "say:");
		skip = 5;
	    }

	    s = chat_buffer;
	    if (chat_bufferlen > (viddef.getWidth() >> 3) - (skip + 1))
		//s = s.substring(chat_bufferlen
		//	- ((viddef.getWidth() >> 3) - (skip + 1)));
	    
	    // sfranzyshen -start
	    s = s.substring(chat_bufferlen 	- ((viddef.getWidth() >> 3) - (skip + 1)));

	    for (x = 0; x < s.length(); x++) {
	    	if (chat_backedit > 0 && chat_backedit == chat_buffer.length() -x && ((int)(cls.realtime>>8)&1) !=0) {
	    		re.DrawChar((x + skip) << 3, v, (char)11);
	    	} else {
	    		re.DrawChar((x + skip) << 3, v, s.charAt(x));
	    	}
	    }
	    
	    if (chat_backedit == 0)
	    	re.DrawChar((x + skip) << 3, v, (int) (10 + ((cls.realtime >> 8) & 1)));
	    // sfranzyshen -stop        

	    v += 8;
	}

	if (v != 0) {
	    SCR.AddDirtyPoint(0, 0);
	    SCR.AddDirtyPoint(viddef.getWidth() - 1, v);
	}
    }

    /*
     * ================ Con_DrawConsole
     * 
     * Draws the console with the solid background ================
     */
    static void DrawConsole(float frac) {

	int width = viddef.getWidth();
	int height = viddef.getHeight();
	int lines = (int) (height * frac);
	if (lines <= 0)
	    return;

	if (lines > height)
	    lines = height;

	// draw the background
	re.DrawStretchPic(0, -height + lines, width, height, "conback");
	SCR.AddDirtyPoint(0, 0);
	SCR.AddDirtyPoint(width - 1, lines - 1);

	String version = Com.sprintf("v%4.2f", new Vargs(1).add(VERSION));
	for (int x = 0; x < 5; x++)
	    re
		    .DrawChar(width - 44 + x * 8, lines - 12, 128 + version
			    .charAt(x));

	// draw the text
	con.vislines = lines;

	int rows = (lines - 22) >> 3; // rows of text to draw

	int y = lines - 30;

	// draw from the bottom up
	if (con.display != con.current) {
	    // draw arrows to show the buffer is backscrolled
	    for (int x = 0; x < con.linewidth; x += 4)
		re.DrawChar((x + 1) << 3, y, '^');

	    y -= 8;
	    rows--;
	}

	int i, j, x, n;

	int row = con.display;
	for (i = 0; i < rows; i++, y -= 8, row--) {
	    if (row < 0)
		break;
	    if (con.current - row >= con.totallines)
		break; // past scrollback wrap point

	    int first = (row % con.totallines) * con.linewidth;

	    for (x = 0; x < con.linewidth; x++)
		re.DrawChar((x + 1) << 3, y, con.text[x + first]);
	}

	// ZOID
	// draw the download bar
	// figure out width
	if (cls.download != null) {
	    int text;
	    if ((text = cls.downloadname.lastIndexOf('/')) != 0)
		text++;
	    else
		text = 0;

	    x = con.linewidth - ((con.linewidth * 7) / 40);
	    y = x - (cls.downloadname.length() - text) - 8;
	    i = con.linewidth / 3;
	    StringBuffer dlbar = new StringBuffer(512);
	    if (cls.downloadname.length() - text > i) {
		y = x - i - 11;
		int end = text + i - 1;
		;
		dlbar.append(cls.downloadname.substring(text, end));
		dlbar.append("...");
	    } else {
		dlbar.append(cls.downloadname.substring(text));
	    }
	    dlbar.append(": ");
	    dlbar.append((char) 0x80);

	    // where's the dot go?
	    if (cls.downloadpercent == 0)
		n = 0;
	    else
		n = y * cls.downloadpercent / 100;

	    for (j = 0; j < y; j++) {
		if (j == n)
		    dlbar.append((char) 0x83);
		else
		    dlbar.append((char) 0x81);
	    }
	    dlbar.append((char) 0x82);
	    dlbar.append((cls.downloadpercent < 10) ? " 0" : " ");
	    dlbar.append(cls.downloadpercent).append('%');
	    // draw it
	    y = con.vislines - 12;
	    for (i = 0; i < dlbar.length(); i++)
		re.DrawChar((i + 1) << 3, y, dlbar.charAt(i));
	}
	// ZOID

	// draw the input prompt, user text, and cursor if desired
	DrawInput();
    }
}