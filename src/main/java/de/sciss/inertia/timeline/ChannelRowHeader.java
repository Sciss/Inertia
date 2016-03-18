/*
 *  ChannelRowHeader.java
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
 *		12-Aug-05	created from de.sciss.eisenkraut.timeline.ChannelRowHeader
 *					; re-adds mouse listening and (de)selection as in meloncillo
 */

package de.sciss.inertia.timeline;

import de.sciss.app.DynamicAncestorAdapter;
import de.sciss.app.DynamicListening;
import de.sciss.gui.HelpGlassPane;
import de.sciss.inertia.gui.GraphicsUtil;
import de.sciss.inertia.session.FlagsPanel;
import de.sciss.inertia.session.Session;
import de.sciss.inertia.session.SessionCollection;
import de.sciss.inertia.session.Track;

import javax.swing.*;
import javax.swing.undo.UndoableEdit;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;

// INERTIA
//import de.sciss.eisenkraut.*;
//import de.sciss.eisenkraut.edit.*;
//import de.sciss.eisenkraut.gui.*;
//import de.sciss.eisenkraut.io.*;
//import de.sciss.eisenkraut.math.*;
//import de.sciss.eisenkraut.session.*;
//import de.sciss.eisenkraut.util.*;

/**
 *	A row header in Swing's table 'ideology'
 *	is a component left to the leftmost
 *	column of each row in a table. It serves
 *	as a kind of label for that specific row.
 *	This class shows a header left to each
 *	sound file's waveform display, with information
 *	about the channel index, possible selections
 *	and soloing/muting. In the future it could
 *	carry insert effects and the like.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.1, 15-Aug-05
 *
 *	@todo		shift+selection and alt+selection sometimes is wrong
 */
public class ChannelRowHeader
extends JPanel
implements MouseListener, DynamicListening
{
	private final Session				doc;
// INERTIA
//	private final Main					root;
	private final JLabel				lbTrackName;
	private final Track					t;

	private boolean				selected		= false;

    private static final Color  colrSelection   = GraphicsUtil.colrSelection;
	private static final Paint	pntTopBorder	= new GradientPaint( 0.0f, 0.0f, new Color( 0xFF, 0xFF, 0xFF, 0xFF ),
																	 0.0f, 8.0f, new Color( 0xFF, 0xFF, 0xFF, 0x00 ));
//	private static final Paint	pntBottomBorder	= new GradientPaint( 0.0f, 0.0f, new Color( 0x9F, 0x9F, 0x9F, 0xFF ),
//																	 0.0f, 8.0f, new Color( 0x9F, 0x9F, 0x9F, 0x00 ),
//																	 true );
	private static final Paint	pntBottomBorder	= new GradientPaint( 0.0f, 0.0f, new Color( 0x9F, 0x9F, 0x9F, 0x00 ),
																	 0.0f, 8.0f, new Color( 0x9F, 0x9F, 0x9F, 0xFF ));
	
	private final SessionCollection.Listener tracksListener;
	private final SessionCollection.Listener selectedTracksListener;
	
	/**
	 */
	public ChannelRowHeader( final Session doc, final Track t )
	{
		super();
		
// INERTIA
//		this.root		= root;
		this.doc		= doc;
		this.t			= t;
		
		final SpringLayout	lay	= new SpringLayout();
		final JButton		pan;
		final FlagsPanel	flags;
		setLayout( lay );
		
 		lbTrackName = new JLabel();
// INERTIA
//		pan			= new PanoramaButton( root, doc, t );
		flags		= new FlagsPanel();
		flags.setObject( t, doc.tracks, doc.bird, Session.DOOR_TRACKS );
		lbTrackName.setFont( GraphicsUtil.smallGUIFont );
		lay.putConstraint( SpringLayout.WEST, lbTrackName, 8, SpringLayout.WEST, this );
		lay.putConstraint( SpringLayout.NORTH, lbTrackName, 8, SpringLayout.NORTH, this );
		add( lbTrackName );
// INERTIA
//		add( pan );
		add( flags );
		lay.putConstraint( SpringLayout.EAST, flags, -4, SpringLayout.EAST, this );
		lay.putConstraint( SpringLayout.SOUTH, flags, -8, SpringLayout.SOUTH, this );
// INERTIA
//		lay.putConstraint( SpringLayout.EAST, pan, -3, SpringLayout.EAST, this );
//		lay.putConstraint( SpringLayout.SOUTH, pan, 0, SpringLayout.NORTH, flags );
		setPreferredSize( new Dimension( 64, 16 )); // XXX
		setMaximumSize( new Dimension( 64, getMaximumSize().height )); // XXX
		setBorder( BorderFactory.createMatteBorder( 0, 0, 0, 2, Color.white ));   // top left bottom right

		// --- Listener ---
        new DynamicAncestorAdapter( this ).addTo( this );
		this.addMouseListener( this );

		tracksListener = new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e ) {}	// XXX could dispose
			
			public void sessionObjectChanged( SessionCollection.Event e )
			{
				if( e.getModificationType() == Track.OWNER_RENAMED ) {
					checkTrackName();
				}
			}

			public void sessionObjectMapChanged( SessionCollection.Event e ) {}		// XXX solo, mute
		};

		selectedTracksListener = new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				selected = doc.selectedTracks.contains( t );
				repaint();
			}
			
			public void sessionObjectChanged( SessionCollection.Event e ) {}
			public void sessionObjectMapChanged( SessionCollection.Event e ) {}
		};
	
        HelpGlassPane.setHelp( this, "ChannelTrack" );
    }
	
	/**
	 *  Determines if this row is selected
	 *  i.e. is part of the selected transmitters
	 *
	 *	@return	<code>true</code> if the row (and thus the transmitter) is selected
	 */
	public boolean isSelected()
	{
		return selected;
	}

	public Track getTrack()
	{
		return t;
	}

	public void paintComponent( Graphics g )
	{
		super.paintComponent( g );
		
		final Graphics2D	g2	= (Graphics2D) g;
		final int			h	= getHeight();
		final int			w	= getWidth();
		
		g2.setPaint( pntTopBorder );
		g2.fillRect( 0, 0, w, 8 );
	g2.translate( 0, h - 9 );
		g2.setPaint( pntBottomBorder );
//		g2.fillRect( 0, h - 9, w, 8 );
		g2.fillRect( 0, 0, w, 8 );
	g2.translate( 0, 9 - h );

		if( selected ) {
			g2.setColor( colrSelection );
			g2.fillRect( 0, 0, w, h );
		}
	}

	// syncs attemptShared to DOOR_TRACKS
	private void checkTrackName()
	{
		if( !doc.bird.attemptShared( Session.DOOR_TRACKS, 250 )) return;
		try {
			if( !t.getName().equals( lbTrackName.getText() )) {
				lbTrackName.setText( t.getName() );
			}
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TRACKS );
		}
	}

