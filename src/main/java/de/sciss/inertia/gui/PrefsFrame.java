/*
 *  PrefsFrame.java
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
 *  Change log:
 *		11-Aug-05	copied from de.sciss.eisenkraut.gui.PrefsFrame
 */

package de.sciss.inertia.gui;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.prefs.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;

// INERTIA
//import de.sciss.eisenkraut.*;
//import de.sciss.eisenkraut.io.*;
//import de.sciss.eisenkraut.math.*;
//import de.sciss.eisenkraut.timeline.*;
//import de.sciss.eisenkraut.util.*;
import de.sciss.inertia.Main;
import de.sciss.inertia.util.PrefsUtil;

import de.sciss.app.AbstractApplication;

import de.sciss.gui.GUIUtil;
import de.sciss.gui.HelpGlassPane;
import de.sciss.gui.KeyStrokeTextField;
import de.sciss.gui.PathField;
import de.sciss.gui.PrefCheckBox;
import de.sciss.gui.PrefComboBox;
import de.sciss.gui.PrefNumberField;
import de.sciss.gui.PrefPathField;
import de.sciss.gui.PrefTextField;
import de.sciss.gui.StringItem;

import de.sciss.io.IOUtil;

import de.sciss.util.NumberSpace;

/**
 *  This is the frame that
 *  displays the user adjustable
 *  application and session preferences
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.12, 20-Aug-05
 */
