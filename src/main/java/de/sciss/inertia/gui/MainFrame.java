/*
 *  MainFrame.java
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
 *		11-Aug-05	created from de.sciss.eisenkraut.gui.MainFrame
 */

package de.sciss.inertia.gui;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.text.*;
import java.util.*;
import java.util.prefs.*;
import javax.swing.*;
import javax.swing.event.*;

// INERTIA
//import de.sciss.eisenkraut.*;
//import de.sciss.eisenkraut.gui.*;
//import de.sciss.eisenkraut.io.*;
//import de.sciss.eisenkraut.realtime.*;
//import de.sciss.eisenkraut.session.*;
//import de.sciss.eisenkraut.util.*;
import de.sciss.inertia.Main;
import de.sciss.util.ProcessingThread;
import de.sciss.inertia.util.PrefsUtil;

import de.sciss.app.AbstractApplication;
import de.sciss.app.Application;

import de.sciss.net.*;

import de.sciss.gui.GUIUtil;
import de.sciss.gui.HelpGlassPane;
import de.sciss.gui.LogTextArea;
import de.sciss.gui.PrefComboBox;

import de.sciss.io.Span;

import de.sciss.jcollider.*;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.12, 20-Aug-05
 */
public class MainFrame
extends BasicFrame
implements Constants, ServerListener
{
	private final Main			root;
	private final PrintStream	logStream;
	
	private boolean				keepScRunning;
	private ProcessingThread	pt				= null;
	private boolean				booted			= false;
	
	private final actionBootClass actionBoot;

	private long playOffset = -1;	// frame offset after phasor+synth launch (-1 = not playing/stopped)
	private float volume	= 1.0f;

	private final JLabel		lbStatus;
	private final PrefComboBox	ggOutputConfig;

	private final Preferences audioPrefs;
		
	private ServerOptions	serverOptions;

	private final MessageFormat	msgStatus;
	private final Object[]		argsStatus		= new Object[ 5 ];
	private final String		unknownStatus;

	public static final Font	fntMonoSpaced;
	
	static {
		final String[] fntNames;
		fntNames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
		if( contains( fntNames, "Monaco" )) {	// Mac OS
			fntMonoSpaced = new Font( "Monaco", Font.PLAIN, 9 );	// looks bigger than it is
		} else {
			fntMonoSpaced = new Font( "Monospaced", Font.PLAIN, 10 );
		}
	}

	public MainFrame( final Main root )
	{
		super( AbstractApplication.getApplication().getName() + " : " +
			   AbstractApplication.getApplication().getResourceString( "frameMain" ));
		
		this.root							= root;

		final Container		cp				= getContentPane();
		final Box			b1				= Box.createHorizontalBox();
		final Box			b2				= Box.createHorizontalBox();
		final Box			b3				= Box.createHorizontalBox();
		final Box			b4				= Box.createHorizontalBox();
		final JPanel		bottomPane		= new JPanel();
		final LogTextArea	lta				= new LogTextArea( 6, 40, false, null );
		final JScrollPane	ggScroll		= new JScrollPane( lta, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
															   JScrollPane.HORIZONTAL_SCROLLBAR_NEVER );
		final JButton		ggBoot;
		final JSlider		ggVolume		= new JSlider( -72, 18, 0 );
		final Dictionary	dictVolume		= ggVolume.createStandardLabels( 12 );
		final Application	app				= AbstractApplication.getApplication();
		audioPrefs							= app.getUserPrefs().node( PrefsUtil.NODE_AUDIO );
		final Preferences	ocPrefs			= audioPrefs.node( PrefsUtil.NODE_OUTPUTCONFIGS );
		final WindowAdapter	winListener;
		final JButton		ggJitHandshake;
		final JCheckBox		ggDumpOSC, ggJitDumpOSC;
		final JComboBox		ggJitCoupling;
		final Action		actionJitHandshake;
		
		logStream	= lta.getLogStream();
		
		actionBoot	= new actionBootClass();
		ggBoot		= new JButton( actionBoot );
		ggBoot.setFocusable( false );	// prevent user from accidentally starting/stopping server
		
		ggDumpOSC	= new JCheckBox( getResourceString( "labelDumpOSC" ));
		ggDumpOSC.addItemListener( new ItemListener() {
			public void itemStateChanged( ItemEvent e ) {
				root.superCollider.dumpOSC( ggDumpOSC.isSelected() ? kDumpText : kDumpOff );
			}
		});
		
		ggOutputConfig	= new PrefComboBox();
		refillConfigs();
		ggOutputConfig.setPreferences( audioPrefs, PrefsUtil.KEY_OUTPUTCONFIG );
		
		ggVolume.setMinorTickSpacing( 3 );
		ggVolume.setMajorTickSpacing( 12 );
		for( Enumeration en = dictVolume.elements(); en.hasMoreElements(); ) {
			((JComponent) en.nextElement()).setFont( GraphicsUtil.smallGUIFont );
		}
		ggVolume.setLabelTable( dictVolume );
		ggVolume.setPaintTicks( true );
		ggVolume.setPaintLabels( true );
		ggVolume.addChangeListener( new ChangeListener() {
			public void stateChanged( ChangeEvent e ) {
				int db = ggVolume.getValue();
				
				volume = (float) Math.pow( 10, (double) db / 20 );
				root.superCollider.setVolume( volume );
			}
		});
		
		msgStatus		= new MessageFormat( getResourceString( "ptrnServerStatus" ), Locale.US );
		unknownStatus	= getResourceString( "labelServerNotRunning" );
		lbStatus		= new JLabel( unknownStatus );
		
		actionJitHandshake	= new actionJitHandshakeClass();
		ggJitHandshake	= new JButton( actionJitHandshake );
		ggJitHandshake.setFocusable( false );
		
		ggJitDumpOSC	= new JCheckBox( getResourceString( "labelDumpOSC" ));
		ggJitDumpOSC.addItemListener( new ItemListener() {
			public void itemStateChanged( ItemEvent e ) {
				root.jitter.dumpOSC( ggJitDumpOSC.isSelected() ? kDumpText : kDumpOff );
			}
		});
		
		ggJitCoupling	= new JComboBox();
		ggJitCoupling.addItem( getResourceString( "jitCoupSlave" ));
		ggJitCoupling.addItem( getResourceString( "jitCoupMaster" ));
		ggJitCoupling.addItem( getResourceString( "jitCoupNone" ));
		ggJitCoupling.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e )
			{
				root.jitter.setCoupling( ggJitCoupling.getSelectedIndex() );
			}
		});

