//
//  DebugAtomWatcher.java
//  Inertia
//
//  Created by Hanns Holger Rutz on 05.10.05.
//  Copyright 2005 __MyCompanyName__. All rights reserved.
//

package de.sciss.inertia.debug;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;

import de.sciss.inertia.gui.*;
import de.sciss.inertia.net.*;

/**
 *	@version	0.27, 09-Oct-05
 */
public class DebugAtomWatcher
extends JFrame
implements SuperColliderPlayer.RunningAtomListener
{
	final SuperColliderPlayer	p;
	final DebugAtomWatcher		enc_this	= this;
	final RazView				razView;

	public DebugAtomWatcher( final SuperColliderPlayer p, int numLayers )
	{
		super( "Atom Watcher" );
	
		this.p	= p;

		razView = new RazView( numLayers );
		
		final javax.swing.Timer timer = new javax.swing.Timer( 500, new ActionListener() {
			public void actionPerformed( ActionEvent e )
			{
				razView.repaint();
			}
		});
		timer.setRepeats( true );
		
		setDefaultCloseOperation( DO_NOTHING_ON_CLOSE );
		addWindowListener( new WindowAdapter() {
			public void windowClosing( WindowEvent e )
			{
				timer.stop();
				p.removeListener( enc_this );
				setVisible( false );
				dispose();
			}
		});
		
		p.addListener( this );
		
		final Container cp = getContentPane();
		cp.setLayout( new BorderLayout() );
		cp.add( razView, BorderLayout.CENTER );
		
		setSize( 400, 200 );
		setVisible( true );
		toFront();
		timer.start();
	}
	
	public void atomInTheLight( SuperColliderPlayer.RunningAtom run )
	{
		razView.addAtom( run, RazView.LIGHT );
	}

	public void atomInTheShadow( SuperColliderPlayer.RunningAtom run )
	{
		razView.addAtom( run, RazView.SHADOW );
	}

	public void atomInTheFilter( SuperColliderPlayer.RunningAtom run )
	{
		razView.addAtom( run, RazView.FILTER );
	}

	public void atomInTheVoid( SuperColliderPlayer.RunningAtom run )
	{
		razView.removeAtom( run );
	}
	
	private static class RazView
	extends JComponent
	{
		private static final int			LIGHT			= 0;
		private static final int			SHADOW			= 1;
		private static final int			FILTER			= 2;
		
		private static final long			TIME_SPAN		= 30000;	// gadget width spans thirty seconds
		
		private static final Color[]		colrType		= { Color.green, Color.gray, Color.blue };
		private static final Color			colrNow			= GraphicsUtil.colrSelection;
	
		private final	java.util.List[]	collRaz;
		private final	java.util.Map		mapRazToColor	= new HashMap();
		private long						leftMarginTime	= 0;	// left gadget margin corresponds to sys abs time (millis)
		private final int					numLayers;
	
		private RazView( int numLayers )
		{
			super();
			
			this.numLayers	= numLayers;
			
			collRaz = new java.util.List[ numLayers ];
			for( int i = 0; i < collRaz.length; i++ ) {
				collRaz[ i ] = new ArrayList();
			}
			
			setOpaque( true );
			setBackground( Color.white );
		}
		
		public void paintComponent( Graphics g )
		{
			super.paintComponent( g );
			
			final int totW = getWidth();
			final int totH = getHeight();

			g.clearRect( 0, 0, totW, totH );
			
			final long now = System.currentTimeMillis();
			if( now > leftMarginTime + TIME_SPAN ) {
				leftMarginTime = now - (now % TIME_SPAN);
			}
			
			int x, w, y = 4, h;
			SuperColliderPlayer.RunningAtom run;
			
			for( int i = 0; i < numLayers; i++ ) {
				h = totH / numLayers - ((collRaz[ i ].size() + 4) << 1);
				for( int j = 0; j < collRaz[ i ].size(); j++ ) {
					run = (SuperColliderPlayer.RunningAtom) collRaz[ i ].get( j );
					g.setColor( (Color) mapRazToColor.get( run ));
					x	= (int) ((run.nodeStartTime - leftMarginTime) * totW / TIME_SPAN);
					w	= (int) ((run.nodeStopTime - run.nodeStartTime) * totW / TIME_SPAN);
					if( x + w < 0 ) {
						if( mapRazToColor.remove( run ) == colrType[ 0 ]) {
							System.err.println( "Wow! Light atom wasn't disposed : layer " + run.layer + " ; " + run.ra.name );
						}
						collRaz[ i ].remove( run );
						j--;
					}
					g.drawRect( x, y, w, h );
					y += 2;
				}
				y += h + 8;
			}
			
			g.setColor( colrNow );
			x = (int) ((now - leftMarginTime) * totW / TIME_SPAN);
			g.fillRect( x - 2, 0, 4, totH );
		}
		
		// @synchronization	call in event thread
		private void addAtom( SuperColliderPlayer.RunningAtom run, int type )
		{
			collRaz[ run.layer ].add( run );
			mapRazToColor.put( run, colrType[ type ]);
//			repaint();
		}

		// @synchronization	call in event thread
		private void removeAtom( SuperColliderPlayer.RunningAtom run )
		{
			collRaz[ run.layer ].remove( run );
			mapRazToColor.remove( run );
//			repaint();
		}
	}
}
