/*
 *  VectorTransformer.java
 *  Meloncillo
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
 *		07-Aug-05	copied from de.sciss.meloncillo.math.VectorTransformer
 */

package de.sciss.inertia.math;

import de.sciss.app.AbstractApplication;
import de.sciss.app.PreferenceNodeSync;
import de.sciss.gui.GUIUtil;
import de.sciss.inertia.gui.VectorEditor;
import de.sciss.inertia.gui.VectorSpace;
import de.sciss.util.NumberSpace;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;

// INERTIA
//import de.sciss.meloncillo.*;
//import de.sciss.meloncillo.gui.*;
//import de.sciss.meloncillo.render.*;
//import de.sciss.meloncillo.util.*;

/**
 *  A VectorTransform is an algorithm
 *  that transforms a data vector, where
 *  data vector is in the sense of a one
 *  dimensional vector as used in
 *  <code>VectorEditor</code> or for the
 *  x y coordinates of a transmitter's
 *  trajectory. This abstract class implements
 *  the <code>PreferenceNodeSync</code> interface
 *  to force the implementation of a
 *  prefs serialization mechanism. This
 *  abstract class has wisdom in that it
 *  provides a list of all subclasses using
 *  the <code>getTransforms</code> method.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.6, 04-Aug-04
 */
