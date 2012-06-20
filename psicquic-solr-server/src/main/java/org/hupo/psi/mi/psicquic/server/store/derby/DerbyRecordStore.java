package org.hupo.psi.mi.psicquic.server.store.derby;

/* =============================================================================
 # $Id::                                                                       $
 # Version: $Rev::                                                             $
 #==============================================================================
 #
 # DerbyRecordStore: apache derby-backed RecordStore implementation
 #
 #=========================================================================== */

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Clob;
import java.sql.PreparedStatement;

import org.w3c.dom.*;
import javax.xml.parsers.*;

import org.hupo.psi.mi.psicquic.server.*;
import org.hupo.psi.mi.psicquic.server.store.*;

import java.io.*;
import java.net.*;
import java.util.*;

import org.hupo.psi.mi.psicquic.util.JsonContext;
import org.hupo.psi.mi.psicquic.server.store.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

//------------------------------------------------------------------------------

public class DerbyRecordStore implements RecordStore{

    Log log;
    Connection dbcon = null;

    String rmgrURL = "";
    
    public DerbyRecordStore(){

        log = LogFactory.getLog( this.getClass() );

        try{
            Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        } catch( Exception ex ){
            ex.printStackTrace();
        }
    }

    public DerbyRecordStore( JsonContext context ){
        try{
            Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        } catch( Exception ex ){
            ex.printStackTrace();
        }
    }
    
    JsonContext context = null;
    
    public void setContext( JsonContext context ){
        this.context = context;
    }
    
    public void initialize(){
       
    }

    private void connect(){
        
        if( dbcon == null ){
            
            Log log = LogFactory.getLog( this.getClass() );
            log.info( "DerbyRecordDao:connect" );
            
            if( context != null && context.getJsonConfig() != null ){
                
                Map derbyCfg = (Map)
                    ((Map) context.getJsonConfig().get("store")).get("derby");
                try{
                    String derbydb = (String) derbyCfg.get("derby-db");
                    log.info( "               location: " + derbydb );

                    dbcon = 
                        DriverManager.getConnection( "jdbc:derby:" + 
                                                     derbydb + ";create=true");
                    
                } catch( Exception ex ){
                    ex.printStackTrace();
                }
                
                
                try{
                    Statement st = dbcon.createStatement();
                    st.setQueryTimeout(5);
                    ResultSet rs = 
                        st.executeQuery( " select count(*) from record" );
                    while( rs.next() ){
                        log.info( "   record count= " + rs.getInt(1) );
                    }
                } catch( Exception ex ){
                    
                    // missing table ?
                    log.info( "   creating record table" ); 
                    create();
                }
            }
        }
    }

    //--------------------------------------------------------------------------

    private void create(){
        try{
            Statement statement = dbcon.createStatement();
            statement.setQueryTimeout(30);  // set timeout to 30 sec.
            
            statement.executeUpdate( "create table record " +
                                     "(pk int generated always as identity," +
                                     " rid varchar(256),"+
                                     " format varchar(32), record clob )");

            statement.executeUpdate( "create index r_rid on record (rid)" );
            statement.executeUpdate( "create index r_ft on record (format)" );
            
        } catch( Exception ex ){
            ex.printStackTrace();
        }
    }

    public void shutdown(){
        if( dbcon != null ){      
            try{
                DriverManager.getConnection("jdbc:derby:;shutdown=true");
            } catch( Exception ex ){
                Log log = LogFactory.getLog( this.getClass() );
                log.info( "DerbyRecordDao(shutdown): " + ex);
            }
        }
    }
    
    //--------------------------------------------------------------------------
    //--------------------------------------------------------------------------

    public void addRecord( String rid, String record, String format ){
        connect();
        try{
            PreparedStatement pst = dbcon
                .prepareStatement( "insert into record (rid, record, format)" +
                                   " values (?,?,?)" );
            
            pst.setString( 1, rid );
            
            if( record != null && format != null ){
                pst.setString( 2, record );
                pst.setString( 3, format );
            
                pst.executeUpdate();
            } 
        }catch( Exception ex ){
            ex.printStackTrace();
        }
    }

    //--------------------------------------------------------------------------

    public String getRecord( String rid, String format ){
        connect();
        String record = "";

	Log log = LogFactory.getLog( this.getClass() );

        try{
            PreparedStatement pst = dbcon
                .prepareStatement( "select rid, record from record" +
                                   " where rid = ? and format= ?" );
            
            pst.setString( 1, rid );
            pst.setString( 2, format );
            ResultSet rs =  pst.executeQuery();
            while( rs.next() ){
                Clob rc = rs.getClob( 2 );
                record = rc.getSubString( 1L, 
                                          new Long(rc.length()).intValue() );
            } 

	log.info( "DerbyRecordDao(getRecord): recId=" + rid + "  record=" + record );

        }catch( Exception ex ){
            ex.printStackTrace();
        }
        return record;
    }

