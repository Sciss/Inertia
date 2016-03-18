/*
 *  SessionCollectionTable.java
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
 *		13-Aug-05	created ; based upon Meloncillo's SessionGroupTable
 */

package de.sciss.inertia.gui;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;

import de.sciss.inertia.edit.EditAddSessionObject;
import de.sciss.inertia.edit.EditAddSessionObjects;
import de.sciss.inertia.edit.EditRemoveSessionObjects;
import de.sciss.inertia.edit.EditSetSessionObjects;
import de.sciss.inertia.session.SessionCollection;
import de.sciss.inertia.session.SessionObject;

import de.sciss.app.AbstractApplication;
import de.sciss.app.UndoManager;
import de.sciss.app.WindowHandler;

import de.sciss.gui.GUIUtil;
import de.sciss.gui.ModificationButton;

import de.sciss.util.LockManager;

public abstract class SessionCollectionTable
extends JPanel
{
	private SessionCollection		scAll			= null;
	private SessionCollection		scSel			= null;
	private int						doors			= 0;
	private LockManager				lm				= null;
	private UndoManager				undo			= null;

	private final Box				b;
	private final Font				fnt;

	public static final int			FLAG_IMMUTABLE	= 0x01;

	private JButton					ggPlus			= null;
	private JButton					ggMinus			= null;
	
	private final JTable			table;
	private final TableModel		tm;
	
	private final SessionCollection.Listener	scAllListener;
	private final SessionCollection.Listener	scSelListener;

	public SessionCollectionTable( int flags )
	{
		super( new BorderLayout() );
		
		final JScrollPane	scroll;
		final boolean		immutable	= (flags & FLAG_IMMUTABLE) != 0;
	
		tm			= new TableModel();
		table		= new JTable( tm );
		table.getTableHeader().setReorderingAllowed( false );
		table.setShowGrid( true );
		table.setGridColor( Color.lightGray );
		
		scroll		= new JScrollPane( table, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
											  JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED );
											  		
		b			= Box.createHorizontalBox();
		
		if( !immutable ) {
			ggPlus	= new ModificationButton( ModificationButton.SHAPE_PLUS );
			ggMinus	= new ModificationButton( ModificationButton.SHAPE_MINUS );
			ggPlus.setEnabled( false );
			ggMinus.setEnabled( false );
			ggPlus.addActionListener( new ActionListener() {
				public void actionPerformed( ActionEvent e )
				{
					if( (scAll == null) || ((lm != null) && !lm.attemptExclusive( doors, 250 ))) return;
					try {
						int row = Math.min( scAll.size(), table.getSelectedRow() + table.getSelectedRowCount() );
						if( row <= 0 ) row = scAll.size();
						
						final SessionObject		so		= createNewSessionObject( scAll );
						final java.util.List	coll	= new ArrayList( 1 );
						
						coll.add( so );
						if( undo != null ) {
							undo.addEdit( new EditAddSessionObject( table, scAll, so, row, lm, doors ));
						} else {
							scAll.add( table, row, so );
						}
						tm.fireTableRowsInserted( row, row );
						if( undo != null ) {
							undo.addEdit( new EditSetSessionObjects( table, scSel, coll, lm, doors ));
						} else {
							scSel.removeAll( table, scSel.getAll() );
							scSel.add( table, so );
						}
						table.setRowSelectionInterval( row, row );
					}
					finally {
						if( lm != null ) lm.releaseExclusive( doors );
					}
				}
			});
			ggMinus.addActionListener( new ActionListener() {
				public void actionPerformed( ActionEvent e )
				{
					if( (scAll == null) || ((lm != null) && !lm.attemptExclusive( doors, 250 ))) return;
					try {
						final int firstRow			= Math.max( 0, table.getSelectedRow() );
						final int lastRow			= Math.min( scAll.size(), firstRow + table.getSelectedRowCount() ) - 1;
						final java.util.List coll	= scSel.getAll();

						if( !scSel.isEmpty() ) {
							if( undo != null ) {
								undo.addEdit( new EditRemoveSessionObjects( table, scSel, coll, lm, doors ));
								undo.addEdit( new EditRemoveSessionObjects( table, scAll, coll, lm, doors ));
							} else {
								scSel.removeAll( table, coll );
								scAll.removeAll( table, coll );
							}
						}

						if( firstRow <= lastRow ) {
							tm.fireTableRowsDeleted( firstRow, lastRow );
						}
					}
					finally {
						if( lm != null ) lm.releaseExclusive( doors );
					}
				}
			});
			b.add( ggPlus );
			b.add( ggMinus );
		} // if( !immutable )
		b.add( Box.createHorizontalGlue() );

		table.getSelectionModel().addListSelectionListener( new ListSelectionListener() {
			public void valueChanged( ListSelectionEvent e )
			{
				if( e.getValueIsAdjusting() ) return;

				if( (scAll == null) || ((lm != null) && !lm.attemptExclusive( doors, 250 ))) return;
				try {
					final java.util.List	selected	= new ArrayList();
					final java.util.List	deselected	= new ArrayList();
					final int				max			= Math.min( e.getLastIndex() + 1, scAll.size() );
					SessionObject			so;
				
					for( int i = e.getFirstIndex(); i < max; i++ ) {
						so = scAll.get( i );
						if( table.isRowSelected( i ) && !scSel.contains( so )) {
							selected.add( so );
						} else if( !table.isRowSelected( i ) && scSel.contains( so )) {
							deselected.add( so );
						}
					}
					if( !selected.isEmpty() ) {
//						doc.getUndoManager().addEdit( new EditAddSessionObjects( table, scSel, selected, lm, doors ));
scSel.addAll( table, selected );
					}
					if( !deselected.isEmpty() ) {
//						doc.getUndoManager().addEdit( new EditRemoveSessionObjects( table, scSel, deselected, lm, doors ));
scSel.removeAll( table, deselected );
					}

					if( ggMinus != null ) ggMinus.setEnabled( !scSel.isEmpty() );
				}
				finally {
					if( lm != null ) lm.releaseExclusive( doors );
				}
			}
		});

		scAllListener = new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				if( e.getSource() == table ) return;
			
				switch( e.getModificationType() ) {
				case SessionCollection.Event.ACTION_ADDED:
					if( (scAll == null) || ((lm != null) && !lm.attemptShared( doors, 250 ))) {
						tm.fireTableStructureChanged();
						return;
					}
					try	{
						final java.util.List coll = calcRowSpans( e.getCollection() );
						Point p;
						for( int i = 0; i < coll.size(); i++ ) {
							p = (Point) coll.get( i );
							tm.fireTableRowsInserted( p.x, p.y );
						}
					}
					finally {
						if( lm != null ) lm.releaseShared( doors );
					}
					break;
				case SessionCollection.Event.ACTION_REMOVED:
					tm.fireTableStructureChanged();
					break;
					
				default:
					break;
				}
			}
			
			public void sessionObjectMapChanged( SessionCollection.Event e ) {}

			public void sessionObjectChanged( SessionCollection.Event e )
			{
				if( e.getSource() == table ) return;

				final java.util.List coll;
				Point p;
			
				switch( e.getModificationType() ) {
				case SessionObject.OWNER_RENAMED:
					if( (scAll == null) || ((lm != null) && !lm.attemptShared( doors, 250 ))) {
						tm.fireTableStructureChanged();
						return;
					}
					try	{
						coll = calcRowSpans( e.getCollection() );
						for( int i = 0; i < coll.size(); i++ ) {
							p = (Point) coll.get( i );
							tm.fireTableRowsUpdated( p.x, p.y );
						}
					}
					finally {
						if( lm != null ) lm.releaseShared( doors );
					}
					break;
					
				default:
					break;
				}
			}
		};

		scSelListener = new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				if( e.getSource() == table ) return;
			
				switch( e.getModificationType() ) {
				case SessionCollection.Event.ACTION_ADDED:
					if( (scAll == null) || ((lm != null) && !lm.attemptShared( doors, 250 ))) {
						tm.fireTableStructureChanged();
						return;
					}
					try {
						addToTableSelection( e.getCollection() );
					}
					finally {
						if( lm != null ) lm.releaseShared( doors );
					}
					break;
					
				case SessionCollection.Event.ACTION_REMOVED:
					tm.fireTableStructureChanged();	// XXX pretty much surely wrong
					break;
					
				default:
					break;
				}
			}

			public void sessionObjectMapChanged( SessionCollection.Event e ) {}
			public void sessionObjectChanged( SessionCollection.Event e ) {}
		};

		this.add( new JLabel( getClass().getName() ), BorderLayout.NORTH );
		this.add( scroll, BorderLayout.CENTER );
		this.add( b, BorderLayout.SOUTH );

		fnt = AbstractApplication.getApplication().getWindowHandler().getDefaultFont();
		GUIUtil.setDeepFont( this, fnt );
	}
	
	// sync : caller must have shared sync on doors
	private void addToTableSelection( java.util.List collSelected )
	{
		final java.util.List collSpans = calcRowSpans( collSelected );
		Point p;
		for( int i = 0; i < collSpans.size(); i++ ) {
			p = (Point) collSpans.get( i );
			table.addRowSelectionInterval( p.x, p.y );
		}
	}
	
	protected JTable getTable()
	{
		return table;
	}
	
	/**
	 *	@sync	MUST be called in the event thread
	 */
	public void setCollection( SessionCollection scAll, SessionCollection scSel,
							   LockManager lm, int doors, UndoManager undo )
	{
		if( this.scAll != null ) this.scAll.removeListener( scAllListener );
		if( this.scSel != null ) this.scSel.removeListener( scSelListener );
	
		this.scAll	= scAll;
		this.scSel	= scSel;
		this.lm		= lm;
		this.doors	= doors;
		this.undo	= undo;

		if( this.scAll != null ) this.scAll.addListener( scAllListener );
		if( this.scSel != null ) this.scSel.addListener( scSelListener );
		
		tm.fireTableStructureChanged();
		table.clearSelection();
		if( ggPlus != null ) ggPlus.setEnabled( scAll != null );

		if( (scAll == null) || ((lm != null) && !lm.attemptShared( doors, 250 ))) {
			tm.fireTableStructureChanged();
			return;
		}
		try	{
			addToTableSelection( scSel.getAll() );
		}
		finally {
			if( lm != null ) lm.releaseShared( doors );
		}
	}

	protected void addButton( JComponent c )
	{
		b.add( c, b.getComponentCount() - 1 );
		GUIUtil.setDeepFont( c, fnt );
	}

	protected abstract SessionObject createNewSessionObject( SessionCollection scAll );

	protected int getValueCount()
	{
		return 0;
	}

	protected String getValueName( int idx )
	{
		return null;
	}

	protected Class getValueClass( int idx )
	{
		return null;
	}

	protected Object getValue( SessionObject so, int idx )
	{
		return null;
	}

	protected void setValue( SessionObject so, Object value, int idx )
	{
	}

	protected boolean isValueEditable( SessionObject so, int idx )
	{
		return false;
	}

	private java.util.List calcRowSpans( java.util.List coll )
	{
		final java.util.List	rowSpans	= new ArrayList();
		final int[]				indices		= new int[ coll.size() ];
		int						min			= scAll.size();
		int						max			= -1;
		
		for( int i = 0; i < indices.length; i++ ) {
			indices[i] = scAll.indexOf( (SessionObject) coll.get( i ));
			if( indices[i] == -1 ) continue;
			if( indices[i] < min ) {
				min = indices[i];
			}
			if( indices[i] > max ) {
				max = indices[i];
			}
		}
findSpan: for( int j = min + 1; min <= max; j++ ) {
			for( int i = 0; i < indices.length; i++ ) {
				if( indices[i] == j ) {
					continue findSpan;
				}
			}
			rowSpans.add( new Point( min, j - 1 ));
			min = max + 1;
			for( int i = 0; i < indices.length; i++ ) {
				if( indices[i] > j && indices[i] < min ) {
					min = indices[i];
				}
			}
			j = min;
		}
		
		return rowSpans;
	}

