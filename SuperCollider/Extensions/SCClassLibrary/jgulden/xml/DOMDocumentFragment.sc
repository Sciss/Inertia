
/* 
 * SuperCollider3 source file "DOMDocumentFragment.sc" 
 * Licensed under the GNU General Public License (GPL).
 */

// --- class DOMDocumentFragment ----------------------------------------------
// 
DOMDocumentFragment : DOMNode  {

    // --- new(owner) : DOMDocumentFragment -----------------------------------
    //      
    *new { arg owner; // type DOMDocument         
        ^super.new.init(owner, DOMNode.node_DOCUMENT_FRAGMENT, "#document-fragment");
    } // end new        


    // --- format(indentLevel) : String ---------------------------------------
    //      
    format { arg indentLevel; // type int         
        var xml = "";        
        
        if ( indentLevel == nil, { indentLevel = 0 });
        this.getChildNodes.do({ arg node;
            xml = xml ++ node.format(indentLevel);
        });
        ^xml;
    } // end format        


} // end DOMDocumentFragment
