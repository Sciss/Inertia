//
//  SyncedMenuAction.java
//  Eisenkraut
//
//  Created by Hanns Holger Rutz on 24.09.05.
//  Copyright 2005 __MyCompanyName__. All rights reserved.
//

package de.sciss.inertia.gui;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;

import de.sciss.gui.MenuAction;

/**
 *	for each SyncedMenuAction a
 *	hash map entry exists in the MenuFactory's
 *	syncedItems object. The value of
 *	that entry is a vector with a copy of the same
 *	menu item for each frame's menu. when the
 *	action of the SyncedMenuAction is performed,
 *	the source's isSelected() is queried and
 *	all other frames' synchronized menu items
 *	(i.e. JCheckBoxMenuItems) are set to the
 *	same state. Used e.g. for the Snap-to-Objects item
 */
public class SyncedMenuAction
extends MenuAction
{
	private static final long serialVersionUID = 0x050923L;
	
	private final java.util.List	syncs		= new ArrayList();	// element : JMenuItem
	private final java.util.Map		mapHosts	= new HashMap();	// maps MenuHosts to JMenuItems

	public SyncedMenuAction( String text, KeyStroke shortcut )
	{
		super( text, shortcut );
	}
	
	public void setProtoType( JMenuItem pMenuItem )
	{
		if( !syncs.isEmpty() ) throw new IllegalStateException();
		syncs.add( pMenuItem );
	}

	public void actionPerformed( ActionEvent e )
	{
		boolean state   = ((AbstractButton) e.getSource()).isSelected();

		setSelected( state );
	}
	
	public void setSelected( boolean state )
	{
		AbstractButton b;

		for( int i = 0; i < syncs.size(); i++ ) {
			b = (AbstractButton) syncs.get( i );
			b.setSelected( state );
		}
	}
	
	public void addSync( MenuHost host, JMenuItem mi )
	{
		syncs.add( mi );
		mapHosts.put( host, mi );
	}
	
	public void removeSync( MenuHost host )
	{
		JMenuItem mi = (JMenuItem) mapHosts.remove( host );
		syncs.remove( mi );
	}
	
	public void removeAll()
	{
		JMenuItem mi;
	
		for( int i = 0; i < syncs.size(); i++ ) {
			mi = (JMenuItem) syncs.get( i );
			mi.getParent().remove( mi );
		}
		
		syncs.clear();
		mapHosts.clear();
	}
}
