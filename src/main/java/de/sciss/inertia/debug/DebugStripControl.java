//
//  DebugStripControl.java
//  Inertia
//
//  Created by Hanns Holger Rutz on 01.10.05.
//  Copyright 2005 __MyCompanyName__. All rights reserved.
//

package de.sciss.inertia.debug;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;

import de.sciss.inertia.gui.*;
import de.sciss.inertia.net.*;
import de.sciss.inertia.session.*;

import de.sciss.gui.SpringPanel;

import de.sciss.jcollider.*;

/**
 *	@version	0.23, 06-Oct-05
 */
public class DebugStripControl
extends SpringPanel
implements Constants
{
	private final Session				doc;
	private final SuperColliderPlayer	player;
	private final Server				server;
	private final int					layer;
	private final int					numCh;
	private final LevelMeter[]			meter;
	private final Bus					levelBus;
	private final int					levelBusOffset;
	private Synth						synth	= null;
	
	private float						volume	= 1.0f;

	public DebugStripControl( Session doc, SuperColliderClient superCollider, final int layer,
							  Bus levelBus, int levelBusOffset )
	{
		super();
	
		this.doc			= doc;
		this.player			= superCollider.getPlayerForDocument( doc );
		this.server			= player.getServer();
		this.layer			= layer;
		this.levelBus		= levelBus;
		this.levelBusOffset	= levelBusOffset;
		this.numCh			= player.getNumInputChannels();
		
		final JSlider		ggVolume	= new JSlider( JSlider.VERTICAL, -72, 18, 0 );
		final Dictionary	dictVolume	= ggVolume.createStandardLabels( 12 );
		final JCheckBox		ggSolo		= new JCheckBox( "S" );
		final JCheckBox		ggPre		= new JCheckBox( "Pre" );

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
				try {
					if( synth != null ) synth.set( "debugVolume", volume );
				}
				catch( IOException e1 ) {
					printError( "setVolume", e1 );
				}
			}
		});

		meter				= new LevelMeter[ numCh ];
		for( int ch = 0; ch < meter.length; ch++ ) {
			meter[ ch ]		= new LevelMeter();
			gridAdd( meter[ ch ], ch, 0 );
		}
		gridAdd( ggVolume, numCh, 0 );
		
		ggSolo.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e )
			{
				try {
					if( synth != null ) synth.set( "solo", ggSolo.isSelected() ? 1f : 0f );
				}
				catch( IOException e1 ) {
					printError( "setSolo", e1 );
				}
			}
		});
		gridAdd( ggSolo, 0, 1, numCh + 1, 1 );

		ggPre.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e )
			{
				try {
					if( synth != null ) player.makeMonitor( synth, layer, "i_aBus", ggPre.isSelected() );
//					if( synth != null ) synth.set( "solo", ggSolo.isSelected() ? 1f : 0f );
				}
				catch( IOException e1 ) {
					printError( "setPre", e1 );
				}
			}
		});
		gridAdd( ggPre, 0, 2, numCh + 1, 1 );
		
		try {
			synth = new Synth( "inertia-debugstrip" + numCh, new String[]
							   { "i_kOutBus" }, new float[] { levelBus.getIndex() + levelBusOffset },
							   server.asTarget() );
			player.makeMonitor( synth, layer, "i_aBus", false );	// this moves the node!
		}
		catch( IOException e1 ) {
			printError( "new DebugStripControl", e1 );
		}
		
		// XXX : mute, solo, pre/post
		
		makeCompactGrid();
	}
	
	public void setPeakAndRMS( int ch, float peak, float rms )
	{
		meter[ ch ].setPeakAndRMS( peak, rms );
	}
	
	private static void printError( String name, Throwable t )
	{
		System.err.println( name + " : " + t.getClass().getName() + " : " + t.getLocalizedMessage() );
	}

	public void dispose()
	{
		try {
			if( synth != null ) {
				synth.free();
				synth = null;
			}
		}
		catch( IOException e1 ) {
			printError( "DebugStripControl.dispose", e1 );
		}
	}
}