public abstract class VectorTransformer
implements PreferenceNodeSync
{
	/**
	 *  Key for getReceiver/Transmitter/RenderModuleTypes() : a human
	 *  readable name for displaying to the user
	 *
	 */
	public static final Object  KEY_HUMANREADABLENAME   = "name";
	/**
	 *  Key for getReceiver/Transmitter/RenderModuleTypes() : a full
	 *  qualifying class name for instantiating an object.
	 *
	 */
	public static final Object  KEY_CLASSNAME			= "class";

	/**
	 *  key in the getTransform maps.
	 *  transforms setting this key's
	 *  value, a Boolean, to true,
	 *  implement the RenderPlugIn interface.
	 */
	public static final String KEY_RENDERPLUGIN			= "canrender";
	
	// list of all known classes and resource-bundle keys
	// for getting a human readable name string
	private static final Object[][] transformerClasses = {
		{ "de.sciss.inertia.math.VectorApplyFunction", "vectorApplyFunction", Boolean.TRUE },
		{ "de.sciss.inertia.math.VectorFlipShift", "vectorFlipShift", Boolean.FALSE },
		{ "de.sciss.inertia.math.VectorRaiseMultiply", "vectorRaiseMultiply", Boolean.TRUE },
		{ "de.sciss.inertia.math.VectorSetMean", "vectorSetMean", Boolean.FALSE },
		{ "de.sciss.inertia.math.VectorSmooth", "vectorSmooth", Boolean.FALSE }};

	/**
	 *  Button components for the options dialog
	 */
	protected final String[]	queryOptions;

	protected VectorTransformer()
	{
		super();
	
		final de.sciss.app.Application	app = AbstractApplication.getApplication();
		
		queryOptions = new String[] {
			app.getResourceString( "buttonCancel" ),
			app.getResourceString( "buttonOk" )
		};
	}

	/**
	 *  Returns an out-of-the-Box popup menu
	 *  listing all known transforms and
	 *  automatically dealing with user
	 *  actions. The provided <code>VectorEditor</code>
	 *  is considered to hold the vector
	 *  data to be transformed. Thus, you
	 *  usually attach the returned menu
	 *  to the same <code>VectorEditor</code> component.
	 *
	 *  @param  edit	the editor to which the popup
	 *					is planned to be attached and
	 *					whose vector is to be transformed.
	 *  @return a newly created popup menu, not yet added to
	 *			to the component, but already equipped with
	 *			action listeners.
	 */
	public final static JPopupMenu createPopupMenu( VectorEditor edit )
	{
		JPopupMenu		pm			= new JPopupMenu();
		JMenuItem		mi;
		ActionListener  listener	= new VectorTransformer.actionTransformClass( edit );
		int				i;
		
		for( i = 0; i < transformerClasses.length; i++ ) {
			mi = new JMenuItem( AbstractApplication.getApplication().getResourceString( (String) transformerClasses[i][1] ));
			mi.setActionCommand( (String) transformerClasses[i][0] );
			mi.addActionListener( listener );
			pm.add( mi );
		}
		
		return pm;
	}
	
	/**
	 *  Queries all known transforms. Each
	 *  element of the returned list implements the
	 *  <code>java.util.Map</code> interface. The keys you're
	 *  usually interested in are <code>Main.KEY_HUMANREADABLENAME</code>
	 *  for GUI display and <code>Main.KEY_CLASSNAME</code> for
	 *  instantiation of the particular transform.
	 *  See <code>AbstractRenderDialog.createGadgets()</code>
	 *  for a common way of creating <code>StringItems</code> and
	 *  <code>JComboBoxes</code> from this kind of list.
	 *  <p>
	 *  A special map entry is the <code>KEY_RENDERPLUGIN</code>
	 *  whose corresponding value is a Boolean. If this is <code>true</code>,
	 *  the corresponding transformer class implements the <code>RenderPlugIn</code>
	 *  interface and can thus be used in the <code>VectorTransformFilter</code>
	 *  plug-in.
	 *  <p>
	 *  Note that the human readable strings are
	 *  likely to change with locale and therefore
	 *  should never be used to identify the transformer
	 *  classes are be stored in preferences.
	 *
	 *  @return a list of maps describing the known transforms
	 *
	 *  @see	#KEY_RENDERPLUGIN
	 */
	public final static java.util.List getTransforms()
	{
		int			i;
		Vector		v   = new Vector();
		Hashtable   h;
	
		for( i = 0; i < transformerClasses.length; i++ ) {
			h   = new Hashtable();
// INERTIA
//			h.put( Main.KEY_CLASSNAME, transformerClasses[i][0] );
//			h.put( Main.KEY_HUMANREADABLENAME, AbstractApplication.getApplication().getResourceString(
//				(String) transformerClasses[i][1] ));
			h.put( KEY_RENDERPLUGIN, transformerClasses[i][2] );
h.put( KEY_CLASSNAME, transformerClasses[i][0] );
h.put( KEY_HUMANREADABLENAME, AbstractApplication.getApplication().getResourceString(
	(String) transformerClasses[i][1] ));
			v.add( h );
		}
		
		return v;
	}
	
	/**
	 *  Queries the relevant parameters from user (if required)
	 *  and transform the vector.
	 *
	 *  @param  edit	the editor whose vector is to be transformed
	 *  @return			<code>true</code>, if the vector was transformed
	 *					<code>false</code>, if the user aborted the dialog
	 */
	public final boolean queryAndTransform( VectorEditor edit )
	{
		boolean		success = query( edit );
		float[]		output;
		NumberSpace nspc;
		VectorSpace vspc;

		if( success ) {
			vspc	= edit.getSpace();
			nspc	= new NumberSpace( vspc.vmin, vspc.vmax, 0.0 );
			try {
				output  = transform( edit.getVector(), nspc, edit.getWrapX(), edit.getWrapY() );
				success = output != null;
				if( success ) {
					handleWrapY( output, nspc, edit.getWrapY() );
					edit.setVector( edit, output );
				}
			}
			catch( IOException e1 ) {
				GUIUtil.displayError( edit, e1, AbstractApplication.getApplication().getResourceString( "vectorFunctionName" ));
				success = false;
			}
		}
		return success;
	}
	
	/**
	 *  Requests to open a dialog with
	 *  the parameters of the function.
	 *  Method returns after the user
	 *  quits the dialog (or immediately
	 *  if there are no parameters to be displayed).
	 *
	 *  @param  parent  the context of the query,
	 *					that is, the place where the dialog
	 *					will appear on the screen. it can be <code>null</code>.
	 *  @return		<code>true</code> if the parameters have been set,
	 *				<code>false</code> if the user cancelled the dialog
	 */
	public abstract boolean query( Component parent );

	/**
	 *  Performs a vector transformation.
	 *
	 *  @param		orig	the vector data to transform
	 *  @param		spc		the number space to use when validating the results
	 *  @param		wrapX   whether data shifted out of the vector should reappear
	 *						at the other end of the vector
	 *  @param		wrapY   whether values exceeding the number space's limited
	 *						should be folded back to the opposite limits.
	 *  @return		the transformed vector (newly allocated).
	 */
	public abstract float[] transform( float[] orig, NumberSpace spc, boolean wrapX, boolean wrapY )
		throws IOException;
	
	private static void handleWrapY( float[] trans, NumberSpace spc, boolean wrapY  )
	{
		int		i;
		float   f1;
		float   min, max, delta;
		int		len = trans.length;
	
		if( wrapY ) {
			min		= (float) spc.min;
			max		= (float) spc.max;
			delta   = max - min;
			for( i = 0; i < len; i++ ) {
				f1 = trans[i];
				while( f1 > max ) f1 -= delta;
				while( f1 < min ) f1 += delta;
				trans[i] = (float) spc.fitValue( f1 );
			}
		} else {
			for( i = 0; i < len; i++ ) {
				trans[i] = (float) spc.fitValue( trans[i] );
			}
		}
	}
	
	/*
	 *  Action for all popup menu items.
	 *  When actionPerformed() is invoked,
	 *  the name of the class of a VectorTransformer
	 *  is read through getActionCommand() instead
	 *  of providing the class itself, which delays
	 *  class loading until the first action is
	 *  performed. this also ensures future capability
	 *  of a plug-in kind of transformer extension!
	 */
	private static class actionTransformClass
	implements ActionListener
	{
		private VectorEditor edit;
	
		private actionTransformClass( VectorEditor edit )
		{
			this.edit = edit;
		}
	
		public void actionPerformed( ActionEvent e )
		{
			String				className = e.getActionCommand();
			VectorTransformer   vt;
			Class				c;

			try {
				c			= Class.forName( className );
				vt			= (VectorTransformer) c.newInstance();
// INERTIA
//				vt.setPreferences( AbstractApplication.getApplication().getUserPrefs().node(
//					PrefsUtil.NODE_SHARED ).node(
//					className.substring( className.lastIndexOf( '.' ) + 1 )));
				vt.queryAndTransform( edit );
			}
			catch( InstantiationException e1 ) {
				System.err.println( e1.getLocalizedMessage() );
			}
			catch( IllegalAccessException e2 ) {
				System.err.println( e2.getLocalizedMessage() );
			}
			catch( LinkageError e3 ) {
				System.err.println( e3.getLocalizedMessage() );
			}
			catch( ClassNotFoundException e4 ) {
				System.err.println( e4.getLocalizedMessage() );
			}
		}
	}
}
