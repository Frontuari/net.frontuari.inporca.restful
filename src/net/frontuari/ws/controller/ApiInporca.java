package net.frontuari.ws.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.ParseException;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.compiere.model.MProduct;
import org.compiere.model.MProduction;
import org.compiere.model.MProductionLine;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.eevolution.model.MPPProductBOM;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


@Path("/mfg-orders/")
public class ApiInporca {
	private static CLogger log = CLogger.getCLogger(ApiInporca.class);
	JSONObject salida= new JSONObject("{\"success\":false,\"message\":null}");
	@POST
	@Path("/")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response app(String x) {
		JSONObject obj = new JSONObject(x);
		JSONObject batch = obj.getJSONObject("production_order");
		
		try {
			return registerProduction(batch);
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	private Response registerProduction(JSONObject b) throws SQLException, JSONException, ParseException {
		
		//	Get JSON Header 
		JSONObject header = b.getJSONObject("production_output");
		JSONObject product = header.getJSONObject("product");
		
		MProduction pro = new MProduction(Env.getCtx(),0,null);
		String DocumentNo=b.getString("code");
		int M_Product_ID= DB.getSQLValue(null, "SELECT M_Product_ID FROM M_Product WHERE IsActive = 'Y' AND AD_Client_ID = 1000000 AND Value = ?", new Object[]{product.getString("code")});
		
		if(M_Product_ID <= 0)
			return msj("El producto a producir de codigo: "+product.getString("code")+" - "+product.getString("name")+" no existe",false);
		
		MProduct p = new MProduct(Env.getCtx(), M_Product_ID, null);
		int c_uom_id=p.getC_UOM_ID();
		int AD_Org_ID=Env.getContextAsInt(Env.getCtx(), "#AD_Org_ID");
		int M_Locator_ID = p.getM_Locator_ID();
		
		MPPProductBOM pbom = MPPProductBOM.getDefault(p, null);
		int pBOMID = 0;
		if(pbom != null)
			pBOMID = pbom.get_ID();
		//-------------------------------
		
		pro.setDocumentNo(DocumentNo);
		pro.setAD_Org_ID(AD_Org_ID);
		pro.setM_Product_ID(M_Product_ID);
		pro.setDatePromised(new Timestamp(System.currentTimeMillis()));
		pro.setMovementDate(new Timestamp(System.currentTimeMillis()));
		pro.setM_Locator_ID(M_Locator_ID);
		pro.setIsCreated("Y");
		pro.setDescription("Produccion reportada desde Mango");
		pro.set_ValueOfColumn("C_UOM_ID", c_uom_id);
		pro.set_ValueOfColumn("TrxType", "P");
		pro.setProductionQty(header.getBigDecimal("amount"));
		pro.set_ValueOfColumn("PP_Product_BOM_ID",pBOMID);
		
		if(pro.save()) {
			int M_Production_ID=pro.get_ID();
			MProductionLine proL = new MProductionLine(Env.getCtx(),0,null);
			
			//--------------Registrar mismo producto
			BigDecimal monto = header.getBigDecimal("amount");
            proL.setM_Production_ID(M_Production_ID);
            proL.setAD_Org_ID(AD_Org_ID);
            proL.setM_Product_ID(M_Product_ID);
            proL.setDescription("");
            proL.setLine(10);
            proL.setPlannedQty(monto);
            proL.setQtyUsed(monto);
            proL.setMovementQty(monto);
            proL.setIsEndProduct(true);
            proL.setM_Locator_ID(M_Locator_ID);
            proL.save();
			
            //	Get JSON Lines
    		JSONArray batch_hopper_lots = b.getJSONArray("consumptions");
    		int line = 10;
			//---------------------------------------
			for (int i = 0; i < batch_hopper_lots.length(); i++) {
				MProductionLine proE = new MProductionLine(Env.getCtx(),0,null);
			    JSONObject obj = batch_hopper_lots.getJSONObject(i);
			    JSONObject lProduct		=obj.getJSONObject("product");
			    int lproduct_id= DB.getSQLValue(null, "SELECT M_Product_ID FROM M_Product WHERE IsActive = 'Y' AND AD_Client_ID = 1000000 AND Value = ?", new Object[]{lProduct.getString("code")});
			    	
			    if(lproduct_id <= 0)
			    	return msj("El producto a consumir de codigo: "+lProduct.getString("code")+" - "+lProduct.getString("name")+" no existe",false);
			    
			    BigDecimal movementQty	=obj.getBigDecimal("amount");
	            
	            MProduct pLine = new MProduct(Env.getCtx(), lproduct_id, null);
	            //	Scrap Percent
	            BigDecimal scrap = (BigDecimal) pLine.get_Value("QtyScrap");
	            BigDecimal scrapQty = BigDecimal.ZERO;
	            if(scrap.compareTo(BigDecimal.ZERO) > 0)
	            {
	            	scrapQty = movementQty.multiply(scrap.divide(new BigDecimal(100), 4, RoundingMode.HALF_UP));
	            	scrapQty = scrapQty.setScale(2, RoundingMode.HALF_UP);
	            }
	            	
	            
	            proE.setDescription("Cantidad Reportada por Mango: "+movementQty+", % de Despercicio del Producto: "+scrap+", Cantidad Desperdicio: "+scrapQty+", Total Consumido: "+movementQty.add(scrapQty));	            	
	            proE.setM_Production_ID(M_Production_ID);
	            proE.setAD_Org_ID(AD_Org_ID);
	            proE.setM_Product_ID(lproduct_id);
	            proE.setLine(line+10);
	            proE.setPlannedQty(movementQty);
	            proE.setQtyUsed(movementQty.add(scrapQty));
	            proE.setMovementQty((movementQty.add(scrapQty)).negate());
	            proE.setIsEndProduct(false);
	            proE.setM_Locator_ID(pLine.getM_Locator_ID());
	            proE.set_ValueOfColumn("C_UOM_ID", pLine.getC_UOM_ID());
	            proE.save();
			}
			return msj("Guardado exitosamente",true);
			
		}else {
			return msj("Error al importar el documento",false);
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

}


