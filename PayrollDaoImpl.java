package com.alfaris.b2b.dao;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import com.alfaris.b2b.dto.PayrollDetailsDto;
import com.alfaris.b2b.dto.PayrollHeaderDto;
import com.alfaris.b2b.dto.PayrollLogDetails;
import com.alfaris.b2b.exception.ClientNotFoundException;
import com.alfaris.b2b.exception.DataAccessException;


public class PayrollDaoImpl implements PayrollDao{
	private static final Logger logger = Logger.getLogger(PayrollDaoImpl.class.getName());
	public List<PayrollDetailsDto> getPayrollData(String clientId,String fileId)throws DataAccessException{
		List<PayrollDetailsDto> payrollDetailsArr = new ArrayList<PayrollDetailsDto>();
		Connection con = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		String query = "SELECT TRANS_REF,TRANSACTION_AMOUNT,STATUS,STATUS_DESC,RECORD_NO from UPS_PAY_DET WHERE CLIENT_ID=? AND FILE_ID=? ORDER BY RECORD_NO";		
		try{
			con = DBUtil.getConnection();
			ps = con.prepareStatement(query);
			ps.setString(1, clientId);
			ps.setString(2, fileId);
			rs = ps.executeQuery();
			while(rs.next()){
				String transactionRef = rs.getString(1);
				double amount = rs.getDouble(2);
				String status = rs.getString(3);
				String statusDesc = rs.getString(4);
				PayrollDetailsDto payrollDetails = new PayrollDetailsDto();
				payrollDetails.setClientId(clientId);
				payrollDetails.setFileRef(fileId);
				payrollDetails.setTransactionRef(transactionRef);
				payrollDetails.setAmount(amount);
				payrollDetails.setStatus(status);
				payrollDetails.setStatusDesc(statusDesc);
				payrollDetailsArr.add(payrollDetails);
			}
		}catch(SQLException e){
			logger.error(e.getMessage(),e);
			throw new DataAccessException();
		}catch(Exception e){
			logger.error(e.getMessage(),e);
			throw new DataAccessException();
		}finally{
			DBUtil.close(rs, ps, con);
		}
		
		return payrollDetailsArr;
	}
	public boolean checkReferenceNoExist(String clientId,String refNo)throws DataAccessException{
		boolean flag = false;
		Connection con = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		logger.info("(checkReferenceNo) Inside checkReferenceNo");
		try {
			String strCheckRefSQL="SELECT PRODUCT_ID FROM UPS_PAY_HDR WHERE CLIENT_ID=? AND FILE_ID=?";
			logger.info("(checkReferenceNo)Check Reference No SQL "+strCheckRefSQL+" CLIENT_ID="+clientId+" FILE_ID="+refNo);
			con = DBUtil.getConnection();
			ps = con.prepareStatement(strCheckRefSQL);
			ps.setString(1, clientId);
			ps.setString(2, refNo);
			rs = ps.executeQuery();
			if(rs.next())
				flag = true;
			logger.info("(checkReferenceNo)Check Reference No status flag for  CLIENT_ID="+clientId+" FILE_ID="+refNo+" is "+flag+" \r\n");
		} catch(SQLException e){
			logger.error(e.getMessage(),e);
			throw new DataAccessException();
		}catch(Exception e){
			logger.error(e.getMessage(),e);
			throw new DataAccessException();
		}finally{
			DBUtil.close(rs, ps, con);
		}
		return flag;
	} 
	public boolean checkReferenceNo(String clientId,String refNo){
		boolean flag = true;
		Connection con = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		logger.info("(checkReferenceNo) Inside checkReferenceNo");
		try {
			String strCheckRefSQL="SELECT PRODUCT_ID FROM UPS_PAY_HDR WHERE CLIENT_ID=? AND FILE_ID=?";
			logger.info("(checkReferenceNo)Check Reference No SQL "+strCheckRefSQL+" CLIENT_ID="+clientId+" FILE_ID="+refNo);
			con = DBUtil.getConnection();
			ps = con.prepareStatement(strCheckRefSQL);
			ps.setString(1, clientId);
			ps.setString(2, refNo);
			rs = ps.executeQuery();
			if(rs.next()){
				flag = true;
			}else{
				flag = false;
			}
			logger.info("(checkReferenceNo)Check Reference No status flag for  CLIENT_ID="+clientId+" FILE_ID="+refNo+" is "+flag+" \r\n");
		} catch(SQLException e){
			logger.error(e.getMessage(),e);
			return flag;
		}catch(Exception e){
			logger.error(e.getMessage(),e);
			return flag;
		}finally{
			DBUtil.close(rs, ps, con);
		}
		return flag;
	} 
	public JSONObject getHeaderStatus(String clientId,String refNo,String msgType)throws DataAccessException{
		JSONObject resObj = new JSONObject();
		Connection con = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		logger.info("(checkReferenceNo) Inside checkReferenceNo");
		try {
			String strCheckRefSQL="SELECT STATUS,STATUS_DESC FROM UPS_PAY_HDR WHERE CLIENT_ID=? AND FILE_ID=? AND CUST_REF=?";
			logger.info("(checkReferenceNo)Check Reference No SQL "+strCheckRefSQL+" CLIENT_ID="+clientId+" FILE_ID="+refNo);
			con = DBUtil.getConnection();
			ps = con.prepareStatement(strCheckRefSQL);
			ps.setString(1, clientId);
			ps.setString(2, refNo);
			ps.setString(3, msgType);
			rs = ps.executeQuery();
			if(rs.next()){
				String status = rs.getString(1);
				String desc = rs.getString(2);
				resObj.put("status", status);
				resObj.put("desc", desc);
			}else{
				resObj.put("status", "");
				resObj.put("desc", "");
			}
		}catch(SQLException e){
			logger.error(e.getMessage(),e);
			throw new DataAccessException();
		}catch(Exception e){
			logger.error(e.getMessage(),e);
			throw new DataAccessException();
		}finally{
			DBUtil.close(ps, con);
		}
		return resObj;
	}
	public boolean insertDataInPayrollHeader(PayrollHeaderDto payrollHeader)throws DataAccessException{
		boolean flag = false;
		DateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		Connection con = null;
		PreparedStatement ps = null;
		String sql="INSERT INTO UPS_PAY_HDR(CLIENT_ID,FILE_ID,CUST_REF,PRODUCT_ID,SERVICES,VALUE_DATE,DATE_RCV,STATUS,STATUS_DESC) VALUES(?,?,?,?,?,?,?,?,?)";
		try {
			String clientId = payrollHeader.getSender();
			String fileId = payrollHeader.getFileRef();
			//String receiver = payrollHeader.getReceiver();
			String customerRef = payrollHeader.getMsgType();
			String valueDateStr = payrollHeader.getValueDate();
			Date valueDate = format.parse(valueDateStr);
			Date dateReceive = new Date();
			long dateReceiveTime = dateReceive.getTime();
			java.sql.Timestamp sqlReceiveDate = new java.sql.Timestamp(dateReceiveTime);	
			long valueDateTime = valueDate.getTime();
		    java.sql.Date sqlValueDate = new java.sql.Date(valueDateTime);	
			logger.info("(checkReferenceNo)Check Reference No SQL "+sql+" CLIENT_ID = "+clientId+" FILE_ID = "+fileId);
			logger.info("Customer Ref : "+customerRef);
			logger.info("Value Date : "+sqlValueDate);
			logger.info("Received Date : "+sqlReceiveDate);
			logger.info("Product Id : B2B");
			logger.info("Service : Payroll");
			logger.info("Status : RECEIVED");
			con = DBUtil.getConnection();
			ps = con.prepareStatement(sql);
			ps.setString(1, clientId);
			ps.setString(2, fileId);
			ps.setString(3, customerRef);
			ps.setString(4, "B2B");
			ps.setString(5, "Payroll");
			ps.setDate(6, sqlValueDate);;
			ps.setTimestamp(7, sqlReceiveDate);
			ps.setString(8,"RECEIVED");
			ps.setString(9,"Successfully Received");
			int count = ps.executeUpdate();
			if(count>0){
				flag = true;
			}else{
				flag = false;
			}
		} catch(SQLException e){
			logger.error(e.getMessage(),e);
			throw new DataAccessException();
		}catch(Exception e){
			logger.error(e.getMessage(),e);
			throw new DataAccessException();
		}finally{
			DBUtil.close(ps, con);
		}
		return flag;
	}
	public boolean insertDataInPayrollLogs(PayrollLogDetails payrollLogs)throws DataAccessException{
		boolean flag = false;
		Connection con = null;
		PreparedStatement ps = null;
		String sql="INSERT INTO UPS_PAY_LOG(CLIENT_ID,REF_NO,SL_NO,ACTIVITY_TIME,FREE_TEXT,MSG_TYPE) VALUES(?,?,?,?,?,?)";
		try {
			Date dateReceive = new Date();
			long dateReceiveTime = dateReceive.getTime();
			java.sql.Timestamp sqlReceiveDate = new java.sql.Timestamp(dateReceiveTime);	
			String clientId = payrollLogs.getClientId();
			String fileId = payrollLogs.getFileRef();
			String freeText = payrollLogs.getFreeText();
			int serialNo = payrollLogs.getSerialNo();
			String msgType = payrollLogs.getMsgType();
			con = DBUtil.getConnection();
			ps = con.prepareStatement(sql);
			ps.setString(1, clientId);
			ps.setString(2, fileId);
			ps.setInt(3, serialNo);
			ps.setTimestamp(4, sqlReceiveDate);
			ps.setString(5, freeText);
			ps.setString(6, msgType);
			int count = ps.executeUpdate();
			if(count>0){
				flag = true;
			}else{
				flag = false;
			}
		} catch(SQLException e){
			logger.error(e.getMessage(),e);
			throw new DataAccessException();
		}catch(Exception e){
			logger.error(e.getMessage(),e);
			throw new DataAccessException();
		}finally{
			DBUtil.close(ps, con);
		}
		return flag;
	}
	public int getSerialNo(String clientId,String refNo,String msgType)throws DataAccessException{
		int serialNo = 1;
		Connection con = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		String sql="SELECT MAX(SL_NO) FROM UPS_PAY_LOG WHERE CLIENT_ID=? AND REF_NO=? AND MSG_TYPE=?";
		try {
			con = DBUtil.getConnection();
			ps = con.prepareStatement(sql);
			ps.setString(1, clientId);
			ps.setString(2, refNo);
			ps.setString(3, msgType);
			rs = ps.executeQuery();
			if(rs.next()){
				int highestUsedSlNo = rs.getInt(1);
				serialNo = highestUsedSlNo+1;
			}
		} catch(SQLException e){
			logger.error(e.getMessage(),e);
			throw new DataAccessException();
		}catch(Exception e){
			logger.error(e.getMessage(),e);
			throw new DataAccessException();
		}finally{
			DBUtil.close(ps, con);
		}
		return serialNo;
	}
}