public class PrefsFrame
extends BasicPalette // BasicFrame
{
	private final Main		root;
	
	/**
	 *  Creates a new preferences frame
	 *
	 *  @param  root	application root
	 *  @param  doc		session document
	 */
    public PrefsFrame( final Main root )
    {
		super( AbstractApplication.getApplication().getResourceString( "framePrefs" ));

		this.root   = root;

		final Container					cp		= getContentPane();
        final Font						fnt		= GraphicsUtil.smallGUIFont;
		final de.sciss.app.Application	app		= AbstractApplication.getApplication();
		SpringLayout					lay;

		JPanel			tab, tabWrap, buttonPanel;
		KeyStrokeTextField ggKeyStroke;
		PrefNumberField	ggNumber;
		PrefPathField   ggPath;
		PrefCheckBox	ggCheckBox;
        PrefComboBox    ggChoice;
		PrefTextField	ggText;
		JButton			ggButton;
		JTabbedPane		ggTabPane;
		JLabel			lb;
        UIManager.LookAndFeelInfo[] lafInfos;

		Preferences		prefs;
		String			key, key2;
		int				i, rows;

		ggTabPane			= new JTabbedPane();

		// ---------- global pane ----------

		tab		= new JPanel();
		lay		= new SpringLayout();
		tab.setLayout( lay );
		rows	= 0;

		prefs   = IOUtil.getUserPrefs();
		key		= IOUtil.KEY_TEMPDIR;
		key2	= "prefsTmpDir";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggPath = new PrefPathField( PathField.TYPE_FOLDER, app.getResourceString( key2 ));
		ggPath.setPreferences( prefs, key );
        HelpGlassPane.setHelp( ggPath, key2 );
		tab.add( ggPath );
		lb.setLabelFor( ggPath );
		rows++;

       	key		= PrefsUtil.KEY_SESSIONINDENT;
		key2	= "prefsSessionIndent";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggCheckBox  = new PrefCheckBox();
		ggCheckBox.setPreferences( prefs, key );
		tab.add( ggCheckBox );
		lb.setLabelFor( ggCheckBox );
		rows++;

       	key		= PrefsUtil.KEY_SESSIONNOXMLHEADER;
		key2	= "prefsSessionNoXMLHeader";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggCheckBox  = new PrefCheckBox();
		ggCheckBox.setPreferences( prefs, key );
		tab.add( ggCheckBox );
		lb.setLabelFor( ggCheckBox );
		rows++;

// INERTIA
//		prefs   = root.cacheManager.getPreferences();
//		key		= CacheManager.KEY_ACTIVE;
//		key2	= "prefsCacheActive";
//		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
//		tab.add( lb );
//		ggCheckBox = new PrefCheckBox();
//		ggCheckBox.setPreferences( prefs, key );
//        HelpGlassPane.setHelp( ggCheckBox, key2 );
//		tab.add( ggCheckBox );
//		lb.setLabelFor( ggCheckBox );
//		rows++;
//		
//		key		= CacheManager.KEY_CAPACITY;
//		key2	= "prefsCacheCapacity";
//		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
//		tab.add( lb );
//		ggNumber  = new PrefNumberField( 0, NumberSpace.createIntSpace( 1, 16384 ),
//										 app.getResourceString( "labelMegaBytes" ));
//		ggNumber.setPreferences( prefs, key );
//        HelpGlassPane.setHelp( ggNumber, key2 );
//		tab.add( ggNumber );
//		lb.setLabelFor( ggNumber );
//		rows++;
//		
//		key		= CacheManager.KEY_FOLDER;
//		key2	= "prefsCacheFolder";
//		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
//		tab.add( lb );
//		ggPath	= new PrefPathField( PathField.TYPE_FOLDER, app.getResourceString( key2 ));
//		ggPath.setPreferences( prefs, key );
//        HelpGlassPane.setHelp( ggPath, key2 );
//		tab.add( ggPath );
//		lb.setLabelFor( ggPath );
//		rows++;

		prefs   = app.getUserPrefs();
        key     = PrefsUtil.KEY_LOOKANDFEEL;
		key2	= "prefsLookAndFeel";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggChoice = new PrefComboBox();
		lafInfos = UIManager.getInstalledLookAndFeels();
        for( i = 0; i < lafInfos.length; i++ ) {
            ggChoice.addItem( new StringItem( lafInfos[i].getClassName(), lafInfos[i].getName() ));
        }
		ggChoice.setPreferences( prefs, key );
		ggChoice.addActionListener( new ActionListener() {
			private final String laf = app.getUserPrefs().get( PrefsUtil.KEY_LOOKANDFEEL, "" );

			public void actionPerformed( ActionEvent e )
			{
				final JComboBox	b		= (JComboBox) e.getSource();
				final String	newName	= ((StringItem) b.getSelectedItem()).getKey();

				if( !newName.equals( laf )) {
					b.removeActionListener( this );
					JOptionPane.showMessageDialog( b,
						app.getResourceString( "warnLookAndFeelUpdate" ),
						app.getResourceString( "prefsLookAndFeel" ), JOptionPane.INFORMATION_MESSAGE );
				}
			}
		});
        HelpGlassPane.setHelp( ggChoice, key2 );
		tab.add( ggChoice );
		lb.setLabelFor( ggChoice );
		rows++;

       	key		= PrefsUtil.KEY_INTRUDINGSIZE;
		key2	= "prefsIntrudingSize";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggCheckBox  = new PrefCheckBox();
		ggCheckBox.setPreferences( prefs, key );
        HelpGlassPane.setHelp( ggCheckBox, key2 );
		tab.add( ggCheckBox );
		lb.setLabelFor( ggCheckBox );
		rows++;

  		prefs   = GUIUtil.getUserPrefs();
     	key		= HelpGlassPane.KEY_KEYSTROKE_HELP;
		key2	= "prefsKeyStrokeHelp";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggKeyStroke = new KeyStrokeTextField();
		ggKeyStroke.setPreferences( prefs, key );
        HelpGlassPane.setHelp( ggKeyStroke, key2 );
		tab.add( ggKeyStroke );
		lb.setLabelFor( ggKeyStroke );
		rows++;
		
		key2	= "prefsGeneral";
		GUIUtil.makeCompactSpringGrid( tab, rows, 2, 4, 2, 4, 2 );	// #row #col initx inity padx pady
		tabWrap = new JPanel( new BorderLayout() );
		tabWrap.add( tab, BorderLayout.NORTH );
        HelpGlassPane.setHelp( tabWrap, key2 );
		ggTabPane.addTab( app.getResourceString( key2 ), null, tabWrap, null );

		// ---------- audio pane ----------

		prefs   = app.getUserPrefs().node( PrefsUtil.NODE_AUDIO );
		tab		= new JPanel();
		lay		= new SpringLayout();
		tab.setLayout( lay );
		rows	= 0;

		key		= PrefsUtil.KEY_SUPERCOLLIDEROSC;
		key2	= "prefsSuperColliderOSC";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggText  = new PrefTextField( 32 );
		ggText.setPreferences( prefs, key );
        HelpGlassPane.setHelp( ggText, key2 );
		tab.add( ggText );
		lb.setLabelFor( ggText );
		rows++;

		key		= PrefsUtil.KEY_SUPERCOLLIDERAPP;
		key2	= "prefsSuperColliderApp";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggPath = new PrefPathField( PathField.TYPE_INPUTFILE, app.getResourceString( key2 ));
		ggPath.setPreferences( prefs, key );
        HelpGlassPane.setHelp( ggPath, key2 );
		tab.add( ggPath );
		lb.setLabelFor( ggPath );
		rows++;

       	key		= PrefsUtil.KEY_AUTOBOOT;
		key2	= "prefsAutoBoot";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggCheckBox  = new PrefCheckBox();
		ggCheckBox.setPreferences( prefs, key );
        HelpGlassPane.setHelp( ggCheckBox, key2 );
		tab.add( ggCheckBox );
		lb.setLabelFor( ggCheckBox );
		rows++;

		key		= PrefsUtil.KEY_AUDIODEVICE;
		key2	= "prefsAudioDevice";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggText  = new PrefTextField( 32 );
		ggText.setPreferences( prefs, key );
        HelpGlassPane.setHelp( ggText, key2 );
		tab.add( ggText );
		lb.setLabelFor( ggText );
		rows++;

		key		= PrefsUtil.KEY_AUDIOINPUTS;
		key2	= "prefsAudioInputChannels";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggNumber  = new PrefNumberField();
		ggNumber.setSpace( NumberSpace.createIntSpace( 0, 16384 ));
		ggNumber.setPreferences( prefs, key );
        HelpGlassPane.setHelp( ggNumber, key2 );
		tab.add( ggNumber );
		lb.setLabelFor( ggNumber );
		rows++;

		key		= PrefsUtil.KEY_AUDIOOUTPUTS;
		key2	= "prefsAudioOutputChannels";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggNumber  = new PrefNumberField();
		ggNumber.setSpace( NumberSpace.createIntSpace( 0, 16384 ));
		ggNumber.setPreferences( prefs, key );
        HelpGlassPane.setHelp( ggNumber, key2 );
		tab.add( ggNumber );
		lb.setLabelFor( ggNumber );
		rows++;

		key		= PrefsUtil.KEY_AUDIORATE;
		key2	= "prefsAudioRate";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggNumber  = new PrefNumberField();
		ggNumber.setSpace( new NumberSpace( 0.0, 384000.0, 0.1 ));
		ggNumber.setPreferences( prefs, key );
        HelpGlassPane.setHelp( ggNumber, key2 );
		tab.add( ggNumber );
		lb.setLabelFor( ggNumber );
		rows++;

		key2	= "prefsAudio";
		GUIUtil.makeCompactSpringGrid( tab, rows, 2, 4, 2, 4, 2 );	// #row #col initx inity padx pady
		tabWrap = new JPanel( new BorderLayout() );
		tabWrap.add( tab, BorderLayout.NORTH );
        HelpGlassPane.setHelp( tabWrap, key2 );
		ggTabPane.addTab( app.getResourceString( key2 ), null, tabWrap, null );

		// ---------- audio pane ----------

		prefs   = app.getUserPrefs().node( PrefsUtil.NODE_VIDEO );
		tab		= new JPanel();
		lay		= new SpringLayout();
		tab.setLayout( lay );
		rows	= 0;

		key		= PrefsUtil.KEY_JITTEROSC;
		key2	= "prefsJitterOSC";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggText  = new PrefTextField( 32 );
		ggText.setPreferences( prefs, key );
        HelpGlassPane.setHelp( ggText, key2 );
		tab.add( ggText );
		lb.setLabelFor( ggText );
		rows++;

		key2	= "prefsVideo";
		GUIUtil.makeCompactSpringGrid( tab, rows, 2, 4, 2, 4, 2 );	// #row #col initx inity padx pady
		tabWrap = new JPanel( new BorderLayout() );
		tabWrap.add( tab, BorderLayout.NORTH );
        HelpGlassPane.setHelp( tabWrap, key2 );
		ggTabPane.addTab( app.getResourceString( key2 ), null, tabWrap, null );

		// ---------- generic gadgets ----------

        ggButton	= new JButton( app.getResourceString( "buttonClose" ));
        buttonPanel = new JPanel( new FlowLayout( FlowLayout.RIGHT, 4, 4 ));
        buttonPanel.add( ggButton );
        ggButton.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent newEvent )
			{
				setVisible( false );
                dispose();
			}	
		});

		cp.setLayout( new BorderLayout() );
		cp.add( ggTabPane, BorderLayout.CENTER );
        cp.add( buttonPanel, BorderLayout.SOUTH );
		GUIUtil.setDeepFont( cp, fnt );

		// ---------- listeners ----------

		init( root );
		pack();
    }
}