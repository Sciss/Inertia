/*
 *  SessionObjectTable.java
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
 *		09-Aug-05	created from de.sciss.meloncillo.session.SessionObjectTable
 *					; allows dynamic setting of the document
 */

package de.sciss.inertia.session;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.swing.AbstractCellEditor;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.undo.CompoundEdit;

import de.sciss.inertia.edit.BasicSyncCompoundEdit;
import de.sciss.inertia.edit.EditPutMapValue;

import de.sciss.app.AbstractApplication;
import de.sciss.app.Document;
import de.sciss.app.DynamicAncestorAdapter;
import de.sciss.app.DynamicListening;
import de.sciss.app.UndoManager;

import de.sciss.gui.GUIUtil;
import de.sciss.gui.NumberEvent;
import de.sciss.gui.NumberField;
import de.sciss.gui.NumberListener;
import de.sciss.gui.PathEvent;
import de.sciss.gui.PathField;
import de.sciss.gui.PathListener;
import de.sciss.gui.StringItem;

import de.sciss.util.LockManager;
import de.sciss.util.MapManager;
import de.sciss.util.NumberSpace;

/**
 *	This class is used to display a session object's
 *	map entries in the observer palette. it is a <code>JTable</code>
 *	with two columns for the key name and the corresponding value.
 *	When plug-ins are activated or deactivated or when the
 *	map changes, the table is automatically updated. The
 *	map manager's contexts are scanned for items that should be
 *	displayed in the palette, and for plug-in specific entries are
 *	matched with the currently active plug-ins.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.1, 13-Aug-05
 */
