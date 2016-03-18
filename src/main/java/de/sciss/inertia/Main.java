/*
 *  Main.java
 *  Eisenkraut
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
 *		11-Aug-05	copied from de.sciss.eisenkraut.Main
 */

package de.sciss.inertia;

import de.sciss.gui.AboutBox;
import de.sciss.gui.GUIUtil;
import de.sciss.gui.HelpFrame;
import de.sciss.inertia.gui.*;
import de.sciss.inertia.net.JitterClient;
import de.sciss.inertia.net.SuperColliderClient;
import de.sciss.inertia.util.PrefsUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.net.InetSocketAddress;
import java.util.prefs.Preferences;

// INERTIA
//import de.sciss.eisenkraut.gui.*;
//import de.sciss.eisenkraut.io.*;
//import de.sciss.eisenkraut.net.*;
//import de.sciss.eisenkraut.realtime.*;
//import de.sciss.eisenkraut.render.*;
//import de.sciss.eisenkraut.session.*;
//import de.sciss.eisenkraut.util.*;

/**
 *  The <code>Main</code> class contains the java VM
 *  startup static <code>main</code> method which
 *  creates a new instance of <code>Main</code>. This instance
 *  will initialize localized strings (ResourceBundle),
 *  Preferences, the <code>transport</code>, the <code>menuFactory</code>
 *  object (a prototype of the applications menu and its
 *  actions).
 *  <p>
 *  Common components are created and registered:
 *  <code>SuperColliderFrame</code>, <code>TransportPalette</code>,
 *  <code>ObserverPalette</code>, and <code>DocumentFrame</code>.
 *  <p>
 *  The <code>Main</code> class extends the <code>Application</code>
 *  class from the <code>de.sciss.app</code> package.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.2, 03-Oct-05
 */
