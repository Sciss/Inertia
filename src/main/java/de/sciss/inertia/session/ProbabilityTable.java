//
//  ProbabilityTable.java
//  Inertia
//
//  Created by Hanns Holger Rutz on 07.08.05.
//  Copyright 2005 __MyCompanyName__. All rights reserved.
//

package de.sciss.inertia.session;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.*;

import de.sciss.io.*;
import de.sciss.util.*;

import de.sciss.inertia.gui.*;

public class ProbabilityTable
extends AbstractSessionObject
{
	public float[]	density;
	public int		percentile25, median, percentile75;	// = table indices
	
	private static final Random	rnd	= new Random( System.currentTimeMillis() );

	protected static final String	SUBDIR		= "prob";
	protected static final String	SUFFIX		= ".aif";

	public ProbabilityTable()
	{
		super();

		density			= new float[ 1024 ];

		fillEqual();
		normalize();
	}

	public void fillEqual()
	{
		float f1		= 1.0f / density.length;
		
		for( int i = 0; i < density.length; i++ ) {
			density[ i ]	= f1;
		}
		percentile25	= (density.length + 3) / 4;		// wird aufgerundet!
		median			= (density.length + 1) / 2;
		percentile75	= (density.length * 3 + 3) / 4;
	}
	
	public void fillGaussian()
	{
		throw new UnsupportedOperationException( "not yet implemented" );
//		median			= (density.length + 1) / 2;
	}

	public void fillTriangle()
	{
		throw new UnsupportedOperationException( "not yet implemented" );
//		median			= (density.length + 1) / 2;
	}
	
	public void normalize()
	{
		double		sum	= 0.0;
		final float f1;	
		
		for( int i = 0; i < density.length; i++ ) {
			sum += density[ i ];
		}
		f1	= (float) (1.0 / sum);
		for( int i = 0; i < density.length; i++ ) {
			density[ i ] *= f1;
		}
	}

	public double realize()
	{
		double	white	= rnd.nextDouble();
		double	sum		= 0.0;
		
		// XXX should use percentile for improved speed!
		for( int i = 0; i < density.length; i++ ) {
			if( sum >= white ) return( (double) i / (double) density.length );
			sum += density[ i ];
		}
		return 1.0;
	}
	
	public void updatePercentiles()
	{
		int		i;
		double	sum	= 0.0;
	
		for( i = 0; i < density.length; i++ ) {
			sum += density[ i ];
			if( sum >= 0.25 ) break;
		}
		percentile25	= i;
		for( ; i < density.length; i++ ) {
			sum += density[ i ];
			if( sum >= 0.5 ) break;
		}
		median			= i;
		for( ; i < density.length; i++ ) {
			sum += density[ i ];
			if( sum >= 0.75 ) break;
		}
		percentile75	= i;
	}
	
	public Class getDefaultEditor()
	{
		return ProbabilityTableEditor.class;
	}

// ---------------- XMLRepresentation interface ---------------- 

	/** 
	 *  Additionally saves the sensitivity tables
	 *  to extra files in the folder specified through
	 *  <code>setDirectory</code>. One <code>InterleavedStreamFile</code>s
	 *  is used for each table, because table sizes might
	 *  differ from each other. The file name's are
	 *  deduced from the receiver's logical name and special
	 *  suffix.
	 *
	 */
	public void toXML( Document domDoc, Element node, Map options )
	throws IOException
	{
		super.toXML( domDoc, node, options );
	
		final AudioFile			af;
		final float[][]			frameBuf	= new float[ 1 ][];
		final File				dir			= new File( (File) options.get(
												XMLRepresentation.KEY_BASEPATH ), SUBDIR );
		final AudioFileDescr	afd;
		
		if( !dir.isDirectory() ) IOUtil.createEmptyDirectory( dir );
		
		afd					= new AudioFileDescr();
		afd.type			= AudioFileDescr.TYPE_AIFF;
		afd.channels		= 1;
		afd.rate			= 1000.0f;	// XXX
		afd.bitsPerSample	= 32;
		afd.sampleFormat	= AudioFileDescr.FORMAT_FLOAT;
		afd.file			= new File( dir, getName() + SUFFIX );
		af					= AudioFile.openAsWrite( afd );
					  
		frameBuf[ 0 ]		= density;
		af.writeFrames( frameBuf, 0, density.length );
		af.truncate();
		af.close();
	}

	/** 
	 *  Additionally recalls the sensitivity tables
	 *  from extra files in the folder specified through
	 *  <code>setDirectory</code>. One <code>InterleavedStreamFile</code>s
	 *  is used for each table, because table sizes might
	 *  differ from each other. The file name's are
	 *  deduced from the receiver's logical name and special
	 *  suffix.
	 *
	 */
	public void fromXML( Document domDoc, Element node, Map options )
	throws IOException
	{
		super.fromXML( domDoc, node, options );

		final AudioFile	af;
		final float[][]	frameBuf	= new float[ 1 ][];
		final int		size;

		// read the tables from a named getName() in the directory getDirectory()

		af = AudioFile.openAsRead( new File( new File( (File) options.get(
				XMLRepresentation.KEY_BASEPATH ), SUBDIR ), getName() + SUFFIX ));
			
		size = (int) af.getFrameNum();
		if( size != density.length ) {
			density = new float[ size ];
		}
		frameBuf[ 0 ] = density;
		af.readFrames( frameBuf, 0, size );
		af.close();
	}
}
