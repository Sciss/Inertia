/*
 *  AudioFileDescr.java
 *  de.sciss.io package
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
 *		21-May-05	created from de.sciss.eisenkraut.io.AudioFileDescr
 *		15-Jul-05	KEY_APPCODE
 *		08-Sep-05	added getFormatSuffix
 */

package de.sciss.io;

import java.io.File;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;

import de.sciss.gui.StringItem;

/**
 *  An <code>AudioFileDescr</code> is
 *  a data structure that describes the
 *  format of an <code>AudioFile</code>.
 *  It was public readable fields for
 *  common parameters such as sample rate
 *  and bitdepth. More specific features
 *  such as markers or gain chunks
 *  are stored using a <code>Map</code> object
 *  being accessed throught the <code>setProperty</code>
 *  and <code>getProperty</code> methods.
 *  A corresponding GUI element,
 *  the <code>AudioFileFormatPane</code> exists
 *  which presents the common fields to the user.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.22, 15-Sep-05
 *
 *  @see		AudioFile
 *  @see		de.sciss.meloncillo.gui.AudioFileFormatPane
 *
 *  @todo		all files are considered big endian at the
 *				moment which might be inconvenient on non
 *				mac systems because there are for example
 *				different endian versions for ircam and we
 *				cannot read the little endian one. aiff however
 *				is per se big endian and should therefore cause
 *				no trouble.
 */
public class AudioFileDescr
{
// -------- public Variablen --------
	/**
	 *  type value : undefined audio file format
	 */
	public static final int TYPE_UNKNOWN	= -1;
	/**
	 *  type value : apple aiff sound file format
	 */
	public static final int TYPE_AIFF		= 0;
	/**
	 *  type value : sun/next file format .au aka .snd
	 */
	public static final int TYPE_SND		= 1;
	/**
	 *  type value : ircam sound file format
	 */
	public static final int TYPE_IRCAM		= 2;
	/**
	 *  type value : wave (riff) sound file format
	 */
	public static final int TYPE_WAVE		= 3;
	/**
	 *  type value : raw (headerless) file format
	 */
	public static final int TYPE_RAW		= 4;

	/**
	 *  sampleFormat type : linear pcm integer
	 */
	public static final int FORMAT_INT		= 0;
	/**
	 *  sampleFormat type : pcm floating point
	 */
	public static final int FORMAT_FLOAT	= 1;

	/**
	 *	This denotes a corresponding
	 *	file on a harddisk. It may be null
	 */
	public File		file;

	// ---- fields supported by all formats ----
	
	/**
	 *  file format such as TYPE_AIFF
	 */
	public int		type;
	/**
	 *  number of channels (interleaved)
	 */
	public int		channels;
	/**
	 *  sampling rate in hertz
	 */
	public float	rate;
	/**
	 *  bits per sample
	 */
	public int		bitsPerSample;
	/**
	 *  sample number format, FORMAT_INT or FORMAT_FLOAT
	 */
	public int		sampleFormat;
	/**
	 *  sound file length in sample frames
	 */
	public long		length;			// in sampleframes
	
	/**
	 *  property key : loop region. value class = Region
	 *
	 *  @see	de.sciss.fscape.util.Region
	 */
	public static final Object KEY_LOOP		=   new Integer( 0 );
	/**
	 *  property key : marker list. value class = java.util.List whose elements are of class Marker
	 *
	 *  @see	de.sciss.fscape.util.Marker
	 */
	public static final Object KEY_MARKERS  =   new Integer( 1 );
	/**
	 *  property key : region list. value class = java.util.List whose elements are of class Region
	 *
	 *  @see	de.sciss.fscape.util.Region
	 */
	public static final Object KEY_REGIONS  =   new Integer( 2 );
	/**
	 *  property key : playback gain (multiplier). value class = Float
	 */
	public static final Object KEY_GAIN		=   new Integer( 3 );
	/**
	 *  property key : application specific code. value class = byte[];
	 *	note that AbstractApplication.getApplication().getMacOSCreator()
	 *	is used to <B>write</B> the app chunk, and when reading
	 *	anything but app-code from the current application is skipped!
	 */
	public static final Object KEY_APPCODE	=   new Integer( 4 );

// -------- protected Variablen --------

