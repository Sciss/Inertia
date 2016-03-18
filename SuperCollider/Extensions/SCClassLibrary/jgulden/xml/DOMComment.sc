
/* 
 * SuperCollider3 source file "DOMComment.sc" 
 * Licensed under the GNU General Public License (GPL).
 */

// --- class DOMComment -------------------------------------------------------
// 
DOMComment : DOMCharacterData  {

    // --- new(owner, comment) : DOMComment -----------------------------------
    //       
    *new { arg owner, comment; // types DOMDocument, String          
        ^super.new.init(owner, DOMNode.node_COMMENT, "#comment", comment, "<!--", "-->", ""); // (start and end will be set to "<!" and ">" in case this is used as a dummy for document-type definitions)
    } // end new        


    // --- getComment() : String ----------------------------------------------
    //     
    getComment {        
        // getComment is an extension to the original DOM.
        // It is a synonym for getData.
        ^this.getData; // type String
    } // end getComment        


    // --- setComment(text) :  ------------------------------------------------
    //     
    setComment { arg text; // type String         
        // setComment is an extension to the original DOM.
        // It is a synonym for setData.
        this.setData(text); // type String
    } // end setComment        


    // --- markDummyDoctype() : void ------------------------------------------
    //   
    markDummyDoctype {        
        // this is called once to mark this as a non-standard comment (e.g. to treat <!DOCTYPE ...>)
        start = "<!";
        end = ">";
    } // end markDummyDoctype        


} // end DOMComment