public class SessionObjectTable
extends JTable
implements DynamicListening
{
	private final Object				sync		= new Object();
	private final java.util.List		keys		= new ArrayList();
	private final java.util.Map			contexts	= new HashMap();
	private final java.util.List		collObjects	= new ArrayList();
	private final AbstractTableModel	model;
	private final TableCellEditor		editor;
	
	private UndoManager					undo		= null;
	private LockManager					lm			= null;
	private int							doors;

	private final MapManager.Listener	soListener, plugListener;
	
	private static final String[] columnNames = new String[] { "key", "value" };	// not used
	
	public SessionObjectTable()
	{
		super();

		model	= new Model();
		
		setModel( model );
		setRowSelectionAllowed( false );
		TableColumn	tc;
		tc		= getColumnModel().getColumn( 0 );
		tc.setMaxWidth( 92 ); // XXX
		tc		= getColumnModel().getColumn( 1 );
		tc.setCellRenderer( new Renderer() );
		editor	= new Renderer();
		tc.setCellEditor( editor );
//		setRowMargin( 4 );
		setRowHeight( 24 );	// XXX
		setBackground( null );
		setShowGrid( false );

		// ------- listeners -------
		soListener = new MapManager.Listener() {
			public void mapChanged( MapManager.Event e )
			{
				if( e.getSource() == sync ) return;
			
				Iterator	iter;
				int			row;
				
				synchronized( sync ) {
					iter = e.getPropertyNames().iterator();
					
					while( iter.hasNext() ) {
						row = keys.indexOf( iter.next() );
						if( row >= 0 ) {
							model.fireTableCellUpdated( row, 1 );
						}
					}
				} // synchronized( sync )
			}
				
			public void mapOwnerModified( MapManager.Event e ) {}
		};
		
		plugListener = new MapManager.Listener() {
			public void mapChanged( MapManager.Event e )
			{
				if( (lm != null) && !lm.attemptShared( doors, 250 )) return;
				try {
					synchronized( sync ) {
						unregisterAll();
						updateKeysAndContexts();	// calls model.fireTableDataChanged();
						registerAll();
					} // synchronized( sync )
				} finally {
					if( lm != null ) lm.releaseShared( doors );
				}
			}
				
			public void mapOwnerModified( MapManager.Event e ) {}
		};

        new DynamicAncestorAdapter( this ).addTo( this );
	}
	
// ---------------- DynamicListening interface ---------------- 

    public void startListening()
    {
// INERTIA
//		root.plugInManager.addListener( plugListener );
	
		if( (lm != null) && !lm.attemptShared( doors, 250 )) return;
		try {
			synchronized( sync ) {
				updateKeysAndContexts(); // calls model.fireTableDataChanged();
				registerAll();
			}
		}
		finally {
			if( lm != null ) lm.releaseShared( doors );
		}
    }

    public void stopListening()
    {
		if( (lm != null) && !lm.attemptShared( doors, 250 )) return;
		try {
			synchronized( sync ) {
				unregisterAll();
			}
		}
		finally {
			if( lm != null ) lm.releaseShared( doors );
		}
    }
	
	// sync: to be called with sync on doors and sync
	private void registerAll()
	{
		SessionObject so;
		
		for( int i = 0; i < collObjects.size(); i++ ) {
			so = (SessionObject) collObjects.get( i );
			so.getMap().addListener( soListener );
		}
	}

	// sync: to be called with sync on doors and sync
	private void unregisterAll()
	{
		SessionObject so;
		
		for( int i = 0; i < collObjects.size(); i++ ) {
			so = (SessionObject) collObjects.get( i );
			so.getMap().removeListener( soListener );
		}
	}

	public void setObjects( java.util.List collObjects, UndoManager undo, LockManager lm, int doors )
	{
		this.undo	= undo;
		this.lm		= lm;
		this.doors	= doors;

		if( lm != null ) lm.waitShared( doors );
		try {
			synchronized( sync ) {
				unregisterAll();
				this.collObjects.clear();
				this.collObjects.addAll( collObjects );
				updateKeysAndContexts();	// calls model.fireTableDataChanged();
				registerAll();
			} // synchronized( sync )
		} finally {
			if( lm != null ) lm.releaseShared( doors );
		}
	}
	
	// sync: to be called with sync on 'sync' and on doors
	// this method invokes model.fireTableDataChanged()
	private void updateKeysAndContexts()
	{
		MapManager			map;
		Object				o;
		MapManager.Context	c;
		SessionObject		so;

		if( isEditing() ) editor.cancelCellEditing();

		keys.clear();
		contexts.clear();
		if( !collObjects.isEmpty() ) {
			so	= (SessionObject) collObjects.get( 0 );
			map	= so.getMap();
			keys.addAll( map.keySet( MapManager.Context.FLAG_OBSERVER_DISPLAY,
									 MapManager.Context.NONE_EXCLUSIVE ));

			for( int i = keys.size() - 1; i >= 0 ; i-- ) {
				o	= keys.get( i );
				c	= map.getContext( o.toString() );
				contexts.put( o, c );
				// remove fields that belong to inactive plug-ins
// INERTIA
//				if( (c.dynamic != null) && 
//					(root.plugInManager.getValue( c.dynamic.toString() ) == null) ) {
//					keys.remove( o );
//				}
if( c.dynamic != null ) keys.remove( o );
			}
		}
		for( int i = 1; i < collObjects.size(); i++ ) {
			so	= (SessionObject) collObjects.get( i );	// only common fields are displayed
			map	= so.getMap();
			keys.retainAll( map.keySet( MapManager.Context.FLAG_OBSERVER_DISPLAY,
										MapManager.Context.NONE_EXCLUSIVE ));
		}
		model.fireTableDataChanged();
	}

	private class Model
	extends AbstractTableModel
	{
		public String getColumnName( int col )
		{
			return columnNames[ col ];
		}
		
		public int getRowCount()
		{
			synchronized( sync ) {
				return keys.size();
			}
		}
		
		public int getColumnCount()
		{
			return 2;
		}
		
		public Object getValueAt( int row, int col )
		{
			if( (lm != null) && !lm.attemptShared( doors, 250 )) return null;
			try {
				synchronized( sync ) {
					if( row >= keys.size() ) return null;

					switch( col ) {
					case 0:
						String resKey = ((MapManager.Context) contexts.get( keys.get( row ))).label;
						if( resKey != null ) {
							// plug-ins will not store resKeys but human readable text
							return( AbstractApplication.getApplication().getResourceString( resKey, resKey ));
						}
						return( keys.get( row ).toString() );
						
					case 1:
						return getCommonValue( row );
						
					default:
						return null;
					}
				}
			}
			finally {
				if( lm != null ) lm.releaseShared( doors );
			}
		}

	    public Class getColumnClass( int col )
		{
			switch( col ) {
			case 0:
				return String.class;
			case 1:
				return MapManager.Context.class;
			default:
				return Object.class;
			}
		}
	
		public boolean isCellEditable( int row, int col )
		{
			return( col == 1 );
		}
		
		// sync: caller must sync on door and sync
		private Object getCommonValue( int row )
		{
			Object val, val2;
			int i;
		
			val = ((SessionObject) collObjects.get( 0 )).getMap().getValue( keys.get( row ).toString() );
			if( val == null ) return null;
			if( collObjects.size() > 1 ) {
				for( i = 1; i < collObjects.size(); i++ ) {
					val2 = ((SessionObject) collObjects.get( i )).getMap().getValue( keys.get( row ).toString() );
					if( val2 == null || !val2.equals( val )) {
						return null;
					}
				}
			}
			return val;
		}

		public void setValueAt( Object value, int row, int col )
		{
			if( (col != 1) || (value == null) ) return;

			if( (lm != null) && !lm.attemptExclusive( doors, 250 )) return;
			try {
				synchronized( sync ) {
					if( row >= keys.size() ) return;

					final String		key		= keys.get( row ).toString();
					final CompoundEdit	edit	= new BasicSyncCompoundEdit( lm, doors );
					SessionObject		so;
					MapManager			map;
					boolean				addEdit	= false;

					for( int i = 0; i < collObjects.size(); i++ ) {
						so	= (SessionObject) collObjects.get( i );
						map = so.getMap();
						if( map.containsKey( key )) {
							if( undo != null ) {
								edit.addEdit( new EditPutMapValue( sync, lm, doors, map, key, value ));
								addEdit = true;
							} else {
								map.putValue( sync, key, value );
							}
						}
					}

					if( addEdit ) {	// implied undo != null
						edit.end();
						undo.addEdit( edit );
					}
				}
			}
			finally {
				if( lm != null ) lm.releaseExclusive( doors );
			}
		}
	} // class Model
	
	private class Renderer
	extends AbstractCellEditor
	implements TableCellRenderer, TableCellEditor, NumberListener, PathListener, ActionListener
	{
		private NumberField	ggNumberField	= null;
		private JTextField	ggTextField		= null;
		private Map			mapPathFields	= new HashMap();
		private PathField	ggOutputPath	= null;
		private JLabel		ggLabel			= null;
		private JCheckBox	ggCheckBox		= null;
		private JComboBox	ggComboBox		= null;
		// they are placed on a panel to avoid that there size is
		// maximized and looks ugly
		private JPanel		panelComboBox, panelTextField;
	
		private Object		editorValue		= null;
	
		private Renderer()
		{
			super();
		}

		public Object getCellEditorValue()
		{
			return editorValue;
		}

		private void prepareNumberField()
		{
			if( ggNumberField == null ) {
				ggNumberField = new NumberField();
				ggNumberField.setSpace( NumberSpace.genericDoubleSpace );
				ggNumberField.addListener( this );
				GUIUtil.setDeepFont( ggNumberField, null );
			}
		}
		
		public Component getTableCellEditorComponent( JTable table, Object value, boolean isSelected, int row, int col )
		{
			editorValue = value;
			return getComponent( value, row, col, true );
		}

		public Component getTableCellRendererComponent( JTable table, Object value,
														boolean isSelected, boolean hasFocus,
														int row, int col )
		{
			Component c = getComponent( value, row, col, false );
			return c;
		}
		
		private Component getComponent( Object value, int row, int col, boolean isEditor )
		{
			NumberSpace ns;

			if( (lm != null) && !lm.attemptShared( doors, 250 )) return null;
			try	{
				synchronized( sync ) {
					if( row >= keys.size() || col != 1 ) return null;

					MapManager.Context c = (MapManager.Context) contexts.get( keys.get( row ));
					switch( c.type ) {
					case MapManager.Context.TYPE_INTEGER:
					case MapManager.Context.TYPE_LONG:
					case MapManager.Context.TYPE_FLOAT:
					case MapManager.Context.TYPE_DOUBLE:
						prepareNumberField();
						if( (c.typeConstraints != null) && (c.typeConstraints instanceof NumberSpace) ) {
							ns = (NumberSpace) c.typeConstraints;
						} else {
							ns = (c.type == MapManager.Context.TYPE_INTEGER) || (c.type == MapManager.Context.TYPE_LONG) ?
								 NumberSpace.genericIntSpace : NumberSpace.genericDoubleSpace;
						}
						if( !ns.equals( ggNumberField.getSpace() )) ggNumberField.setSpace( ns );

						ggNumberField.setNumber( (value == null) || !(value instanceof Number) ?
												 new Double( Double.NaN ) : (Number) value );
						return ggNumberField;

					case MapManager.Context.TYPE_BOOLEAN:
						if( ggCheckBox == null ) {
							ggCheckBox = new JCheckBox();
							ggCheckBox.setFocusable( false );
							GUIUtil.setDeepFont( ggCheckBox, null );
						} else {
							ggCheckBox.removeActionListener( this );
						}
						ggCheckBox.setSelected( (value == null) || !(value instanceof Boolean) ?
											    false : ((Boolean) value).booleanValue() );
						ggCheckBox.addActionListener( this );
						return ggCheckBox;
	
					case MapManager.Context.TYPE_STRING:
						if( c.typeConstraints != null && (c.typeConstraints instanceof StringItem[]) ) {
							if( ggComboBox == null ) {
								ggComboBox		= new JComboBox();
								GUIUtil.setDeepFont( ggComboBox, null );
								panelComboBox	= new JPanel( new BorderLayout() );
								panelComboBox.add( ggComboBox, BorderLayout.WEST );
								ggComboBox.setFocusable( false );	// because on Aqua it looks truncated
							} else {
								ggComboBox.removeActionListener( this );
								ggComboBox.removeAllItems();
							}
							StringItem[] items = (StringItem[]) c.typeConstraints;
							int idx = -1;
							for( int i = 0; i < items.length; i++ ) {
								ggComboBox.addItem( items[ i ]);
								if( items[ i ].getKey().equals( value )) idx = i;
							}
							ggComboBox.setSelectedIndex( idx );
							ggComboBox.addActionListener( this );
							return panelComboBox;
						} else {
							if( ggTextField == null ) {
								ggTextField		= new JTextField();
								panelTextField	= new JPanel( new BorderLayout() );
								panelTextField.add( ggTextField, BorderLayout.NORTH );
								GUIUtil.setDeepFont( ggTextField, null );
								ggTextField.addActionListener( this );
							}
							ggTextField.setText( (value == null) ? "" : value.toString() );
							return panelTextField;
						}
	
					case MapManager.Context.TYPE_FILE:
						Integer		type;
						PathField	ggPath;
						if( c.typeConstraints != null && (c.typeConstraints instanceof Integer) ) {
							type = (Integer) c.typeConstraints;
						} else {
							type = new Integer( PathField.TYPE_INPUTFILE );
						}
						ggPath = (PathField) mapPathFields.get( type );
						if( ggPath == null ) {
							ggPath = new PathField( type.intValue(), c.label );
							ggPath.addPathListener( this );
							GUIUtil.setDeepFont( ggPath, null );
							mapPathFields.put( type, ggPath );
						}
						ggPath.setPath( (value == null) || !(value instanceof File) ?
										new File( "" ) : (File) value );
						return ggPath;
						
					default:
						if( ggLabel == null ) {
							ggLabel = new JLabel();
							GUIUtil.setDeepFont( ggLabel, null );
						}
						ggLabel.setText( keys.get( row ).toString() );
						return ggLabel;
					}
				} // synchronized( sync )
			}
			finally {
				if( lm != null ) lm.releaseShared( doors );
			}
		}

		// from text fields --> editorValue instanceof String
		// from checkboxes --> editorValue instanceof Boolean
		// from comboboxes --> editorValue instanceof StringItem
		public void actionPerformed( ActionEvent e )
		{
			if( e.getSource() == ggTextField ) {
				editorValue = ggTextField.getText();
			} else if( e.getSource() == ggCheckBox ) {
				editorValue = new Boolean( ggCheckBox.isSelected() );
			} else if( e.getSource() == ggComboBox ) {
				Object o	= ggComboBox.getSelectedItem();
				if( (o != null) && (o instanceof StringItem) ) {
					editorValue = ((StringItem) o).getKey();
				} else {
					editorValue = null;
				}
			}
			fireEditingStopped();
		}

		// from number fields --> editorValue instanceof Number
		public void numberChanged( NumberEvent e )
		{
			editorValue = e.getNumber();
			fireEditingStopped();
		}

		// from path fields --> editorValue instanceof File
		public void pathChanged( PathEvent e )
		{
			editorValue = e.getPath();
			fireEditingStopped();
		}
	} // class Renderer
}