// ----------------- internal classes -----------------

	private class TableModel
	extends AbstractTableModel
	{
		public String getColumnName( int col )
		{
			if( col == 0 ) {
				return "name";
			} else {
				return getValueName( col - 1 );
			}
		}
		
		public int getRowCount()
		{
			if( (scAll == null) || ((lm != null) && !lm.attemptShared( doors, 250 ))) return 0;
			try	{
				return scAll.size();
			}
			finally {
				if( lm != null ) lm.releaseShared( doors );
			}
		}
		
		public int getColumnCount()
		{
			return 1 + getValueCount();
		}
		
		public Object getValueAt( int row, int col )
		{
			if( (scAll == null) || ((lm != null) && !lm.attemptShared( doors, 250 ))) return null;
			try {
				if( row > scAll.size() ) return null;
				
				final SessionObject so = scAll.get( row );
		
				if( col == 0 ) {
					return so.getName();
				} else {
					return getValue( so, col - 1 );
				}
			}
			finally {
				if( lm != null ) lm.releaseShared( doors );
			}
		}

	    public Class getColumnClass( int col )
		{
			if( col == 0 ) {
				return String.class;
			} else {
				return getValueClass( col - 1 );
			}
		}
	
		public boolean isCellEditable( int row, int col )
		{
			if( (scAll == null) || ((lm != null) && !lm.attemptShared( doors, 250 ))) return false;
			try {
				if( row > scAll.size() ) return false;
				if( col == 0 ) return false;
				
				return isValueEditable( scAll.get( row ), col - 1 );
			}
			finally {
				if( lm != null ) lm.releaseShared( doors );
			}
		}
		
		public void setValueAt( Object value, int row, int col )
		{
			if( (scAll == null) || ((lm != null) && !lm.attemptExclusive( doors, 250 ))) return;
			try {
				if( row > scAll.size() ) return;

				if( col > 0 ) {
					setValue( scAll.get( row ), value, col - 1 );
				}
			}
			finally {
				if( lm != null ) lm.releaseExclusive( doors );
			}
		}
	}
}