
/* 
 * SuperCollider3 source file "DOMCDATASection.sc" 
 * Licensed under the GNU General Public License (GPL).
 */

// --- class DOMCDATASection --------------------------------------------------
// 
DOMCDATASection : DOMCharacterData  {

    // --- new(owner, cdata) : DOMCDATASection --------------------------------
    //       
    *new { arg owner, cdata; // types DOMDocument, String          
        ^super.new.init(owner, DOMNode.node_CDATA_SECTION, "#cdata-section", cdata, "<![CDATA[", "]]>", "");
    } // end new        


} // end DOMCDATASection
