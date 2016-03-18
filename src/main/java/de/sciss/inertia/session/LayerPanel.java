//
//  LayerPanel.java
//  Inertia
//
//  Created by SeaM on 30.09.05.
//  Copyright 2005 __MyCompanyName__. All rights reserved.
//

package de.sciss.inertia.session;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;

import de.sciss.inertia.*;
import de.sciss.inertia.gui.*;
import de.sciss.inertia.net.*;

public class LayerPanel
extends JPanel
implements LayerManager.Listener
{
	private final Icon[]			icnMovie;
	private final String[]			txtMovie;

	private static final Color		colrFocus		= new Color( 0xA0, 0xA0, 0xFF );

	private final java.util.List	movieLabels		= new ArrayList();	// element: ListLabel
	private final java.util.List	filterCombos	= new ArrayList();	// element: JComboBox
	
	private int						focussedLayer	= -1;
	
	private final LayerPanel		enc_this		= this;

	private static final String[]	filterNames	= {	// XXX has to go outside GUI class
		"---", "Lowpass", "AmpMod", "Filter1", "Filter2", "Filter3", "Filter4"
	};
				
	public LayerPanel( final Main root, final Session doc )
	{
		super();
		
		JLabel					lb;
		ListLabel				llb;
		JComboBox				cb;
		final MouseInputAdapter	mia;
		final int				numLayers	= doc.layers.getNumLayers();

		setComponentOrientation( ComponentOrientation.LEFT_TO_RIGHT );
		setLayout( new GridLayout( numLayers + 1, 3 ));	// rows, cols

		icnMovie	= new Icon[ numLayers ];
		txtMovie	= new String[ numLayers ];
		for( int ch = 0; ch < icnMovie.length; ch++ ) {
			icnMovie[ ch ]	= new ColorIcon( (float) ch / icnMovie.length );
			txtMovie[ ch ]	= String.valueOf( (char) (ch + 65) );
		}

//		lb	= new JLabel( "Layer", JLabel.RIGHT );
//		lb.setBorder( BorderFactory.createMatteBorder( 0, 0, 1, 1, Color.gray ));
//		add( lb );
final JCheckBox check = new JCheckBox( "Mobile" );
check.addActionListener( new ActionListener() {
	public void actionPerformed( ActionEvent e )
	{
		final SuperColliderPlayer player	= root.superCollider.getPlayerForDocument( doc );
		if( player == null ) return;

		if( check.isSelected() ) {
			player.startMobile();
		} else {
			player.stopMobile();
		}
	}
});
//check.setBorder( BorderFactory.createMatteBorder( 0, 0, 1, 1, Color.gray ));
add( check );

		lb	= new JLabel( "Movie", JLabel.CENTER );
		lb.setBorder( BorderFactory.createMatteBorder( 0, 0, 1, 1, Color.gray ));
		add( lb );
		lb	= new JLabel( "Filter", JLabel.LEFT );
		lb.setBorder( BorderFactory.createMatteBorder( 0, 0, 1, 0, Color.gray ));
		add( lb );
		
		for( int ch = 0; ch < numLayers; ch++ ) {
			lb	= new JLabel( String.valueOf( ch + 1 ), JLabel.RIGHT );
			lb.setBorder( BorderFactory.createMatteBorder( 0, 0, 0, 1, Color.gray ));
			add( lb );
			llb	= new ListLabel();
			for( int ch2 = 0; ch2 < icnMovie.length; ch2++ ) {
				llb.addItem( icnMovie[ ch2 ], txtMovie[ ch2 ]);
			}
			llb.setSelectedIndex( doc.layers.getMovieForLayer( ch ));
			llb.setBorder( BorderFactory.createMatteBorder( 0, 0, 0, 1, Color.gray ));
			add( llb );
			movieLabels.add( llb );
			if( ch == 0 ) {
				add( new JLabel() );	// top most layer cannot be filtered
				filterCombos.add( null );
			} else {
				cb = new JComboBox();
				for( int flt = 0; flt < filterNames.length; flt++ ) {
					cb.addItem( filterNames[ flt ]);
				}
				add( cb );
				filterCombos.add( cb );
				cb.addActionListener( new ActionListener() {
					public void actionPerformed( ActionEvent e ) {
						final int		ch	= filterCombos.indexOf( e.getSource() );
						if( ch == -1 ) return;
						final JComboBox cb	= (JComboBox) e.getSource();
						
						if( cb.getSelectedIndex() == 0 ) {
							doc.layers.setFilter( enc_this, ch, null );
						} else if( cb.getSelectedIndex() > 0 ) {
							doc.layers.setFilter( enc_this, ch, cb.getSelectedItem().toString() );
						}
					}
				});
			}
		}
		
		doc.layers.addListener(  this );

		mia = new MouseInputAdapter() {
			public void mousePressed( MouseEvent e )
			{
				if( focussedLayer == -1 ) return;
				
				doc.layers.switchLayers( enc_this, focussedLayer, focussedLayer + 1 );
			}
		
			public void mouseMoved( MouseEvent e )
			{
				final Component c	= getComponentAt( e.getX(), e.getY() );
				final int		idx	= c == null ? -1 : movieLabels.indexOf( c );
				
				if( idx == -1 ) {
					if( focussedLayer != -1 ) {
						((Component) movieLabels.get( focussedLayer )).setBackground( null );
						((Component) movieLabels.get( focussedLayer + 1 )).setBackground( null );
						focussedLayer = -1;
					}
				} else if( idx != focussedLayer ) {
					if( focussedLayer != -1 ) {
						((Component) movieLabels.get( focussedLayer )).setBackground( null );
						((Component) movieLabels.get( focussedLayer + 1 )).setBackground( null );
					}
					focussedLayer = Math.max( 0, Math.min( doc.layers.getNumLayers() - 2, 
						c.getY() + (c.getHeight() >> 1) < e.getY() ? idx : idx - 1 ));
					((Component) movieLabels.get( focussedLayer )).setBackground( colrFocus );
					((Component) movieLabels.get( focussedLayer + 1 )).setBackground( colrFocus );
				}
			}
		};

		this.addMouseListener( mia );
		this.addMouseMotionListener( mia );
	}
	
	// ---------- LayerManager.Listener interface ----------
	
	public void layersSwitched( LayerManager.Event e )
	{
		final int movieX, movieY;
		final ListLabel lbMovieX, lbMovieY;

		lbMovieX	= (ListLabel) movieLabels.get( e.getFirstLayer() );
		lbMovieY	= (ListLabel) movieLabels.get( e.getSecondLayer() );
		movieX		= e.getManager().getMovieForLayer( e.getFirstLayer() );
		movieY		= e.getManager().getMovieForLayer( e.getSecondLayer() );
		lbMovieX.setSelectedIndex( movieX );
		lbMovieY.setSelectedIndex( movieY );
	}
	
	public void layersFiltered( LayerManager.Event e )
	{
		if( e.getSource() != this ) {
			((JComboBox) filterCombos.get( e.getFirstLayer() )).setSelectedItem( e.getParam() );
		}
	}
}
