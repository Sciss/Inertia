////
////  AudioFileObject.java
////  Inertia
////
////  Created by Hanns Holger Rutz on 07.08.05.
////  Copyright 2005 __MyCompanyName__. All rights reserved.
////
//
//package de.sciss.inertia.session;
//
//import java.io.*;
//import java.net.*;
//import java.util.*;
//import javax.xml.parsers.*;
//import org.w3c.dom.*;
//import org.xml.sax.*;
//
//import de.sciss.io.*;
//import de.sciss.util.*;
//
//import de.sciss.inertia.gui.*;
//
//public class AudioFileObject
//extends AbstractSessionObject
//{
//	private AudioFileDescr	afd;
//
//	private static final String  MAP_KEY_AUDIOFILE	= "audiofile";
//
//	public AudioFileObject()
//	{
//		super();
//
//		afd	= new AudioFileDescr();
//	}
//
//	public AudioFileDescr getDescr()
//	{
//		return afd;
//	}
//
//	public void setDescr( Object source, AudioFileDescr afd )
//	{
//		this.afd	= afd;
//		getMap().putValue( this, MAP_KEY_AUDIOFILE, afd.file );
//		setName( afd.file.getName() );	// XXX not necessarily unique!
//	}
//
//	public Class getDefaultEditor()
//	{
//		return null;
//	}
//
//// ---------------- MapManager.Listener interface ----------------
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
//		if( keySet.contains( MAP_KEY_AUDIOFILE )) {
//			val		= e.getManager().getValue( MAP_KEY_AUDIOFILE );
//			if( val != null ) {
//				try {
//					final AudioFile af = AudioFile.openAsRead( (File) val );
//					afd = af.getDescr();
//					af.close();
//				}
//				catch( IOException e1 ) {
//					System.err.println( e1.getClass().getName() + " : " + e1.getLocalizedMessage() );
//					afd = null;
//				}
//			}
//		}
//	}
//
//// ---------------- XMLRepresentation interface ----------------
//
//	public void toXML( Document domDoc, Element node, Map options )
//	throws IOException
//	{
//		super.toXML( domDoc, node, options );
//	}
//
//	public void fromXML( Document domDoc, Element node, Map options )
//	throws IOException
//	{
//		super.fromXML( domDoc, node, options );
//	}
//}
