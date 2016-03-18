/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Class dependancies: xml package by jens gulden (http://swiki.hfbk-hamburg.de:8888/MusicTechnology/747)
 *
 *	a quick + dirty translation from java (just playback-only relevant methods)
 *
 *	@version	1.0, 24-Nov-06
 *	@author		Hanns Holger Rutz
 */
InertiaSession {
	classvar <kFileVersion	= 1.0;
	classvar <kNumMovies	= 4;

	var <timeline;
	var <tracks;
	var <layers;
	var <transport;
	var <>markers;
	var <player;

	*new {
		^super.new.prInit;
	}
	
	prInit {
		timeline	= InertiaTimeline.new;
		tracks	= List.new;
		layers	= InertiaLayerManager( kNumMovies );
		transport	= InertiaTransport( this );
	}
	
	createPlayer {
		if( player.isNil, {
			player = InertiaPlayer( this );
		}, {
			"InertiaSession.createPlayer : player already exists".error;
		});
	}

	*load { arg path, audioPath;
		var domDoc, options, doc, warn, folder;
		
		domDoc	= DOMDocument.new( path );
		doc		= this.new;
		options	= IdentityDictionary.new;
		folder	= PathName( path ).pathOnly;
		options.put( \path, folder );
		doc.fromXML( domDoc, domDoc.getDocumentElement, options );
		if( audioPath.notNil, {
			doc.tracks.do({ arg track; track.molecules.do({ arg molec; molec.atoms.do({ arg atom;
				atom.file = audioPath ++ PathName( atom.file ).fileName;
			})})});
		});
		
//"markers.begin".postln;
//0.wait;
		doc.markers	= ScissUtil.readMarkersFromAIFF( folder ++ "markers.aif" ).collect({ arg ev;
			MarkerStake( ev.pos, ev.name )
		});
//"markers.done".postln;
//0.wait;
		
		warn		= options[ \warn ];
		if( warn.notNil, { warn.warn; });
		
		^doc;
	}

	fromXML { arg domDoc, node, options;
		var nl, elem, elem2, key, val, val2, val3, val4, so, soList, rootNL, d1, o;

		options.put( \session, this );

		soList	= List.new;
		rootNL	= node.getChildNodes;
//		app		= AbstractApplication.getApplication();

		// check attributes
		d1 = node.getAttribute( "compatible" ).asFloat;
		if( d1 > kFileVersion, { Error( "Incompatible session file version : " ++ d1 ).throw });
		d1 = node.getAttribute( "version" ).asFloat;
		if( d1 > kFileVersion, { options.put( \warn,
			"The file was saved in a newer format.\nSome information might get lost!\nSaved version : " ++ d1 );
		});
		val = node.getAttribute( "platform" );
		if( val == "Mac OS X", { val = "osx" });
		if( thisProcess.respondsTo( \platform ) and: { val != thisProcess.platform.name.asString }, {
			o	 = options[ \warn ];
			val2 = (o.isNil.if( "", { o.asString ++ "\n" })) ++
					"The file was saved on a different platform.\n" ++
					"Some path references might be wrong!\nPlatform : " ++ val;
			options.put( \warn, val2 );
		});
		
		rootNL.do({ arg elem;
			if( elem.isKindOf( DOMElement ), {
				val	= elem.getTagName;
	
				case
				// zero or one "map" element
				{ val == "map" }
				{
// not needed
//					getMap().fromXML( domDoc, elem, options );
				}
				
				// zero or more "object" elements
				{ val == "object" }
				{
					val2 = elem.getAttribute( "name" );
					if( val2 == "timeline", {
						timeline.fromXML( domDoc, elem, options );
					}, {
						("Warning: unknown session object type: '"++val2++"'").error;
					});
				}
				{ val == "coll" }
				{
					val2 = elem.getAttribute( "name" );

					case
					{ val2 == "probtables" }
					{
// not needed
//						soList.clear();
//						nl = elem.getChildNodes();
//						for( int m = 0; m < nl.getLength(); m++ ) {
//							elem2	= (Element) nl.item( m );
//							val		= elem2.getTagName();
//							if( !val.equals( XML_ELEM_OBJECT )) continue;
//
//							if( elem2.hasAttribute( XML_ATTR_CLASS )) {
//								val	= elem2.getAttribute( XML_ATTR_CLASS );
//							} else {	// #IMPLIED
//								val = "de.sciss.inertia.session.ProbabilityTable";
//							}
//							so = (SessionObject) Class.forName( val ).newInstance();
//							if( so instanceof XMLRepresentation ) {
//								((XMLRepresentation) so).fromXML( domDoc, elem2, options );
//							}
//							tables.getMap().copyContexts( null, MapManager.Context.FLAG_DYNAMIC,
//																MapManager.Context.NONE_EXCLUSIVE, so.getMap() );
//							soList.add( so );
//						}
//						tables.addAll( this, soList );
//
					}
					{ val2 == "tracks" }
					{
						soList.clear;
						nl = elem.getChildNodes;
						nl.do({ arg elem2;
							val3 = elem2.getTagName;
							if( val3 == "object", {	
								if( elem2.hasAttribute( "class" ), {
									val4 = elem2.getAttribute( "class" );
								}, {	// #IMPLIED
									val4 = "de.sciss.meloncillo.session.Track";
								});
//								so = (SessionObject) Class.forName( val ).newInstance();
//								if( so instanceof XMLRepresentation ) {
//									((XMLRepresentation) so).fromXML( domDoc, elem2, options );
//								}
so = InertiaTrack.new.fromXML( domDoc, elem2, options );
//								tracks.getMap().copyContexts( null,
//									MapManager.Context.FLAG_DYNAMIC,
//								 	MapManager.Context.NONE_EXCLUSIVE, so.getMap() );
// XXX
								soList.add( so );
							});
						});
//						tracks.clear( this ); // ! XXX
//						tracks.addAll( this, soList );
						tracks.clear; // ! XXX
						tracks.addAll( soList );
					}
					{
						("Warning: unknown session collection type: '"++val2++"'").error;
					};
	
				}
				// optional prefs
				{ val == "prefs" }
				{
				
				}	
				// dtd doesn't allow other elements so we never get here
				{
					("Warning: unknown session node: '"++val++"'").error;
				};
			});
		}); // for root-nodes

/*
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


		activeTracks.addAll( this, tracks.getAll() );
*/
	}
}

InertiaSessionObject {
	classvar <kFlagsSolo			= 0x01;
	classvar <kFlagsMute			= 0x02;
	classvar <kFlagsSoloSafe		= 0x04;
	classvar <kFlagsVirtualMute		= 0x08;

	classvar flagsMuted			= 0x0A;	// FLAGS_MUTE | FLAGS_VIRTUALMUTE;

	var <name;

	var <flags = 0;

	isMuted {
		^((flags & flagsMuted) != 0);
	}
}

InertiaTimeline : InertiaSessionObject {
	var <rate, <length, <position;

	var elm;

	*new {
		^super.new.prInit;
	}

	prInit {
	 	elm = EventManager( this );
	}
	
//	/**
//	 *  Clears the timeline, i.e. the length is set to zero,
//	 *  selection and visible span are cleared.
//	 *  The caller should ensure
//	 *  that event dispatching is paused!
//	 *
//	 *  @param  source  who originated the action
//	 */
//	public void clear( Object source )
//	{
//		setRate( source, 1000 );
//		setPosition( source, 0 );
//		setSelectionSpan( source, new Span() );
//		setVisibleSpan( source, new Span() );
//		setLength( source, 0 );
//		getMap().clearValues( source );
//	}

	/**
	 *  Changes the current the timeline's playback/insert position. This
	 *  fires a <code>TimelineEvent</code> (<code>POSITIONED</code>).
	 *  Often you'll want to use an <code>EditSetTimelinePosition</code> object.
	 *
	 *  @param  source		the source of the <code>TimelineEvent</code>
	 *  @param  position	the new timeline offset (the position
	 *						of the vertical bar in the timeline frame)
	 *						in frames
	 *
	 *  @see	de.sciss.meloncillo.edit.EditSetTimelinePosition
	 *  @see	TimelineEvent#POSITIONED
	 */
    setPosition { arg source, argPos;
        position = argPos;
        if( source.notNil, { this.prDispatchPosition( source )});
//		getMap().putValue( this, MAP_KEY_POSITION, new Long( position ));
	}

	/**
	 *  Register a <code>TimelineListener</code>
	 *  which will be informed about changes of
	 *  the timeline (i.e. changes in rate and length,
	 *  scrolling in the timeline frame and selection
	 *  of timeline portions by the user).
	 *
	 *  @param  listener	the <code>TimelineListener</code> to register
	 *  @see	de.sciss.meloncillo.util.EventManager#addListener( Object )
	 */
	addTimelineListener { arg listener;
		elm.addListener( listener );
	}

	/**
	 *  Unregister a <code>TimelineListener</code>
	 *  from receiving timeline events.
	 *
	 *  @param  listener	the <code>TimelineListener</code> to unregister
	 *  @see	de.sciss.meloncillo.util.EventManager#removeListener( Object )
	 */
	removeTimelineListener { arg listener;
		elm.removeListener( listener );
	}

	/**
	 *  This is called by the EventManager
	 *  if new events are to be processed
	 */
	processEvent { arg e;
		var listener;

		elm.countListeners.do({ arg i;
			listener = elm.getListener( i );
			switch( e.getID,
				InertiaTimelineEvent.kChanged, { listener.timelineChanged( e )},
				InertiaTimelineEvent.kPositioned, { listener.timelinePositioned( e )},
				InertiaTimelineEvent.kSelected, { listener.timelineSelected( e )},
				InertiaTimelineEvent.kScrolled, { listener.timelineScrolled( e )}
			);
		});
	}

	// utility function to create and dispatch a TimelineEvent
	prDispatchChange { arg source;
		var e2;
		
		e2 = InertiaTimelineEvent( source, InertiaTimelineEvent.kChanged, thisThread.seconds, 0, nil );
		elm.dispatchEvent( e2 );
	}

	// utility function to create and dispatch a TimelineEvent
	prDispatchPosition { arg source;
		var e2;
		
		e2 = InertiaTimelineEvent( source, InertiaTimelineEvent.kPositioned, thisThread.seconds, 0, position );
		elm.dispatchEvent( e2 );
	}
    
//	// utility function to create and dispatch a TimelineEvent
//	prDispatchSelection { arg source;
//		var e2;
//		
//		e2 = InertiaTimelineEvent( source, InertiaTimelineEvent.kSelected, thisThread.seconds, 0, getSelectionSpan() );
//		elm.dispatchEvent( e2 );
//	}

//	// utility function to create and dispatch a TimelineEvent
//	prDispatchScroll { arg object;
//	{
//		var e2;
//		
//		e2 = InertiaTimelineEvent( source, InertiaTimelineEvent.kScrolled, thisThread.seconds, 0, getVisibleSpan() );
//		elm.dispatchEvent( e2 );
//	}

// ---------------- XMLRepresentation interface ---------------- 

//	public void toXML( org.w3c.dom.Document domDoc, Element node, Map options )
//	throws IOException
//	{
//		super.toXML( domDoc, node, options );
//	}
//
	fromXML { arg domDoc, node, options;
		var val, nl2, val2, val3, val4;
	
		name = node.getAttribute( "name" );

		node.getChildNodes.do({ arg elem;
			if( elem.isKindOf( DOMElement ), {
				val = elem.getTagName;
	
				case
				// zero or one "map" element
				{ val == "map" }
				{
//					getMap().fromXML( domDoc, elem, options );
					nl2 = elem.getChildNodes;
					nl2.do({ arg elem2;
						if( elem2.isKindOf( DOMElement ), {
							val2 = elem2.getTagName;
							if( val2 == "entry", {
								val3 = elem2.getAttribute( "key" );
								val4 = elem2.getAttribute( "value" );
								case
								{ val3 == "len" }
								{
									length = val4.asInteger;
								}
								{ val3 == "pos" }
								{
									position = val4.asInteger;
								}
								{ val3 == "rate" }
								{
									rate = val4.asInteger;
								}
								{
									("Warning: unknown map attribute: '"++val3++"'").error;
								};								
							}, {
								("Warning: unknown map element: '"++val2++"'").error;
							});
						});
					});
				}
				{
					("Warning: unknown element: '"++val++"'").error;
				};
			});
		});
	}
}

InertiaTimelineEvent : BasicEvent {
// --- ID values ---
	/**
	 *  returned by getID() : a portion of the timeline
	 *  has been selected or deselected
	 */
	classvar <kSelected	= 0;
	/**
	 *  returned by getID() : the basic properties of
	 *  the timeline, rate or length, have been modified.
     *  <code>actionObj</code> is a (potentially empty)
     *  <code>Span</code> object
	 */
	classvar <kChanged		= 1;
	/**
	 *  returned by getID() : the 'playback head' of
	 *  the timelime has been moved
	 */
	classvar <kPositioned	= 2;
	/**
	 *  returned by getID() : the visible portion of
	 *  the timelime has been changed.
     *  <code>actionObj</code> is a (potentially empty)
     *  <code>Span</code> object
	 */
	classvar <kScrolled	= 3;

	var <actionID;   // currently not in use
	var <actionObj;  // used depending on the event ID

	/**
	 *  Constructs a new <code>TimelineEvent</code>
	 *
	 *  @param  source		who originated the action
	 *  @param  ID			one of <code>CHANGED</code>, <code>SELECTED</code>,
	 *						<code>POSITIONED</code> and <code>SCROLLED</code>
	 *  @param  when		system time when the event occured
	 *  @param  actionID	currently unused - thus use zero
	 *  @param  actionObj   for <code>SELECTED</code> and <code>SCROLLED</code>
	 *						this is a <code>Span</code> describing the new
	 *						visible or selected span.
	 */
	*new { arg source, id, when, actionID, actionObj;
		^super.new( source, id, when ).prInitITE( actionID, actionObj );
	}
	
	prInitITE { arg argActionID, argActionObj;
		actionID   = argActionID;
		actionObj  = argActionObj;
	}
	
	incorporate { arg oldEvent;
		if( oldEvent.isKindOf( InertiaTimelineEvent ) and: { (this.getSource == oldEvent.getSource) and: {
			this.getID == oldEvent.getID }}, {
			
			// XXX beware, when the actionID and actionObj
			// are used, we have to deal with them here
			
			^true;

		}, { ^false });
	}
}

InertiaProbability : InertiaSessionObject {	// AbstractSessionObject

//	private static final StringItem[]	TYPE_ITEMS	= {
//		new StringItem( "white", "White" ),
//		new StringItem( "brown", "Brownian Walk" ),
//		new StringItem( "table", "Density Table" ),
//		new StringItem( "seq", "Sequence" ),
//		new StringItem( "series", "Series" ),
//		new StringItem( "alea", "Alea" ),
//		new StringItem( "rota", "Rotation" )
//	};

	classvar <kTypeNames;
	
	classvar <kTypeWhite		= 0;
	classvar <kTypeBrown		= 1;
	classvar <kTypeTable		= 2;
	classvar <kTypeSeq			= 3;
	classvar <kTypeSeries		= 4;
	classvar <kTypeAlea			= 5;
	classvar <kTypeRota			= 6;

	var samples			= nil;
	var seriesSamples	= nil;
	var seriesLen 		= 0;
	var sampleIdx		= 0;
	var type			= 0; // kTypeWhite;
	var rotaInc			= 1;
	
	var <min = 0.0, <max = 0.0;
		
	*initClass {
//		kTypeNames = [ "white", "brown", "table", "seq", "series", "alea", "rota" ];
		kTypeNames = Dictionary[ "white" -> 0, "brown" -> 1, "table" -> 2, "seq" -> 3, "series" -> 3, "alea" -> 4, "rota" -> 5 ];
	}
	
	*new {
		^super.new.prInit;
	}
	
	prInit {
//		MapManager	map	= getMap();
//
//		map.putContext( this, MAP_KEY_MIN, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY,
//			MapManager.Context.TYPE_DOUBLE, null, null, null, null ));
//		map.putContext( this, MAP_KEY_MAX, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY,
//			MapManager.Context.TYPE_DOUBLE, null, null, null, null ));
//		map.putContext( this, MAP_KEY_TYPE, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY,
//			MapManager.Context.TYPE_STRING, TYPE_ITEMS, null, null, TYPE_ITEMS[ TYPE_WHITE ].getKey() ));
//		map.putContext( this, MAP_KEY_BF, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY,
//			MapManager.Context.TYPE_DOUBLE, null, null, null, new Double( 0.1 )));
//		map.putContext( this, MAP_KEY_SAMPLES, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY,
//			MapManager.Context.TYPE_STRING, null, null, null, "0.0 1.0" ));
//		map.putContext( this, MAP_KEY_TABLE, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY,
//			MapManager.Context.TYPE_STRING, null, null, null, "P1" ));
	}

//	public Probability duplicate()
//	{
//		Probability result = new Probability();
//		result.setName( this.getName() );
//		result.getMap().cloneMap( this.getMap() );
//		result.setDocument( doc );
//		
//		return result;
//	}
//
//	public void setMinSpace( NumberSpace spc, Number defaultValue, String label )
//	{
//		final MapManager.Context c = getMap().getContext( MAP_KEY_MIN );
//		getMap().putContext( this, MAP_KEY_MIN, new MapManager.Context( c.flags, c.type, spc, label, null, defaultValue ));
//	}
//
//	public void setMaxSpace( NumberSpace spc, Number defaultValue, String label )
//	{
//		final MapManager.Context c = getMap().getContext( MAP_KEY_MAX );
//		getMap().putContext( this, MAP_KEY_MAX, new MapManager.Context( c.flags, c.type, spc, label, null, defaultValue ));
//	}

	realize {
		var d;
	
		switch( type, 
			kTypeWhite, {
				d	= 1.0.rand;
			},
			kTypeAlea, {
				if( samples.size == 0, { ^min });
				sampleIdx	= samples.size.rand;
				d			= samples[ sampleIdx ];
			},
			kTypeSeries, {
				if( samples.size == 0, { ^min });
				sampleIdx	= seriesLen.rand;
				d			= seriesSamples[ sampleIdx ];
				seriesLen	= seriesLen - 1;
				if( seriesLen == 0, {
					seriesSamples 	= samples.copy;
					seriesLen	 	= samples.size;
				}, {
					seriesSamples.removeAt( sampleIdx );
				});
			},
			kTypeSeq, {
				if( samples.size == 0, { ^min });
				d			= samples[ sampleIdx ];
				sampleIdx	= (sampleIdx + 1) % samples.size;
			},
			kTypeRota, {
				if( samples.size == 0, { ^min });
				d			= samples[ sampleIdx ];
				sampleIdx	= sampleIdx + rotaInc;
				if( sampleIdx < 0, {
					sampleIdx = sampleIdx.neg % samples.size;
					rotaInc	  = rotaInc.neg;
				}, { if( sampleIdx >= samples.size, {
					sampleIdx = max( 0, ((samples.size - 1) << 1) - sampleIdx );
					rotaInc	  = rotaInc.neg;
				})});
			},
			kTypeBrown, {
				d	= 1.0.rand;
				("brownian not yet implemented!!!").error;
			},
			kTypeTable, {
//			
//				final ProbabilityTable	pt	= getTable();
//				if( pt == null ) {
//					System.err.println( "table not found" );
//					d	= 0.0;
//				} else {
//					d	= pt.realize();
//				}
				"table not found".error;
				d = 0.0;
			},
//		default:
			{
				("!probability has invalid type "++type++"!").error;
				d = 0.0;
			}
		);
		
		^((d * (max - min)) + min);
	}
	
//	getMin {
//		final Number num = (Number) getMap().getValue( MAP_KEY_MIN );
//		if( num != null ) {
//			return num.doubleValue();
//		} else {
//			return 0.0;
//		}
//	}
//
//	getMax {
//		final Number num = (Number) getMap().getValue( MAP_KEY_MAX );
//		if( num != null ) {
//			return num.doubleValue();
//		} else {
//			return getMin();
//		}
//	}

//	private ProbabilityTable getTable()
//	{
//		final String tabName = (String) getMap().getValue( MAP_KEY_TABLE );
//		if( (tabName != null) && (doc != null) ) {
//			return( (ProbabilityTable) doc.tables.findByName( tabName ));
//		} else {
//			return null;
//		}
//	}
//
//	public void setDocument( Session doc )
//	{
//		this.doc = doc;
//	}

//    public void setMin( Object source, double min )
//    {
//		getMap().putValue( source, MAP_KEY_MIN, new Double( min ));
//	}
//
//	public void setMin( Object source, double min, CompoundEdit ce, LockManager lm, int doors )
//    {
//		ce.addEdit( new EditPutMapValue( source, lm, doors, getMap(), MAP_KEY_MIN, new Double( min )));
//    }
//
//    public void setMax( Object source, double max )
//    {
//		getMap().putValue( source, MAP_KEY_MAX, new Double( max ));
//	}
//
//    public void setMax( Object source, double max, CompoundEdit ce, LockManager lm, int doors )
//    {
//		ce.addEdit( new EditPutMapValue( source, lm, doors, getMap(), MAP_KEY_MAX, new Double( max )));
//    }
//
//	public void move( Object source, double delta, double tot_min, double tot_max,
//					  CompoundEdit ce, LockManager lm, int doors )
//	{
//		final double min = getMin();
//		final double max = getMax();
//	
//		if( delta > 0 ) {
//			delta = Math.min( delta, tot_max - max );
//		} else {
//			delta = Math.max( delta, tot_min - min );
//		}
//		setMax( source, max + delta, ce, lm, doors );
//		setMin( source, min + delta, ce, lm, doors );
//	}
//	
//	public void shiftStart( Object source, double delta, double tot_min, double tot_max,
//							CompoundEdit ce, LockManager lm, int doors )
//	{
//		final double min = getMin();
//		final double max = getMax();
//	
//		if( delta > 0 ) {
//			delta = Math.min( delta, max - min );
//		} else {
//			delta = Math.max( delta, tot_min - min );
//		}
//		setMin( source, min + delta, ce, lm, doors );
//	}
//	
//	public void shiftStop( Object source, double delta, double tot_min, double tot_max,
//						   CompoundEdit ce, LockManager lm, int doors )
//	{
//		final double min = getMin();
//		final double max = getMax();
//
//		if( delta > 0 ) {
//			delta = Math.min( delta, tot_max - max );
//		} else {
//			delta = Math.max( delta, min - max );
//		}
//		setMax( source, max + delta, ce, lm, doors );
//	}
//	
//	public Class getDefaultEditor()
//	{
//		return null;
//	}
//
//	public void debugDump( int indent )
//	{
//		super.debugDump( indent );
//		printIndented( indent, "--- table :" );
//		final ProbabilityTable table = getTable();
//		if( table != null ) {
//			table.debugDump( indent + 1 );
//		} else {
//			printIndented( indent + 1, "null" );
//		}
//	}
//
//	
// ---------------- MapManager.Listener interface ---------------- 
//
//	public void mapChanged( MapManager.Event e )
//	{
//		super.mapChanged( e );
//		
//		final Object source = e.getSource();
//		
//		if( source == this ) return;
//		
//		final Set	keySet		= e.getPropertyNames();
//		Object		val;
//
//		if( keySet.contains( MAP_KEY_TYPE )) {
//			val		= e.getManager().getValue( MAP_KEY_TYPE );
//			if( val != null ) {
//				for( int i = 0; i < TYPE_ITEMS.length; i++ ) {
//					if( val.equals( TYPE_ITEMS[ i ].getKey() )) {
//						type = i;
//						break;
//					}
//				}
//			}
//		}
//		if( keySet.contains( MAP_KEY_SAMPLES )) {
//			val		= e.getManager().getValue( MAP_KEY_SAMPLES );
//			if( val != null ) {
//				final StringTokenizer strTok = new StringTokenizer( val.toString() );
//				try {
//					synchronized( sampleSync ) {
//						samples			= new double[ strTok.countTokens() ];
//						seriesSamples	= new double[ samples.length ];
//						for( int i = 0; i < samples.length; i++ ) {
//							samples[ i ]= Math.max( 0.0, Math.min( 1.0, Double.parseDouble( strTok.nextToken() )));
//							seriesSamples[ i ] = samples[ i ];
//						}
//						sampleIdx		= 0;
//						seriesLen		= samples.length;
//						rotaInc			= 1;
//					} // synchronized( sampleSync )
//				}
//				catch( NumberFormatException e1 ) {
//					System.err.println( e1.getClass().getName() + " : " + e1.getLocalizedMessage() );
//				}
//			}
//		}
//	}
//
// ---------------- XMLRepresentation interface ---------------- 
//
//	public void toXML( org.w3c.dom.Document domDoc, Element node, Map options )
//	throws IOException
//	{
//		super.toXML( domDoc, node, options );
//	}

	fromXML { arg domDoc, node, options;
		var val, nl2, val2, val3, val4;
	
//		doc = (Session) options.get( Session.OPTIONS_KEY_SESSION );
//	
//		super.fromXML( domDoc, node, options );
		name = node.getAttribute( "name" );

		node.getChildNodes.do({ arg elem;
			if( elem.isKindOf( DOMElement ), {
				val = elem.getTagName;
	
				case
				// zero or one "map" element
				{ val == "map" }
				{
//					getMap().fromXML( domDoc, elem, options );
					nl2 = elem.getChildNodes;
					nl2.do({ arg elem2;
						if( elem2.isKindOf( DOMElement ), {
							val2 = elem2.getTagName;
							if( val2 == "entry", {
								val3 = elem2.getAttribute( "key" );
								val4 = elem2.getAttribute( "value" );
								case
								{ val3 == "type" }
								{
//									type = kTypeNames.indexOf( val4 );
									type = kTypeNames[ val4 ];
									if( type.isNil, {
										("Illegal prob type " ++ type).error;
									});
								}
								{ val3 == "min" }
								{
									min = val4.asFloat;
								}
								{ val3 == "max" }
								{
									max = val4.asFloat;
								}
								{ val3 == "samples" }
								{
									samples			= val4.split( $\  ).collect({ arg x; x.asFloat.clip( 0.0, 1.0 )});
									seriesSamples 	= samples.copy;
									sampleIdx		= 0;
									seriesLen		= samples.size;
									rotaInc			= 1;
								}
								{ val3 == "flags" }
								{
									flags = val4.asInteger;
								}
								{ val3 == "table" }
								{
									// NOT USED
								}
								{ val3 == "bf" }
								{
									// NOT USED
								}
								{
									("Warning: unknown map attribute: '"++val3++"'").error;
								};								
							}, {
								("Warning: unknown map element: '"++val2++"'").error;
							});
						});
					});
				}
				{
					("Warning: unknown element: '"++val++"'").error;
				};
			});
		});
	}
}

InertiaAtom : InertiaSessionObject {	// AbstractSessionObject
//	public final SessionCollection	probabilities		= new SessionCollection();
	var <probTime, <probVolume, <probPitch;
	var <>file, <fileStart = 0, <fileStop = 0, <fadeIn = 0, <fadeOut = 0;

//	public static final int			OWNER_PROB_OBJECT	=	0x2000;	// prob added the first time
//	public static final int			OWNER_PROB_INTERIEUR=	0x2001;	// min, max or table
//
//	private static final String		XML_VALUE_PROBS		= "probs";
//
//	public static final String		MAP_KEY_AUDIOFILE	= "audiofile";
//	public static final String		MAP_KEY_AFSTART		= "afstart";
//	public static final String		MAP_KEY_AFSTOP		= "afstop";
//	public static final String		MAP_KEY_FADEIN		= "fadein";
//	public static final String		MAP_KEY_FADEOUT		= "fadeout";

	*new {
		^super.new.prInit;
	}

	prInit {
//		propabilities	= IdentityDictionary.new;
//		probabilities.addListener( new SessionCollection.Listener() {
//			// when prob is first added
//			public void sessionCollectionChanged( SessionCollection.Event e )
//			{
//				getMap().dispatchOwnerModification( this, OWNER_PROB_OBJECT, e.getCollection() );
//			}
//
//			// when prob changes (min, max or table)
//			public void sessionObjectMapChanged( SessionCollection.Event e )
//			{
//				getMap().dispatchOwnerModification( this, OWNER_PROB_INTERIEUR, e.getCollection() );
//			}
//
//			// shouldn't occur ?
//			public void sessionObjectChanged( SessionCollection.Event e )
//			{
//				getMap().dispatchOwnerModification( this, OWNER_PROB_OBJECT, e.getCollection() );
//			}
//		});
//
//		final MapManager	map	= getMap();
//		final NumberSpace	spc	= new NumberSpace( 0.0, Double.POSITIVE_INFINITY, 0.001 );
//
//		map.putContext( this, MAP_KEY_AFSTART, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY,
//			MapManager.Context.TYPE_DOUBLE, spc, null, null, new Double( 0.0 )));
//		map.putContext( this, MAP_KEY_AFSTOP, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY,
//			MapManager.Context.TYPE_DOUBLE, spc, null, null, new Double( 0.0 )));	// XXX  test
//		map.putContext( this, MAP_KEY_AUDIOFILE, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY,
//			MapManager.Context.TYPE_FILE, null, null, null, new File( "" )));
//		map.putContext( this, MAP_KEY_FADEIN, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY,
//			MapManager.Context.TYPE_DOUBLE, spc, null, null, new Double( 0.05 )));
//		map.putContext( this, MAP_KEY_FADEOUT, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY,
//			MapManager.Context.TYPE_DOUBLE, spc, null, null, new Double( 0.1 )));
//			
//		probabilities.setName( XML_VALUE_PROBS );
	}
	
//	// note the name is the same and should
//	// thus be changed
//	public Atom duplicate()
//	{
//		Atom result = new Atom();
//		result.setName( this.getName() );
//		result.getMap().cloneMap( this.getMap() );
//		
//		assert result.probabilities.isEmpty() : "Atom.duplicate : probs not initially empty";
//		
//		for( int i = 0; i < this.probabilities.size(); i++ ) {
//			result.probabilities.add( this, ((Probability) this.probabilities.get( i )).duplicate() );
//		}
//		
//		return result;
//	}
	
//	public void createDefaultProbs( Session doc )
//	{
//		Probability prob;
//		NumberSpace	spc;
//		
//		prob = new Probability();
//		prob.setName( PROB_TIME );
//		spc	= new NumberSpace( 0.0, Double.POSITIVE_INFINITY, 0.01 );
//		prob.setMinSpace( spc, new Double( 0.0 ), null );
//		prob.setMaxSpace( spc, new Double( 0.0 ), null );
//		probabilities.add( this, prob );
//		
//		prob = new Probability();
//		prob.setName( PROB_VOLUME );
//		spc	= new NumberSpace( -96.0, 24.0, 0.1 );
//		prob.setMinSpace( spc, new Double( 0.0 ), null );
//		prob.setMaxSpace( spc, new Double( 0.0 ), null );
//		probabilities.add( this, prob );
//
//		prob = new Probability();
//		prob.setName( PROB_PITCH );
//		spc	= new NumberSpace( -72.0, 24.0, 0.01 );
//		prob.setMinSpace( spc, new Double( 0.0 ), null );
//		prob.setMaxSpace( spc, new Double( 0.0 ), null );
//		probabilities.add( this, prob );
//	}

	getTimeStart {
		^probTime.min;
	}

	getTimeStop {
		^probTime.max;
	}

	getTimeLength {
		^(this.getTimeStop - this.getTimeStart);
	}
	
	getFileLength {
		^(fileStop - fileStart);
	}

//    public void setFileStart( Object source, double afStart )
//    {
//		getMap().putValue( source, MAP_KEY_AFSTART, new Double( afStart ));
//    }
//
//    public void setFileStop( Object source, double afStop )
//    {
//		getMap().putValue( source, MAP_KEY_AFSTOP, new Double( afStop ));
//    }
	
	realize {
		^InertiaAtomRealized( this );
	}

// ---------------- XMLRepresentation interface ---------------- 
//
//	public void toXML( Document domDoc, Element node, Map options )
//	throws IOException
//	{
//		super.toXML( domDoc, node, options );	// map
//
//		Element			childElement, child2;
//		SessionObject	so;
//	
//		// prob collection
//		childElement = (Element) node.appendChild( domDoc.createElement( Session.XML_ELEM_COLL ));
//		childElement.setAttribute( Session.XML_ATTR_NAME, XML_VALUE_PROBS );
//		for( int i = 0; i < probabilities.size(); i++ ) {
//			so		= probabilities.get( i );
//			child2	= (Element) childElement.appendChild( domDoc.createElement( Session.XML_ELEM_OBJECT ));
//			if( so instanceof XMLRepresentation ) {
//				((XMLRepresentation) so).toXML( domDoc, child2, options );
//			}
//		}
//	}

	fromXML { arg domDoc, node, options;
		var nl, soList, nl2, val2, val3, val4, elem2, val, so;
		
		nl		= node.getChildNodes;
		soList	= List.new;

//		setName( node.getAttribute( XML_ATTR_NAME ));
		name = node.getAttribute( "name" );

		nl.do({ arg elem;
			if( elem.isKindOf( DOMElement ), {
				val = elem.getTagName;
	
				case
				// zero or one "map" element
				{ val == "map" }
				{
//					getMap().fromXML( domDoc, elem, options );
					nl2 = elem.getChildNodes;
					nl2.do({ arg elem2;
						if( elem2.isKindOf( DOMElement ), {
							val2 = elem2.getTagName;
							if( val2 == "entry", {
								val3 = elem2.getAttribute( "key" );
								val4 = elem2.getAttribute( "value" );
								case
								{ val3 == "audiofile" }
								{
									file = val4;
								}
								{ val3 == "afstart" }
								{
									fileStart = val4.asFloat;
								}
								{ val3 == "afstop" }
								{
									fileStop = val4.asFloat;
								}
								{ val3 == "fadein" }
								{
									fadeIn = val4.asFloat;
								}
								{ val3 == "fadeout" }
								{
									fadeOut = val4.asFloat;
								}
								{ val3 == "flags" }
								{
									flags = val4.asInteger;
								}
								{
									("Warning: unknown map attribute: '"++val3++"'").error;
								};								
							}, {
								("Warning: unknown map element: '"++val2++"'").error;
							});
						});
					});
				}
				// zero or more "object" elements
				{ val == "object" }
				{
					val2 = elem.getAttribute( "name" );
					("Warning: unknown session object type: '"++val2++"'").error;
				}
				{ val == "coll" }
				{
					val2 = elem.getAttribute( "name" );
					if( val2 == "probs", {	
//						soList.clear;
						nl2 = elem.getChildNodes;
						nl2.do({ arg elem2;
							val3 = elem2.getTagName;
							if( val3 == "object", {
								if( elem2.hasAttribute( "class" ), {
									val	= elem2.getAttribute( "class" );
								}, {	// #IMPLIED
									val = "de.sciss.inertia.session.Probability";
								});
//								so = (SessionObject) Class.forName( val ).newInstance();
//								if( so instanceof XMLRepresentation ) {
//									((XMLRepresentation) so).fromXML( domDoc, elem2, options );
//								}
so = InertiaProbability.new.fromXML( domDoc, elem2, options );
case
{ so.name == "time" }
{
	probTime = so;
}
{ so.name == "volume" }
{
	probVolume = so;
}
{ so.name == "pitch" }
{
	probPitch = so;
}
{
	("Warning: unknown probability: '"++so.name++"'").error;
};
	//							probabilities.getMap().copyContexts( null, MapManager.Context.FLAG_DYNAMIC,
	//								MapManager.Context.NONE_EXCLUSIVE, so.getMap() );
	// XXX
//								soList.add( so );
							});
						});
//						probabilities.addAll( soList );
	
					}, {
						("Warning: unknown session collection type: '"+val2+"'").error;
					});
				}
				// dtd doesn't allow other elements so we never get here
				{
					("Warning: unknown session node: '"++val++"'").error;
				};
			});
		}); // for root-nodes
	}
}

InertiaAtomRealized {
	var <startTime, <stopTime, <volume, <pitch;
	var <fileStart, <fadeIn, <fadeOut;
	var <file;

	*new { arg ptrn;
		^super.new.prInit( ptrn );
	}
	
	prInit { arg ptrn;
		startTime		= ptrn.probTime.realize;
		volume		= ptrn.probVolume.realize;
		pitch		= ptrn.probPitch.realize;
		fileStart		= ptrn.fileStart;
		stopTime		= startTime + ptrn.getFileLength;
		file			= ptrn.file;
		fadeIn		= min( ptrn.fadeIn, stopTime - startTime );
		fadeOut		= min( ptrn.fadeOut, stopTime - startTime - fadeIn );
	}
}

InertiaMolecule : InertiaSessionObject {	// AbstractSessionObject
	classvar <startComparator;
	classvar <stopComparator;

	var <atoms;

	var <start = 0.0, <stop = 0.0;	// in seconds

//	public static final int			OWNER_SPAN				=	0x3000;	// start, stop
//	public static final int			OWNER_ATOM_INTERIEUR	=	0x3001;
//	public static final int			OWNER_ATOM_COLL			=	0x3002;

	var	radiation;
	
//	private final Random			rnd				= new Random( System.currentTimeMillis() );
	
	var	collAtomSeries;	// pour random mixage

	var freeze		= false;
	var	frozenAtom	= nil;
		
//	private static final java.util.List	atomTimeKeys	= new ArrayList();
	
	*initClass {
		startComparator = { arg o1, o2;
			var n1, n2;
		
			if( o1.respondsTo( \start ), {
				n1 = o1.start;
				if( o2.respondsTo( \start ), {
					n2 = o2.start;
				}, { if( o2.isNumber, {
					n2 = o2; // .asInteger;
				}, {
					Error( "Class Cast : " ++ o2.class.name ).throw;
				})});
			}, { if( o1.isNumber, {
				n1 = o1; // .asInteger;
				if( o2.respondsTo( \start ), {
					n2 = o2.start;
				}, { if( o2.isNumber, {
					n2 = o2; // .asInteger;
				}, {
					Error( "Class Cast : " ++ o2.class.name ).throw;
				})});
			}, {
				Error( "Class Cast : " ++ o1.class.name ).throw;
			})});
			
			if( n1 < n2, -1, { if( n1 > n2, 1, 0 )});
//			^(n1 <= n2);
		};

		stopComparator = { arg o1, o2;
			var n1, n2;
		
			if( o1.respondsTo( \stop ), {
				n1 = o1.stop;
				if( o2.respondsTo( \stop ), {
					n2 = o2.stop;
				}, { if( o2.isNumber, {
					n2 = o2; // .asInteger;
				}, {
					Error( "Class Cast : " ++ o2.class.name ).throw;
				})});
			}, { if( o1.isNumber, {
				n1 = o1; // .asInteger;
				if( o2.respondsTo( \stop ), {
					n2 = o2.stop;
				}, { if( o2.isNumber, {
					n2 = o2; // .asInteger;
				}, {
					Error( "Class Cast : " ++ o2.class.name ).throw;
				})});
			}, {
				Error( "Class Cast : " ++ o1.class.name ).throw;
			})});
			
			if( n1 < n2, -1, { if( n1 > n2, 1, 0 )});
//			^(n1 <= n2);
		};

//		atomTimeKeys.add( Atom.MAP_KEY_AFSTART );
//		atomTimeKeys.add( Atom.MAP_KEY_AFSTOP );
	}
	
	*new {
		^super.new.prInit;
	}
	
	prInit {
		atoms			= List.new;	// new SessionCollection;
		radiation		= Array.fill( InertiaSession.kNumMovies, 0 );
		collAtomSeries	= List.new;
		
//		atoms.addListener( new SessionCollection.Listener() {
//			public void sessionCollectionChanged( SessionCollection.Event e )
//			{
//				synchronized( collAtomSeries ) {
//					collAtomSeries.clear();	// re-triggers schnucki phonics when getRealizedAtoms is called
//					frozenAtom = null;
//					recalcStartStop( e.getSource() );
//				}
//				getMap().dispatchOwnerModification( e.getSource(), OWNER_ATOM_COLL, e.getCollection() );
//			}
//			
//			// when atom changes
//			public void sessionObjectMapChanged( SessionCollection.Event e )
//			{
//				if( e.setContainsAny( atomTimeKeys )) {
////					System.err.println( "Molecule("+getName()+") : atom listener : sessionObjectMapChanged time keys" );
//					recalcStartStop( e.getSource() );
//				}
//			
//				// dispatching is performed inside recalcStartStop !
////				getMap().dispatchOwnerModification( this, OWNER_PROB_INTERIEUR, e.getCollection() );
//			}
//
//			// i.e. atom renamed, it's probs changed
//			public void sessionObjectChanged( SessionCollection.Event e )
//			{
//				if( e.getModificationType() == Atom.OWNER_PROB_INTERIEUR ) {
//					// the modified probs; e.getCollection() are the atoms!!
//					final java.util.List	coll			= (java.util.List) e.getModificationParam();
//					boolean					timeAffected	= false;
//					for( int i = 0; i < coll.size(); i++ ) {
//						if( ((Probability) coll.get( i )).getName().equals( Atom.PROB_TIME )) {
////							System.err.println( "yo its time!" );
//							timeAffected = true;
//							break;
//						}
//					}
//					if( timeAffected ) {
//						recalcStartStop( e.getSource() );
//					}
//				}
//				getMap().dispatchOwnerModification( e.getSource(), OWNER_ATOM_INTERIEUR, e.getCollection() );
//			}
//		});
//		
//		getMap().putContext( this, MAP_KEY_Y, new MapManager.Context( 0,
//			MapManager.Context.TYPE_DOUBLE, null, null, null, new Double( 0.0 )));
//		getMap().putContext( this, MAP_KEY_HEIGHT, new MapManager.Context( 0,
//			MapManager.Context.TYPE_DOUBLE, null, null, null, new Double( 1.0 )));
//		getMap().putContext( this, MAP_KEY_FREEZE, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY,
//			MapManager.Context.TYPE_BOOLEAN, null, null, null, new Boolean( false )));
//
//		atoms.setName( XML_VALUE_ATOMS );
	}
	
	iterateAtomSeries {
		var idx;
	
		if( freeze && frozenAtom.notNil, { ^frozenAtom });
	
		if( collAtomSeries.isEmpty, {
			if( atoms.isEmpty, { ^nil });
			collAtomSeries.addAll( atoms );
		});
		idx = collAtomSeries.size.rand; // rnd.nextInt( collAtomSeries.size() );
		
		frozenAtom = collAtomSeries.removeAt( idx );
		
		^frozenAtom;
	}
	
	prRecalcStartStop {
		var oldStart = start, oldStop = stop;

		start	= inf;
		stop	= 0.0;
					
		atoms.do({ arg a;
			start	= min( start, a.getTimeStart );
			stop	= max( stop, a.getTimeStop + a.getFileLength );
		});
		if( start == inf, { start = 0.0 });
		
//		if( ((start != oldStart) || (stop != oldStop)) && (source != null) ) {
//			getMap().dispatchOwnerModification( source, OWNER_SPAN, null );
//		}
	}

//	public void moveVertical( Object source, double delta, double min, double max,
//							  CompoundEdit ce, LockManager lm, int doors )
//	{
//		if( delta > 0 ) {
//			setY( source, Math.min( max - getHeight(), getY() + delta ), ce, lm, doors );
//		} else {
//			setY( source, Math.max( min, getY() + delta ), ce, lm, doors );
//		}
//	}
//
//	// note the name is the same and should
//	// thus be changed
//	public Molecule duplicate()
//	{
//		Molecule result = new Molecule();
//		result.setName( this.getName() );
//		result.getMap().cloneMap( this.getMap() );
//		
//		for( int i = 0; i < this.atoms.size(); i++ ) {
//			result.atoms.add( this, ((Atom) this.atoms.get( i )).duplicate() );
//		}
//		
//		return result;
//	}
//
//	public void shiftVerticalStart( Object source, double delta, double min, double max,
//									CompoundEdit ce, LockManager lm, int doors )
//	{
//		if( delta > 0 ) {
//			delta = Math.min( delta, getHeight() );
//			setY( source, getY() + delta, ce, lm, doors );
//			setHeight( source, getHeight() - delta, ce, lm, doors );
//		} else {
//			delta = Math.max( delta, -getY() + min );
//			setY( source, getY() + delta, ce, lm, doors  );
//			setHeight( source, getHeight() - delta, ce, lm, doors );
//		}
//	}
//	
//	public void shiftVerticalStop( Object source, double delta, double min, double max,
//								   CompoundEdit ce, LockManager lm, int doors )
//	{
//		if( delta > 0 ) {
//			delta = Math.min( delta, max - getHeight() - getY() );
//			setHeight( source, getHeight() + delta, ce, lm, doors );
//		} else {
//			delta = Math.max( delta, -getHeight() );
//			setHeight( source, getHeight() + delta, ce, lm, doors );
//		}
//	}
//	
	getRadiation { arg ch;
		^radiation[ ch ];
	}
	
	setRadiation { arg ch, nuRaad;
		var owldRaad		= radiation[ ch ];
		radiation[ ch ]		= nuRaad;
		
		^owldRaad;
	}
	
//	public void clear( Object source )
//	{
//		atoms.clear( source );
//		start	= 0.0;
//		stop	= 0.0;
//	}

//	getStart {
//		^start;
//	}
//	
//	getStop {
//		^stop;
//	}
	
	getLength {
		^(stop - start);
	}

//	public double getY()
//	{
//		final Number num = (Number) getMap().getValue( MAP_KEY_Y );
//		if( num != null ) {
//			return num.doubleValue();
//		} else {
//			return 0.0;
//		}
//	}
//	
//	public double getHeight()
//	{
//		final Number num = (Number) getMap().getValue( MAP_KEY_HEIGHT );
//		if( num != null ) {
//			return num.doubleValue();
//		} else {
//			return( 1.0 - getY() );
//		}
//	}
//
//	public void setY( Object source, double y, CompoundEdit ce, LockManager lm, int doors )
//	{
//		ce.addEdit( new EditPutMapValue( source, lm, doors, getMap(), MAP_KEY_Y, new Double( y )));
//	}
//	
//	public void setY( Object source, double y )
//	{
//		getMap().putValue( this, MAP_KEY_Y, new Double( y ));
//	}
//	
//	public void setHeight( Object source, double height, CompoundEdit ce, LockManager lm, int doors )
//	{
//		ce.addEdit( new EditPutMapValue( source, lm, doors, getMap(), MAP_KEY_HEIGHT, new Double( height )));
//	}
//
//	public void setHeight( Object source, double height )
//	{
//		getMap().putValue( this, MAP_KEY_HEIGHT, new Double( height ));
//	}
//	
//	public void debugDump( int indent )
//	{
//		super.debugDump( indent );
//		printIndented( indent, "start = "+start+"; stop = "+stop );
//		printIndented( indent, "--- atoms :" );
//		atoms.debugDump( indent + 1 );
//	}
	
// ---------------- MapManager.Listener interface ---------------- 
//
//	public void mapChanged( MapManager.Event e )
//	{
//		super.mapChanged( e );
//		
//		final Object source = e.getSource();
//		
//		if( source == this ) return;
//		
//		final Set	keySet		= e.getPropertyNames();
//		Object		val;
//
//		if( keySet.contains( MAP_KEY_FREEZE )) {
//			val		= e.getManager().getValue( MAP_KEY_FREEZE );
//			if( val != null ) {
//				freeze	= ((Boolean) val).booleanValue();
//			}
//		}
//	}

// ---------------- XMLRepresentation interface ---------------- 

//	public void toXML( org.w3c.dom.Document domDoc, Element node, Map options )
//	throws IOException
//	{
//		super.toXML( domDoc, node, options );
//
//		Element				childElement, child2;
//		NodeList			nl;
//		SessionObject		so;
//
//		// atom collection
//		childElement = (Element) node.appendChild( domDoc.createElement( XML_ELEM_COLL ));
//		childElement.setAttribute( XML_ATTR_NAME, XML_VALUE_ATOMS );
//		for( int i = 0; i < atoms.size(); i++ ) {
//			so		= atoms.get( i );
//			child2	= (Element) childElement.appendChild( domDoc.createElement( XML_ELEM_OBJECT ));
//			if( so instanceof XMLRepresentation ) {
//				((XMLRepresentation) so).toXML( domDoc, child2, options );
//			}
//		}
//	}

	fromXML { arg domDoc, node, options;
		var nl, nl2, elem2, key, val, val2, val3, val4, so, o, soList, rootNL;
	
//		super.fromXML( domDoc, node, options );
	
		soList		= List.new;
		rootNL		= node.getChildNodes;

//		atoms.pauseDispatcher();
		name = node.getAttribute( "name" );

		rootNL.do({ arg elem;
			if( elem.isKindOf( DOMElement ), {
				val		= elem.getTagName;

				case
				// zero or one "map" element
				{ val == "map" }
				{
					nl2 = elem.getChildNodes;
					nl2.do({ arg elem2;
						if( elem2.isKindOf( DOMElement ), {
							val2 = elem2.getTagName;
							if( val2 == "entry", {
								val3 = elem2.getAttribute( "key" );
								val4 = elem2.getAttribute( "value" );
								case
								{ val3 == "height" }
								{
									// NOT USED
								}
								{ val3 == "y" }
								{
									// NOT USED
								}
								{ val3 == "freeze" }
								{
									freeze = val4 == "true";
								}
								{ val3 == "flags" }
								{
									flags = val4.asInteger;
								}
								{
									("Warning: unknown map attribute: '"++val3++"'").error;
								};								
							}, {
								("Warning: unknown map element: '"++val2++"'").error;
							});
						});
					});
				}
				// zero or more "object" elements
				{ val == "object" }
				{
					("Warning: unknown session object type: '"++val++"'").error;
					
				}
				{ val == "coll" }
				{
					val2 = elem.getAttribute( "name" );

					if( val2 == "atoms", {
						soList.clear;
						nl = elem.getChildNodes;
						nl.do({ arg elem2;
							val3 = elem2.getTagName;
							if( val3 == "object", {
								if( elem2.hasAttribute( "class" ), {
									val4 = elem2.getAttribute( "class" );
								}, {	// #IMPLIED
									val4 = "de.sciss.inertia.session.Atom";
								});
//								so = (SessionObject) Class.forName( val ).newInstance();
//								if( so instanceof XMLRepresentation ) {
//									((XMLRepresentation) so).fromXML( domDoc, elem2, options );
//								}
so = InertiaAtom.new.fromXML( domDoc, elem2, options );
//								atoms.getMap().copyContexts( null, MapManager.Context.FLAG_DYNAMIC,
//																 MapManager.Context.NONE_EXCLUSIVE, so.getMap() );
// XXX
								soList.add( so );
							});
						});
						atoms.addAll( soList );
//						recalcStartStop( null );
						this.prRecalcStartStop;

					}, {
						("Warning: unknown session collection type: '"++val2++"'").error;
					});
				}
				// dtd doesn't allow other elements so we never get here
				{
					("Warning: unknown session node: '"++val++"'").error;
				};
			});
		}); // for root-nodes
//		finally {
//			atoms.resumeDispatcher();
//		}
	}
}

InertiaTrack : InertiaSessionObject {	// extends AbstractSessionObject
	var <molecules;
	var collMolecByStart, collMolecByStop;
	
	// radiation is a running incremental
	// counter which is used to determine which
	// molecule spans have already been calculated / processed.
	// each time the transport is started (supercolliderplayer
	// calling increaseRadiation()), the
	// counter is increased, thereby forcing all molecules
	// to appear to be virgin.
	// ETERNAL VIRGINITY, what a sick concept, dude!
	var radiation;

	*new {
		^super.new.prInit;
	}
	
	prInit {
		molecules 		= List.new;	// new SessionCollection
		collMolecByStart	= List.new;
		collMolecByStop	= List.new;
		radiation			= Array.fill( InertiaSession.kNumMovies, 0 );

//		molecules.addListener( new SessionCollection.Listener() {
//			public void sessionCollectionChanged( SessionCollection.Event e )
//			{
//				java.util.List collMolecs = e.getCollection();
//				switch( e.getModificationType() ) {
//				case SessionCollection.Event.ACTION_ADDED:
//					for( int i = 0; i < collMolecs.size(); i++ ) {
//						sortAddMolecule( (Molecule) collMolecs.get( i ));
//					}
//					break;
//
//				case SessionCollection.Event.ACTION_REMOVED:
//					synchronized( sync ) {
//						collMolecByStart.removeAll( collMolecs );
//						collMolecByStop.removeAll( collMolecs );
//					}
//					break;
//				
//				default:
//					break;
//				}
//			}
//
//			// when molec changes
//			public void sessionObjectMapChanged( SessionCollection.Event e )
//			{
//			}
//
//			// when molec bounds change
//			public void sessionObjectChanged( SessionCollection.Event e )
//			{
//				if( e.getModificationType() == Molecule.OWNER_SPAN ) {
//					resortMolecules( e.getCollection() );
//				}
//			}
//		});
//		
//		molecules.setName( XML_VALUE_MOLECULES );
	}

	/*
	 *	the algorithm for determining the
	 *	relevant molecules seems to be working
	 *	quite well : two lists are maintained
	 *	(collMolecByStart and collMolecByStop)
	 *	which are ordered by zone start and stop
	 *	; for display the common subset of all
	 *	elements in collMolecByStart whose
	 *	start position is smaller than the visual
	 *	span's stop and of all elements in collMolecByStop whose
	 *	stop position is greater than the visual
	 *	span's start is calculated, using
	 *	Collections.binarySearch both for getting
	 *	the sublists and for getting the common elements.
	 *	Note that List.retainAll() is comparably
	 *	hell slow for determining the common elements.
	 */
//	public java.util.List getMolecules( double startSec, double stopSec )
	getMolecules { arg startSec, stopSec;
		var collUntil, collFrom, collA, collB, comp, idx, o;
	
		idx		 = this.prBinarySearch( collMolecByStart, stopSec, InertiaMolecule.startComparator );
		if( idx < 0, { idx = (idx + 1).neg; });
//		collUntil	= collMolecByStart.subList( 0, idx );
		collUntil	= collMolecByStart.copyFromStart( idx - 1 );
		idx		 = this.prBinarySearch( collMolecByStop, startSec, InertiaMolecule.stopComparator );
		if( idx < 0, { idx = (idx + 1).neg });
//		collFrom	= collMolecByStop.subList( idx, collMolecByStop.size() );
		collFrom	= collMolecByStop.copyToEnd( idx );
		
		if( collUntil.size < collFrom.size, {
			collA  = collUntil;	// new ArrayList( collUntil );
			collB  = collFrom;
			comp   = InertiaMolecule.stopComparator;
		}, {
			collA  = collFrom;	// new ArrayList( collFrom );
			collB  = collUntil;
			comp   = InertiaMolecule.startComparator;
		});

		^collA.reject({ arg obj; this.prBinarySearch( collB, obj, comp ) < 0 });
	}

	prBinarySearch { arg coll, newObject, function;
		var index;
		var low	= 0;
		var high	= coll.size - 1;

		while({ 
			index  = (high + low) div: 2;
			low   <= high;
		}, {
			switch( function.value( coll.at( index ), newObject ),
			0, { ^index; },
			-1, {
				low = index + 1;
			},
			1, {
				high = index - 1;
			},
			{
				"Illegal result from comparator".error;
				^-1;
			});
		});
		^(low.neg - 1);	// as in java.util.Collections.binarySearch !
	}

	// inserts in collMolecByStart/Stop, NOT molecules
	// sync : call inside synchronized( sync ) !
	sortAddMolecule { arg molec;
		var idx;

		idx		= this.prBinarySearch( collMolecByStart, molec, InertiaMolecule.startComparator );
		if( idx < 0, { idx = (idx + 1).neg });
		collMolecByStart.insert( idx, molec );
		idx		= this.prBinarySearch( collMolecByStop, molec, InertiaMolecule.stopComparator );
		if( idx < 0, { idx = (idx + 1).neg });
		collMolecByStop.insert( idx, molec );
	}
	
	// sync: synchronizes on sync
	resortMolecules { arg collMolecs;
		collMolecByStart.removeAll( collMolecs );
		collMolecByStop.removeAll( collMolecs );

		collMolecs.do({ arg molec;
			this.sortAddMolecule( molec );
		});
	}

	increaseRadiation { arg ch;
		radiation[ ch ] = radiation[ ch ] + 1;
	}
	
	getRealizedAtoms { arg ch, startSec, stopSec;
		var collMolec, collRaz, ra;
	
		collMolec	= this.getMolecules( startSec, stopSec );
		collRaz		= List.new;

		// track muted
		if( this.isMuted, { ^collRaz; });
		
		collMolec.do({ arg molec;
			if( molec.isMuted.not, {
				if( molec.setRadiation( ch, radiation[ ch ]) < radiation[ ch ], {	// ok dis wan's not yet calculated
					ra	= molec.iterateAtomSeries.realize;
					if( ra.startTime >= startSec, {
						collRaz.add( ra );
					});
				}, {		// leave 'm to guantanamo white terrorism
	//if( SuperColliderPlayer.RAZ_DEBUG ) System.err.println( "already realized : "+this.getName()+"->"+molec.getName()+" @ ch "+ch );
				});
			});
		});
		^collRaz;
	}

//	public Class getDefaultEditor()
//	{
//		return null;
//	}
//	
//	public void clear( Object source )
//	{
//		synchronized( sync ) {
//			selectedAtoms.clear( source );
//			molecules.clear( source );
//			selectedMolecules.clear( source );
//			collMolecByStart.clear();
//			collMolecByStop.clear();
//		}
//	}
//	
//	public void verifyTimeSorting()
//	{
//		if( (collMolecByStart.size() != collMolecByStop.size()) ||
//			(collMolecByStart.size() != molecules.size()) ) {
//		
//			System.err.println( "List's corropted.  molecules.size() = "+ molecules.size()+
//				"; collMolecByStart.size() = "+collMolecByStart.size()+
//				"; collMolecByStop.size() = "+collMolecByStop.size() );
//		}
//	
//		double lastTime = 0.0;
//		Molecule molec;
//	
//		for( int i = 0; i < collMolecByStart.size(); i++ ) {
//			molec = (Molecule) collMolecByStart.get( i );
//			if( molec.getStart() < lastTime ) {
//				System.err.println( "collMolecByStart corrupted. Molec "+molec.getName()+" has start time "+
//					molec.getStart() + "; predecessor has " + lastTime );
//			}
//			lastTime = molec.getStart();
//		}
//
//		lastTime = 0.0;
//		for( int i = 0; i < collMolecByStop.size(); i++ ) {
//			molec = (Molecule) collMolecByStop.get( i );
//			if( molec.getStop() < lastTime ) {
//				System.err.println( "collMolecByStop corrupted. Molec "+molec.getName()+" has stop time "+
//					molec.getStop() + "; predecessor has " + lastTime );
//			}
//			lastTime = molec.getStop();
//		}
//	}
//	
//	public void debugDump( int indent )
//	{
//		super.debugDump( indent );
//		molecules.debugDump( indent + 1 );
//		printIndented( indent, "--- selected molecules: " );
//		for( int i = 0; i < selectedMolecules.size(); i++ ) {
//			printIndented( indent + 2, selectedMolecules.get( i ).getName() + " ; " );
//		}
//	}
	
// ---------------- XMLRepresentation interface ---------------- 

//	public void toXML( org.w3c.dom.Document domDoc, Element node, Map options )
//	throws IOException
//	{
//		super.toXML( domDoc, node, options );
//
//		Element				childElement, child2;
//		NodeList			nl;
//		SessionObject		so;
//
//		// molecule collection
//		childElement = (Element) node.appendChild( domDoc.createElement( XML_ELEM_COLL ));
//		childElement.setAttribute( XML_ATTR_NAME, XML_VALUE_MOLECULES );
//		for( int i = 0; i < molecules.size(); i++ ) {
//			so		= molecules.get( i );
//			child2	= (Element) childElement.appendChild( domDoc.createElement( XML_ELEM_OBJECT ));
//			if( so instanceof XMLRepresentation ) {
//				((XMLRepresentation) so).toXML( domDoc, child2, options );
//			}
//		}
//	}

	fromXML { arg domDoc, node, options;
		var nl, nl2, elem, elem2, key, val, so, soList, rootNL, val2, val3, val4;
	
//		super.fromXML( domDoc, node, options );
		name = node.getAttribute( "name" );
	
		soList		= List.new;
		rootNL		= node.getChildNodes;

		rootNL.do({ arg elem;
			if( elem.isKindOf( DOMElement ), {
				val = elem.getTagName;
	
				case 
				// zero or one "map" element
				{ val == "map" }
			 	{
					nl2 = elem.getChildNodes;
					nl2.do({ arg elem2;
						if( elem2.isKindOf( DOMElement ), {
							val2 = elem2.getTagName;
							if( val2 == "entry", {
								val3 = elem2.getAttribute( "key" );
								val4 = elem2.getAttribute( "value" );
								case
								{ val3 == "flags" }
								{
									flags = val4.asInteger;
								}
								{
									("Warning: unknown map attribute: '"++val3++"'").error;
								};								
							}, {
								("Warning: unknown map element: '"++val2++"'").error;
							});
						});
					});
				}
				// zero or more "object" elements
				{ val == "object" }
				{
					("Warning: unknown session object type: '"++val++"'" ).error;
				}
				{ val == "coll" }
				{
					val2 = elem.getAttribute( "name" );
					if( val2 == "molecules", {
						soList.clear;
						nl = elem.getChildNodes;
						nl.do({ arg elem2;
							val3 = elem2.getTagName;
							if( val3 == "object", {
								if( elem2.hasAttribute( "class" ), {
									val	= elem2.getAttribute( "class" );
								}, {	// #IMPLIED
									val = "de.sciss.inertia.session.Molecule";
								});
//								so = (SessionObject) Class.forName( val ).newInstance();
//								if( so instanceof XMLRepresentation ) {
//									((XMLRepresentation) so).fromXML( domDoc, elem2, options );
//								}
so = InertiaMolecule.new.fromXML( domDoc, elem2, options );
//								molecules.getMap().copyContexts( null, MapManager.Context.FLAG_DYNAMIC,
//																 MapManager.Context.NONE_EXCLUSIVE, so.getMap() );
// XXX
								soList.add( so );
							});
						});
						molecules.addAll( soList );
						this.resortMolecules( molecules );
	
					}, {
						("Warning: unknown session collection type: '"++val2++"'").error;
					});
				}
				// dtd doesn't allow other elements so we never get here
				{
					("Warning: unknown session node: '"++val++"'" ).error;
				}
			});
		}); // for root-nodes
	}
}


InertiaLayerManager {
// implements EventManager.Processor
	var <numLayers;
	var movies;		// array index = layer, element = movie quadrant
	var volumes;
	
	var elm;

	*new { arg numLayers;
		^super.new.prInit( numLayers );
	}
	
	prInit { arg argNumLayers;
		numLayers	= argNumLayers;
		movies		= Array.series( numLayers, 0 );
		volumes		= Array.fill( numLayers, 1.0 );
	}
	
	dispose {
		if( elm.notNil, { elm.dispose });
	}

	getVolume { arg quadrant;
		^volumes[ quadrant ];
	}

	setVolume { arg quadrant, volume;
		volumes[ quadrant ] = volume;
//		int quadrant;
//		for( layer = 0; layer < movies.length; layer++ ) {
//			if( movies[ layer ] == quadrant )
	}
	
	/**
	 *	Switches the movie/sound sources
	 *	of two layers. I.e., when movieX plays
	 *	to layerA, and movieY plays to layerB,
	 *	calling this method will make movieX
	 *	play to layerB, and movieY play to layerA.
	 *
	 *	@param	source	the object which is responsible for the switch
	 *	@param	layerA	the first layer of the switch operation
	 *	@param	layerB	the second layer of the switch operation
	 */
	switchLayers { arg source, layer1, layer2;
		var tmp;
		
		if( (layer1 < 0) || (layer1 >= numLayers) || (layer2 < 0) || (layer2 >= numLayers), {
			("switchLayers : illegal layers " ++ layer1 ++", " ++ layer2).error;
			^this;
		});
	
		tmp					= movies[ layer1 ];
		movies[ layer1 ]	= movies[ layer2 ];
		movies[ layer2 ]	= tmp;
		
		if( source.notNil && elm.notNil, { this.prDispatchLayerSwitch( source, layer1, layer2 )});
	}
	
	setFilter { arg source, layer, filterName;
		if( source.notNil && elm.notNil, { this.prDispatchLayerFilter( source, layer, filterName )});
	}

	addListener { arg listener;
		if( elm.isNil, {
			elm = EventManager( this );
		});
		elm.addListener( listener );
	}

	removeListener { arg listener;
		elm.removeListener( listener );
	}

//	public int getNumLayers()
//	{
//		return numLayers;
//	}
	
	getMovieForLayer { arg layer;
		^movies[ layer ];
	}

	getLayerForMovie { arg quadrant;
		^movies.indexOf( quadrant );
//		^block { arg break;
//			movies.do({ arg quad2, layer;
//				if( quad2 == quadrant, { break.value( layer )});
//			});
//			nil;
//		};
	}
	
	prDispatchLayerSwitch { arg source, layer1, layer2;
		elm.dispatchEvent( InertiaLayerManagerEvent( this, source, InertiaLayerManagerEvent.kLayersSwitched, layer1, layer2, nil ));
	}

	prDispatchLayerFilter { arg source, layer, filterName;
		elm.dispatchEvent( InertiaLayerManagerEvent( this, source, InertiaLayerManagerEvent.kLayersFiltered, layer, layer, filterName ));
	}
	
// --------------------- EventManager.Processor interface ---------------------
	
	processEvent { arg e;
		var listener;
		
		elm.countListeners.do({ arg i;
			listener = elm.getListener( i );
			switch( e.getID,
				InertiaLayerManagerEvent.kLayersSwitched, { listener.layersSwitched( e )},
				InertiaLayerManagerEvent.kLayersFiltered, { listener.layersFiltered( e )},
				{
					("Assertion Failed : illegal ID " ++ e.getID).error;
				}
			);
		});
	}

// ------------------ internal classses / interfaces ------------------
}	

InertiaLayerManagerEvent : BasicEvent {
	classvar <kLayersSwitched	= 0;
	classvar <kLayersFiltered	= 1;
			
	var layers, param, layer1, layer2;
	
	*new { arg layers, source, id, layer1, layer2, param;
		^super.new( source, id, thisThread.seconds ).prInitLME( layers, layer1, layer2, param );
	}
	
	prInitLME { arg argLayers, argLayer1, argLayer2, argParam;
		layers			= argLayers;
		layer1			= argLayer1;
		layer2			= argLayer2;
		param			= argParam;
	}
		
	getManager { ^layers }

	getParam { ^param }

	getFirstLayer { ^layer1 }

	getSecondLayer { ^layer2 }	
	
	/**
	 *  Returns false always at the moment
	 */
	incorporate { arg oldEvent;
		^false;
	}
}
