/*
 *  AtomTable.java
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
 *		07-Aug-05	created
 */

package de.sciss.inertia.gui;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;

import de.sciss.inertia.Main;
import de.sciss.inertia.edit.EditAddSessionObject;
import de.sciss.inertia.edit.EditAddSessionObjects;
import de.sciss.inertia.edit.EditRemoveSessionObjects;
import de.sciss.inertia.edit.EditSetSessionObjects;
import de.sciss.inertia.session.Atom;
import de.sciss.inertia.session.Probability;
import de.sciss.inertia.session.ProbabilityTable;
import de.sciss.inertia.session.Session;
import de.sciss.inertia.session.SessionCollection;
import de.sciss.inertia.session.SessionObject;
import de.sciss.inertia.session.Track;

import de.sciss.app.AbstractApplication;
import de.sciss.app.UndoManager;
import de.sciss.app.WindowHandler;

import de.sciss.gui.GUIUtil;
import de.sciss.gui.ModificationButton;

import de.sciss.util.LockManager;

public class AtomTable
extends SessionCollectionTable
{
	private final JComboBox			ggProbTableNames;
	private final SessionCollection	tables;
	private final Session			doc;
	private Track					t					= null;	// the one whose atoms are shown

	public AtomTable( final Session doc )
	{
		super( 0 );
		
		this.doc			= doc;
		this.tables			= doc.tables;
		
		final TableColumnModel	tcm;
		final TableCellEditor	tce;
		final ObserverPalette	observer;
		
		ggProbTableNames	= new JComboBox();
		tcm					= getTable().getColumnModel();
		tce					= new DefaultCellEditor( ggProbTableNames );
		observer			= (ObserverPalette) AbstractApplication.getApplication().getComponent( Main.COMP_OBSERVER );

		getTable().setCellSelectionEnabled( true );
		
		fillProbTableNames();

		for( int i = 1; i < tcm.getColumnCount(); i++ ) {
			tcm.getColumn( i ).setCellEditor( tce );
		}

		tcm.getSelectionModel().addListSelectionListener( new ListSelectionListener() {
			public void valueChanged( ListSelectionEvent e )
			{
				if( e.getValueIsAdjusting() ) return;

				final int idx = getTable().getSelectedColumn() - 1;
				if( (idx < 0) || (idx >= getValueCount()) ) return;

				if( (t == null) || !t.bird.attemptShared( Track.DOOR_ATOMS, 250 )) return;
				try {
					final java.util.List	collProbs	= new ArrayList();
					final String			probName	= getValueName( idx );
					final int[]				rows		= getTable().getSelectedRows();
					Atom					a;
					SessionObject			prob;
				
					for( int i = 0; i < rows.length; i++ ) {
//System.err.println( "row : "+rows[ i ]+"; probName "+probName );
						if( rows[ i ] >= t.atoms.size() ) continue;
						a		= (Atom) t.atoms.get( rows[ i ]);
						prob	= (Probability) a.probabilities.findByName( probName );
						if( prob != null ) collProbs.add( prob );
					}
	
					observer.setProbObjects( collProbs, doc.getUndoManager(), t.bird, Track.DOOR_ATOMS );
				}
				finally {
					t.bird.releaseShared( Track.DOOR_ATOMS );
				}
			}
		});

		tables.addListener( new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				fillProbTableNames();
			}
			
			public void sessionObjectMapChanged( SessionCollection.Event e ) {}

			public void sessionObjectChanged( SessionCollection.Event e )
			{
				fillProbTableNames();
			}
		});

		doc.selectedTracks.addListener( new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				updateCollection();
			}
			
			public void sessionObjectMapChanged( SessionCollection.Event e ) {}
			public void sessionObjectChanged( SessionCollection.Event e ) {}
		});

		GUIUtil.setDeepFont( ggProbTableNames, null );
		updateCollection();
	}
	
	private void updateCollection()
	{
		if( !doc.bird.attemptShared( Session.DOOR_TRACKS, 250 )) {
			if( t != null ) setCollection( null, null, null, 0, doc.getUndoManager() );
			return;
		}
		try {
			if( doc.selectedTracks.size() == 1 ) {
				final Track newT = (Track) doc.selectedTracks.get( 0 );
				if( newT != t ) {
					t	= newT;
					setCollection( t.atoms, t.selectedAtoms, t.bird, Track.DOOR_ATOMS, doc.getUndoManager() );
				}
			} else {
				if( t != null ) setCollection( null, null, null, 0, doc.getUndoManager() );
			}
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TRACKS );
		}
	}
	
	private void fillProbTableNames()
	{
		ggProbTableNames.removeAllItems();
		for( int i = 0; i < tables.size(); i++ ) {
			ggProbTableNames.addItem( tables.get( i ).getName() );
		}
	}

	protected SessionObject createNewSessionObject( SessionCollection scAll )
	{
		final String name = SessionCollection.createUniqueName( Session.SO_NAME_PTRN,
			new Object[] { new Integer( 1 ), Track.ATOM_NAME_PREFIX, "" }, scAll.getAll() );
		final SessionObject so = new Atom();
		so.setName( name );
		return so;
	}

	protected int getValueCount()
	{
		return Atom.PROB_ALL.length;
	}

	protected String getValueName( int idx )
	{
		return Atom.PROB_ALL[ idx ];
	}

	protected Class getValueClass( int idx )
	{
		return String.class;
	}

	protected Object getValue( SessionObject so, int idx )
	{
		final Probability prob = (Probability) ((Atom) so).probabilities.findByName( getValueName( idx ));
		if( prob != null ) {
			final ProbabilityTable probTab = prob.getTable();
			if( probTab != null ) {
				return probTab.getName();
			}
		}
		return null;
	}

	protected void setValue( SessionObject so, Object value, int idx )
	{
		if( value == null ) return;
		final ProbabilityTable	probTab	= (ProbabilityTable) tables.findByName( value.toString() );
		if( probTab == null ) return;
		
		final Atom	a		= (Atom) so;
		Probability	prob	= (Probability) a.probabilities.findByName( getValueName( idx ));
		
		if( prob == null ) {
			prob	= new Probability();
			prob.setName( getValueName( idx ));
			a.probabilities.add( this, prob );
		}
		prob.setTable( this, probTab );
	}

	protected boolean isValueEditable( SessionObject so, int idx )
	{
		return true;
	}
}