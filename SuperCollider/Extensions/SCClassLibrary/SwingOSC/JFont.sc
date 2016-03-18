/*
 *	JFont
 *	(SwingOSC classes for SuperCollider)
 *
 *	Replacement for the cocoa font class.
 *
 *	@author		SuperCollider Developers
 *	@author		Hanns Holger Rutz
 *	@version		0.45, 21-Jan-07
 */
JFont {
	classvar <>verbose = false;

	classvar <>default;
	
	classvar defaultSansFace, defaultSerifFace, defaultMonoFace;
	classvar names;
	
	var <>name, <>size, <>style;
		
	*initClass {
		if( thisProcess.respondsTo( \platform ) and: {ÊthisProcess.platform.name === \osx }, {
			default			= JFont( "LucidaGrande", 11 );
			defaultSansFace	= "LucidaGrande";
			defaultSerifFace	= "Times";
			defaultMonoFace	= "Monaco";
		}, {
			default			= JFont( "SansSerif", 12 );
			defaultSansFace	= "SansSerif";
			defaultSerifFace	= "Serif";
			defaultMonoFace	= "Monospaced";
		});
	}
		
	*new { arg name, size, style = 0;
		^super.newCopyArgs( name, size, style );
	}
	
	setDefault { default = this }
	
	*availableFonts { arg server;
		if( names.notNil, {Ê^names });
		
		// need to fetch names (asynchronous)
		if( thisThread.isKindOf( Routine ), {
			^this.prQueryFontNames( server );
		}, {
			"JFont.availableFonts : asynchronous call outside routine".warn;
			{ this.prQueryFontNames( server )}.fork( SwingOSC.clock );
			^[ "Dialog", "DialogInput", "Monospaced", "SansSerif", "Serif" ];		});
	}
	
	*antiAliasing_ { arg flag = false;
		if( verbose, { "JFont.antiAliasing : has no effect".error; });
	}
	
	*smoothing_ { arg flag = false;
		if( verbose, { "JFont.smoothing : has no effect".error; });
	}

	storeArgs { ^[ name, size, style ] }

	boldVariant {
		^this.class.new( name, size, style | 1 );
	}

	*defaultSansFace {
		^defaultSansFace;
	}
	
	*defaultSerifFace {
		^defaultSerifFace;
	}
	
	*defaultMonoFace {
		^defaultMonoFace;
	}

	*prQueryFontNames { arg server;
		var qid, fonts, numFonts, reply, off, chunkSize, fontNames, success = true;
		
		if( verbose, {Ê"JFont.availableFonts : querying...".postln });
		server	= server ?? SwingOSC.default;
		server.sendMsg( '/method', '[', '/local', \fnt, '[', '/new', 'java.util.ArrayList', ']', ']', \addAll,
			'[', '/method', 'java.util.Arrays', \asList,
				'[', '/methodr', '[', '/method', 'java.awt.GraphicsEnvironment', \getLocalGraphicsEnvironment, ']', \getAvailableFontFamilyNames, ']',
			']' );
		qid		= UniqueID.next;
		reply	= server.sendMsgSync([ '/query', qid, '[', '/method', \fnt, \size, ']' ], [ '/info', qid ]);
		if( reply.notNil, {
			numFonts	= reply[ 2 ];
		}, {
			"JFont.availableFonts : timeout".error;
			numFonts 	= 0;
			success	= false;
		});
		off		= 0;
		fontNames	= Array( numFonts );
		while({ (off < numFonts) && success }, {
			// 128 queries is about 4.5 KB sending and probably < 8 KB receiving
			// (worst case: all font names have a length of 64 chars)
			chunkSize	= min( 128, numFonts - off );
			reply	= server.sendMsgSync([ '/query' ] ++ Array.fill( chunkSize, { arg i; [ qid, '[', '/method', \fnt, \get, off + i, ']' ]}).flatten,
									  [ '/info', qid ]);
			if( reply.notNil, {
				chunkSize.do({ arg i; fontNames.add( reply[ (i << 1) + 2 ].asString )});
				off = off + chunkSize;
			}, {
				"JFont.availableFonts : timeout".error;
				success	= false; // leave loop
			});
		});
		server.sendMsg( '/free', \fnt );
		if( success, {Ênames = fontNames });
		if( verbose, {Ê"JFont.availableFonts : query done.".postln });
	}
}