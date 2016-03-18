//
//  MissingLinksDialog.java
//  Inertia
//
//  Created by Hanns Holger Rutz on 20.08.05.
//  Copyright 2005 __MyCompanyName__. All rights reserved.
//

package de.sciss.inertia.gui;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.table.*;
import javax.swing.undo.CompoundEdit;

import de.sciss.inertia.edit.BasicSyncCompoundEdit;
import de.sciss.inertia.edit.EditPutMapValue;
import de.sciss.inertia.session.*;

import de.sciss.app.AbstractApplication;

import de.sciss.gui.GUIUtil;
import de.sciss.gui.PathEvent;
import de.sciss.gui.PathField;
import de.sciss.gui.PathListener;

import de.sciss.util.LockManager;
import de.sciss.util.MapManager;

public class MissingLinksDialog
extends JDialog
{
	private final java.util.List	links	= new ArrayList();
	private final JTable			table;
	private final TableModel		tm;
	private final Session			doc;

	public MissingLinksDialog( Session doc )
	{
		super( doc.getFrame(), AbstractApplication.getApplication().getResourceString( "menuMissingLinks" ),
			   true );

		this.doc = doc;

		final Container		cp;
		final JButton		ggClose, ggCommit;
		final JScrollPane	scroll;
		final Box			b, b2;
		final JPanel		bottomPanel;
		final PathField		ggFolder;
	
		createLinks( doc );

		tm			= new TableModel();
		table		= new JTable( tm );
		table.setSelectionMode( ListSelectionModel.MULTIPLE_INTERVAL_SELECTION );
		table.setDefaultRenderer( String.class, new LinksCellRenderer() );
		
		scroll		= new JScrollPane( table, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
											  JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED );

		ggClose		= new JButton( new actionCloseClass() );
		ggCommit	= new JButton( new actionCommitClass() );
		ggFolder	= new PathField( PathField.TYPE_FOLDER, getResourceString( "labelReplacementFolder" ));
		ggFolder.addPathListener( new PathListener() {
			public void pathChanged( PathEvent e )
			{
				findReplacements( e.getPath() );
			}
		});
		
		b			= Box.createHorizontalBox();
		b.add( Box.createHorizontalGlue() );
		b.add( ggClose );
		b2			= Box.createHorizontalBox();
		b2.add( Box.createHorizontalGlue() );
		b2.add( ggCommit );
		
		bottomPanel	= new JPanel( new SpringLayout() );
		bottomPanel.add( new JLabel( getResourceString( "labelReplacementFolder" )));
		bottomPanel.add( ggFolder );
		bottomPanel.add( b2 );
		bottomPanel.add( new JSeparator() );
		bottomPanel.add( b );

		GUIUtil.makeCompactSpringGrid( bottomPanel, 5, 1, 4, 2, 4, 2 );	// #row #col initx inity padx pady
		
		cp			= getContentPane();
		cp.setLayout( new BorderLayout() );
		cp.add( new JLabel( getResourceString( "labelListOfMissingFiles" )), BorderLayout.NORTH );
		cp.add( bottomPanel, BorderLayout.SOUTH );
		cp.add( scroll, BorderLayout.CENTER );
		
		GUIUtil.setDeepFont( this, null );
		
		pack();
		setVisible( true ); // show();
	}
	
	private void findReplacements( File folder )
	{
		MissingLink ml;
		File		rplc;
	
		for( int i = 0; i < links.size(); i++ ) {
			ml		= (MissingLink) links.get( i );
			rplc	= new File( folder, ml.missing.getName() );
			if( rplc.isFile() ) {
				ml.replacement	= rplc;
			} else {
				ml.replacement	= null;
			}
		}
		
		tm.fireTableStructureChanged();
	}
	
	private void createLinks( Session doc )
	{
		Track				t;
		Molecule			molec;
		Atom				a;
		MapManager			map;
		String				key;
		MapManager.Context	con;
		Object				value;
		String				ownerName;
	
		if( !doc.bird.attemptShared( Session.DOOR_TRACKS | Session.DOOR_MOLECULES, 1000 )) return;
		try {
			for( int i = 0; i < doc.tracks.size(); i++ ) {
				t			= (Track) doc.tracks.get( i );
				for( int j = 0; j < t.molecules.size(); j++ ) {
					molec	= (Molecule) t.molecules.get( j );
					for( int k = 0; k < molec.atoms.size(); k++ ) {
						a		= (Atom) molec.atoms.get( k );
						map		= a.getMap();
						for( Iterator iter = map.keySet(
							 MapManager.Context.ALL_INCLUSIVE, MapManager.Context.NONE_EXCLUSIVE ).iterator();
							 iter.hasNext(); ) {
						
							key		= (String) iter.next();
							con		= map.getContext( key );
							value	= map.getValue( key );
							if( (value != null) && (value instanceof File) && (con != null) &&
								(con.type == MapManager.Context.TYPE_FILE)
							&&
								
								(((con.typeConstraints != null) && (con.typeConstraints instanceof Integer) &&
									((((Integer) con.typeConstraints).intValue() & PathField.TYPE_BASICMASK) != PathField.TYPE_OUTPUTFILE))
							||	(con.typeConstraints == null))
							
							&&	!((File) value).isFile() ) {
								
								ownerName	= t.getName() + '\u2192' + molec.getName() + '\u2192' + a.getName();
								links.add( new MissingLink( (File) value, ownerName, a, key ));
							}
						}
					}
				}
			}
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TRACKS | Session.DOOR_MOLECULES );
		}
	}

	private static String getResourceString( String key )
	{
		return AbstractApplication.getApplication().getResourceString( key );
	}
	