public class Main
extends de.sciss.app.AbstractApplication
{
    private static final String APP_NAME	= "Inertia";

    /*
     *  Current version of Meloncillo. This is stored
     *  in the preferences file.
     *
     *  @todo   should be saved in the session file as well
     */
    private static final double APP_VERSION		= 0.33;

    /*
     *  The MacOS file creator string.
     */
    private static final String CREATOR		= "????";

    private final de.sciss.app.DocumentHandler	docHandler	= new de.sciss.inertia.session.DocumentHandler();
    private final de.sciss.app.WindowHandler	winHandler	= new de.sciss.inertia.gui.WindowHandler();

    public String getName ()
    {
        return APP_NAME;
    }

    /**
     *  Value for add/getComponent(): the preferences frame
     *
     *  @see	#getComponent( Object )
     */
    public static final Object COMP_PREFS			= PrefsFrame.class.getName();
    /**
     *  Value for add/getComponent(): the about box
     *
     *  @see	#getComponent( Object )
     */
    public static final Object COMP_ABOUTBOX		= AboutBox.class.getName();

    /**
     *  Value for add/getComponent(): the observer palette
     *
     *  @see	#getComponent( Object )
     */
    public static final Object COMP_OBSERVER		= ObserverPalette.class.getName();

    /**
     *  Value for add/getComponent(): input/output setup
     *
     *  @see	#getComponent( Object )
     */
    public static final Object COMP_IOSETUP			= IOSetupFrame.class.getName();

    public static final Object COMP_RECORDER		= new Object();

// INERTIA
//	/**
//	 *  Value for add/getComponent(): the filter-process dialog
//	 *
//	 *  @see	#getComponent( Object )
//	 *  @see	de.sciss.meloncillo.render.FilterDialog
//	 */
//	public static final Object COMP_FILTER			= FilterDialog.class.getName();

    /**
     *  Value for add/getComponent(): the main log frame
     *
     *  @see	#getComponent( Object )
     */
    public static final Object COMP_MAIN			= MainFrame.class.getName();
    /**
     *  Value for add/getComponent(): the online help display frame
     *
     *  @see	#getComponent( Object )
     */
    public static final Object COMP_HELP    		= HelpFrame.class.getName();

// INERTIA
//	/**
//	 *  Value for add/getComponent(): audio file header information
//	 *
//	 *  @see	#getComponent( Object )
//	 *  @see	de.sciss.meloncillo.gui.AudioFileInfoPalette
//	 */
//	public static final Object COMP_AUDIOINFO  		= AudioFileInfoPalette.class.getName();

    /**
     *  Clipboard (global, systemwide)
     */
    public static final Clipboard clipboard	= Toolkit.getDefaultToolkit().getSystemClipboard();

    /**
     *  Instance for getting copies of the global menu
     */
    public final MenuFactory menuFactory;

    /**
     */
    public final SuperColliderClient superCollider;

    /**
     */
    public final JitterClient jitter;

// INERTIA
//	/**
//	 *  Instance for managing waveform cache files
//	 */
//	public final CacheManager cacheManager;

    private final MainFrame		mainFrame;

    public Main( String[] args )
    {
        super( Main.class, APP_NAME );

        final java.util.List	warnings;
        final Preferences		prefs			= getUserPrefs();
        final double			prefsVersion;
        final ObserverPalette	frameObserver;
        String					name;

        // ---- init prefs ----

        prefsVersion = prefs.getDouble( PrefsUtil.KEY_VERSION, 0.0 );
        if( prefsVersion < APP_VERSION ) {
            warnings = PrefsUtil.createDefaults( prefs, prefsVersion );
        } else {
            warnings = null;
        }
        
        // ---- init look-and-feel
        name = prefs.get( PrefsUtil.KEY_LOOKANDFEEL, null );
        if( args.length >= 3 && args[ 0 ].equals( "-laf" )) {
            UIManager.installLookAndFeel( args[ 1 ], args[ 2 ]);
            if( name == null ) name = args[ 2 ];
        }
        lookAndFeelUpdate( prefs.get( PrefsUtil.KEY_LOOKANDFEEL, name ));

        // ---- init infrastructure ----

// INERTIA
//		cacheManager		= new CacheManager( prefs.node( CacheManager.DEFAULT_NODE ));
        superCollider		= new SuperColliderClient( this );	// must be created before menuFactory
        menuFactory			= new MenuFactory( this );
        jitter				= new JitterClient( new InetSocketAddress( "127.0.0.1", 57111 ));

        // ---- listeners ----

//		MRJAdapter.addOpenDocumentListener( new ActionListener() {
//			public void actionPerformed( ActionEvent e )
//			{
//				handleOpenFile( (ApplicationEvent) e );
//			}
//		});

        // ---- component views ----

        mainFrame   = new MainFrame( this );
        addComponent( COMP_MAIN, mainFrame );
        System.setOut( mainFrame.getLogStream() );
        System.setErr( mainFrame.getLogStream() );

        frameObserver = new ObserverPalette( this );
        addComponent( COMP_OBSERVER, frameObserver );

// INERTIA
//		if( prefsVersion == 0.0 ) { // means no preferences found, so display splash screen
//    		new de.sciss.eisenkraut.debug.WelcomeScreen( this );
//		}

        if( warnings != null ) {
            for( int i = 0; i < warnings.size(); i++ ) {
                System.err.println( warnings.get( i ));
            }
        }

        if( prefs.node( PrefsUtil.NODE_AUDIO ).getBoolean( PrefsUtil.KEY_AUTOBOOT, false )) {
            superCollider.boot();
        }
    }

    private boolean forcedQuit = false;

    public synchronized void quit()
    {
        if( !menuFactory.closeAll( forcedQuit )) return;

        jitter.quit();
        superCollider.quit();
        super.quit();
    }

    public void forceQuit()
    {
        forcedQuit = true;
        this.quit();
    }

    private void lookAndFeelUpdate( String className )
    {
        if( className != null ) {
            try {
                UIManager.setLookAndFeel( className );
// INERTIA
//				BasicFrame.lookAndFeelUpdate();
            }
            catch( Exception e1 ) {
                GUIUtil.displayError( null, e1, null );
            }
        }
    }

    /**
     *  java VM starting method. does some
     *  static initializations and then creates
     *  an instance of <code>Main</code>.
     *
     *  @param  args	are not parsed.
     */
    public static void main( final String args[] )
    {
        // --- run the main application ---
        // Schedule a job for the event-dispatching thread:
        // creating and showing this application's GUI.
        SwingUtilities.invokeLater( new Runnable() {
            public void run()
            {
                new Main( args );
            }
        });
    }

//	private void handleOpenFile( ApplicationEvent e )
//	{
//		menuFactory.openDocument( e.getFile() );
//	}

// ------------ Application interface ------------

    public String getMacOSCreator()
    {
        return CREATOR;
    }

    public double getVersion()
    {
        return APP_VERSION;
    }

    public de.sciss.app.WindowHandler getWindowHandler()
    {
        return winHandler;
    }

    public de.sciss.app.DocumentHandler getDocumentHandler()
    {
        return docHandler;
    }
}