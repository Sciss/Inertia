///*
// *  OSCBufSetNMessage.java
// *  ---
// *
// *  Copyright (c) 2004-2005 Hanns Holger Rutz. All rights reserved.
// *
// *	This software is free software; you can redistribute it and/or
// *	modify it under the terms of the GNU General Public License
// *	as published by the Free Software Foundation; either
// *	version 2, june 1991 of the License, or (at your option) any later version.
// *
// *	This software is distributed in the hope that it will be useful,
// *	but WITHOUT ANY WARRANTY; without even the implied warranty of
// *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
// *	General Public License for more details.
// *
// *	You should have received a copy of the GNU General Public
// *	License (gpl.txt) along with this software; if not, write to the Free Software
// *	Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
// *
// *
// *	For further information, please contact Hanns Holger Rutz at
// *	contact@sciss.de
// *
// *
// *  Changelog:
// *		17-Dec-05	copied from Meloncillo
// */
//
//package de.sciss.inertia.debug;
//
//import java.io.*;
//import java.nio.*;
//import java.util.*;
//
//import de.sciss.net.*;
//
///**
// *  Specialized OSC message class
// *  for float array buffer transfers.
// *
// *  @author		Hanns Holger Rutz
// *  @version	0.69, 24-Dec-04
// */
//public class OSCBufSetNMessage
//extends SpecificOSCMessage
//{
//    private float[] floatArray = null;
//    private int bufNum, startIdx, numFrames, arrayOffset;
//    private static final byte[] HEADER = "/b_setn\u0000,iii".getBytes();
//    private static final String NAME = "/b_setn";
//
//
//    //  [ "/b_setn", bufNum, startIdx, numFrames, float1, float2, float3 ... ]
//	public OSCBufSetNMessage( int bufNum, int startIdx, int numFrames,
//                              float[] floatArray, int arrayOffset )
//	{
//		super( NAME, NO_ARGS );
//
//        this.bufNum     = bufNum;
//        this.startIdx   = startIdx;
//        this.numFrames  = numFrames;
//        this.floatArray = floatArray;
//        this.arrayOffset= arrayOffset;
//	}
//
//    public OSCBufSetNMessage()
//    {
//        super( NAME, NO_ARGS );
//    }
//
//    public int getBufferIndex()
//    {
//        return bufNum;
//    }
//
//    public int getStartOffset()
//    {
//        return startIdx;
//    }
//
//	// XXX should be getNumSamples (frameNum * channels) ??
//    public int getFrameNumber()
//    {
//        return numFrames;
//    }
//
//    public float[] getFloatArray()
//    {
//        return floatArray;
//    }
//
//	public int getSize()
//	throws IOException
//	{
//		return( 8 + ((4 + numFrames + 4) & ~3) + ((3 * numFrames) << 2) );
//	}
//
//	public OSCMessage decodeSpecific( ByteBuffer b )
//	throws BufferUnderflowException, IOException
//	{
//		int                 i, pos1, pos2;
//		byte                type;
//		ArrayList           args;
//		byte[]              blob;
//        OSCBufSetNMessage   msg;
//		FloatBuffer         fb;
//
////System.err.println( "/b_setn decoding" );
//
//        // ',iii'
//		if( b.getInt() != 0x2C696969 ) throw new OSCException( OSCException.FORMAT, null );
//
//        pos1 = b.position();    // 'ffff'
//        for( i = b.getInt(); i == 0x66666666; i = b.getInt() ) ;
//        msg  = new OSCBufSetNMessage();
//        msg.numFrames = b.position() - 4 - pos1;
//        switch( i ) {
//        case 0x66666600:
//            msg.numFrames += 3;
//            break;
//        case 0x66660000:
//            msg.numFrames += 2;
//            break;
//        case 0x66000000:
//            msg.numFrames += 1;
//            break;
//        case 0x00000000:
//            break;
//        default:
//            if( (i & 0xFF) != 0 ) {
//                skipToValues( b );
//            }
//            break;
//        }
//
//        msg.bufNum      = b.getInt();
//        msg.startIdx    = b.getInt();
//
//        if( msg.numFrames != b.getInt() ) throw new OSCException( OSCException.FORMAT, null );
//
//        fb              = b.asFloatBuffer();
//		msg.floatArray  = new float[ msg.numFrames ];
//        msg.arrayOffset = 0;
////System.err.println( "decoding float array : "+fb.position()+" / "+fb.limit()+" / "+msg.numFrames );
//        fb.get( msg.floatArray, 0, msg.numFrames );
//        b.position( b.position() + (msg.numFrames << 2) );
//
//        return msg;
//	}
//
//	public void encode( ByteBuffer b )
//	throws BufferOverflowException, IOException
//	{
//		int         i, j;
//        FloatBuffer fb;
//
//		b.put( HEADER );
//        for( i = 0; i < numFrames; ) {
//            j = Math.min( numFrames - i, FLOATTAGS.length );
//            b.put( FLOATTAGS, 0, j );
//            i += j;
//        }
//		terminateAndPadToAlign( b );
//
//        b.putInt( bufNum );
//        b.putInt( startIdx );
//        b.putInt( numFrames );
//        fb = b.asFloatBuffer();
//        fb.put( floatArray, arrayOffset, numFrames );
//        b.position( b.position() + (numFrames << 2) );
//	}
//}