	private final java.util.Map properties;
	
	private static final boolean[][] supports = {
		{ true, false, false, false, false },	// Loop for AIFF, SND, IRCAM, WAVE, Raw
		{ true, false, false, true, false },	// Markers
		{ false, false, true, false, false },	// Regions
		{ true, false, false, true, false },	// Gain
		{ true, false, false, false, false }	// App-specific
	};
												
	private static final StringItem[] FORMAT_ITEMS  = {
		new StringItem( "aiff", "AIFF" ),
		new StringItem( "au", "NeXT/Sun AU" ),
		new StringItem( "ircam", "IRCAM" ),
		new StringItem( "wave", "WAVE" ),
		new StringItem( "raw", "Raw" )
	};
	private static final String[] FORMAT_SUFFICES	= { "aif", "au", "irc", "wav", "raw" };

	private static final String			msgPtrn		= "{0,choice,0#AIFF|1#NeXT/Sun AU|2#IRCAM|3#WAVE|4#Raw} audio, {1,choice,0#no channels|1#mono|2#stereo|2<{1,number,integer}-ch} {2,number,integer}-bit {3,choice,0#int|1#float} {4,number,0.###} kHz, {5,number,integer}:{6,number,00.000}";
	private static final MessageFormat	msgForm		= new MessageFormat( msgPtrn, Locale.US );  // XXX US locale to allow parsing via Double.parseDouble()
											
// -------- public Methoden --------

	/**
	 *  Construct a new <code>AudioFileDescr</code>
	 *  whose fields are all undefined
	 */
	public AudioFileDescr()
	{
		properties =   new HashMap();
	}
	
	/**
	 *  Construct a new <code>AudioFileDescr</code>
	 *  whose common fields are copied from a
	 *  template (type, channels, rate, bitsPerSample,
	 *  sampleFormat, length, properties).
	 *
	 *  @param  orig	a preexisting description whose
	 *					values will be copied to the newly
	 *					constructed description
	 *
	 *	@warning	things like the marker list are not duplicated,
	 *				they refer to the same instance
	 */
	public AudioFileDescr( AudioFileDescr orig )
	{
		this.file			= orig.file;
		this.type			= orig.type;
		this.channels		= orig.channels;
		this.rate			= orig.rate;
		this.bitsPerSample  = orig.bitsPerSample;
		this.sampleFormat   = orig.sampleFormat;
		this.length			= orig.length;
		synchronized( orig.properties ) {
			this.properties		= new HashMap( orig.properties );
		}
	}
	
	/**
	 *  Returns the file format type
	 *
	 *  @return the type of the file, e.g. TYPE_AIFF
	 */
	public int getType()
	{
		return type;
	}

	/**
	 *  Gets a specific property
	 *
	 *  @param  key the key of the property to query,
	 *				such as KEY_MARKERS
	 *  @return		the property's value or null
	 *				if this property doesn't exist.
	 *				the class of the property varies
	 *				depending on the property type. see
	 *				the key's description to find out what
	 *				kind of object is returned
	 */
	public Object getProperty( Object key )
	{
		synchronized( properties ) {
			return( properties.get( key ));
		}
	}

	/**
	 *  Sets a specific property. Use the
	 *  <code>isPropertySupported</code> method
	 *  to find out if the chosen file format can store
	 *  the property.
	 *
	 *  @param  key		the key of the property to set
	 *  @param  value   the properties value. Note that the
	 *					value is not checked at all. It is the
	 *					callers responsibility to ensure the value's
	 *					class is the one specified for the particular key.
	 *
	 *  @see	#isPropertySupported( Object )
	 */
	public void setProperty( Object key, Object value )
	{
		synchronized( properties ) {
			properties.put( key, value );
		}
	}

