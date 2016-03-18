//
//  DebugMixer.java
//  Inertia
//
//  Created by Hanns Holger Rutz on 02.10.05.
//  Copyright 2005 __MyCompanyName__. All rights reserved.
//

package de.sciss.inertia.debug;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import javax.swing.*;

import de.sciss.inertia.*;
import de.sciss.inertia.gui.*;
import de.sciss.inertia.net.*;
import de.sciss.inertia.session.*;

import de.sciss.jcollider.*;
import de.sciss.jcollider.gui.*;
import de.sciss.net.*;

import de.sciss.gui.GUIUtil;
import de.sciss.gui.MenuAction;

import de.sciss.app.AbstractApplication;

public class DebugMixer
extends JFrame
{
	private final DebugStripControl[]	strips;
	private final Server				server;
	private final SuperColliderPlayer	player;
	private final int					numCh;
	private final Bus					levelBus;
	private final OSCResponderNode		cSetNResp;
	private final javax.swing.Timer		meterTimer;
	private final Main					root;
	private final int					numLayers;

	public DebugMixer( Main root, Session doc )
	{
		super( "Debug Mixer : " + doc.getName() );
		
		this.root	= root;
		player		= root.superCollider.getPlayerForDocument( doc );
		server		= player.getServer();
		numLayers	= doc.layers.getNumLayers();
		
		final Container				cp		= getContentPane();
		final JPanel				pStrips, pNames, pCtrl;
		final JCheckBox				ggMeterTask;
		final OSCMessage			meterBangMsg;

		pStrips		= new JPanel( new GridLayout( 1, numLayers ));
		pNames		= new JPanel( new GridLayout( 1, numLayers ));
		pCtrl		= new JPanel( new FlowLayout() );
		ggMeterTask	= new JCheckBox( "Meter Task" );

		numCh		= player.getNumInputChannels();
		levelBus	= Bus.control( server, numCh * numLayers * 2 );
		strips		= new DebugStripControl[ numLayers ];
		
		meterBangMsg = new OSCMessage( "/c_getn", new Object[] {
				new Integer( levelBus.getIndex() ), new Integer( levelBus.getNumChannels() )});;

		meterTimer	= new javax.swing.Timer( 33, new ActionListener() {
			public void actionPerformed( ActionEvent e )
			{
				try {
					server.sendMsg( meterBangMsg );
				}
				catch( IOException e1 ) {}
			}
		});

		cSetNResp = new OSCResponderNode( server.getAddr(), "/c_setn", new OSCListener() {
			public void messageReceived( OSCMessage msg, SocketAddress sender, long time )
			{
				final int	busIndex = ((Number) msg.getArg( 0 )).intValue();
				
				if( busIndex != levelBus.getIndex() ) return;

				try {
					final int argStop = ((Number) msg.getArg( 1 )).intValue() + 2;
					float peak, rms;
					DebugStripControl strip;
						
					for( int i = 2, layer = 0; (i < argStop) && (layer < strips.length); layer++ ) {
						strip = strips[ layer ];
						if( strip != null ) {
							for( int ch = 0; ch < numCh; ch++ ) {
								peak	= ((Number) msg.getArg( i++ )).floatValue();
								rms		= ((Number) msg.getArg( i++ )).floatValue();
								strip.setPeakAndRMS( ch, peak, rms );
							}
						} else {
							i += numCh << 1;
						}
					}
				}
//				catch( IOException e1 ) {
//					printError( "Receive /c_setn", e1 );
//				}
				catch( ClassCastException e2 ) {
					printError( "Receive /c_setn", e2 );
				}
			}
		});
		try {
			cSetNResp.add();
		}
		catch( IOException e1 ) {
			printError( "new DebugMixer", e1 );
		}
		
		ggMeterTask.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e )
			{
				if( ggMeterTask.isSelected() ) {
					meterTimer.restart();
				} else {
					meterTimer.stop();
				}
			}
		});

		for( int i = 0; i < strips.length; i++ ) {
			strips[ i ]		= new DebugStripControl( doc, root.superCollider, i, levelBus, numCh * i * 2 );
			pStrips.add( strips[ i ]);
			pNames.add( new JLabel( String.valueOf( (char) (i + 65) )));
		}
		
		setDefaultCloseOperation( DO_NOTHING_ON_CLOSE );
		addWindowListener( new WindowAdapter() {
			public void windowClosing( WindowEvent e )
			{
				disposeMixer();
			}
		});
		
		pCtrl.add( ggMeterTask );
		pCtrl.add( new JButton( new actionNodeTreeClass() ));
		pCtrl.add( new JButton( new actionAtomWatcherClass() ));
		pCtrl.add( new JButton( new actionRazRequestClass() ));
		pCtrl.add( new JButton( new actionScopeTestClass() ));
		
		cp.setLayout( new BoxLayout( cp, BoxLayout.Y_AXIS ));
		cp.add( pStrips );
		cp.add( pNames );
		cp.add( pCtrl );
		
		GUIUtil.setDeepFont( cp, null );
	
		pack();
		setVisible( true );
		toFront();
	}
	
	private static void printError( String name, Throwable t )
	{
		System.err.println( name + " : " + t.getClass().getName() + " : " + t.getLocalizedMessage() );
	}

	public void disposeMixer()
	{
		DebugStripControl strip;
	
		setVisible( false );
		meterTimer.stop();
		try {
			cSetNResp.remove();
		}
		catch( IOException e1 ) {
			printError( "disposeMixer", e1 );
		}
		for( int i = 0; i < strips.length; i++ ) {
			strip		= strips[ i ];
			strips[ i ]	= null;
			if( strip != null ) strip.dispose();
		}
		levelBus.free();
		dispose();
	}
	
	public static Action getDebugMixerAction()
	{
		return new actionOpenDebugMixerClass();
	}
	
	private static class actionOpenDebugMixerClass
	extends MenuAction
	{
		private actionOpenDebugMixerClass()
		{
			super( "Open Debug Mixer" );
		}
		
		public void actionPerformed( ActionEvent e )
		{
			final Main		root	= (Main) AbstractApplication.getApplication();
			final Session	doc		= (Session) root.getDocumentHandler().getActiveDocument();

			if( doc == null ) return;
			
			new DebugMixer( root, doc );
		}
	}

	private class actionNodeTreeClass
	extends MenuAction
	{
		private actionNodeTreeClass()
		{
			super( "Node Tree" );
		}
		
		public void actionPerformed( ActionEvent e )
		{
//			final NodeTreePanel	ntp	= new NodeTreePanel( root.superCollider.getNodeWatcher(), player.getRootNode() );
			final NodeTreePanel	ntp	= new NodeTreePanel( root.superCollider.getNodeWatcher(), server.getDefaultGroup() );
			GUIUtil.setDeepFont( ntp, MainFrame.fntMonoSpaced );
			final JFrame f	= ntp.makeWindow();
			f.addWindowListener( new WindowAdapter() {
				public void windowClosing( WindowEvent e )
				{
					f.setVisible( false );
					f.dispose();
					ntp.dispose();
				}
			});
		}
	}

	private class actionAtomWatcherClass
	extends MenuAction
	{
		private actionAtomWatcherClass()
		{
			super( "Atom Watcher" );
		}
		
		public void actionPerformed( ActionEvent e )
		{
			new DebugAtomWatcher( player, numLayers );
		}
	}

	private class actionScopeTestClass
	extends MenuAction
	{
		private actionScopeTestClass()
		{
			super( "Scope Test" );
		}
		
		public void actionPerformed( ActionEvent e )
		{
			try {
				new DebugScopeTest();
			}
			catch( IOException e1 ) {
				System.err.println( e1 );
			}
		}
	}

	private class actionRazRequestClass
	extends MenuAction
	{
		private actionRazRequestClass()
		{
			super( "Raz Debug" );
		}
		
		public void actionPerformed( ActionEvent e )
		{
			SuperColliderPlayer.RAZ_DEBUG = true;
		}
	}
}