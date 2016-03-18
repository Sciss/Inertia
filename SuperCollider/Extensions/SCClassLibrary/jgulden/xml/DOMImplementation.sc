
/* 
 * SuperCollider3 source file "DOMImplementation.sc" 
 * Licensed under the GNU General Public License (GPL).
 */

// --- class DOMImplementation ------------------------------------------------
// 
DOMImplementation {

    // --- attributes

    classvar instance; // type  DOMImplementation


    // --- instance() : DOMImplementation -------------------------------------
    //     
    *instance {        
        if ( DOMImplementation.instance == nil , {
            DOMImplementation.instance = DOMInstance.new;
        };)
        ^DOMImplementation.instance; // type  DOMImplementation
    } // end instance        


    // --- hasFeature(feature, version) : boolean -----------------------------
    //       
    hasFeature { arg feature, version; // types String, String          
        // This is completely useless and will most likely never be used. 
        // But it's part of the DOM specification...
        if ( (feature.compare("XML", true) == 0), {
            if ( version == nil, {
                ^true;
            },{
                if ( version.compare("1.0") <= 0, {
                    ^true;
                });
            });
        };)
        ^false;            
    } // end hasFeature        


} // end DOMImplementation