// ---------------- DynamicListening interface ---------------- 

    public void startListening()
    {
		doc.tracks.addListener( tracksListener );
		doc.selectedTracks.addListener( selectedTracksListener );
		checkTrackName();
    }

    public void stopListening()
    {
		doc.tracks.removeListener( tracksListener );
		doc.selectedTracks.removeListener( selectedTracksListener );
    }

// ---------------- MouseListener interface ---------------- 
// we're listening to the ourselves

	public void mouseEntered( MouseEvent e ) {}
	public void mouseExited( MouseEvent e ) {}

	/**
	 *	Handle mouse presses.
	 *	<pre>
	 *  Keyboard shortcuts as in ProTools:
	 *  Alt+Click   = Toggle item & set all others to same new state
	 *  Meta+Click  = Toggle item & set all others to opposite state
	 *	</pre>
	 *
	 *	@synchronization	attempts exclusive on TRNS + GRP
	 */
	public void mousePressed( MouseEvent e )
    {
		UndoableEdit	edit;
		java.util.List	collTracks;
	
		if( !doc.bird.attemptExclusive( Session.DOOR_TRACKS, 250 )) return;
		try {
			if( e.isAltDown() ) {
				selected = !selected;   // toggle item
				if( selected ) {		// select all
					collTracks = doc.activeTracks.getAll();
				} else {				// deselect all
					collTracks = new ArrayList( 1 );
				}
			} else if( e.isMetaDown() ) {
				selected = !selected;   // toggle item
				if( selected ) {		// deselect all except uns
					collTracks = new ArrayList( 1 );
					collTracks.add( t );
				} else {				// select all except us
					collTracks = doc.activeTracks.getAll();
					collTracks.remove( t );
				}
			} else {
				if( e.isShiftDown() ) {
					collTracks = doc.selectedTracks.getAll();
					selected = !selected;
					if( selected ) {
						collTracks.add( t );			// add us to selection
					} else {
						collTracks.remove( t );		// remove us from selection
					}
				} else {
					if( selected ) return;						// no action
					selected			= true;
					collTracks			= new ArrayList( 1 );	// deselect all except uns
					collTracks.add( t );
				}
			}
// XXX shouldn't use an undoable edit here!
//			edit = new EditSetSessionObjects( this, doc.selectedTracks, collTracks, doc.bird, Session.DOOR_TRACKS );
//			doc.getUndoManager().addEdit( edit );
java.util.List collRemoved	= doc.selectedTracks.getAll();
collTracks.removeAll( collRemoved );
collRemoved.removeAll( collTracks );
if( !collTracks.isEmpty() ) doc.selectedTracks.addAll( this, collTracks );
if( !collRemoved.isEmpty() ) doc.selectedTracks.removeAll( this, collRemoved );

			repaint();
		}
		finally {
			doc.bird.releaseExclusive( Session.DOOR_TRACKS );
		}
    }

	public void mouseReleased( MouseEvent e ) {}
	public void mouseClicked( MouseEvent e ) {}
}