    //--------------------------------------------------------------------------
    
    public List<String> getRecordList( List<String> rid, String format ){
        
        connect();
        List<String> recordList = new ArrayList<String>();
        
        try{
            PreparedStatement pst = dbcon
                .prepareStatement( "select rid, record from record" +
                                   " where rid = ? and format = ?" );
            
            for( Iterator<String> i = rid.iterator(); i.hasNext(); ){
                pst.setString( 1, i.next() );
                pst.setString( 2, format );
                ResultSet rs =  pst.executeQuery();
                
                while( rs.next() ){
                    Clob rc = rs.getClob(2);
                    String record = 
                        rc.getSubString( 1L, 
                                         new Long(rc.length()).intValue() );
                    recordList.add( record );
                }
            }
        } catch( Exception ex ){
            ex.printStackTrace();
        }

        return recordList;
    }


    //--------------------------------------------------------------------------
    //--------------------------------------------------------------------------
    
    public void addFile( String format, String fileName, 
                         InputStream is ){


        Map trCfg = (Map) ((Map) context.getJsonConfig().get("store"))
            .get("transform");
        
        List rtrList = (List) trCfg.get( format );
        
        if( rtrList != null ){
            for( Iterator it = rtrList.iterator(); it.hasNext(); ){
                
                Map itr = (Map) it.next();

                if( ((String) itr.get("type")).equalsIgnoreCase("XSLT") &&
                    (Boolean) itr.get("active") ){
                    
                    if( itr.get( "transformer" ) == null ){

                        log.info( " Initializing transformer: format=" + format
                                  + " type=XSLT config=" + itr.get("config") );
                        
                        PsqTransformer rt =
                            new XsltTransformer( (Map) itr.get("config") );
                        itr.put( "transformer", rt );
                    }

                    PsqTransformer rt 
                        = (PsqTransformer) itr.get( "transformer" );
                    rt.start( fileName, is );
		    
		    while( rt.hasNext() ){
					
			Map cdoc= rt.next();
			String recId = (String) cdoc.get( "recId" );
			NodeList fl = (NodeList) cdoc.get( "dom" );
		       
			String vStr = null;
			String vType = (String) itr.get("view");

			for( int j = 0; j< fl.getLength() ;j++ ){
			    if( fl.item(j).getNodeName().equals( "field") ){
				Element fe = (Element) fl.item(j);
				String name = fe.getAttribute("name");
				String value = fe.getFirstChild().getNodeValue();
				
				if( name.equals( "recId" ) ){
				    recId = value;
				}
				if( name.equals( "view" ) ){
				    vStr= value;
				}
			    }
			}
			
			try{
			    this.add( recId, vType, vStr );
			}catch( Exception ex ){
			    ex.printStackTrace();
			}
		    }
		}
            }            
        }
    }

    private void add( String pid, String vType, String view ){
        
        log.info( "PID=" + pid ); 
        log.info( "  VTP=" + vType ); 
        log.info( "  VIEW=" + view.substring(0,24) + "..." ); 

        // add record
        //-----------
        
        try{
            String postData = URLEncoder.encode("op", "UTF-8") + "="
                + URLEncoder.encode( "add", "UTF-8");
            postData += "&" + URLEncoder.encode("pid", "UTF-8") + "="
                + URLEncoder.encode( pid, "UTF-8");
            postData += "&" + URLEncoder.encode("vt", "UTF-8") + "="
                + URLEncoder.encode( vType, "UTF-8");
            
            postData += "&" + URLEncoder.encode("vv", "UTF-8") + "="
                + URLEncoder.encode( view, "UTF-8");

            String rmgrUrlStr = (String) 
                ((Map) context.getJsonConfig().get("store")).get("derby");            

            URL url = new URL( rmgrUrlStr );
            URLConnection conn = url.openConnection();
            conn.setDoOutput( true );
            OutputStreamWriter wr =
                new OutputStreamWriter( conn.getOutputStream() );
            wr.write(postData);
            wr.flush();
            
            // Get the response
            BufferedReader rd =
                new BufferedReader( new InputStreamReader(conn.getInputStream()));
            String line;

            log.info( "  Response:" );
            while ((line = rd.readLine()) != null) {           
                log.info( line );
            }
            wr.close();
            rd.close();
        } catch (Exception e) {
        }
    }
}