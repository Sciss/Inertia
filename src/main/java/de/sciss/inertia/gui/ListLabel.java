//
//  RenderableLabel.java
//  Inertia
//
//  Created by SeaM on 30.09.05.
//  Copyright 2005 __MyCompanyName__. All rights reserved.
//

package de.sciss.inertia.gui;

import java.awt.*;
import java.util.*;
import javax.swing.*;

public class ListLabel
extends JLabel
implements ListCellRenderer
{
	private final java.util.List	collIcons	= new ArrayList();
	private final java.util.List	collTexts	= new ArrayList();
	
	private int selectedIndex	= -1;

	public ListLabel()
	{
		setOpaque( true );
		setHorizontalAlignment( CENTER );
		setVerticalAlignment( CENTER );
	}
	
	public void addItem( Icon icon, String text )
	{
		collIcons.add( icon );
		collTexts.add( text );
		if( selectedIndex == -1 ) setSelectedIndex( 0 );
	}
	
	public void setSelectedIndex( int selectedIndex )
	{
		if( selectedIndex < collIcons.size() ) {
			setIcon( (Icon) collIcons.get( selectedIndex ));
			setText( (String) collTexts.get( selectedIndex ));
		} else {
			setIcon( null );
			setText( null );
		}
		this.selectedIndex	= selectedIndex;
	}
	
	public int getSelectedIndex()
	{
		return selectedIndex;
	}

	public Component getListCellRendererComponent( JList list, Object value, int index,
												   boolean isSelected, boolean cellHasFocus )
	{
		//Get the selected index. (The index param isn't
		//always valid, so just use the value.)
		int selectedIndex = ((Integer) value).intValue();

		if( isSelected ) {
			setBackground( list.getSelectionBackground() );
			setForeground( list.getSelectionForeground() );
		} else {
			setBackground( list.getBackground() );
			setForeground( list.getForeground() );
		}
		setSelectedIndex( selectedIndex );
		setFont( list.getFont() );
		return this;
	}
}