b2.add( new JLabel( getResourceString( "labelSuperCollider" ) + " :" ));
		b2.add( ggBoot );
		b2.add( ggDumpOSC );
		b2.add( ggOutputConfig );
		b2.add( lbStatus );
		b2.add( Box.createHorizontalGlue() );
		b3.add( lbStatus );
		b3.add( Box.createHorizontalGlue() );

		b4.add( new JLabel( getResourceString( "labelJitter" ) + " :" ));
		b4.add( ggJitHandshake );
		b4.add( ggJitDumpOSC );
//		b4.add( new JLabel( getResourceString( "labelCoupling" )));
		b4.add( ggJitCoupling );
		b4.add( Box.createHorizontalGlue() );

        if( app.getUserPrefs().getBoolean( PrefsUtil.KEY_INTRUDINGSIZE, false )) {
    		b3.add( Box.createHorizontalStrut( 16 ));
        }
		b1.add( new JLabel( getResourceString( "labelGain" )));
		b1.add( ggVolume );
		
        cp.setLayout( new BorderLayout() );
		b1.setBorder( BorderFactory.createEmptyBorder( 2, 4, 2, 4 ));
//		bottomPane.setLayout( new BorderLayout( 4, 2 ));
//		bottomPane.add( b2, BorderLayout.NORTH );
//		bottomPane.add( b3, BorderLayout.SOUTH );
		bottomPane.setLayout( new BoxLayout( bottomPane, BoxLayout.Y_AXIS ));
		bottomPane.add( b2 );
		bottomPane.add( b3 );
		bottomPane.add( b4 );
		bottomPane.setBorder( BorderFactory.createEmptyBorder( 2, 4, 2, 4 ));
		cp.add( b1, BorderLayout.NORTH );
		cp.add( ggScroll, BorderLayout.CENTER );
		cp.add( bottomPane, BorderLayout.SOUTH );

		GUIUtil.setDeepFont( cp, GraphicsUtil.smallGUIFont );
		
		lbStatus.setFont( fntMonoSpaced );
		lta.setFont( fntMonoSpaced );

		// ---- listeners -----
		
		winListener = new WindowAdapter() {
			public void windowClosing( WindowEvent e ) {
				root.quit();
			}
		};
		this.addWindowListener( winListener );
		
		root.superCollider.addListener( this );
		
        HelpGlassPane.setHelp( getRootPane(), "MainFrame" );

		setDefaultCloseOperation( DO_NOTHING_ON_CLOSE );
		
		init( root );
		setVisible( true );
		toFront();
	}
	
	public void refillConfigs()
	{
		try {
			final String[] cfgNames = audioPrefs.node( PrefsUtil.NODE_OUTPUTCONFIGS ).childrenNames();
			ggOutputConfig.removeAllItems();
			for( int i = 0; i < cfgNames.length; i++ ) {
				ggOutputConfig.addItem( cfgNames[ i ]);
			}
		}
		catch( BackingStoreException e1 ) {
			logStream.println( e1.toString() );
		}
	}
	
	private static boolean contains( String[] array, String name )
	{
		for( int i = 0; i < array.length; i++ ) {
			if( array[ i ].equals( name )) return true;
		}
		return false;
	}
	
	protected boolean alwaysPackSize()
	{
		return false;
	}

	public PrintStream getLogStream()
	{
		return logStream;
	}

	private String getResourceString( String key )
	{
		return AbstractApplication.getApplication().getResourceString( key );
	}
	
	private void updateStatus()
	{
		final Server.Status s = root.superCollider.getStatus();
		if( s != null ) {
			argsStatus[ 0 ]	= new Float( s.sampleRate );
			argsStatus[ 1 ]	= new Float( s.avgCPU );
			argsStatus[ 2 ]	= new Integer( s.numUGens );
			argsStatus[ 3 ]	= new Integer( s.numSynths );
			argsStatus[ 4 ]	= new Integer( s.numGroups );
			lbStatus.setText( msgStatus.format( argsStatus ));
		} else {
			lbStatus.setText( unknownStatus );
		}
	}
	
