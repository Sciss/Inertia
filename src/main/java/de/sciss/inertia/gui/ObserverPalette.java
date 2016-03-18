/*
 *  ObserverPalette.java
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
 *		12-Aug-05	created from de.sciss.eisenkraut.gui.ObserverPalette
 *					; adds support for dynamic document changes
 */

package de.sciss.inertia.gui;

import de.sciss.app.AbstractApplication;
import de.sciss.app.DynamicAncestorAdapter;
import de.sciss.app.DynamicListening;
import de.sciss.app.UndoManager;
import de.sciss.gui.*;
import de.sciss.inertia.Main;
import de.sciss.inertia.edit.BasicSyncCompoundEdit;
import de.sciss.inertia.edit.EditSetSessionObjectName;
import de.sciss.inertia.session.*;
import de.sciss.inertia.timeline.TimelineEvent;
import de.sciss.inertia.timeline.TimelineListener;
import de.sciss.io.Span;
import de.sciss.util.LockManager;
import de.sciss.util.NumberSpace;

import javax.swing.*;
import javax.swing.undo.CompoundEdit;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;

/**
 *  The <code>ObserverPalette</code> is a
 *  GUI component that displays context sensitive
 *  data and is closely linked to the user's
 *  mouse activities. It contains tabbed panels
 *  for general cursor information and information
 *  concerning receivers, transmitters and the
 *  timeline. Depending on the tab, the data is
 *  presented in editable text or number fields.
 *  <p>
 *  The cursor info pane is 'passive' because in this way
 *  it is easily expandible to display data of new
 *  components. The interested components are hence
 *  resonsible for calling <code>showCursorInfo</code>.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.5, 02-Aug-05
 */
