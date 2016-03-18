/*
 *  UnitLabel.java
 *  de.sciss.gui package
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
 *		16-Sep-05	created
 */

package de.sciss.gui;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.beans.*;
import java.util.*;
import javax.swing.*;

/**
 *  @version	0.25, 17-Sep-05
 */
public class UnitLabel
extends JLabel
implements Icon, PropertyChangeListener
{
	private static final int[]		polyX		= { 0, 4, 8 };
	private static final int[]		polyY		= { 0, 4, 0 };
	
	private static final Color		colrTri		= new Color( 0x00, 0x00, 0x00, 0xB0 );
	private static final Color		colrTriD	= new Color( 0x00, 0x00, 0x00, 0x55 );

	private final Color				colrLab		= null;
	private final Color				colrLabD	= new Color( 0x00, 0x00, 0x00, 0x7F );

	private final UnitLabel			enc_this	= this;
	private final JPopupMenu		pop			= new JPopupMenu();
	private final ButtonGroup		bg			= new ButtonGroup();

	private final java.util.List	units		= new ArrayList();

	private	ActionListener			al			= null;
	private int						selectedIdx	= -1;

	public UnitLabel()
	{
		super();
		setHorizontalTextPosition( LEFT );
//		setVerticalTextPosition( BOTTOM );

		setFocusable( true );
		addMouseListener( new MouseAdapter() {
			public void mousePressed( MouseEvent e )
			{
				if( isEnabled() && units.size() > 1 ) {
					requestFocus();
					pop.show( enc_this, 0, enc_this.getHeight() );
				}
			}
		});

		this.addPropertyChangeListener( "font", this );
		this.addPropertyChangeListener( "enabled", this );
		this.addPropertyChangeListener( "insets", this );
	}
	
	public int getSelectedIndex()
	{
		return selectedIdx;
	}

	public void setSelectedIndex( int idx )
	{
		this.selectedIdx = idx;	// so we won't fire
		if( idx >= 0 && idx < units.size() ) {
			((UnitAction) units.get( idx)).setLabel();
		}
	}
	
	public void addUnit( String name )
	{
		addUnit( new UnitAction( name ));
	}
	
	public void addUnit( Icon icon )
	{
		addUnit( new UnitAction( icon ));
	}
	
	public void addUnit( String name, Icon icon )
	{
		addUnit( new UnitAction( name, icon ));
	}
	
	private void addUnit( UnitAction a )
	{
		final JCheckBoxMenuItem cbmi = new JCheckBoxMenuItem( a );
		bg.add( cbmi );
		pop.add( cbmi );
		units.add( a );
		if( units.size() == 1 ) {
			a.setLabel();
			cbmi.setSelected( true );
		}
		updatePreferredSize();
	}

	private void updatePreferredSize()
	{
		final Font			fnt		= getFont();
		final FontMetrics	fntMetr	= getFontMetrics( fnt );
		UnitAction			ua;
		Dimension			d;
		int					w		= 4;
		int					h		= 4;
		final Insets		in		= getInsets();
	
		for( int i = 0; i < units.size(); i++ ) {
			ua	= (UnitAction) units.get( i );
			d	= ua.getPreferredSize( fntMetr );
			w	= Math.max( w, d.width );
			h	= Math.max( h, d.height );
		}
		
		d	= new Dimension( w + in.left + in.right, h + in.top + in.bottom );
		setMinimumSize( d );
		setPreferredSize( d );
	}

//	private void checkPopup( MouseEvent e )
//	{
//		if( e.isPopupTrigger() && isEnabled() ) {
//			pop.show( this, 0, getHeight() );
//		}
//	}
	
// ------------------- PropertyChangeListener interface -------------------

	/**
	 *  Forwards <code>Font</code> property
	 *  changes to the child gadgets
	 */
	public void propertyChange( PropertyChangeEvent e )
	{
		if( e.getPropertyName().equals( "font" )) {
			final Font			fnt		= this.getFont();
			final MenuElement[]	items	= pop.getSubElements();
			for( int i = 0; i < items.length; i++ ) {
				items[ i ].getComponent().setFont( fnt );
			}
			updatePreferredSize();
			
		} else if( e.getPropertyName().equals( "enabled" )) {
			setForeground( isEnabled() ? colrLab : colrLabD );

		} else if( e.getPropertyName().equals( "insets" )) {
			updatePreferredSize();
		}
	}
	
	private void fireUnitChanged()
	{
        final ActionListener l = al;
		if( l != null ) {
			l.actionPerformed( new ActionEvent( this, ActionEvent.ACTION_PERFORMED, getText() ));
		}
	}

     public synchronized void addActionListener( ActionListener l )
	 {
		al = AWTEventMulticaster.add( al, l);
     }
	 
     public synchronized void removeActionListener( ActionListener l )
	 {
		al = AWTEventMulticaster.remove( al, l);
     }

// ----------------- Icon interface -----------------
	
	public int getIconWidth()
	{
		return units.size() > 1 ? 9 : 0;
	}
	
	public int getIconHeight()
	{
		return units.size() > 1 ? 5 : 0;
	}
	
	public void paintIcon( Component c, Graphics g, int x, int y )
	{
		if( units.size() < 2 ) return;
	
		final Graphics2D		g2		= (Graphics2D) g;
		final AffineTransform	atOrig	= g2.getTransform();

		g2.translate( x, y );
		g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
		g2.setColor( isEnabled() ? colrTri : colrTriD );
		g2.fillPolygon( polyX, polyY, 3 );
		
		g2.setTransform( atOrig );
	}
	
// ----------------- internal classes -----------------
	
	private class UnitAction
	extends AbstractAction
	{
		private final String	name;
		private final Icon		icon;
	
		private UnitAction( String name )
		{
			super( name );
			this.name	= name;
			this.icon	= new CompoundIcon( null, enc_this, enc_this.getIconTextGap() );
		}

		private UnitAction( Icon icon )
		{
			super();
			putValue( SMALL_ICON, icon );
			this.name	= null;
			this.icon	= new CompoundIcon( icon, enc_this, enc_this.getIconTextGap() );
		}

		private UnitAction( String name, Icon icon )
		{
			super( name, icon );
			this.name	= name;
			this.icon	= new CompoundIcon( icon, enc_this, enc_this.getIconTextGap() );
		}
	
		public void actionPerformed( ActionEvent e )
		{
			setLabel();
		}
		
		private void setLabel()
		{
			enc_this.setText( name );
			enc_this.setIcon( icon );
			final int newIndex	= enc_this.units.indexOf( this );
			if( newIndex != enc_this.selectedIdx ) {
				selectedIdx = newIndex;
				fireUnitChanged();
			}
		}
		
		private Dimension getPreferredSize( FontMetrics fntMetr )
		{
			int w, h;
			
			if( name != null ) {
				w	= fntMetr.stringWidth( name ) + enc_this.getIconTextGap();
				h	= fntMetr.getHeight();
			} else {
				w	= 0;
				h	= 0;
			}
			
			return new Dimension( w + icon.getIconWidth(), Math.max( h, icon.getIconHeight() ));
		}
	}
	
	private static class CompoundIcon
	implements Icon
	{
		private final Icon	iconWest, iconEast;
		private final int	gap;
	
		private CompoundIcon( Icon iconWest, Icon iconEast, int gap )
		{
			this.iconWest	= iconWest;
			this.iconEast	= iconEast;
			this.gap		= gap;
		}

		public int getIconWidth()
		{
			return (iconWest == null ? 0 : iconWest.getIconWidth() + gap) +
				   (iconEast == null ? 0 : iconEast.getIconWidth());
		}
		
		public int getIconHeight()
		{
			return Math.max( iconWest == null ? 0 : iconWest.getIconHeight(),
							 iconEast == null ? 0 : iconEast.getIconHeight() );
		}
		
		public void paintIcon( Component c, Graphics g, int x, int y )
		{
			if( iconWest != null ) {
				iconWest.paintIcon( c, g, x, ((iconWest.getIconHeight() - getIconHeight()) >> 1) );
			}
			if( iconEast != null ) {
				iconEast.paintIcon( c, g, x + (iconWest == null ? 0 : iconWest.getIconWidth() + gap),
					y + getIconHeight() - iconEast.getIconHeight() );
			}
		}
	}
}