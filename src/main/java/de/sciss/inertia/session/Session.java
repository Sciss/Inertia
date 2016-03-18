/*
 *  Session.java
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
 *		07-Aug-05	created from de.sciss.eisenkraut.session.Session
 */

package de.sciss.inertia.session;

import java.io.*;
import java.text.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.*;

// INERTIA
//import de.sciss.eisenkraut.Main;
//import de.sciss.eisenkraut.io.*;
//import de.sciss.eisenkraut.realtime.*;
//import de.sciss.eisenkraut.timeline.*;
//import de.sciss.eisenkraut.util.*;
import de.sciss.inertia.io.MarkerManager;
import de.sciss.inertia.realtime.*;
import de.sciss.inertia.timeline.Timeline;
import de.sciss.util.MapManager;
import de.sciss.util.LockManager;
import de.sciss.util.XMLRepresentation;

import de.sciss.app.AbstractApplication;
import de.sciss.app.BasicEvent;
import de.sciss.app.EventManager;

import de.sciss.io.AudioFile;
import de.sciss.io.AudioFileDescr;
import de.sciss.io.IOUtil;
import de.sciss.io.Span;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.23, 05-Oct-05
 */
public class Session
extends AbstractSessionObject
implements de.sciss.app.Document, EntityResolver, FilenameFilter	// EventManager.Processor
{
	public static final int	DEFAULT_RATE	= 1000;
	public static final int	NUM_TRACKS		= 3;

	public static final int	NUM_MOVIES		= 4;
	public static final int	NUM_TRANSPORTS_	= NUM_MOVIES; // one for each movie quadrant

	public static final String[]	TRACK_NAMES = { "Sync", "Assoc", "Free" };

	public final Timeline	timeline	= new Timeline();

	/**
	 *  Use this <code>LockManager</code> to gain access to
	 *  <code>receiverCollection</code>, <code>transmitterCollection</code>,
	 *  <code>timeline</code> and a transmitter's <code>MultirateTrackEditor</code>.
	 */
	public final LockManager bird = new LockManager( 4 );

	public  static final int DOOR_TIME		= 0x01;
	public  static final int DOOR_TRACKS	= 0x02;
	public  static final int DOOR_TABLES	= 0x04;
	public  static final int DOOR_MOLECULES	= 0x08;

	public  static final int DOOR_ALL		= DOOR_TIME + DOOR_TRACKS + DOOR_TABLES + DOOR_MOLECULES;

	private DocumentFrame	frame		= null;

	/**
	 *	Denotes the path to this session or
	 *	<code>null</code> if not yet saved
	 */
	public static final String MAP_KEY_PATH		= "path";

//	public static final String MAP_KEY_SCREEN	= "screen";

	// --- xml representation ---

	/**
	 *	System ID for XML session files
	 */
	public static final String SESSION_DTD	= "inertia.dtd";

	/**
	 *  The default file extension for storing sessions
	 *  is .llo for the extended markup language.
	 */
	public static final String FILE_EXTENSION   = ".xml";

	public static final String  XML_ROOT		= "inertia";

	// used for restoring groups in fromXML()
	protected static final String	OPTIONS_KEY_SESSION	= "session";

	private static final double FILE_VERSION	= 1.0;
	
	private static final String XML_ATTR_VERSION		= "version";
	private static final String XML_ATTR_COMPATIBLE		= "compatible";
	private static final String XML_ATTR_PLATFORM		= "platform";
	private static final String XML_ELEM_PREFS			= "prefs";
	private static final String XML_ELEM_NODE			= "node";

	private static final String XML_VALUE_PROBTABLES	= "probtables";
	private static final String XML_VALUE_TRACKS		= "tracks";

	private static final String	FILE_MARKERS			= "markers.aif";

	// --- event handling ---

//	private final EventManager elm = new EventManager( this );
	
	private final de.sciss.app.UndoManager	undo				= new de.sciss.app.UndoManager( this );
	private boolean							dirty				= false;

	public final SessionCollection			tracks				= new SessionCollection();
//	public final SessionCollection			atoms				= new SessionCollection();
	public final SessionCollection			tables				= new SessionCollection();

	public final SessionCollection			selectedTracks		= new SessionCollection();
	public final SessionCollection			selectedTables		= new SessionCollection();

	public final SessionCollection			activeTracks		= new SessionCollection();

	public static final String				PROB_NAME_PREFIX	= "P";
	public static final MessageFormat		SO_NAME_PTRN		= new MessageFormat( "{1}{0,number,integer}{2}", Locale.US );

	public final MarkerManager				markers				= new MarkerManager();
	public final  LayerManager				layers				= new LayerManager( NUM_MOVIES );

	private MultiTransport					transport			= null;

	public Session()
	{
		super();
		
		Track t;
	
		for( int i = 0; i < NUM_TRACKS; i++ ) {
			t = new Track();
			t.setName( TRACK_NAMES[ i ]);
			tracks.add( this, t );
		}
	
		clear( null );
		timeline.setRate( this, DEFAULT_RATE );
		
//		getMap().putContext( this, MAP_KEY_SCREEN, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY,
//			MapManager.Context.TYPE_STRING, null, null, null, new String( "A" )));
//		getMap().putValue( this, MAP_KEY_SCREEN, new String( "A" ));
	}
	
	public MultiTransport getTransport()
	{
		return transport;
	}

	public void setTransport( MultiTransport transport )
	{
		this.transport	= transport;
	}

	public DocumentFrame getFrame()
	{
		return frame;
	}

	public void setFrame( DocumentFrame frame )
	{
		this.frame = frame;
	}

	/**
	 *  Creates message format arguments for a generic
	 *  session object name pattern found in <code>SO_NAME_PTRN</code>.
	 *	This method seeks for the first occurance of an integer
	 *	number inside the concrete <code>realization</code> and puts
	 *	the prefix in <code>args[1]</code> and the suffix in <code>args[2]</code>.
	 *	The integer itself is saved as an <code>Integer</code> instance in
	 *	<code>args[0]</code>. If no integer is found, a default value of 1
	 *	is taken.
	 * 
	 *	@param	realization		a concrete version from which the pattern should
	 *							be derived, such as &quot;Lautsprecher-13-oben&quot;
	 *	@param	args			array of length greater or equal three whose
	 *							elements will be replaced by this method, i.e. for
	 *							the example above, <code>args[0]</code> will become
	 *							<code>new Integer( 13 )</code>, <code>args[1]</code>
	 *							will become &quot;Lautsprecher-&quot; and <code>args[2]</code>
	 *							will become &quot;-oben&quot;.
	 *
	 *	@see	SessionCollection#createUniqueName( MessageFormat, Object[], java.util.List )
	 *	@see	#SO_NAME_PTRN
	 */
	public static void makeNamePattern( String realization, Object[] args )
	{
		int		i, j;
		String	numeric = "0123456789";
		
		for( i = 0, j = realization.length(); i < realization.length(); i++ ) {
			if( numeric.indexOf( realization.charAt( i )) > 0 ) {
				for( j = i + 1; j < realization.length(); j++ ) {
					if( numeric.indexOf( realization.charAt( j )) == -1 ) break;
				}
				break;
			}
		}
		
		args[ 1 ] = realization.substring( 0, i );
		args[ 2 ] = realization.substring( j );
		if( j > i ) {
			args[ 0 ]	= new Integer( Integer.parseInt( realization.substring( i, j )));
		} else {
			args[ 0 ]	= new Integer( 1 );
		}
	}

	public void clear( Object source )
//	throws IOException
	{
		timeline.clear( source );
		selectedTracks.clear( source );
		selectedTables.clear( source );
		tables.clear( source );
		activeTracks.clear( source );
		
		for( int i = 0; i < tracks.size(); i++ ) {
			((Track) tracks.get( i )).clear( source );
		}
		
//		files.clear( source );
//		if( mte != null ) {
//			tracks.clear( source );
//			mte.clear();
			updateTitle();
//		}
		markers.clear( source );
	}
	
//	public MultirateTrackEditor getMTE()
//	{
//		return mte;
//	}
	
//	public void setAudioFileDescr( AudioFileDescr afd )
//	{
//		this.afd	= afd;
//		updateTitle();
//	}
//	
//	public AudioFileDescr getAudioFileDescr()
//	{
//		return afd;
//	}

// ---------------- Document interface ----------------

	public de.sciss.app.Application getApplication()
	{
		return AbstractApplication.getApplication();
	}

	public de.sciss.app.UndoManager getUndoManager()
	{
		return undo;
	}

	public void dispose()
	{
		layers.dispose();
		
		if( transport != null ) {
			transport.quit();
			transport = null;
		}
		if( frame != null ) {
			frame.setVisible( false );
			frame.dispose();
			frame = null;
		}
	}

	public boolean isDirty()
	{
		return dirty;
	}

	public void setDirty( boolean dirty )
	{
		if( !this.dirty == dirty ) {
			this.dirty = dirty;
			updateTitle();
		}
	}
	
	private void updateTitle()
	{
		if( frame != null ) frame.updateTitle();
	}

// ---------------- SessionObject interface ---------------- 

	/**
	 *  This simply returns <code>null</code>!
	 */
	public Class getDefaultEditor()
	{
		return null;
	}

// ---------------- XMLRepresentation interface ---------------- 

	/**
	 *  Encodes the session into XML format
	 *  for storing onto harddisc.
	 *
	 *  @param  domDoc		the document containing the XML code
	 *  @param  node		the root node to which the session
	 *						nodes ("ichnogram", "session/" and "shared/")
	 *						are attached
	 *  @throws IOException if an error occurs. XML related errors are
	 *						mapped to IOExceptions.
	 */
	public void toXML( org.w3c.dom.Document domDoc, Element node, Map options )
	throws IOException
	{
		Element				childElement, child2;
		NodeList			nl;
		SessionObject		so;

		try {
			bird.waitShared( DOOR_ALL );

			node.setAttribute( XML_ATTR_VERSION, String.valueOf( FILE_VERSION ));
			node.setAttribute( XML_ATTR_COMPATIBLE, String.valueOf( FILE_VERSION ));
			node.setAttribute( XML_ATTR_PLATFORM, System.getProperty( "os.name" ));
			
			// map
			childElement = (Element) node.appendChild( domDoc.createElement( XML_ELEM_MAP ));
			getMap().toXML( domDoc, childElement, options );

			// timeline object
			childElement = (Element) node.appendChild( domDoc.createElement( XML_ELEM_OBJECT ));
			timeline.toXML( domDoc, childElement, options );

			// prob table collection
			childElement = (Element) node.appendChild( domDoc.createElement( XML_ELEM_COLL ));
			childElement.setAttribute( XML_ATTR_NAME, XML_VALUE_PROBTABLES );
			for( int i = 0; i < tables.size(); i++ ) {
				so		= tables.get( i );
				child2	= (Element) childElement.appendChild( domDoc.createElement( XML_ELEM_OBJECT ));
				if( so instanceof XMLRepresentation ) {
					((XMLRepresentation) so).toXML( domDoc, child2, options );
				}
			}

			// tracks
			childElement = (Element) node.appendChild( domDoc.createElement( XML_ELEM_COLL ));
			childElement.setAttribute( XML_ATTR_NAME, XML_VALUE_TRACKS );
			for( int i = 0; i < tracks.size(); i++ ) {
				so		= tracks.get( i );
				child2	= (Element) childElement.appendChild( domDoc.createElement( XML_ELEM_OBJECT ));
				if( so instanceof XMLRepresentation ) {
					((XMLRepresentation) so).toXML( domDoc, child2, options );
				}
			}
		}
		finally {
			bird.releaseShared( DOOR_ALL );
		}
		
		
		// ------ markers ------
		final File				f			= new File( (File) options.get(
												XMLRepresentation.KEY_BASEPATH ), FILE_MARKERS );
		final AudioFileDescr	afd			= new AudioFileDescr();
		final AudioFile			af;
		
		afd.type			= AudioFileDescr.TYPE_AIFF;
		afd.channels		= 1;
		afd.rate			= 1000.0f;
		afd.bitsPerSample	= 32;
		afd.sampleFormat	= AudioFileDescr.FORMAT_FLOAT;
		afd.file			= f;
		markers.copyToAudioFile( afd );
		af					= AudioFile.openAsWrite( afd );
		af.close();
		
		// now add the session related stuff from
		// the main preferences' session and shared nodes
		
// INERTIA
//		childElement	= (Element) node.appendChild( domDoc.createElement( XML_ELEM_PREFS ));
//		child2			= (Element) childElement.appendChild( domDoc.createElement( XML_ELEM_NODE ));
//		child2.setAttribute( XML_ATTR_NAME, PrefsUtil.NODE_SESSION );
//		PrefsUtil.toXML( app.getUserPrefs().node( PrefsUtil.NODE_SESSION ), true, domDoc, child2, options );
//		child2			= (Element) childElement.appendChild( domDoc.createElement( XML_ELEM_NODE ));
//		child2.setAttribute( XML_ATTR_NAME, PrefsUtil.NODE_SHARED );
//		PrefsUtil.toXML( app.getUserPrefs().node( PrefsUtil.NODE_SHARED ), true, domDoc, child2, options );
	}

	/**
	 *  Clears the sessions
	 *  and recreates its objects from the
	 *  passed XML root node.
	 *
	 *  @param  domDoc		the document containing the XML code
	 *  @param  node		the document root node ("ichnogram")
	 *  @throws IOException if an error occurs. XML related errors are
	 *						mapped to IOExceptions.
	 */
	public void fromXML( org.w3c.dom.Document domDoc, Element node, Map options )
	throws IOException
	{
		NodeList						nl;
		Element							elem, elem2;
		double							d1;
		String							key, val, val2;
		SessionObject					so;
		Object							o;
		final ArrayList					soList		= new ArrayList();
		final NodeList					rootNL		= node.getChildNodes();
		final de.sciss.app.Application	app			= AbstractApplication.getApplication();

		options.put( OPTIONS_KEY_SESSION, this );

//		updateFileAttr( domDoc, node );
		try {
			bird.waitExclusive( DOOR_ALL );

			tracks.pauseDispatcher();
			tables.pauseDispatcher();
			selectedTracks.pauseDispatcher();
			selectedTables.pauseDispatcher();
			activeTracks.pauseDispatcher();

			timeline.pauseDispatcher();
			clear( null );
			
//			super.fromXML( domDoc, node, options );	// parses optional map
			
			// check attributes
			d1 = Double.parseDouble( node.getAttribute( XML_ATTR_COMPATIBLE ));
			if( d1 > FILE_VERSION ) throw new IOException( app.getResourceString( "errIncompatibleFileVersion" ) + " : " + d1 );
			d1 = Double.parseDouble( node.getAttribute( XML_ATTR_VERSION ));
			if( d1 > FILE_VERSION ) options.put( XMLRepresentation.KEY_WARNING,
				app.getResourceString( "warnNewerFileVersion" ) + " : " + d1 );
			val = node.getAttribute( XML_ATTR_PLATFORM );
			if( !val.equals( System.getProperty( "os.name" ))) {
				o	 = options.get( XMLRepresentation.KEY_WARNING );
				val2 = (o == null ? "" : o.toString() + "\n") +
					   app.getResourceString( "warnDifferentPlatform" ) + " : " + val;
				options.put( XMLRepresentation.KEY_WARNING, val2 );
			}


			for( int k = 0; k < rootNL.getLength(); k++ ) {
				if( !(rootNL.item( k ) instanceof Element) ) continue;
				
				elem	= (Element) rootNL.item( k );
				val		= elem.getTagName();

				// zero or one "map" element
				if( val.equals( XML_ELEM_MAP )) {
					getMap().fromXML( domDoc, elem, options );

				// zero or more "object" elements
				} else if( val.equals( XML_ELEM_OBJECT )) {
					val		= elem.getAttribute( XML_ATTR_NAME );
					if( val.equals( Timeline.XML_OBJECT_NAME )) {
						timeline.fromXML( domDoc, elem, options );
					} else {
						System.err.println( "Warning: unknown session object type: '"+val+"'" );
					}
					
				} else if( val.equals( XML_ELEM_COLL )) {
					val		= elem.getAttribute( XML_ATTR_NAME );

					if( val.equals( XML_VALUE_PROBTABLES )) {
						soList.clear();
						nl = elem.getChildNodes();
						for( int m = 0; m < nl.getLength(); m++ ) {
							elem2	= (Element) nl.item( m );
							val		= elem2.getTagName();
							if( !val.equals( XML_ELEM_OBJECT )) continue;

							if( elem2.hasAttribute( XML_ATTR_CLASS )) {
								val	= elem2.getAttribute( XML_ATTR_CLASS );
							} else {	// #IMPLIED
								val = "de.sciss.inertia.session.ProbabilityTable";
							}
							so = (SessionObject) Class.forName( val ).newInstance();
							if( so instanceof XMLRepresentation ) {
								((XMLRepresentation) so).fromXML( domDoc, elem2, options );
							}
							tables.getMap().copyContexts( null, MapManager.Context.FLAG_DYNAMIC,
																MapManager.Context.NONE_EXCLUSIVE, so.getMap() );
							soList.add( so );
						}
						tables.addAll( this, soList );

					} else if( val.equals( XML_VALUE_TRACKS )) {
						soList.clear();
						nl = elem.getChildNodes();
						for( int m = 0; m < nl.getLength(); m++ ) {
							elem2	= (Element) nl.item( m );
							val		= elem2.getTagName();
							if( !val.equals( XML_ELEM_OBJECT )) continue;

							if( elem2.hasAttribute( XML_ATTR_CLASS )) {
								val	= elem2.getAttribute( XML_ATTR_CLASS );
							} else {	// #IMPLIED
								val = "de.sciss.meloncillo.session.Track";
							}
							so = (SessionObject) Class.forName( val ).newInstance();
							if( so instanceof XMLRepresentation ) {
								((XMLRepresentation) so).fromXML( domDoc, elem2, options );
							}
							tracks.getMap().copyContexts( null, MapManager.Context.FLAG_DYNAMIC,
														  MapManager.Context.NONE_EXCLUSIVE, so.getMap() );
							soList.add( so );
						}
						tracks.clear( this ); // ! XXX
						tracks.addAll( this, soList );

					} else {
						System.err.println( "Warning: unknown session collection type: '"+val+"'" );
					}

				// optional prefs
				} else if( val.equals( "prefs" )) {
// INERTIA
//					nl		= elem.getChildNodes();
//					for( int i = 0; i < nl.getLength(); i++ ) {
//						if( !(nl.item( i ) instanceof Element) ) continue;
//						elem = (Element) nl.item( i );
//						val	 = elem.getAttribute( "name" );
//						if( val.equals( PrefsUtil.NODE_SESSION )) {
//							PrefsUtil.fromXML( app.getUserPrefs().node( PrefsUtil.NODE_SESSION ), domDoc, elem, options );
//						} else if( val.equals( PrefsUtil.NODE_SHARED )) {
//							if( !app.getUserPrefs().getBoolean( PrefsUtil.KEY_RECALLFRAMES, false )) {	// install filter
//								java.util.Set set = new HashSet( 3 );
//								set.add( PrefsUtil.KEY_LOCATION );
//								set.add( PrefsUtil.KEY_SIZE );
//								set.add( PrefsUtil.KEY_VISIBLE );
//								options.put( PrefsUtil.OPTIONS_KEY_FILTER, set );
//							}
//							PrefsUtil.fromXML( app.getUserPrefs().node( PrefsUtil.NODE_SHARED ), domDoc, elem, options );
//						} else {
//							System.err.println( "Warning: unknown preferences tree '"+val+"'" );
//						}
//					}
					
				// dtd doesn't allow other elements so we never get here
				} else {
					System.err.println( "Warning: unknown session node: '"+val+"'" );
				}
			} // for root-nodes

			// ------ markers ------
			final File				f			= new File( (File) options.get(
													XMLRepresentation.KEY_BASEPATH ), FILE_MARKERS );
			if( f.isFile() ) {
				final AudioFile			af		= AudioFile.openAsRead( f );
				final AudioFileDescr	afd		= af.getDescr();
				
				afd.length	= timeline.getLength(); // crucial, otherwise MarkerManager deletes markers
				markers.copyFromAudioFile( afd );
				af.close();
			} else {
				markers.clear( this );
			}
		}
		catch( ClassNotFoundException e1 ) {
			throw IOUtil.map( e1 );
		}
		catch( InstantiationException e2 ) {
			throw IOUtil.map( e2 );
		}
		catch( IllegalAccessException e3 ) {
			throw IOUtil.map( e3 );
		}
		catch( NumberFormatException e4 ) {
			throw IOUtil.map( e4 );
		}
		catch( ClassCastException e5 ) {
			throw IOUtil.map( e5 );
		}
		finally {
			tracks.resumeDispatcher();
			tables.resumeDispatcher();
			selectedTracks.resumeDispatcher();
			selectedTables.resumeDispatcher();
			activeTracks.addAll( this, tracks.getAll() );
			activeTracks.resumeDispatcher();

			timeline.resumeDispatcher();

			bird.releaseExclusive( DOOR_ALL );
		}
	}

// ---------------- FilenameFilter interface ---------------- 

	/**
	 *  Filter Session Files for Open or Save Dialog.
	 *  Practically it checks the file name's suffix
	 *
	 *  @param  dir		parent dir of the passed file
	 *  @param  name	filename to check
	 *  @return			true, if the filename is has
	 *					valid XML extension
	 */
	public boolean accept( File dir, String name )
	{
		return( name.endsWith( FILE_EXTENSION ));
	}

// ---------------- EntityResolver interface ---------------- 

	/**
	 *  This Resolver can be used for loading documents.
	 *	If the required DTD is the Meloncillo session DTD
	 *	("ichnogram.dtd"), it will return this DTD from
	 *	a java resource.
	 *
	 *  @param  publicId	ignored
	 *  @param  systemId	system DTD identifier
	 *  @return				the resolved input source for
	 *						the Meloncillo session DTD or <code>null</code>
	 *
	 *	@see	javax.xml.parsers.DocumentBuilder#setEntityResolver( EntityResolver )
	 */
	public InputSource resolveEntity( String publicId, String systemId )
	throws SAXException
	{
		if( systemId.endsWith( SESSION_DTD )) {	// replace our dtd with java resource
			InputStream dtdStream = getClass().getClassLoader().getResourceAsStream( SESSION_DTD );
			InputSource is = new InputSource( dtdStream );
			is.setSystemId( SESSION_DTD );
			return is;
		}
		return null;	// unknown DTD, use default behaviour
	}
}