// ------------- ServerListener interface -------------

	public void serverAction( ServerEvent e )
	{
		switch( e.getID() ) {
		case ServerEvent.RUNNING:
			actionBoot.booted();
			break;

		case ServerEvent.STOPPED:
			actionBoot.terminated();
			updateStatus();
			break;

		case ServerEvent.COUNTS:
			updateStatus();
			break;
			
		default:
			break;
		}
	}

// ------------- interne klassen -------------

	private class actionBootClass
	extends AbstractAction
	{
		private actionBootClass()
		{
			super( getResourceString( "buttonBoot" ));
		}
		
		public void actionPerformed( ActionEvent e )
		{
			if( booted ) {
				root.superCollider.stop();
			} else {
				root.superCollider.boot();
			}
		}
		
		private void terminated()
		{
			booted = false;
			putValue( NAME, getResourceString( "buttonBoot" ));
		}

		private void booted()
		{
			booted = true;
			putValue( NAME, getResourceString( "buttonTerminate" ));
		}
	} // class actionBootClass

	private class actionJitHandshakeClass
	extends AbstractAction
	{
		private actionJitHandshakeClass()
		{
			super( getResourceString( "buttonHandshake" ));
		}
		
		public void actionPerformed( ActionEvent e )
		{
			root.jitter.handshake();
		}		
	} // class actionJitHandshakeClass
}