public class ObserverPalette
extends BasicPalette
implements de.sciss.app.DocumentListener, DynamicListening
{
	private JLabel[]								lbCursorInfo;
	private final NumberField						ggTimelineStart, ggTimelineStop;
	private final JTabbedPane						ggTabPane;
	private final SessionObjectTable				sotSession, sotProb;

	private final int								NUM_SOT		= 3;
	private final int								SOT_OFFSET	= 3;
//	private final String[]							TAB_NAMES	= { "observerTrack", "observerAtom" };
//	private final String[]							HELP_NAMES	= { "ObserverTrack", "ObserverAtom" };
//	private final int[]								DOORS		= { Session.DOOR_TRACKS, Track.DOOR_ATOMS };
	private final String[]							TAB_NAMES	= { "observerTrack", "observerMolecule", "observerAtom" };
	private final String[]							HELP_NAMES	= { "ObserverTrack", "ObserverMolecule", "ObserverAtom" };
	private final int[]								DOORS		= { Session.DOOR_TRACKS, Session.DOOR_MOLECULES, Session.DOOR_MOLECULES };
	private final SessionObjectTable[]				sot			= new SessionObjectTable[ NUM_SOT ];
	private final sessionCollectionListenerClass[]	scl			= new sessionCollectionListenerClass[ NUM_SOT ];
	private final JTextField[]						ggName		= new JTextField[ NUM_SOT ];
	private final timelineListenerClass				tl;

	public static final int CURSOR_TAB		= 0;
	public static final int SESSION_TAB		= 1;
	public static final int PROB_TAB		= 2;
	public static final int TRACK_TAB		= 3;
	public static final int MOLEC_TAB		= 4;
	public static final int ATOM_TAB		= 5;
	public static final int TIMELINE_TAB	= 6;
		
	public static final int NUM_CURSOR_ROWS	= 5;
	
	private static final java.util.List				collEmpty		= new ArrayList( 1 );
	
	private java.util.List							collProbNames	= collEmpty;
	private final ProbNameListener					probNameListener;
	private final JToolBar							probBar;
	private final JTextField						ggProbName;
	
	private boolean									isListening	= false;
	
	private final FlagsPanel						ggMolecFlags;
	
	/**
	 *  Constructs a new <code>ObserverPalette</code>
	 *
	 *  @param  root	application root
	 */
	public ObserverPalette( Main root )
	{
		super( AbstractApplication.getApplication().getResourceString( "paletteObserver" ));
		
		final Container		cp		= getContentPane();
		final NumberSpace	spcTime = new NumberSpace( 0.0, Double.POSITIVE_INFINITY, 0.0 );
		JPanel				c;
		JLabel				lb;
		GridBagLayout		lay;
		GridBagConstraints  con;

		ggTabPane		= new JTabbedPane();
        HelpGlassPane.setHelp( ggTabPane, "ObserverPalette" );
        ggTabPane.setTabLayoutPolicy( JTabbedPane.WRAP_TAB_LAYOUT );

		// ----- cursor tab ------
		c				= new JPanel();
		lay				= new GridBagLayout();
		con				= new GridBagConstraints();
		con.insets		= new Insets( 2, 2, 2, 2 );
		c.setLayout( lay );
		con.weightx		= 1.0;
		con.gridwidth   = GridBagConstraints.REMAINDER;
		lbCursorInfo	= new JLabel[ NUM_CURSOR_ROWS ];
		for( int i = 0; i < NUM_CURSOR_ROWS; i++ ) {
			lb			= new JLabel();
			lay.setConstraints( lb, con );
			c.add( lb );
			lbCursorInfo[i] = lb;
		}
		ggTabPane.addTab( getResourceString( "observerCursor" ), null, c, null );
        HelpGlassPane.setHelp( c, "ObserverCursor" );
        
		// ----- session tab ------
		sotSession		= new SessionObjectTable();
		ggTabPane.addTab( getResourceString( "observerSession" ), null, sotSession, null );
        HelpGlassPane.setHelp( c, "ObserverSession" );

		// ----- session tab ------
		c				= new JPanel();
		lay				= new GridBagLayout();
		con				= new GridBagConstraints();
		con.insets		= new Insets( 2, 2, 2, 2 );
		c.setLayout( lay );
		lb				= new JLabel( getResourceString( "labelName" ), JLabel.RIGHT );
		con.weightx		= 0.0;
		con.gridwidth   = 1;
		lay.setConstraints( lb, con );
		c.add( lb );
		ggProbName		= new JTextField( 12 );
		ggProbName.setEditable( false );
		con.weightx		= 1.0;
		con.gridwidth   = GridBagConstraints.REMAINDER;
		lay.setConstraints( ggProbName, con );
		lb.setLabelFor( ggProbName );
		c.add( ggProbName );
		con.fill		= GridBagConstraints.BOTH;
		con.weighty		= 1.0;
		sotProb			= new SessionObjectTable();
		lay.setConstraints( sotProb, con );
		c.add( sotProb );
		ggTabPane.addTab( getResourceString( "observerProbability" ), null, c, null );
		HelpGlassPane.setHelp( c, "ObserverProbability" );
		
		probNameListener	= new ProbNameListener();
		probBar				= new JToolBar();
		probBar.setFloatable( false );

		ggMolecFlags		= new FlagsPanel();

		// ----- session collection objects tabs ------
		for( int ID = 0; ID < NUM_SOT; ID++ ) {
			c				= new JPanel();
			lay				= new GridBagLayout();
			con				= new GridBagConstraints();
			con.insets		= new Insets( 2, 2, 2, 2 );
			c.setLayout( lay );
			lb				= new JLabel( getResourceString( "labelName" ), JLabel.RIGHT );
			con.weightx		= 0.0;
			con.gridwidth   = 1;
			lay.setConstraints( lb, con );
			c.add( lb );
			ggName[ ID ]	= new JTextField( 12 );
			con.weightx		= 1.0;
			con.gridwidth   = GridBagConstraints.REMAINDER;
			lay.setConstraints( ggName[ ID ], con );
			lb.setLabelFor( ggName[ ID ]);
			c.add( ggName[ ID ]);
			con.fill		= GridBagConstraints.BOTH;
			con.weighty		= 1.0;

			sot[ ID ]		= new SessionObjectTable();
			lay.setConstraints( sot[ ID ], con );
			c.add( sot[ ID ]);
			
			if( ID == ATOM_TAB - SOT_OFFSET ) {
				lb				= new JLabel( getResourceString( "labelProbs" ), JLabel.RIGHT );
				con.fill		= GridBagConstraints.HORIZONTAL;
				con.weightx		= 0.0;
				con.gridwidth   = 1;
				lay.setConstraints( lb, con );
				c.add( lb );
				con.weightx		= 1.0;
				con.gridwidth   = GridBagConstraints.REMAINDER;
				lay.setConstraints( probBar, con );
				c.add( probBar );

			} else if( ID == MOLEC_TAB - SOT_OFFSET ) {
				con.fill		= GridBagConstraints.HORIZONTAL;
				con.weightx		= 0.0;
				con.gridwidth   = GridBagConstraints.REMAINDER;
				lay.setConstraints( ggMolecFlags, con );
				c.add( ggMolecFlags );
			}
			
			ggTabPane.addTab( getResourceString( TAB_NAMES[ ID ]), null, c, null );
			HelpGlassPane.setHelp( c, HELP_NAMES[ ID ]);
			scl[ ID ]		= new sessionCollectionListenerClass( ID );
		}
		
		// ----- timeline tab ------
		c				= new JPanel();
		lay				= new GridBagLayout();
		con				= new GridBagConstraints();
		con.insets		= new Insets( 2, 2, 2, 2 );
		c.setLayout( lay );
		lb				= new JLabel( getResourceString( "observerStart" ), JLabel.RIGHT );
		con.weightx		= 0.0;
		con.gridwidth   = 1;
		lay.setConstraints( lb, con );
		c.add( lb );
		ggTimelineStart	= new NumberField();
		ggTimelineStart.setSpace( spcTime );
		ggTimelineStart.setFlags( NumberField.HHMMSS );
		con.weightx		= 0.5;
		con.gridwidth   = GridBagConstraints.REMAINDER;
		lay.setConstraints( ggTimelineStart, con );
		c.add( ggTimelineStart );
		lb.setLabelFor( ggTimelineStart );
		lb				= new JLabel( getResourceString( "observerStop" ), JLabel.RIGHT );
		con.weightx		= 0.0;
		con.gridwidth   = 1;
		lay.setConstraints( lb, con );
		c.add( lb );
		ggTimelineStop	= new NumberField();
		ggTimelineStop.setSpace( spcTime );
		ggTimelineStop.setFlags( NumberField.HHMMSS );
		con.weightx		= 0.5;
		con.gridwidth   = GridBagConstraints.REMAINDER;
		lay.setConstraints( ggTimelineStop, con );
		c.add( ggTimelineStop );
		lb.setLabelFor( ggTimelineStop );
		ggTabPane.addTab( getResourceString( "observerTimeline" ), null, c, null );
        HelpGlassPane.setHelp( c, "ObserverTimeline" );
		
		tl				= new timelineListenerClass();
        
		cp.setLayout( new BorderLayout() );
		cp.add( BorderLayout.CENTER, ggTabPane );
		
		GUIUtil.setDeepFont( cp, GraphicsUtil.smallGUIFont );
		
		// --- Listener ---		
		AbstractApplication.getApplication().getDocumentHandler().addDocumentListener( this );
		new DynamicAncestorAdapter( this ).addTo( ggTabPane );
		
		init( root );
	}
	
	/**
	 *	Returns <code>false</code>
	 */
	protected boolean alwaysPackSize()
	{
		return false;
	}

	public void setProbObjects( java.util.List collProbs, UndoManager undo, LockManager lm, int doors )
	{
		sotProb.setObjects( collProbs, undo, lm, doors );
		if( !collProbs.isEmpty() ) showTab( PROB_TAB );
	}

	/**
	 *  Switch the display to a specific
	 *  tab, where zero is the cursor info tab.
	 *
	 *  @param  tabIndex	the index of the tab to show
	 */
	public void showTab( int tabIndex )
	{
		ggTabPane.setSelectedIndex( tabIndex );
	}

	/**
	 *  Display information in the cursor pane.
	 *  It's up to the caller what kind of information
	 *  is displayed. This method simply displays
	 *  up to four lines of text. This method doesn't
	 *  switch the display to the cursor pane.
	 *
	 *  @param  info	an array of zero to four strings
	 *					which will be displayed in the
	 *					cursor tab.
	 */
	public void showCursorInfo( String[] info )
	{
		int i, j;
		
		j = Math.min( NUM_CURSOR_ROWS, info.length );
		
		for( i = 0; i < j; i++ ) {
			lbCursorInfo[ i ].setText( info[ i ]);
		}
		for( ; i < NUM_CURSOR_ROWS; i++ ) {
			lbCursorInfo[ i ].setText( "" );
		}
	}
	
	private void setAtomObjects( java.util.List atoms, UndoManager undo, LockManager lm, int doors )
	{
		final java.util.List	collNames	= new ArrayList();
		Atom					a;
		Probability				prob;

		probNameListener.setObjects( atoms, undo, lm, doors );

		if( (lm != null) && !lm.attemptShared( doors, 250 )) return;
		try {
			for( int i = 0; i < atoms.size(); i++ ) {
				a = (Atom) atoms.get( i );
				for( int j = 0; j < a.probabilities.size(); j++ ) {
					prob = (Probability) a.probabilities.get( j );
					if( !collNames.contains( prob.getName() )) {
						collNames.add( prob.getName() );
					}
				}
			}
		}
		finally {
			if( lm != null ) lm.releaseShared( doors );
		}
		
		Collections.sort( collNames );

		if( !collNames.equals( collProbNames )) {
			collProbNames	= collNames;
			probBar.removeAll();
			ButtonGroup		bg	= new ButtonGroup();
			JToggleButton	tb;
			for( int i = 0; i < collProbNames.size(); i++ ) {
				tb = new JToggleButton( (String) collProbNames.get( i ));
				bg.add( tb );
				probBar.add( tb );
				tb.addActionListener( probNameListener );
				GUIUtil.setDeepFont( tb, null );
			}
		}
	}

	private String getResourceString( String key )
	{
		return AbstractApplication.getApplication().getResourceString( key );
	}
    
	private void changeDocument( Session doc )
	{
		if( isListening ) {
			final java.util.List collOne	= new ArrayList( 1 );
			if( doc != null ) collOne.add( doc );
			sotSession.setObjects( collOne, doc == null ? null : doc.getUndoManager(), null, 0 );	// XXX sync
		}
	}

	public void startListening()
	{
		isListening = true;
		final Session			doc		= (Session) AbstractApplication.getApplication().getDocumentHandler().getActiveDocument();
		final java.util.List	collOne	= new ArrayList( 1 );
		if( doc != null ) collOne.add( doc );
		sotSession.setObjects( collOne, doc == null ? null : doc.getUndoManager(), null, 0 );	// XXX sync
	}
	
	public void stopListening()
	{
		isListening = false;
		sotSession.setObjects( collEmpty, null, null, 0 );
	}
	
// ---------------- DocumentListener interface ---------------- 

	public void documentFocussed( de.sciss.app.DocumentEvent e )
	{
		tl.changeDocument();
		for( int i = 0; i < NUM_SOT; i++ ) {
			scl[ i ].changeDocument();
		}
		this.changeDocument( (Session) e.getDocument() );
	}

	public void documentAdded( de.sciss.app.DocumentEvent e ) {}
	public void documentRemoved( de.sciss.app.DocumentEvent e ) {}

// ---------------- internal classes ---------------- 

	private class timelineListenerClass
	implements TimelineListener, DynamicListening, NumberListener
	{
		private Session doc			= null;
		private boolean	isListening	= false;
	
		private timelineListenerClass()
		{
			new DynamicAncestorAdapter( this ).addTo( ggTabPane );
		}
	
		// attempts shared on DOOR_TIME
		private void updateTimeline()
		{
			if( doc != null ) {

				if( !doc.bird.attemptShared( Session.DOOR_TIME, 250 )) return;
				try {
					final Span	span	= doc.timeline.getSelectionSpan();
					final int	rate	= doc.timeline.getRate();

					ggTimelineStart.setNumber( new Double( (double) span.getStart() / rate ));
					ggTimelineStop.setNumber( new Double( (double) span.getStop() / rate ));
					if( !ggTimelineStart.isEnabled() ) ggTimelineStart.setEnabled( true );
					if( !ggTimelineStop.isEnabled() )  ggTimelineStop.setEnabled( true );
				}
				finally {
					doc.bird.releaseShared( Session.DOOR_TIME );
				}
				
			} else {
				
				ggTimelineStart.setNumber( new Double( Double.NaN ));
				ggTimelineStop.setNumber( new Double( Double.NaN ));
				ggTimelineStart.setEnabled( false );
				ggTimelineStop.setEnabled( false );
			}
		}

		public void changeDocument()
		{
			if( isListening ) {
				stopListening();
				startListening();
			}
		}

		public void startListening()
		{
			doc = (Session) AbstractApplication.getApplication().getDocumentHandler().getActiveDocument();
			if( doc != null ) {
				doc.timeline.addTimelineListener( this );
			}
			ggTimelineStart.addListener( this );
			ggTimelineStop.addListener( this );
			updateTimeline();
			isListening = true;
		}

		public void stopListening()
		{
			isListening = false;
			if( doc != null ) doc.timeline.removeTimelineListener( this );
			ggTimelineStart.removeListener( this );
			ggTimelineStop.removeListener( this );
		}
		
		public void timelineSelected( TimelineEvent e )
		{
			if( doc == null ) return;
		
			try {
				doc.bird.waitShared( Session.DOOR_TIME );
				showTab( doc.timeline.getSelectionSpan().isEmpty() ? CURSOR_TAB : TIMELINE_TAB );   // intelligently switch between the tabs
				updateTimeline();
			}
			finally {
				doc.bird.releaseShared( Session.DOOR_TIME );
			}
		}

		public void timelineChanged( TimelineEvent e )
		{
			updateTimeline();
		}
		
		public void timelinePositioned( TimelineEvent e ) {}
		public void timelineScrolled( TimelineEvent e ) {}

		public void numberChanged( NumberEvent e )
		{
			int					i, num;
			Point2D				anchor;
			CompoundEdit		edit;
			Span				span;
			long				n;
			double				d		= e.getNumber().doubleValue();
		
			if( (e.getSource() == ggTimelineStart) || (e.getSource() == ggTimelineStop) ) {

				if( !doc.bird.attemptExclusive( Session.DOOR_TIME, 250 )) return;
				try {
					span	= doc.timeline.getSelectionSpan();
					n		= (long) (d * doc.timeline.getRate() + 0.5);
					
					if( e.getSource() == ggTimelineStart ) {
						span	= new Span( Math.max( 0, Math.min( span.getStop(), n )), span.getStop() );
					} else {
						span	= new Span( span.getStart(), Math.min( doc.timeline.getLength(),
															 Math.max( span.getStart(), n )) );
					}
	// INERTIA
	//				doc.getUndoManager().addEdit( new EditSetTimelineSelection( this, doc, span ));
	doc.timeline.setSelectionSpan( this, span );
				}
				finally {
					doc.bird.releaseExclusive( Session.DOOR_TIME );
				}
			}
		}
	}

	private class sessionCollectionListenerClass
	implements ActionListener, SessionCollection.Listener, DynamicListening
	{
		private final						int				ID;
		private Session						doc				= null;
		private boolean						isListening		= false;
		private LockManager					lm;
		private SessionCollection			scSel, scAll;
	
		private sessionCollectionListenerClass( int ID )
		{
			this.ID	= ID;
			new DynamicAncestorAdapter( this ).addTo( ggTabPane );
		}
	
		public void sessionCollectionChanged( SessionCollection.Event e )
		{
			if( (doc == null) || (scSel == null) ) return;

			if( lm != null ) lm.waitShared( DOORS[ ID ]);
			try {
				showTab( scSel.isEmpty() ? CURSOR_TAB : ID + SOT_OFFSET );   // intelligently switch between the tabs
				setObjects( scSel.getAll(), doc.getUndoManager(), lm, DOORS[ ID ]);
				updateFields( e );
			}
			finally {
				if( lm != null ) lm.releaseShared( DOORS[ ID ]);
			}
			
			// track tab informs molec tab!
			if( ID == TRACK_TAB - SOT_OFFSET ) {
				scl[ MOLEC_TAB - SOT_OFFSET ].updateObjects();
			// molec tab informs atom tab!
			} else if( ID == MOLEC_TAB - SOT_OFFSET ) {
				scl[ ATOM_TAB - SOT_OFFSET ].updateObjects();
				if( scSel.size() == 1 ) {
					ggMolecFlags.setObject( scSel.get( 0 ), scAll, lm, DOORS[ ID ]);
					ggMolecFlags.setVisible( true );
				} else {
					ggMolecFlags.setObject( null, null, lm, DOORS[ ID ]);
					ggMolecFlags.setVisible( false );
				}
			}
		}
		
		public void sessionObjectMapChanged( SessionCollection.Event e ) {}

		public void sessionObjectChanged( SessionCollection.Event e )
		{
			if( doc == null ) return;

			if( e.getModificationType() == SessionObject.OWNER_RENAMED ) {
				if( lm != null ) lm.waitShared( DOORS[ ID ]);
				try {
					updateFields( e );
				}
				finally {
					if( lm != null ) lm.releaseShared( DOORS[ ID ]);
				}
			}
		}

		// to be called with shared lock on 'doors'!
		private void updateFields( SessionCollection.Event e )
		{
			final SessionObject	so;
			final int			numObj		= scSel == null ? 0 : scSel.size();
			final String		name;

			if( numObj >= 1 ) {
				so		= (SessionObject) scSel.get( 0 );
				name	= numObj == 1 ? so.getName() : getResourceString( "observerMultiSelection" );
			} else {
				name	= getResourceString( "observerNoSelection" );
			}
			ggName[ ID ].setText( name );
		}

		public void actionPerformed( ActionEvent e )
		{
			if( doc == null ) return;
		
			final int				num;
			final CompoundEdit		edit;
			final java.util.List	coll, coll2;
			final Object[]			args;
			SessionObject			so;
			String					name;

			if( e.getSource() == ggName[ ID ]) {
//System.err.println( "ici" );
				if( !lm.attemptExclusive( DOORS[ ID ], 250 )) return;
				try {
					coll	= scSel.getAll();
					coll2	= scAll.getAll();
					coll.retainAll( coll2 );
					num		= coll.size();
					if( num == 0 ) return;
					
					args	= new Object[ 3 ];
					
					coll2.removeAll( coll );
					edit	= new BasicSyncCompoundEdit( lm, DOORS[ ID ]);
					name	= ggName[ ID ].getText();
					if( num == 1 ) {
						so = (SessionObject) coll.get( 0 );
//						if( !coll2.contains( so )) {
//							System.err.println( "Didn't rename "+so.getName()+" (not part of selected molecule)" );
//							System.err.println( "scSel : " );
//							scSel.debugDump( 1 );
//							System.err.println( "scAll : " );
//							scAll.debugDump( 1 );
//							return; // Atom Tab !!
//						}
						if( SessionCollection.findByName( coll2, name ) != null ) {
							Session.makeNamePattern( name, args );
							name = SessionCollection.createUniqueName( Session.SO_NAME_PTRN, args, coll2 );
						}
						edit.addEdit( new EditSetSessionObjectName( this, doc, so, name, DOORS[ ID ]));
					} else {
						Session.makeNamePattern( name, args );
						for( int i = 0; i < num; i++ ) {
							so		= (SessionObject) coll.get( i );
//							if( !coll2.contains( so )) {
//								System.err.println( "Didn't rename "+so.getName()+" (not part of selected molecule)" );
//								continue; // Atom Tab !!
//							}
							name	= SessionCollection.createUniqueName( Session.SO_NAME_PTRN, args, coll2 );
							edit.addEdit( new EditSetSessionObjectName( this, doc, so, name, DOORS[ ID ]));
							coll2.add( so );
						}
					}
					edit.end();
					doc.getUndoManager().addEdit( edit );
				}
				finally {
					lm.releaseExclusive( DOORS[ ID ]);
				}
			}
		}

		public void changeDocument()
		{
			if( isListening ) {
				stopListening();
				startListening();
			}
		}
		
		private void updateObjects()
		{
			changeDocument();
			if( isListening && (scSel != null) && (doc != null) ) {
				if( (lm == null) || lm.attemptShared( DOORS[ ID ], 250 )) {
					try {
						setObjects( scSel.getAll(), doc.getUndoManager(), lm, DOORS[ ID ]);
					}
					finally {
						if( lm != null ) lm.releaseShared( DOORS[ ID ]);
					}
				}
			}
		}

		public void startListening()
		{
			doc = (Session) AbstractApplication.getApplication().getDocumentHandler().getActiveDocument();
			
			scSel	= null;
			scAll	= null;
			lm		= null;
			if( doc != null ) {
				switch( ID ) {
				case TRACK_TAB - SOT_OFFSET:
					scSel	= doc.selectedTracks;
					scAll	= doc.tracks;
					lm		= doc.bird;
					break;
					
				case MOLEC_TAB - SOT_OFFSET:
					if( !doc.bird.attemptShared( Session.DOOR_TRACKS, 250 )) break;
					try {
						if( doc.selectedTracks.size() != 1 ) break;
						final Track t		= (Track) doc.selectedTracks.get( 0 );
						scSel				= t.selectedMolecules;
						scAll				= t.molecules;
						lm					= doc.bird;
					}
					finally {
						doc.bird.releaseShared( Session.DOOR_TRACKS );
					}
					break;
					
				case ATOM_TAB - SOT_OFFSET:
					if( !doc.bird.attemptShared( Session.DOOR_TRACKS | Session.DOOR_MOLECULES, 250 )) break;
					try {
						if( doc.selectedTracks.size() != 1 ) break;
						final Track t		= (Track) doc.selectedTracks.get( 0 );
						if( t.selectedMolecules.size() != 1 ) break;
						final Molecule m	= (Molecule) t.selectedMolecules.get( 0 );
						scSel				= t.selectedAtoms;
						scAll				= m.atoms;
						lm					= doc.bird;
//System.err.println( "scAll from "+m.getName() );
					}
					finally {
						doc.bird.releaseShared( Session.DOOR_TRACKS | Session.DOOR_MOLECULES );
					}
					break;

				default:
					assert false : ID;
					scSel	= null;
					scAll	= null;
					lm		= doc.bird;
					break;
				}

				if( scSel != null ) scSel.addListener( this );
				ggName[ ID ].addActionListener( this );
				if( (lm == null) || lm.attemptShared( DOORS[ ID ], 250 )) {
					try {
						updateFields( null );
					}
					finally {
						if( lm != null ) lm.releaseShared( DOORS[ ID ]);
					}
				}
			}
			isListening = true;
		}

		private void setObjects( java.util.List collObjects, UndoManager undo, LockManager lm, int doors )
		{
			sot[ ID ].setObjects( collObjects, undo, lm, doors );
			if( ID == ATOM_TAB - SOT_OFFSET ) {
				setAtomObjects( collObjects, undo, lm, doors );
			}
		}

		public void stopListening()
		{
			isListening			= false;
			if( scSel != null ) scSel.removeListener( this );
			ggName[ ID ].removeActionListener( this );
			setObjects( collEmpty, null, null, 0 );
			doc					= null;
			lm					= null;
			scSel				= null;
			scAll				= null;
		}
	} // class sessionCollectionListenerClass
	
	private class ProbNameListener
	implements ActionListener
	{
		private java.util.List	atoms;
		private UndoManager		undo;
		private LockManager		lm;
		private int				doors;
		
		private void setObjects( java.util.List atoms, UndoManager undo, LockManager lm, int doors )
		{
			this.atoms	= atoms;
			this.undo	= undo;
			this.lm		= lm;
			this.doors	= doors;
		}
	
		public void actionPerformed( ActionEvent e )
		{
			if( (lm != null) && !lm.attemptShared( doors, 250 )) return;
			try {
				final java.util.List	probs		= new ArrayList();
				final String			probName	= ((AbstractButton) e.getSource()).getText();
				Probability				prob;
				
				ggProbName.setText( probName );
			
				if( atoms != null ) {
					for( int i = 0; i < atoms.size(); i++ ) {
						prob = (Probability) ((Atom) atoms.get( i )).probabilities.findByName( probName );
						if( prob != null ) probs.add( prob );
					}
				}
				
				setProbObjects( probs, undo, lm, doors );
				if( !probs.isEmpty() ) showTab( PROB_TAB );
			}
			finally {
				if( lm != null ) lm.releaseShared( doors );
			}
		}
	}
}