// ----------------- internal classes -----------------

	private class actionCloseClass
	extends AbstractAction
	{
		private actionCloseClass()
		{
			super( getResourceString( "buttonClose" ));
		}
		
		public void actionPerformed( ActionEvent e )
		{
			setVisible( false );
			dispose();
		}
	}
	
	private class actionCommitClass
	extends AbstractAction
	{
		private actionCommitClass()
		{
			super( getResourceString( "buttonCommitChanges" ));
		}
		
		public void actionPerformed( ActionEvent e )
		{
			MissingLink		ml;
			CompoundEdit	ce	= new BasicSyncCompoundEdit( doc.bird, Session.DOOR_MOLECULES, getTitle() );
		
			if( !doc.bird.attemptExclusive( Session.DOOR_MOLECULES, 1000 )) return;
			try {
				for( int i = links.size() - 1; i >= 0; i-- ) {
					if( table.isRowSelected( i )) {
						ml = (MissingLink) links.get( i );
						if( ml.replacement != null ) {
							links.remove( i );
							ce.addEdit( new EditPutMapValue( this, doc.bird, Session.DOOR_MOLECULES, ml.owner.getMap(), ml.key, ml.replacement ));
//							ml.owner.getMap().putValue( this, ml.key, ml.replacement );
						}
					}
				}
				tm.fireTableStructureChanged();
			}
			finally {
				doc.bird.releaseExclusive( Session.DOOR_MOLECULES );
			}
			
			ce.end();
			if( ce.isSignificant() ) doc.getUndoManager().addEdit( ce );
		}
	}
	
	private class TableModel
	extends AbstractTableModel
	{
		public String getColumnName( int col )
		{
			switch( col ) {
			case 0:
				return "missing";
			case 1:
				return "object";
			case 2:
				return "key";
			case 3:
				return "replacement";
			default:
				assert false : col;
				return null;
			}
		}
		
		public int getRowCount()
		{
			return links.size();
		}
		
		public int getColumnCount()
		{
			return 4;
		}
		
		public Object getValueAt( int row, int col )
		{
			if( row > links.size() ) return null;
			
			final MissingLink ml = (MissingLink) links.get( row );
	
			switch( col ) {
			case 0:
				return ml.missing.getAbsolutePath();
			case 1:
				return ml.ownerName;
			case 2:
				return ml.key;
			case 3:
				return ml.replacement == null ? null : ml.replacement.getAbsolutePath();
			default:
				assert false: col;
				return null;
			}
		}

	    public Class getColumnClass( int col )
		{
			return String.class;
		}
	
		public boolean isCellEditable( int row, int col )
		{
			return false;
		}
		
		public void setValueAt( Object value, int row, int col )
		{
			// not editable
		}
	}

	private static final Color colrRplc		= new Color( 0xC0, 0xFF, 0xC0 );
	private static final Color colrRplcSel	= new Color( 0x30, 0x60, 0x30 );
	private static final Color colrNorm		= new Color( 0xF8, 0xF8, 0xF8 );
	private static final Color colrNormSel	= new Color( 0x50, 0x50, 0x50 );

	private class LinksCellRenderer
	extends JLabel
	implements TableCellRenderer
	{
		private LinksCellRenderer()
		{
			super();
			setOpaque( true );
			GUIUtil.setDeepFont( this, null );
		}

		public Component getTableCellRendererComponent( JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column )
		{
			final boolean hasRplc = (row < links.size()) && ((MissingLink) links.get( row )).replacement != null;
			setBackground( hasRplc ? (isSelected ? colrRplcSel : colrRplc) : (isSelected ? colrNormSel : colrNorm) );
			setForeground( isSelected ? Color.white : Color.black );
			setText( value == null ? "" : value.toString() );
			return this;
		}
	}

	private static class MissingLink
	implements Comparable
	{
		private final File			missing;
		private final String		ownerName;
		private final SessionObject	owner;
		private final String		key;
		private File				replacement;
		
		private MissingLink( File missing, String ownerName, SessionObject owner, String key )
		{
			this.missing		= missing;
			this.ownerName		= ownerName;
			this.owner			= owner;
			this.key			= key;
			this.replacement	= null;
		}

		public int compareTo( Object o )
		{
			return missing.compareTo( o );
		}
	}
}