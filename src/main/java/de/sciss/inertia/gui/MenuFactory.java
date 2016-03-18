/*
 *  MenuFactory.java
 *  Inertia
 *
 *  Copyright (c) 2004-2005 Hanns Holger Rutz. All rights reserved.
 *
 *	This software is free software; you can redistribute it and/or
 *	modify it under the terms of the GNU General Public License
 *	as published by the Free Software Foundation; either
 *	version 2, june 1991 of the License, or (at your option) any later version.
 *
 *	This software is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *	General Public License for more details.
 *
 *	You should have received a copy of the GNU General Public
 *	License (gpl.txt) along with this software; if not, write to the Free Software
 *	Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 *
 *
 *  Changelog:
 *		11-Aug-05	copied de.sciss.eisenkraut.gui.MenuFactory
 */

package de.sciss.inertia.gui;

import de.sciss.app.AbstractApplication;
import de.sciss.app.EventManager;
import de.sciss.app.LaterInvocationManager;
import de.sciss.app.PreferenceEntrySync;
import de.sciss.gui.AboutBox;
import de.sciss.gui.GUIUtil;
import de.sciss.gui.HelpFrame;
import de.sciss.gui.MenuAction;
import de.sciss.inertia.Main;
import de.sciss.inertia.debug.DebugDumpTracks;
import de.sciss.inertia.debug.DebugMixer;
import de.sciss.inertia.io.PathList;
import de.sciss.inertia.net.RecorderDialog;
import de.sciss.inertia.realtime.MultiTransport;
import de.sciss.inertia.session.DocumentFrame;
import de.sciss.inertia.session.Session;
import de.sciss.inertia.util.PrefsUtil;
import de.sciss.io.IOUtil;
import de.sciss.util.ProcessingThread;
import de.sciss.util.RunnableProcessing;
import de.sciss.util.XMLRepresentation;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.swing.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

// INERTIA
//import de.sciss.eisenkraut.*;
//import de.sciss.eisenkraut.debug.*;
//import de.sciss.eisenkraut.edit.*;
//import de.sciss.eisenkraut.io.*;
//import de.sciss.eisenkraut.math.*;
//import de.sciss.eisenkraut.realtime.*;
//import de.sciss.eisenkraut.render.*;
//import de.sciss.eisenkraut.session.*;
//import de.sciss.eisenkraut.util.*;

/**
 *  <code>JMenu</code>s cannot be added to more than
 *  one frame. Since on MacOS there's one
 *  global menu for all the application windows
 *  we need to 'duplicate' a menu prototype.
 *  Synchronizing all menus is accomplished
 *  by using the same action objects for all
 *  menu copies. However when items are added
 *  or removed, synchronization needs to be
 *  performed manually. That's the point about
 *  this class.
 *  <p>
 *  <code>JInternalFrames</code> have been removed
 *  because they don't offer a constistent look-and-feel
 *  on MacOS and besides the main window would
 *  have to occupy most of the visible screen.
 *  Unfortunately this means we cannot use
 *  'floating' palettes any more.
 *  <p>
 *  There can be only one instance of <code>MenuFactory</code>
 *  for the application, and that will be created by the
 *  <code>Main</code> class.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.5, 02-Aug-05
 *
 *
 *  @todo   on operating systems that do not have a
 *			global screen menu bar but attach a menubar
 *			directly to a windows frame &mdash; this happens
 *			on Linux and Windows &mdash; not every frame should
 *			display the global menu. Small windows such
 *			as the palettes should go without a menubar
 *			but nevertheless a way of responding to accelerator
 *			keys should be found.
 *  @todo   see actionNewReceiversClass.actionPerformed.
 */
