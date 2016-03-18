/*
 *  ProgressMonitor.java
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
 *		11-Aug-05	copied from de.sciss.eisenkraut.gui.ProgressMonitor
 */

package de.sciss.inertia.gui;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

// INERTIA
//import de.sciss.eisenkraut.util.*;
import de.sciss.inertia.util.PrefsUtil;
import de.sciss.util.ProgressComponent;

import de.sciss.app.AbstractApplication;

import de.sciss.gui.GUIUtil;
import de.sciss.gui.ProgressBar;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.5, 02-Aug-05
 */
public class ProgressMonitor
implements ProgressComponent, ActionListener
{
	private final javax.swing.Timer	timer;
	private final JFrame parent;

	private JDialog		dlg		= null;
	private ProgressBar pb		= null;
	private float		p		= 0.0f;
	private String		title;

	public ProgressMonitor( JFrame parent, String title, int popupDelayMillis )
	{
		this.parent	= parent;
		this.title	= title;
	
		timer = new javax.swing.Timer( popupDelayMillis, this );
		timer.setRepeats( false );
		timer.start();
	}

	public ProgressMonitor( JFrame parent, String title )
	{
		this( parent, title, 2000 );
	}

// ------------------ ActionListener interface ------------------

	// invoked by the swing timer
	public void actionPerformed( ActionEvent e )
	{
		if( dlg == null ) {
			dlg = new JDialog( parent, title );
			final Container cp = dlg.getContentPane();
			cp.setLayout( new BorderLayout() );
			pb = new ProgressBar();
			pb.setProgression( p );
			cp.add( pb, BorderLayout.CENTER );
			if( AbstractApplication.getApplication().getUserPrefs().getBoolean(
				PrefsUtil.KEY_INTRUDINGSIZE, false )) {
				
				cp.add( Box.createHorizontalStrut( 16 ), BorderLayout.EAST );
			}
			dlg.setLocationRelativeTo( parent );
			dlg.setVisible( true );
			dlg.toFront();
			GUIUtil.setDeepFont( cp, GraphicsUtil.smallGUIFont );
		}
	}

// ------------------ ProgressComponent interface ------------------

	public Component getComponent()
	{
		return dlg;
	}
	
	public void resetProgression()
	{
		p = 0.0f;
		if( pb != null ) pb.reset();
	}
	
	public void setProgression( float p )
	{
		this.p	= p;
	
		if( pb != null ) {
			if( p >= 0 ) {
				pb.setProgression( p );
			} else {
				pb.setIndeterminate( true );
			}
		}
	}
	
	public void	finishProgression( boolean success )
	{
		timer.stop();
		p = 1.0f;
		if( dlg != null ) {
			dlg.setVisible( false );
			dlg.dispose();
			dlg = null;
		}
	}
	
	public void setProgressionText( String text )
	{
		title = text;
		if( dlg != null ) dlg.setTitle( title );
	}
	
	public void showMessage( int type, String text )
	{
		System.out.println( text );
	}
	
	public void displayError( Exception e, String processName )
	{
		GUIUtil.displayError( parent, e, processName );
	}
}
