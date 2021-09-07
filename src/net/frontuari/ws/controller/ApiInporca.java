package net.frontuari.ws.controller;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.ParseException;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.compiere.model.MProduction;
import org.compiere.model.MProductionLine;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


@Path("/mfg-orders/")
public class ApiInporca {
	private boolean debug=true;
	private static CLogger log = CLogger.getCLogger(ApiInporca.class);
	JSONObject salida= new JSONObject("{\"success\":false,\"message\":null}");
	@POST
	@Path("/")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response app(String x) {
		//System.out.println(x);
		ResultSet rs;
		String sql;
		
		JSONObject obj = new JSONObject(x);
		JSONObject batch = obj.getJSONObject("batch");
		
		String production_order_code=batch.getString("production_order_code");
		sql="SELECT * FROM pp_order WHERE documentno='"+production_order_code+"'";
		System.out.println(sql);
		rs=q(sql);
		try {
		
			if(rs.next()) {
				  return registerProduction(batch,rs);
				  
			}else {
				return msj("Orden Nro. "+production_order_code+" no encontrada.",false);
				
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 

		
		
		
		
		System.out.println(production_order_code);
		
		return null;
	}
	
	private Response registerProduction(JSONObject b,ResultSet rs) throws SQLException, JSONException, ParseException {
		
		MProduction pro = new MProduction(Env.getCtx(),null,null);
		String DocumentNo=b.getString("production_order_code");
		//int M_Production_ID=b.getInt("id");
		int M_Product_ID=rs.getInt("m_product_id");
		Timestamp start_date=dateFormat(b.getString("start_date"));
		Timestamp end_date=dateFormat(b.getString("end_date"));
		int c_uom_id=rs.getInt("c_uom_id");
		int m_warehouse_id=rs.getInt("m_warehouse_id");
		int PP_Order_ID=rs.getInt("pp_order_id");
		int AD_Org_ID=rs.getInt("AD_Org_ID");
		//-----extract M_LOCATOR_ID-------
		String sql="SELECT l.m_locator_id FROM m_locator l INNER JOIN m_warehouse w USING(m_warehouse_id) WHERE w.m_warehouse_id='"+m_warehouse_id+"'";
		System.out.println(sql);
		ResultSet rsL =q(sql);
		int M_Locator_ID = 0;
		if(rsL.next()) {
			M_Locator_ID=rsL.getInt("m_locator_id");
		}
		//-------------------------------
		JSONArray batch_hopper_lots=b.getJSONArray("batch_hopper_lots");
		
		//batch_hopper_lots.length();
		
		//pro.setM_Production_ID(M_Production_ID);
		pro.setDocumentNo(DocumentNo);
		pro.setM_Product_ID(M_Product_ID);
		pro.setDatePromised(start_date);
		pro.setMovementDate(end_date);
		pro.setM_Locator_ID(M_Locator_ID);
		pro.setIsCreated("Y");
		
		pro.set_Attribute("C_UOM_ID", c_uom_id);
		pro.set_Attribute("PP_Order_ID", PP_Order_ID);
		
		
		pro.set_Attribute("TrxType", "P");
		pro.setAD_Org_ID(AD_Org_ID);
		if(pro.save()) {
			int M_Production_ID=pro.get_ID();
			MProductionLine proL = new MProductionLine(Env.getCtx(),null,null);
			
			
			//--------------Registrar mismo producto
			BigDecimal monto = BigDecimal.valueOf(0.00);
            proL.setM_Production_ID(M_Production_ID);
            proL.setAD_Org_ID(AD_Org_ID);
            proL.setM_Product_ID(M_Product_ID);
            proL.setDescription("");
            proL.setLine(0);
            proL.setQtyUsed(monto);
            proL.setMovementQty(monto);
            proL.setIsEndProduct(true);
            proL.setM_Locator_ID(M_Locator_ID);
            proL.save();
			
			
			//---------------------------------------
			
			
			for (int i = 0; i < batch_hopper_lots.length(); i++) {
				MProductionLine proE = new MProductionLine(Env.getCtx(),null,null);
			    JSONObject obj = batch_hopper_lots.getJSONObject(i);
			    String lProductCode		=obj.getString("product_code");
			    int lproduct_id=0;
			    sql="SELECT m_product_id FROM m_product p WHERE p.value='"+lProductCode+"'";
			    ResultSet rsP=q(sql);
			    if(rsP.next()) {
			    	lproduct_id=rsP.getInt("m_product_id");
			    }else {
			    	return msj("Producto "+lProductCode+" no encontrado",false);
			    }
			    //String lproduct_lot_code=obj.getString("product_lot_code");
	            BigDecimal lreal_amount	=obj.getBigDecimal("real_amount");
	            BigDecimal b1 = new BigDecimal("-1");
	            BigDecimal MovementQty = lreal_amount.multiply(b1);
	            //int lbatch_id		=obj.getInt("batch_id");
	            int lhopper_id		=obj.getInt("hopper_id");
	            String lhopper_name	=obj.getString("hopper_name");
	            
	            //String lproduct_code=obj.getString("product_code");
	            //int lproduct_lot_id	=obj.getInt("product_lot_id");
	            
	            
	            proE.setM_Production_ID(M_Production_ID);
	            proE.setAD_Org_ID(AD_Org_ID);
	            proE.setM_Product_ID(lproduct_id);
	            proE.setDescription(lhopper_name);
	            proE.setLine(lhopper_id);
	            proE.setQtyUsed(lreal_amount);
	            proE.setMovementQty(MovementQty);
	            proE.setIsEndProduct(false);
	            proE.setM_Locator_ID(M_Locator_ID);
	            proE.save();
	            
	          
			}			
			return msj("Guardado exitosamente",true);
			
		}else {
			return msj("La orden ya existe",false);
		}
	}
	
	private void d(Object msj) {
		if(debug) {
			System.out.println(msj);
		}
		
	}
	
	private Response msj(String msj,boolean tipo) {
		
    	
    	salida.put("message", msj);
    	if(tipo) {
    		salida.put("success", true);
    		return Response.status(200).entity(salida.toString()).build();
    	}else {
    		log.severe("Error API BATCH: "+msj);
    		return Response.status(422).entity(salida.toString()).build();
    	}
    	
    	
	}
	private Timestamp dateFormat(String timestampAsString) {
		System.out.println(timestampAsString);
		Timestamp ts = Timestamp.valueOf(timestampAsString.replace("T"," ").replace("-04:00",""));
		return ts;
	}
	private ResultSet q(String sql) {
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		
		pstmt = DB.prepareStatement(sql, null);
		try {
			rs = pstmt.executeQuery();
			return rs;
		} catch (SQLException e) {		
			e.printStackTrace();
			return null;
			
		}	
	}

}


