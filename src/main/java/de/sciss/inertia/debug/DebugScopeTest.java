//
//  DebugScopeTest.java
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
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;

import de.sciss.gui.*;
import de.sciss.jcollider.*;
import de.sciss.net.*;

import de.sciss.inertia.gui.*;

/**
 *	@version	0.33, 17-Dec-05
 */
public class DebugScopeTest
extends JFrame
implements OSCListener, Runnable
{
	private final	VectorDisplay		vd;
	private final	OSCTransmitter		trns;
	private final	OSCReceiver			rcv;
	private float[]	vector;
	private final	JToggleButton		ggToggle;

	public DebugScopeTest()
	throws IOException
	{
		super( "Scope Test" );

		final InetSocketAddress	addr;
		final DatagramChannel	dch;
		final Container			cp		= getContentPane();
		final java.util.Map		mapMsg	= new HashMap();

		dch		= DatagramChannel.open();

		setDefaultCloseOperation( DO_NOTHING_ON_CLOSE );
		addWindowListener( new WindowAdapter() {
			public void windowClosing( WindowEvent e )
			{
				try {
					rcv.stopListening();
					dch.close();
				}
				catch( IOException e1 ) {
					System.err.println( e1 );
				}
				setVisible( false );
				dispose();
			}
		});
		
        addr    = new InetSocketAddress( InetAddress.getLocalHost(), 57110 );
		rcv		= new OSCReceiver( dch, addr );
		mapMsg.put( "/b_setn", new OSCBufSetNMessage() );
		rcv.setCustomMessageDecoders( mapMsg );
		trns	= new OSCTransmitter( dch, addr );

        rcv.addOSCListener( this );
		rcv.startListening();
		
		vd = new VectorDisplay();
		vd.setSpace( null, VectorSpace.createLinSpace( 0.0, 1.0, -1.0, 1.0, null, null, null, null ));
		vd.setFillArea( false );

		cp.setLayout( new BorderLayout() );
		cp.add( vd, BorderLayout.CENTER );
		cp.add( new JButton( new actionSampleClass() ), BorderLayout.SOUTH );
		ggToggle = new JToggleButton( "Keep goin'" );
		cp.add( ggToggle, BorderLayout.NORTH );
		
		setSize( 400, 200 );
		setVisible( true );
		toFront();
	}
	
	public void messageReceived( OSCMessage msg, SocketAddress addr, long when )
	{
System.err.println( msg.getName() );
		if( msg instanceof OSCBufSetNMessage ) {
			vector = ((OSCBufSetNMessage) msg).getFloatArray();
			SwingUtilities.invokeLater( this );
		}
	}

	public void run()
	{
		vd.setVector( null, vector );
		if( ggToggle.isSelected() ) query();	// keep it going
	}
	
	private void query()
	{
		try {
			trns.send( new OSCMessage( "/b_getn", new Object[] { new Integer( 0 ), new Integer( 0 ), new Integer( 512 )}));
		}
		catch( IOException e1 ) {
			System.err.println( e1 );
		}
	}
	
	private class actionSampleClass
	extends MenuAction
	{
		private actionSampleClass()
		{
			super( "Sample" );
		}
		
		public void actionPerformed( ActionEvent e )
		{
			query();
		}
	}
	
//	private class ScopePanel
//	extends JComponent
//	{
//		private ScopePanel()
//		{
//			super();
//			
//			setOpaque( true );
//			setBackground( Color.white );
//		}
//		
//		public void paintComponent( Graphics g )
//		{
//			super.paintComponent( g );
//		}
//	}
}