public class MenuFactory
implements de.sciss.app.DocumentListener
{
    /**
     *	<code>KeyStroke</code> modifier mask
     *	representing the platform's default
     *	menu accelerator (e.g. Apple-key on Mac,
     *	Ctrl on Windows).
     *
     *	@see	Toolkit#getMenuShortcutKeyMask()
     */
    public static final int MENU_SHORTCUT				= Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

    public static final int MI_FILE_CLOSE				= 0x101;
    public static final int M_FILE_IMPORT				= 0x102;
    public static final int MI_FILE_IMPORTMARKERS		= 0x103;
    public static final int MI_FILE_SAVE				= 0x104;
    public static final int MI_FILE_SAVEAS				= 0x105;
    public static final int MI_FILE_SAVECOPY			= 0x106;

    public static final int M_EDIT						= 0x200;
    public static final int MI_EDIT_UNDO				= 0x201;
    public static final int MI_EDIT_REDO				= 0x202;
    public static final int MI_EDIT_CUT					= 0x203;
    public static final int MI_EDIT_COPY				= 0x204;
    public static final int MI_EDIT_PASTE				= 0x205;
    public static final int MI_EDIT_CLEAR				= 0x206;
    public static final int MI_EDIT_SELECTALL			= 0x207;

    public static final int M_TIMELINE					= 0x300;
    public static final int MI_TIMELINE_INSERTSILENCE	= 0x301;
    public static final int MI_TIMELINE_TRIMTOSELECTION	= 0x302;

    public static final int M_PROCESS					= 0x400;
    public static final int MI_PROCESS_FADEIN			= 0x401;
    public static final int MI_PROCESS_FADEOUT			= 0x402;
    public static final int MI_PROCESS_GAIN				= 0x403;
    public static final int MI_PROCESS_INVERT			= 0x404;
    public static final int MI_PROCESS_REVERSE			= 0x405;
    public static final int MI_PROCESS_ROTATECHANNELS	= 0x406;
    public static final int MI_PROCESS_SILENCE			= 0x407;

    public static final int MI_OPERATION_MISSINGLINKS	= 0x501;
    public static final int MI_OPERATION_COMPACTAUDIO	= 0x502;

    private final Main		root;
    private final JMenuBar	protoType;

    private JMenu   openRecentMenu;
    private JMenu   debugMenu;

    private actionOpenClass				actionOpen;
    private actionOpenRecentClass		actionOpenRecent;

    private SyncedBooleanPrefsMenuAction	actionInsertionFollowsPlay, actionViewNullLinie,
                                            actionViewVerticalRulers, actionViewMarkers;
    private SyncedIntPrefsMenuAction		actionTimeUnitsSamples, actionTimeUnitsMinSecs;

    private Action	actionClearRecent, actionNewEmpty, actionClose, actionCloseAll,
                    actionImport, actionImportMarkers,
                    actionSave, actionSaveAs, actionSaveCopy,
                    actionEdit, actionUndo, actionRedo, actionCut, actionCopy, actionPaste, actionClear, actionSelectAll,
                    actionShowIOSetup, actionShowMain, actionShowRecorder, actionShowTransport,
                    actionShowObserver, actionShowMeter, actionAbout,
                    actionHelpManual, actionHelpShortcuts, actionHelpWebsite,
                    actionTimeline, actionTrimToSelection, actionInsertSilence,
                    actionProcess, actionMissingLinks, actionCompactAudio,
                    actionFadeIn, actionFadeOut, actionGain, actionInvert, actionReverse,
                    actionRotateChannels, actionSilence;

    private Action  actionDebugDumpUndo, actionDebugDumpPrefs, actionDebugDumpTracks,
                    actionDebugCache, actionDebugMarkers, actionDebugLoadDefs, actionDebugMixer;

    private final java.util.List	collMenuHosts		= new ArrayList();
    private final java.util.Map		syncedItems			= new HashMap();
    private final java.util.List	collGlobalKeyCmd	= new ArrayList();
    private final PathList			openRecentPaths;

    // --- publicly accessible actions ---
    /*
     *  Action that handles
     *  the application-quit operation.
     */
    private actionQuitClass				actionQuit;
    /*
     *  Action that opens the
     *  preferences frame.
     */
    private actionPreferencesClass		actionPreferences;

    /**
     *  The constructor is called only once by
     *  the <code>Main</code> class and will create a prototype
     *  main menu from which all copies are
     *  derived.
     *
     *  @param  root	application root
     */
    public MenuFactory( Main root )
    {
        this.root   = root;

        openRecentPaths = new PathList( 8, AbstractApplication.getApplication().getUserPrefs(), PrefsUtil.KEY_OPENRECENT );
        protoType		= new JMenuBar();

        createActions();
        createProtoType();

        // ---- listeners -----

        root.getDocumentHandler().addDocumentListener( this );
    }

    /**
     *  Requests a copy of the main menu.
     *  When the frame is disposed, it should
     *  call the <code>forgetAbout</code> method.
     *
     *  @param  who		the frame who requests the menu. The
     *					menu will be set for the frame by this
     *					method.
     *
     *  @see	#forgetAbout( JFrame )
     *  @see	javax.swing.JFrame#setJMenuBar( JMenuBar )
     *  @synchronization	must be called in the event thread
     */
    public void gimmeSomethingReal( BasicFrame who )
    {
        Action		a;
        String		entry;
        int			i;
        JRootPane   rp  = who.getRootPane();

        JMenuBar copy = createMenuBarCopy( who );
        who.setJMenuBar( copy );
        collMenuHosts.add( who );
        for( i = 0; i < collGlobalKeyCmd.size(); i++ ) {
            a		= (Action) collGlobalKeyCmd.get( i );
            entry   = (String) a.getValue( Action.NAME );
            rp.getInputMap( JComponent.WHEN_IN_FOCUSED_WINDOW ).put( (KeyStroke) a.getValue( Action.ACCELERATOR_KEY ), entry );
            rp.getActionMap().put( entry, a );
        }
    }

    /**
     *  Tell the <code>MenuFactory</code> that a frame
     *  is being disposed, therefore allowing the removal
     *  of the menu bar which will free resources and
     *  remove unnecessary synchronization.
     *
     *  @param  who		the frame which is about to be disposed
     *
     *  @todo   this method should remove any key actions
     *			attached to the input maps.
     *  @synchronization	must be called in the event thread
     */
    public void forgetAbout( JFrame who )
    {
        collMenuHosts.remove( who );
        who.setJMenuBar( null );
    }

    public boolean closeAll( boolean force )
    {
        final de.sciss.app.DocumentHandler	dh	= AbstractApplication.getApplication().getDocumentHandler();
        Session								doc;

        while( dh.getDocumentCount() > 0 ) {
            doc	= (Session) dh.getDocument( 0 );
            if( !doc.getFrame().closeDocument( force )) return false;
        }

        return true;
    }

    /**
     *  Sets all JMenuBars enabled or disabled.
     *  When time taking asynchronous processing
     *  is done, like loading a session or bouncing
     *  it to disk, the menus need to be disabled
     *  to prevent the user from accidentally invoking
     *  menu actions that can cause deadlocks if they
     *  try to gain access to blocked doors. This
     *  method traverses the list of known frames and
     *  sets each frame's menu bar enabled or disabled.
     *
     *  @param  enabled		<code>true</code> to enable
     *						all menu bars, <code>false</code>
     *						to disable them.
     *  @synchronization	must be called in the event thread
     */
    public void setMenuBarsEnabled( boolean enabled )
    {
        JMenuBar mb;

        for( int i = 0; i < collMenuHosts.size(); i++ ) {
            mb = ((JFrame) collMenuHosts.get( i )).getJMenuBar();
            if( mb != null ) mb.setEnabled( enabled );
        }
    }

    private static int uniqueNumber = 0;	// increased by addGlobalKeyCommand()
    /**
     *  Adds an action object invisibly to all
     *  menu bars, enabling its keyboard shortcut
     *  to be accessed no matter what window
     *  has the focus.
     *
     *  @param  a   the <code>Action</code> whose
     *				accelerator key should be globally
     *				accessible. The action
     *				is stored in the input and action map of each
     *				registered frame's root pane, thus being
     *				independant of calls to <code>setMenuBarsEnabled/code>.
     *
     *  @throws java.lang.IllegalArgumentException  if the action does
     *												not have an associated
     *												accelerator key
     *
     *  @see  javax.swing.Action#ACCELERATOR_KEY
     *  @synchronization	must be called in the event thread
     */
    public void addGlobalKeyCommand( Action a )
    {
        JFrame		frame;
        JRootPane   rp;
        String		entry;
        int			i;
        KeyStroke   acc		= (KeyStroke) a.getValue( Action.ACCELERATOR_KEY );

        if( acc == null ) throw new IllegalArgumentException();

        entry = "key" + String.valueOf( uniqueNumber++ );
        a.putValue( Action.NAME, entry );

        for( i = 0; i < collMenuHosts.size(); i++ ) {
            frame   = (JFrame) collMenuHosts.get( i );
            rp		= frame.getRootPane();
            rp.getInputMap( JComponent.WHEN_IN_FOCUSED_WINDOW ).put( acc, entry );
            rp.getActionMap().put( entry, a );
        }
        collGlobalKeyCmd.add( a );
    }

    private void createActions()
    {
        final int shortcutKeyMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
//		final de.sciss.app.Application	app = AbstractApplication.getApplication();

        // --- file menu ---
        actionNewEmpty	= new actionNewEmptyClass( getResourceString( "menuNewEmpty" ),
                                                KeyStroke.getKeyStroke( KeyEvent.VK_N, shortcutKeyMask ));
        actionOpen		= new actionOpenClass(  getResourceString( "menuOpen" ),
                                                KeyStroke.getKeyStroke( KeyEvent.VK_O, shortcutKeyMask ));
        actionOpenRecent = new actionOpenRecentClass( getResourceString( "menuOpenRecent" ));
        actionClearRecent = new actionClearRecentClass( getResourceString( "menuClearRecent" ), null );
        actionClose		= new actionDummyClass( getResourceString( "menuClose" ),
                                                KeyStroke.getKeyStroke( KeyEvent.VK_W, shortcutKeyMask ),
                                                MI_FILE_CLOSE );
        actionCloseAll	= new actionCloseAllClass( getResourceString( "menuCloseAll" ), null );
        actionImport	= new actionDummyClass( getResourceString( "menuImport" ), null, M_FILE_IMPORT );
        actionImportMarkers = new actionDummyClass( getResourceString( "menuImportMarkers" ), null,
                                                MI_FILE_IMPORTMARKERS );
        actionSave		= new actionDummyClass( getResourceString( "menuSave" ),
                                                KeyStroke.getKeyStroke( KeyEvent.VK_S, shortcutKeyMask ),
                                                MI_FILE_SAVE );
        actionSaveAs	= new actionDummyClass( getResourceString( "menuSaveAs" ), KeyStroke.getKeyStroke(
                                                KeyEvent.VK_S, shortcutKeyMask + KeyEvent.SHIFT_MASK ),
                                                MI_FILE_SAVEAS );
        actionSaveCopy	= new actionDummyClass( getResourceString( "menuSaveCopy" ), null, MI_FILE_SAVECOPY );
        actionQuit		= new actionQuitClass(  getResourceString( "menuQuit" ),
                                                KeyStroke.getKeyStroke( KeyEvent.VK_Q, shortcutKeyMask ));

        // --- edit menu ---
        actionEdit		= new actionDummyClass( getResourceString( "menuEdit" ), null, M_EDIT );
//actionEdit.setEnabled( true );
        actionUndo		= new actionDummyClass( getResourceString( "menuUndo" ),
                                                KeyStroke.getKeyStroke( KeyEvent.VK_Z, shortcutKeyMask ),
                                                MI_EDIT_UNDO );
        actionRedo		= new actionDummyClass( getResourceString( "menuRedo" ), KeyStroke.getKeyStroke(
                                                KeyEvent.VK_Z, shortcutKeyMask + KeyEvent.SHIFT_MASK ),
                                                MI_EDIT_REDO );
        actionCut		= new actionDummyClass( getResourceString( "menuCut" ),
                                                KeyStroke.getKeyStroke( KeyEvent.VK_X, shortcutKeyMask ),
                                                MI_EDIT_CUT );
        actionCopy		= new actionDummyClass( getResourceString( "menuCopy" ),
                                                KeyStroke.getKeyStroke( KeyEvent.VK_C, shortcutKeyMask ),
                                                MI_EDIT_COPY );
        actionPaste		= new actionDummyClass( getResourceString( "menuPaste" ),
                                                KeyStroke.getKeyStroke( KeyEvent.VK_V, shortcutKeyMask ),
                                                MI_EDIT_PASTE );
        actionClear		= new actionDummyClass( getResourceString( "menuClear" ),
                                                KeyStroke.getKeyStroke( KeyEvent.VK_BACK_SPACE, 0 ),
                                                MI_EDIT_CLEAR );
        actionSelectAll = new actionDummyClass( getResourceString( "menuSelectAll" ),
                                                KeyStroke.getKeyStroke( KeyEvent.VK_A, shortcutKeyMask ),
                                                MI_EDIT_SELECTALL );
        actionPreferences = new actionPreferencesClass( getResourceString( "menuPreferences" ),
                                                KeyStroke.getKeyStroke( KeyEvent.VK_COMMA, shortcutKeyMask ));

        // --- timeline menu ---
        actionTimeline	= new actionDummyClass( getResourceString( "menuTimeline" ), null, M_TIMELINE );
//actionTimeline.setEnabled( true );
        actionInsertSilence = new actionDummyClass( getResourceString( "menuInsertSilence" ), KeyStroke.getKeyStroke(
                                                KeyEvent.VK_E, shortcutKeyMask + KeyEvent.SHIFT_MASK ),
                                                MI_TIMELINE_INSERTSILENCE );
        actionTrimToSelection = new actionDummyClass( getResourceString( "menuTrimToSelection" ),
                                                KeyStroke.getKeyStroke( KeyEvent.VK_F5, shortcutKeyMask ),
                                                MI_TIMELINE_TRIMTOSELECTION );

        // --- process menu ---
        actionProcess	= new actionDummyClass( getResourceString( "menuProcess" ), null, M_PROCESS );
        actionFadeIn	= new actionDummyClass(	getResourceString( "menuFadeIn" ),
                                                KeyStroke.getKeyStroke( KeyEvent.VK_I, KeyEvent.CTRL_MASK ),
                                                MI_PROCESS_FADEIN );
        actionFadeOut	= new actionDummyClass( getResourceString( "menuFadeOut" ),
                                                KeyStroke.getKeyStroke( KeyEvent.VK_O, KeyEvent.CTRL_MASK ),
                                                MI_PROCESS_FADEOUT );
        actionGain		= new actionDummyClass( getResourceString( "menuGain" ),
                                                 KeyStroke.getKeyStroke( KeyEvent.VK_N, KeyEvent.CTRL_MASK ),
                                                MI_PROCESS_GAIN );
        actionInvert	= new actionDummyClass( getResourceString( "menuInvert" ), null,
                                                MI_PROCESS_INVERT );
        actionReverse	= new actionDummyClass( getResourceString( "menuReverse" ), null,
                                                MI_PROCESS_REVERSE );
        actionRotateChannels = new actionDummyClass( getResourceString( "menuRotateChannels" ), null,
                                                MI_PROCESS_ROTATECHANNELS );
        actionSilence	= new actionDummyClass( getResourceString( "menuSilence" ),
                                                KeyStroke.getKeyStroke( KeyEvent.VK_E, shortcutKeyMask ),
                                                MI_PROCESS_SILENCE );

        // --- operation menu ---

        actionInsertionFollowsPlay = new SyncedBooleanPrefsMenuAction( getResourceString( "menuInsertionFollowsPlay" ), null );
        syncedItems.put( actionInsertionFollowsPlay, new ArrayList() );
        actionMissingLinks	= new actionDummyClass( getResourceString( "menuMissingLinks" ), null,
                                                MI_OPERATION_MISSINGLINKS );
        actionCompactAudio	= new actionDummyClass( getResourceString( "menuCompactAudio" ), null,
                                                MI_OPERATION_COMPACTAUDIO );

        // --- view menu ---

        actionViewNullLinie = new SyncedBooleanPrefsMenuAction( getResourceString( "menuViewNullLinie" ), null );
        syncedItems.put( actionViewNullLinie, new ArrayList() );
        actionViewVerticalRulers = new SyncedBooleanPrefsMenuAction( getResourceString( "menuViewVerticalRulers" ), null );
        syncedItems.put( actionViewVerticalRulers, new ArrayList() );
        actionViewMarkers = new SyncedBooleanPrefsMenuAction( getResourceString( "menuViewMarkers" ), null );
        syncedItems.put( actionViewMarkers, new ArrayList() );
        actionTimeUnitsSamples = new SyncedIntPrefsMenuAction( getResourceString( "menuTimeUnitsSamples" ), null, PrefsUtil.TIME_SAMPLES );
        syncedItems.put( actionTimeUnitsSamples, new ArrayList() );
        actionTimeUnitsMinSecs = new SyncedIntPrefsMenuAction( getResourceString( "menuTimeUnitsMinSecs" ), null, PrefsUtil.TIME_MINSECS );
        syncedItems.put( actionTimeUnitsMinSecs, new ArrayList() );

        // --- window menu ---
        actionShowIOSetup		= new actionIOSetupClass( getResourceString( "frameIOSetup" ), null );
        actionShowMain			= new actionShowWindowClass( getResourceString( "frameMain" ), KeyStroke.getKeyStroke(
                                        KeyEvent.VK_NUMPAD2, MENU_SHORTCUT ), Main.COMP_MAIN );
        actionShowObserver		= new actionShowWindowClass( getResourceString( "paletteObserver" ), KeyStroke.getKeyStroke(
                                        KeyEvent.VK_NUMPAD3, MENU_SHORTCUT ), Main.COMP_OBSERVER );
        actionShowRecorder		= new actionRecorderClass( getResourceString( "frameRecorder" ), null );
 
        actionAbout				= new actionAboutClass( getResourceString( "menuAbout" ), null );

        // --- debug menu ---
        actionDebugDumpPrefs	= PrefsUtil.getDebugDumpAction( root );
        actionDebugDumpTracks	= new DebugDumpTracks();
        actionDebugLoadDefs		= root.superCollider.getDebugLoadDefsAction();
// INERTIA
//		actionDebugCache		= root.cacheManager.getDebugDumpAction();
        actionDebugMixer		= DebugMixer.getDebugMixerAction();

        // --- help menu ---
        actionHelpManual		= new actionURLViewerClass( getResourceString( "menuHelpManual" ), null, "index", false );
        actionHelpShortcuts		= new actionURLViewerClass( getResourceString( "menuHelpShortcuts" ), null, "Shortcuts", false );
        actionHelpWebsite		= new actionURLViewerClass( getResourceString( "menuHelpWebsite" ), null, getResourceString( "appURL" ), true );
    }

    private void createProtoType()
    {
        JMenu							mainMenu, subMenu;
        JCheckBoxMenuItem				cbmi;
        JRadioButtonMenuItem			rbmi;
        ButtonGroup						bg;
        JMenuItem						mi;
        Preferences						prefs;
        final de.sciss.app.Application	app		= AbstractApplication.getApplication();
//		final de.sciss.app.UndoManager	undo	= doc.getUndoManager();

        // --- file menu ---

        mainMenu	= new JMenu( getResourceString( "menuFile" ));
        subMenu		= new JMenu( getResourceString( "menuNew" ));
        subMenu.add( new JMenuItem( actionNewEmpty ));
        mainMenu.add( subMenu );
        mainMenu.add( new JMenuItem( actionOpen ));
        openRecentMenu = new JMenu( actionOpenRecent );
        if( openRecentPaths.getPathCount() > 0 ) {
            for( int i = 0; i < openRecentPaths.getPathCount(); i++ ) {
                openRecentMenu.add( new JMenuItem( new actionOpenRecentClass( openRecentPaths.getPath( i ))));
            }
            actionOpenRecent.setPath( openRecentPaths.getPath( 0 ));
            actionOpenRecent.setEnabled( true );
        }
        openRecentMenu.addSeparator();
        openRecentMenu.add( new JMenuItem( actionClearRecent ));
        mainMenu.add( openRecentMenu );
        mainMenu.add( new JMenuItem( actionClose ));
        mainMenu.add( new JMenuItem( actionCloseAll ));
        mainMenu.addSeparator();
        subMenu		= new JMenu( actionImport );
        subMenu.add( new JMenuItem( actionImportMarkers ));
        mainMenu.add( subMenu );
        mainMenu.addSeparator();
        mainMenu.add( new JMenuItem( actionSave ));
        mainMenu.add( new JMenuItem( actionSaveAs ));
        mainMenu.add( new JMenuItem( actionSaveCopy ));
        mi			= new JMenuItem(); // root.getQuitJMenuItem();
        mi.setAction( actionQuit );
//		if( !QuitJMenuItem.isAutomaticallyPresent() ) {
            mainMenu.addSeparator();
            mainMenu.add( mi );
//		}
        protoType.add( mainMenu );

        // --- edit menu ---

        mainMenu	= new JMenu( actionEdit );
        mainMenu.add( new JMenuItem( actionUndo ));
        mainMenu.add( new JMenuItem( actionRedo ));
        mainMenu.addSeparator();
        mainMenu.add( new JMenuItem( actionCut ));
        mainMenu.add( new JMenuItem( actionCopy ));
        mainMenu.add( new JMenuItem( actionPaste ));
        mainMenu.add( new JMenuItem( actionClear ));
        mainMenu.addSeparator();
        mainMenu.add( new JMenuItem( actionSelectAll ));
        mi			= new JMenuItem(); // root.getPreferencesJMenuItem();
        mi.setAction( actionPreferences );
//		if( !PreferencesJMenuItem.isAutomaticallyPresent() ) {
            mainMenu.addSeparator();
            mainMenu.add( mi );
            actionEdit.setEnabled( true );
//		}
        protoType.add( mainMenu );

        // --- timeline menu ---

        mainMenu	= new JMenu( actionTimeline );
        mainMenu.add( new JMenuItem( actionTrimToSelection ));
        mainMenu.add( new JMenuItem( actionInsertSilence ));
        protoType.add( mainMenu );

        // --- process menu ---

// INERTIA
//		mainMenu  = new JMenu( actionProcess );
//		mainMenu.add( new JMenuItem( actionFadeIn ));
//		mainMenu.add( new JMenuItem( actionFadeOut ));
//		mainMenu.add( new JMenuItem( actionGain ));
//		mainMenu.add( new JMenuItem( actionInvert ));
//		mainMenu.add( new JMenuItem( actionReverse ));
//		mainMenu.add( new JMenuItem( actionRotateChannels ));
//		mainMenu.add( new JMenuItem( actionSilence ));
//		protoType.add( mainMenu );

        // --- operation menu ---

        mainMenu	= new JMenu( getResourceString( "menuOperation" ));
        prefs		= app.getUserPrefs();
        cbmi		= new JCheckBoxMenuItem( actionInsertionFollowsPlay );
        ((java.util.List) syncedItems.get( actionInsertionFollowsPlay )).add( cbmi );
        actionInsertionFollowsPlay.setPreferences( prefs, PrefsUtil.KEY_INSERTIONFOLLOWSPLAY );
        mainMenu.add( cbmi );
        protoType.add( mainMenu );
        mainMenu.addSeparator();
        mainMenu.add( new JMenuItem( actionMissingLinks ));
        mainMenu.add( new JMenuItem( actionCompactAudio ));

        // --- view menu ---

        mainMenu	= new JMenu( getResourceString( "menuView" ));
        prefs		= app.getUserPrefs();
        subMenu		= new JMenu( getResourceString( "menuTimeUnits" ));
        bg			= new ButtonGroup();
        rbmi		= new JRadioButtonMenuItem( actionTimeUnitsSamples );
        bg.add( rbmi );
        ((java.util.List) syncedItems.get( actionTimeUnitsSamples )).add( rbmi );
        actionTimeUnitsSamples.setPreferences( prefs, PrefsUtil.KEY_TIMEUNITS );
        subMenu.add( rbmi );
        rbmi		= new JRadioButtonMenuItem( actionTimeUnitsMinSecs );
        bg.add( rbmi );
        ((java.util.List) syncedItems.get( actionTimeUnitsMinSecs )).add( rbmi );
        actionTimeUnitsMinSecs.setPreferences( prefs, PrefsUtil.KEY_TIMEUNITS );
        subMenu.add( rbmi );
        mainMenu.add( subMenu );

        cbmi		= new JCheckBoxMenuItem( actionViewNullLinie );
        ((java.util.List) syncedItems.get( actionViewNullLinie )).add( cbmi );
        actionViewNullLinie.setPreferences( prefs, PrefsUtil.KEY_VIEWNULLLINIE );
        mainMenu.add( cbmi );
        cbmi		= new JCheckBoxMenuItem( actionViewVerticalRulers );
        ((java.util.List) syncedItems.get( actionViewVerticalRulers )).add( cbmi );
        actionViewVerticalRulers.setPreferences( prefs, PrefsUtil.KEY_VIEWVERTICALRULERS );
        mainMenu.add( cbmi );
        cbmi		= new JCheckBoxMenuItem( actionViewMarkers );
        ((java.util.List) syncedItems.get( actionViewMarkers )).add( cbmi );
        actionViewMarkers.setPreferences( prefs, PrefsUtil.KEY_VIEWMARKERS );
        mainMenu.add( cbmi );
        protoType.add( mainMenu );

        // --- window menu ---

        mainMenu	= new JMenu( getResourceString( "menuWindow" ));
        mainMenu.add( new JMenuItem( actionShowIOSetup ));
        mainMenu.addSeparator();
        mainMenu.add( new JMenuItem( actionShowMain ));
        mainMenu.add( new JMenuItem( actionShowObserver ));
        mainMenu.add( new JMenuItem( actionShowRecorder ));
        protoType.add( mainMenu );

        // --- debug menu ---

        mainMenu	= new JMenu( "Debug" );
        mainMenu.add( new JMenuItem( actionDebugDumpPrefs ));
        mainMenu.add( new JMenuItem( actionDebugDumpTracks ));
        mainMenu.add( new JMenuItem( actionDebugLoadDefs ));
// INERTIA
//		mainMenu.add( new JMenuItem( actionDebugCache ));
        mainMenu.add( new JMenuItem( actionDebugMixer ));
        protoType.add( mainMenu );

        // --- help menu ---

        mainMenu	= new JMenu( getResourceString( "menuHelp" ));
        mainMenu.add( new JMenuItem( actionHelpManual ));
        mainMenu.add( new JMenuItem( actionHelpShortcuts ));
        mainMenu.addSeparator();
        mainMenu.add( new JMenuItem( actionHelpWebsite ));
        mi			= new JMenuItem(); // root.getAboutJMenuItem();
        mi.setAction( actionAbout );
//		if( !AboutJMenuItem.isAutomaticallyPresent() ) {
            mainMenu.addSeparator();
            mainMenu.add( mi );
//		}
        protoType.add( mainMenu );
    }

    private JMenuBar createMenuBarCopy( BasicFrame who )
    {
        JMenuBar	copy	= new JMenuBar();
        int			i;

        for( i = 0; i < protoType.getMenuCount(); i++ ) {
            copy.add( createMenuCopy( who, protoType.getMenu( i )));
        }

        return copy;
    }

    private JMenu createMenuCopy( BasicFrame who, JMenu pMenu )
    {
        JMenu			cMenu   = new JMenu( pMenu.getText() );
        JMenuItem		pMenuItem, cMenuItem;
        Action			action;
        ButtonGroup		bg		= null;	// THERE CAN BE ONLY ONE GROUP PER SUBMENU NOW!
        java.util.List	v;

        action = createRealAction( who, pMenu.getAction() );
        if( action != null ) {
            cMenu.setAction( action );
        }
        cMenu.setVisible( pMenu.isVisible() );

        for( int i = 0; i < pMenu.getItemCount(); i++ ) {
            pMenuItem   = pMenu.getItem( i );
            if( pMenuItem != null ) {
                action		= createRealAction( who, pMenuItem.getAction() );
                if( pMenuItem instanceof JMenu ) {  // recursive into submenus
                    cMenuItem = createMenuCopy( who, (JMenu) pMenuItem );
                } else if( pMenuItem instanceof JCheckBoxMenuItem ) {
                    cMenuItem = new JCheckBoxMenuItem( pMenuItem.getText(), ((JCheckBoxMenuItem) pMenuItem).isSelected() );
                    v		  = (java.util.List) syncedItems.get( action );
                    v.add( cMenuItem );
                } else if( pMenuItem instanceof JRadioButtonMenuItem ) {
                    cMenuItem = new JRadioButtonMenuItem( pMenuItem.getText(), ((JRadioButtonMenuItem) pMenuItem).isSelected() );
                    v		  = (java.util.List) syncedItems.get( action );
                    v.add( cMenuItem );
                    if( bg == null ) bg = new ButtonGroup();
                    bg.add( cMenuItem );
                } else {
                    cMenuItem = new JMenuItem( pMenuItem.getText() );
                }
                if( action != null ) {
                    cMenuItem.setAction( action );
                }
                cMenu.add( cMenuItem );
            } else {  // components used other that JMenuItems are separators
                cMenu.add( new JSeparator() );
            }
        }

        return cMenu;
    }

    private Action createRealAction( BasicFrame who, Action a )
    {
        if( (a != null) && (a instanceof actionDummyClass) ) {
            return who.replaceDummyAction( ((actionDummyClass) a).getID(), a );
        } else {
            return a;
        }
    }

//	// returns the current active window
//	private JFrame fuckINeedTheWindow( ActionEvent e )
//	{
//		JFrame host;
//
//		for( int i = 0; i < collMenuHosts.size(); i++ ) {
//			host = (JFrame) collMenuHosts.get( i );
//			if( host.isActive() ) return host;
//		}
//		return null;
//	}

    public void openDocument( File f )
    {
        actionOpen.perform( f );
    }

    public void showPreferences()
    {
        actionPreferences.perform();
    }

    // adds a file to the top of
    // the open recent menu of all menubars
    // and the prototype. calls
    // openRecentPaths.addPathToHead() and
    // thus updates the preferences settings
    // iteratively calls addRecent( JMenuBar, File, boolean )
    public void addRecent( File path )
    {
        JMenuBar	mb;
        JMenu		m;
        int			i;
        boolean		removeTail;

        i = openRecentPaths.indexOf( path );
        if( i == 0 ) return;
        if( (i == -1) && (openRecentPaths.getCapacity() == openRecentPaths.getPathCount()) ) {
            i = openRecentPaths.getPathCount() - 1;
        }
        if( i > 0 ) openRecentPaths.remove( path );
        for( int j = 0; j < collMenuHosts.size(); j++ ) {
            mb	= ((JFrame) collMenuHosts.get( j )).getJMenuBar();
            if( mb == null ) continue;
            m	= (JMenu) findMenuItem( mb, actionOpenRecent );
            if( m == null ) continue;
            if( i > 0 ) m.remove( i );
            m.insert( new JMenuItem( new actionOpenRecentClass( path )), 0 );
        }

        m	= (JMenu) findMenuItem( protoType, actionOpenRecent );
        if( m != null ) {
            if( i > 0 ) m.remove( i );
            m.insert( new JMenuItem( new actionOpenRecentClass( path )), 0 );
        }

        openRecentPaths.addPathToHead( path );
        actionOpenRecent.setPath( path );
    }

    // find the menuitem whose action is
    // the action passed to the method.
    // traverse the whole hierarchy of the given menubar.
    // iteratively calls findMenuItem( JMenu, Action )
    private JMenuItem findMenuItem( JMenuBar mb, Action action )
    {
        int			i;
        JMenuItem   mi  = null;

        for( i = 0; mi == null && i < mb.getMenuCount(); i++ ) {
            mi = findMenuItem( mb.getMenu( i ), action );
        }
        return mi;
    }

    private JMenuItem findMenuItem( JMenu m, Action action )
    {
        int			i;
        JMenuItem   mi;

        for( i = 0; i < m.getItemCount(); i++ ) {
            mi = m.getItem( i );
            if( mi != null ) {
                if( mi.getAction() == action ) return mi;
                if( mi instanceof JMenu ) {
                    mi = findMenuItem( (JMenu) mi, action );
                    if( mi != null ) return mi;
                }
            }
        }
        return null;
    }

    private static String getResourceString( String key )
    {
        return AbstractApplication.getApplication().getResourceString( key );
    }

// ---------------- DocumentListener interface ---------------- 

    public void documentAdded( de.sciss.app.DocumentEvent e )
    {
        if( !actionCloseAll.isEnabled() ) actionCloseAll.setEnabled( true );
    }

    public void documentRemoved( de.sciss.app.DocumentEvent e )
    {
        if( AbstractApplication.getApplication().getDocumentHandler().getDocumentCount() == 0 ) {
            actionCloseAll.setEnabled( false );
        }
    }

    public void documentFocussed( de.sciss.app.DocumentEvent e ) {}

// ---------------- Action objects for file (session) operations ---------------- 

    private static class actionDummyClass
    extends MenuAction
    {
        private final int ID;

        private actionDummyClass( String text, KeyStroke shortcut, int ID )
        {
            super( text, shortcut );

            setEnabled( false );
            this.ID = ID;
        }

        private int getID()
        {
            return ID;
        }

        public void actionPerformed( ActionEvent e ) {}
    }

    // action for the New-Empty Document menu item
    private class actionNewEmptyClass
    extends MenuAction
    {
        private actionNewEmptyClass( String text, KeyStroke shortcut )
        {
            super( text, shortcut );
        }

        public void actionPerformed( ActionEvent e )
        {
            final Session doc = new Session();

            doc.setTransport( new MultiTransport( doc, doc.timeline, Session.NUM_MOVIES, doc.bird,
                                Session.DOOR_TIME ));
            doc.setFrame( new DocumentFrame( root, doc ));
            AbstractApplication.getApplication().getDocumentHandler().addDocument( this, doc );
        }
    }

    // action for the Open-Session menu item
    private class actionOpenClass
    extends MenuAction
    implements RunnableProcessing, FilenameFilter
    {
        private String text;

        private actionOpenClass( String text, KeyStroke shortcut )
        {
            super( text, shortcut );

            this.text = text;
        }

        /*
         *  Open a Session. If the current Session
         *  contains unsaved changes, the user is prompted
         *  to confirm. A file chooser will pop up for
         *  the user to select the session to open.
         */
        public void actionPerformed( ActionEvent e )
        {
            File f = queryFile();
            if( f != null ) perform( f );
        }

        private File queryFile()
        {
            FileDialog  fDlg;
            String		strFile, strDir;

            Frame		frame = (Frame) root.getComponent( Main.COMP_MAIN );

            fDlg	= new FileDialog( frame, AbstractApplication.getApplication().getResourceString(
                "fileDlgOpen" ), FileDialog.LOAD );
            fDlg.setFilenameFilter( this );
            // fDlg.setDirectory();
            // fDlg.setFile();
            fDlg.setVisible( true ); // show();
            strDir	= fDlg.getDirectory();
            strFile	= fDlg.getFile();

            if( strFile == null ) return null;   // means the dialog was cancelled

            return( new File( strDir, strFile ));
        }

        // FilenameFilter interfac
        public boolean accept( File dir, String name )
        {
            return( name.endsWith( Session.FILE_EXTENSION ));
        }

        /**
         *  Loads a new session file.
         *  If transport is running, is will be stopped.
         *  The console window is cleared an a <code>ProcessingThread</code>
         *  started which loads the new session.
         *
         *  @param  path	the file of the session to be loaded
         *
         *  @synchronization	this method must be called in event thread
         */
        protected ProcessingThread perform( File path )
        {
            final Object[] args = new Object[2];
            args[0] = path;					// AudioFile.openAsRead( path );
            return( new ProcessingThread( this, new de.sciss.inertia.gui.ProgressMonitor( null,
                getResourceString( "labelOpening" ) + " " + path.getName() + " ..." ), null, text, args, 0 ));
        }

        public boolean run( ProcessingThread context, Object argument )
        {
            boolean							success		= false;
            final org.w3c.dom.Document		domDoc;
            final DocumentBuilderFactory	builderFactory;
            final DocumentBuilder			builder;
            final NodeList					nl;
            final Object[]					args		= (Object[]) argument;
            final File						f			= (File) args[0];
            final Session					doc			= new Session();
            final Map						options		= new HashMap();		// (Map) argument;

            builderFactory  = DocumentBuilderFactory.newInstance();
            builderFactory.setValidating( true );
            doc.getUndoManager().discardAllEdits();

            context.setProgression( -1f );

            try {
                builder		= builderFactory.newDocumentBuilder();
                builder.setEntityResolver( doc );
                domDoc		= builder.parse( f );
                context.setProgression( -1f );
                options.put( XMLRepresentation.KEY_BASEPATH, f.getParentFile() );
                doc.fromXML( domDoc, domDoc.getDocumentElement(), options );
                doc.getMap().putValue( this, Session.MAP_KEY_PATH, f );
                doc.setName( f.getName() );
                doc.setTransport( new MultiTransport( doc, doc.timeline, Session.NUM_MOVIES, doc.bird,
                                    Session.DOOR_TIME ));
                args[1]		= doc;
                context.setProgression( 1.0f );
                success		= true;
            }
            catch( ParserConfigurationException e1 ) {
                context.setException( e1 );
            }
            catch( SAXParseException e2 ) {
                context.setException( e2 );
            }
            catch( SAXException e3 ) {
                context.setException( e3 );
            }
            catch( IOException e4 ) {
                context.setException( e4 );
            }

            return success;
        } // run()

        /**
         *  When the sesion was successfully
         *  loaded, its name will be put in the
         *  Open-Recent menu. All frames' bounds will be
         *  restored depending on the users preferences.
         *  <code>setModified</code> will be called on
         *  the <code>Main</code> class and the
         *  main frame's title is updated
         */
        public void finished( ProcessingThread context, Object argument, boolean success )
        {
            if( success ) {
                final Object[]			args	= (Object[]) argument;
                final File				f		= (File) args[0];
                final Session			doc		= (Session) args[1];

                addRecent( f );

                doc.setFrame( new DocumentFrame( root, doc ));
                AbstractApplication.getApplication().getDocumentHandler().addDocument( this, doc );
            }
        }
    }

    // action for the Open-Recent menu
    private class actionOpenRecentClass
    extends MenuAction
    {
        private File path;

        // new action with path set to null
        private actionOpenRecentClass( String text )
        {
            super( text );
            setPath( null );
        }

        // new action with given path
        private actionOpenRecentClass( File path )
        {
//			super( IOUtil.abbreviate( path.getParent(), 40 ));
            super( IOUtil.abbreviate( path.getAbsolutePath(), 40 ));
            setPath( path );
        }

        // set the path of the action. this
        // is the file that will be loaded
        // if the action is performed
        private void setPath( File path )
        {
            this.path = path;
            setEnabled( (path != null) && path.isFile() );
        }

        /**
         *  If a path was set for the
         *  action and the user confirms
         *  an intermitting confirm-unsaved-changes
         *  dialog, the new session will be loaded
         */
        public void actionPerformed( ActionEvent e )
        {
            if( path == null ) return;
            actionOpen.perform( path );
        }
    } // class actionOpenRecentClass

    // action for clearing the Open-Recent menu
    private class actionClearRecentClass
    extends MenuAction
    {
        private actionClearRecentClass( String text, KeyStroke shortcut )
        {
            super( text, shortcut );
        }

        public void actionPerformed( ActionEvent e )
        {
            JMenuBar	mb;
            int			i;

            openRecentPaths.clear();
            actionOpenRecent.setPath( null );

            for( i = 0; i < collMenuHosts.size(); i++ ) {
                mb = ((JFrame) collMenuHosts.get( i )).getJMenuBar();
                if( mb != null ) clearRecent( mb );
            }
            clearRecent( protoType );
        }

        private void clearRecent( JMenuBar mb )
        {
            JMenu		m;
            JMenuItem   mi;
            int			i;
            Action		a;

            m = (JMenu) findMenuItem( mb, actionOpenRecent );
            if( m != null ) {
                for( i = m.getItemCount() - 1; i >= 0; i-- ) {
                    mi  = m.getItem( i );
                    if( mi != null ) {
                        a   = mi.getAction();
                        if( a != null && a instanceof actionOpenRecentClass ) {
                            m.remove( mi );
                        }
                    }
                }
            }
        }
    } // class actionClearRecentClass

    // action for the Save-Session menu item
    private class actionCloseAllClass
    extends MenuAction
    {
        private actionCloseAllClass( String text, KeyStroke shortcut )
        {
            super( text, shortcut );
            setEnabled( false );	// initially no docs open
        }

        public void actionPerformed( ActionEvent e )
        {
            closeAll( false );
        }
    }

    // action for Application-Quit menu item
    private class actionQuitClass
    extends MenuAction
    {
        private String text;

        private actionQuitClass( String text, KeyStroke shortcut )
        {
            super( text, shortcut );

            this.text = text;
        }

        public void actionPerformed( ActionEvent e )
        {
            AbstractApplication.getApplication().quit();
//			quit( false );
        }

        /**
         *  Quits the application. Transport
         *  is stopped, session prefs are cleared
         *  and prefs is written to disk.
         *  <code>System.exit</code> is called.
         *
         *  @param  force   if set to <code>false</code> and
         *					the session contains unsaved changes,
         *					a confirmation dialog will be presented
         *					to the user. if the user cancels the
         *					quit action, the method simply returns.
         *  @see	java.lang.System#exit( int )
         *  @todo   disable realtime if enabled
         */
//		protected void quit( boolean force )
//		{
//			if( !force && !confirmUnsaved( null, text )) return;
//
//			try {
//				root.transport.quit();
//				root.superCollider.quit();	// XXX better create globale quit listener collection
//				// clear session prefs
////				PrefsUtil.removeAll( Main.prefs.node( PrefsUtil.NODE_SESSION ), true );
//				AbstractApplication.getApplication().getUserPrefs().flush();
//			}
//			catch( BackingStoreException e1 ) {
//				GUIUtil.displayError( null, e1, getResourceString( "errSavePrefs" ));
//				System.exit( 1 );
//			}
//			System.exit( 0 );
//		}
    }

// ---------------- Action objects for edit operations ---------------- 

    /**
     *  Action to be attached to
     *  the Preference item of the Edit menu.
     *  Will bring up the Preferences frame
     *  when the action is performed.
     */
    public class actionPreferencesClass
    extends MenuAction
    {
        private actionPreferencesClass( String text, KeyStroke shortcut )
        {
            super( text, shortcut );
        }

        public void actionPerformed( ActionEvent e )
        {
            perform();
        }

        /**
         *  Opens the preferences frame
         */
        public void perform()
        {
            JFrame prefsFrame = (JFrame) root.getComponent( Main.COMP_PREFS );

            if( prefsFrame == null ) {
                prefsFrame = new PrefsFrame( root );
                root.addComponent( Main.COMP_PREFS, prefsFrame );
            }
            prefsFrame.setVisible( true );
            prefsFrame.toFront();
        }
    }

// ---------------- Action objects for surface operations ---------------- 

    // adds PreferenceEntrySync functionality to the superclass
    // note that unlike PrefCheckBox and the like, it's only
    // valid to listen to the prefs changes, not the action events
    private abstract class SyncedPrefsMenuAction
    extends SyncedMenuAction
    implements PreferenceEntrySync, PreferenceChangeListener, LaterInvocationManager.Listener
    {
        protected Preferences prefs				= null;
        protected String key					= null;
        private final LaterInvocationManager lim= new LaterInvocationManager( this );

        private SyncedPrefsMenuAction( String text, KeyStroke shortcut )
        {
            super( text, shortcut );
        }

        public void setPreferenceKey( String key )
        {
            this.key = key;
            if( (prefs != null) && (key != null) ) {
                laterInvocation( new PreferenceChangeEvent( prefs, key, prefs.get( key, null )));
            }
        }

        public void setPreferenceNode( Preferences prefs )
        {
            if( this.prefs != null ) {
                this.prefs.removePreferenceChangeListener( this );
            }
            this.prefs  = prefs;
            if( prefs != null ) {
                prefs.addPreferenceChangeListener( this );
                if( key != null ) {
                    laterInvocation( new PreferenceChangeEvent( prefs, key, prefs.get( key, null )));
                }
            }
        }

        public void setPreferences( Preferences prefs, String key )
        {
            this.key = key;
            setPreferenceNode( prefs );
        }

        public Preferences getPreferenceNode() { return prefs; }
        public String getPreferenceKey() { return key; }

        public void preferenceChange( PreferenceChangeEvent e )
        {
            if( e.getKey().equals( key )) {
                if( EventManager.DEBUG_EVENTS ) System.err.println( "@menu preferenceChange : "+key+" --> "+e.getNewValue() );
                lim.queue( e );
            }
        }
    }

    // adds PreferenceEntrySync functionality to the superclass
    // note that unlike PrefCheckBox and the like, it's only
    // valid to listen to the prefs changes, not the action events
    private class SyncedBooleanPrefsMenuAction
    extends SyncedPrefsMenuAction
    {
        private static final long serialVersionUID = 0x050915L;

        private SyncedBooleanPrefsMenuAction( String text, KeyStroke shortcut )
        {
            super( text, shortcut );
        }

        /**
         *  Switches button state
         *  and updates preferences.
         */
        public void actionPerformed( ActionEvent e )
        {
            boolean state   = ((AbstractButton) e.getSource()).isSelected();

            setSelected( state );

            if( (prefs != null) && (key != null) ) {
                prefs.putBoolean( key, state );
            }
        }

        // o instanceof PreferenceChangeEvent
        public void laterInvocation( Object o )
        {
            String prefsValue   = ((PreferenceChangeEvent) o).getNewValue();
            if( prefsValue == null ) return;
            boolean prefsVal	= Boolean.valueOf( prefsValue ).booleanValue();

            setSelected( prefsVal );
        }
    }

    // adds PreferenceEntrySync functionality to the superclass
    // note that unlike PrefCheckBox and the like, it's only
    // valid to listen to the prefs changes, not the action events
    private class SyncedIntPrefsMenuAction
    extends SyncedPrefsMenuAction
    {
        private static final long serialVersionUID = 0x050915L;

        private final int ID;

        private SyncedIntPrefsMenuAction( String text, KeyStroke shortcut, int ID )
        {
            super( text, shortcut );
            this.ID = ID;
        }

        /**
         *  Fired when radio button is checked
         */
        public void actionPerformed( ActionEvent e )
        {
            if( (prefs != null) && (key != null) ) {
                if( prefs.getInt( key, -1 ) != ID ) {
                    prefs.putInt( key, ID );
                }
            }
        }

        // o instanceof PreferenceChangeEvent
        public void laterInvocation( Object o )
        {
            final int prefsVal	= prefs.getInt( key, -1 );

            if( prefsVal == ID ) setSelected( true );
        }
    }

// ---------------- Action objects for window operations ---------------- 

    // action for the About menu item
    private class actionAboutClass
    extends MenuAction
    {
        private actionAboutClass( String text, KeyStroke shortcut )
        {
            super( text, shortcut );
        }

        /**
         *  Brings up the About-Box
         */
        public void actionPerformed( ActionEvent e )
        {
            JFrame aboutBox = (JFrame) root.getComponent( Main.COMP_ABOUTBOX );

            if( aboutBox == null ) {
                aboutBox = new AboutBox();
                root.addComponent( Main.COMP_ABOUTBOX, aboutBox );
            }
            aboutBox.setVisible( true );
            aboutBox.toFront();
        }
    }

    // action for the IOSetup menu item
    private class actionIOSetupClass
    extends MenuAction
    {
        private actionIOSetupClass( String text, KeyStroke shortcut )
        {
            super( text, shortcut );
        }

        /**
         *  Brings up the IOSetup
         */
        public void actionPerformed( ActionEvent e )
        {
            JFrame ioSetup = (JFrame) root.getComponent( Main.COMP_IOSETUP );

            if( ioSetup == null ) {
                ioSetup = new IOSetupFrame( root );	// automatically adds component
            }
            ioSetup.setVisible( true );
            ioSetup.toFront();
        }
    }

    // action for the IOSetup menu item
    private class actionRecorderClass
    extends MenuAction
    {
        private actionRecorderClass( String text, KeyStroke shortcut )
        {
            super( text, shortcut );
        }

        /**
         *  Brings up the IOSetup
         */
        public void actionPerformed( ActionEvent e )
        {
            JFrame recorder = (JFrame) root.getComponent( Main.COMP_RECORDER );

            if( recorder == null ) {
                recorder = new RecorderDialog( root );	// automatically adds component
            }
            recorder.setVisible( true );
            recorder.toFront();
        }
    }

    // generic action for bringing up
    // a window which is identified by
    // a component object. the frame is
    // looked up using the Main's getComponent()
    // method.
    private class actionShowWindowClass extends MenuAction
    {
        Object component;

        // @param   component   the key for getting the
        //						component using Main.getComponent()
        private actionShowWindowClass( String text, KeyStroke shortcut, Object component )
        {
            super( text, shortcut );

            this.component = component;
        }

        /**
         *  Tries to find the component using
         *  the <code>Main</code> class' <code>getComponent</code>
         *  method. It does not instantiate a
         *  new object if the component is not found.
         *  If the window is already open, this
         *  method will bring it to the front.
         */
        public void actionPerformed( ActionEvent e )
        {
            JFrame frame = (JFrame) root.getComponent( component );
            if( frame != null ) {
                frame.setVisible( true );
                frame.toFront();
            }
        }
    }

    // generic action for bringing up
    // a html Session either in the
    // help viewer or the default web browser
    private class actionURLViewerClass extends MenuAction
    {
        private final String	theURL;
        private final boolean	openWebBrowser;

        // @param	theURL			what file to open ; when using the
        //							help viewer, that's the relative help file name
        //							without .html extension. when using web browser,
        //							that's the complete URL!
        // @param   openWebBrowser	if true, use the default web browser,
        //							if false use internal help viewer
        private actionURLViewerClass( String text, KeyStroke shortcut, String theURL, boolean openWebBrowser )
        {
            super( text, shortcut );

            this.theURL			= theURL;
            this.openWebBrowser	= openWebBrowser;
        }

        /**
         *  Tries to find the component using
         *  the <code>Main</code> class' <code>getComponent</code>
         *  method. It does not instantiate a
         *  new object if the component is not found.
         *  If the window is already open, this
         *  method will bring it to the front.
         */
        public void actionPerformed( ActionEvent e )
        {
            if( openWebBrowser ) {
                try {
                    Desktop.getDesktop ().browse ( new URL (theURL).toURI () );
//					MRJAdapter.openURL( theURL );
                }
                catch( IOException e1 ) {
                    GUIUtil.displayError( null, e1, NAME );
                } catch ( URISyntaxException e1 )
                {
                    GUIUtil.displayError( null, e1, NAME );
                }
            } else {
                HelpFrame helpFrame = (HelpFrame) root.getComponent( Main.COMP_HELP );

                if( helpFrame == null ) {
                    helpFrame = new HelpFrame();
                    root.addComponent( Main.COMP_HELP, helpFrame );
                }
                helpFrame.loadHelpFile( theURL );
                helpFrame.setVisible( true );
                helpFrame.toFront();
            }
        }
    }
}