	/**
	 *  Reports if a sound file format can handle
	 *  a particular property.
	 *
	 *  @param  key		the key of the property to check
	 *  @return			<code>true</code> the sound file format
	 *					given by <code>getType()</code> supports
	 *					the property. <code>false</code> if not.
	 *					Note that if a property is not supported,
	 *					it is no harm to set it using <code>setProperty</code>,
	 *					it just won't be written to the sound file's header.
	 */
	public boolean isPropertySupported( Object key )
	{
		if( key instanceof Integer ) {
			int idx = ((Integer) key).intValue();
			if( idx >= 0 && idx < supports.length ) {
				if( type >= 0 && type < supports[idx].length ) {
					return supports[idx][type];
				}
			}
		}
		return false;
	}
	
	/**
	 *	Create a human readable text string describing
	 *  the audio file format.
	 *
	 *  @return a text string containing information about
	 *			the file format, resolution and sample rate,
	 *			channel number. Suitable for copying to a
	 *			GUI text label.
	 */
	public String getFormat()
	{
		int			millis;
		Object[]	msgArgs = new Object[7];

		msgArgs[0]  = new Integer( type );
		msgArgs[1]  = new Integer( channels );
		msgArgs[2]  = new Integer( bitsPerSample );
		msgArgs[3]  = new Integer( sampleFormat );
		msgArgs[4]  = new Float( rate / 1000 );
		millis		= (int) (AudioFileDescr.samplesToMillis( this, length ) + 0.5);
		msgArgs[5]  = new Integer( millis / 60000 );
		msgArgs[6]  = new Double( (double) (millis % 60000) / 1000 );

		return( msgForm.format( msgArgs ));
	}

	/**
	 *  Gets a list of items suitable to
	 *  attaching to a PrefComboBox, describing the
	 *  supported audio file formats.
	 *
	 *  @return a list of items for a PrefComboBox which
	 *			list all supported audio file formats.
	 *  
	 *  @see	de.sciss.meloncillo.gui.PrefComboBox	PrefComboBox to learn about the use of StringItems
	 */
	public static StringItem[] getFormatItems()
	{
		return FORMAT_ITEMS;
	}

	/**
	 *  Gets the suffix commonly
	 *	used for attaching to a file name of
	 *	the given format.
	 *
	 *	@param	type	format such as TYPE_AIFF, TYPE_RAW etc.
	 *
	 *  @return the suffix string such as "aif", "raw"
	 *			or <code>null</code> if the type was invalid.
	 */
	public static String getFormatSuffix( int type )
	{
		if( (type >= 0) && (type < FORMAT_SUFFICES.length) ) {
			return FORMAT_SUFFICES[ type ];
		} else {
			return null;
		}
	}

	/**
	 *  Utility method to convert milliseconds to sample frames
	 *  according to the given audio file format
	 *
	 *  @param  afd		the audio file description whose
	 *					rate field is used to do the conversion
	 *  @param  ms		arbitrary offset in milliseconds. note
	 *					that this doesn't have to be within the
	 *					range of the current length of the audio file.
	 *  @return the time offset which was specified in milliseconds,
	 *			converted to sample frames (round to integer value if needed).
	 */
	public static double millisToSamples( AudioFileDescr afd, double ms )
	{
		return( (ms / 1000) * ((double) afd.rate ));
	}

	/**
	 *  Utility method to convert sample frames to milliseconds
	 *  according to the given audio file format
	 *
	 *  @param  afd		the audio file description whose
	 *					rate field is used to do the conversion
	 *  @param  samples	arbitrary offset in sample frames. note
	 *					that this doesn't have to be within the
	 *					range of the current length of the audio file.
	 *  @return the time offset which was specified in sample frames,
	 *			converted to milliseconds (round to integer value if needed).
	 */
	public static double samplesToMillis( AudioFileDescr afd, long samples )
	{
		return( (double) samples / afd.rate * 1000 );
	}
}
// class AudioFileDescr