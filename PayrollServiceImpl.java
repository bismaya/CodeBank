package com.alfaris.b2b.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
//import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import com.alfaris.b2b.dao.ClientDao;
import com.alfaris.b2b.dao.DAOFactory;
import com.alfaris.b2b.dao.PayrollDao;
import com.alfaris.b2b.dto.PayrollDetailsDto;
import com.alfaris.b2b.dto.PayrollHeaderDto;
import com.alfaris.b2b.dto.PayrollLogDetails;
import com.alfaris.b2b.dto.PayrollRequest;
import com.alfaris.b2b.exception.ClientNotFoundException;
import com.alfaris.b2b.exception.DataAccessException;
import com.alfaris.b2b.exception.SignatureVerifyException;
import com.alfaris.b2b.exception.TagMissingException;
import com.alfaris.b2b.reader.RequestMessageReader;

public class PayrollServiceImpl implements PayrollService{
	private static final Logger logger = Logger.getLogger(PayrollServiceImpl.class.getName());
	public String getPayrollResponse(X509Certificate cert,byte[] bytes,String sourceBank,String dir,String dirRes,String printFlag,boolean payrollAckFlag,File schemaFile){
		String decimalRegExp = "^\\d+(\\.\\d{1,2})?$";
		StringBuilder trnPaymentResMsg = new StringBuilder();
		DateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		DateFormat fileFormatormat = new SimpleDateFormat("ddMMyyyyHHmmss");
		ClientDao clientdao = DAOFactory.getClientDao();
		PayrollDao payrollDao = DAOFactory.getPayrollDao();
		String type = "";
		//String regexAlfaNumeric = "^[a-zA-Z0-9]+$";
		String regex = "\\d+";
		logger.info("PayrollServiceImpl->(getPayrollResponse) Inside getPayrollResponse");
		try {
			if(cert!=null){
				if(bytes.length>0){
					//logger.info("(PayrollServiceImpl)Orginal Payroll Message Content 1: --Start-- "+new String(bytes)+" --End--");
					//PublicKey publicKey = cert.getPublicKey();
					BigInteger clientSlNo = cert.getSerialNumber();
					String slNoStr = clientSlNo.toString();
					logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll Client Serial No: "+slNoStr+" \r\n");
					PayrollRequest payrollRequest = RequestMessageReader.readPayrollRequest(bytes,schemaFile);	
					//logger.info("(PayrollServiceImpl)Orginal Payroll Message Content 2: --Start-- "+new String(bytes)+" --End--");
					String sender = payrollRequest.getSender();
					String receiver = payrollRequest.getReceiver();
					String msgType = payrollRequest.getMsgType();
					String totalHeaderAmount = payrollRequest.getTotalAmount();
					String timeStamp = payrollRequest.getTimeStamp();
					String payrollMsgRef = payrollRequest.getPayrollMsgRef();
					String msgDescReq = payrollRequest.getMsgDesc();
					String payrollMsgType = "MT100-Payroll";//payrollRequest.getPayrollMsgType();
					String signature = payrollRequest.getSignature();
					String msgDescInput = payrollRequest.getMsgDesc();
					boolean wpsMessageFlag = payrollRequest.isWpsMessage();
					String molEstablishmentId = payrollRequest.getMolEstablishmentId();
					logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll Sender:"+sender+" Receiver:"+receiver+" MsgType:"+msgType+" msgDesc:"+msgDescReq+" \r\n");
					logger.info("PayrollServiceImpl->(getPayrollResponse) timeStamp:"+timeStamp+" Payroll Message Ref:"+payrollMsgRef+" Payroll Message Type:"+payrollMsgType+" \r\n");
					/*File folder = new File(dir);
					File[] listOfFiles = folder.listFiles();
					logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll Folder "+folder.getPath()+"\r\n");
					if(null != listOfFiles){
						logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll Folder files count "+listOfFiles.length+"\r\n");
					}else{
						logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll Folder files count 0\r\n");
					}*/
					//String fileName = "Payroll_"+payrollMsgRef+".xml";
					//String ackFileName = "Payroll_"+payrollMsgRef+"ACK"+fileFormatormat.format(new Date())+".xml";
					boolean writeFileFlag = false;
					Calendar c1 = Calendar.getInstance();
					Calendar c2 = Calendar.getInstance();
					Date dateTime = format.parse(timeStamp);
					c2.setTime(dateTime);
					logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll Type Falg for Ack/Payroll "+payrollAckFlag+" \r\n");
					if(payrollAckFlag)
						type = "PayrollACK";
					else{
						type = "Payroll";
						//c1.add(Calendar.DATE, 1); // add 1 days		
						logger.info("PayrollServiceImpl (getPayrollResponse) After 1 days time stamp "+c1.getTime());
					}logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll Type set as "+type+" \r\n");
					if(signature!=null && signature!="" && signature.length()>0){
						boolean flag = PKCS7SignVerify.verifySignature(cert,bytes, signature,type);					
						if(flag){
							logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll Signature is valid \r\n");
							if(sender!=null && sender!="" && sender.length()>0){
								if(receiver!=null && receiver!="" && receiver.length()>0){
									if(receiver.trim().equals(sourceBank.substring(0,4))){
										if(!wpsMessageFlag && !payrollAckFlag){
											trnPaymentResMsg = generateExceptionMessage("WPSMessage is always True for WPS Payroll");
										}else{
											logger.info("Payroll "+!payrollAckFlag);
											logger.info("MOL Establishment ID is : "+(molEstablishmentId==null || molEstablishmentId=="" || molEstablishmentId.length()==0));
											if(!payrollAckFlag && (molEstablishmentId==null || molEstablishmentId=="" || molEstablishmentId.length()==0)){
												trnPaymentResMsg = generateExceptionMessage("MOL Establishment ID is Missing");
											}else{
												if(!payrollAckFlag && (molEstablishmentId.length()>18 || molEstablishmentId.length()<3)){
													trnPaymentResMsg = generateExceptionMessage("MOL Establishment ID is not valid");
												}else if(!payrollAckFlag && molEstablishmentId.indexOf("-")!=1 && molEstablishmentId.indexOf("-")!=2){
													trnPaymentResMsg = generateExceptionMessage("MOL Establishment ID is not valid");
												}else{
													int index = 0;
													String prefixMolId = "";
													String sufixMolId = "";
													if(!payrollAckFlag){
														index = molEstablishmentId.indexOf("-");
														prefixMolId = molEstablishmentId.substring(0, index);
														sufixMolId = molEstablishmentId.substring(index+1);														
													}
													if(!payrollAckFlag && (prefixMolId.length()>2 || sufixMolId.length()>15)){
														trnPaymentResMsg = generateExceptionMessage("MOL Establishment ID is not valid");
													}else if(!payrollAckFlag && (prefixMolId.length()<1 || sufixMolId.length()<1)){
														trnPaymentResMsg = generateExceptionMessage("MOL Establishment ID is not valid");
													}else if(!payrollAckFlag && (!prefixMolId.matches(regex) || !sufixMolId.matches(regex))){
														trnPaymentResMsg = generateExceptionMessage("MOL Establishment ID is not valid");
													}else{
														if(msgType!=null && msgType!="" && msgType.length()>0){
															if((!payrollAckFlag && msgType.trim().equals("WPSPRMSG")) || (payrollAckFlag && msgType.trim().equals("WPSPRREQ"))){
																if(msgDescInput!=null && msgDescInput!="" && msgDescInput.length()>0){
																	if(payrollMsgRef!=null && payrollMsgRef!="" && payrollMsgRef.length()>0){
																		logger.info("Payroll Ref. Length : "+payrollMsgRef.trim().length());
																		if(!payrollAckFlag && payrollMsgRef.trim().length()>16){
																			trnPaymentResMsg = generateExceptionMessage("Payroll Reference Number should not be more than 16 characters");
																		}else{
																			if(payrollMsgRef.matches(regex)){
																				if(!payrollAckFlag && (totalHeaderAmount==null || totalHeaderAmount=="" || totalHeaderAmount.length()==0)){
																					trnPaymentResMsg = generateExceptionMessage("Payroll Transaction Amount is missing");																
																				}else{
																					if(!payrollAckFlag && Double.parseDouble(totalHeaderAmount)<0){
																						trnPaymentResMsg = generateExceptionMessage("Payroll Transaction Amount value shouldn't be negetive");
																					}else{
																						if(!payrollAckFlag && !totalHeaderAmount.matches(decimalRegExp)){
																							trnPaymentResMsg = generateExceptionMessage("Payroll Transaction Amount decimal value should not more than 2 decimal point");
																						}else{																	
																							String registeredSlNo = clientdao.getSerialNo(sender.trim());
																							logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll registered serial No: "+registeredSlNo+" \r\n");
																							if(registeredSlNo== null || registeredSlNo==""){
																								trnPaymentResMsg = generateExceptionMessage("Certificate Serial Number is not Registered"); 
																							}else{
																								if(slNoStr.trim().equalsIgnoreCase(registeredSlNo.trim())){
																									logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll Signature is valid \r\n");
																									if (c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)) {
																										boolean fileRefFlag = payrollDao.checkReferenceNo(sender, payrollMsgRef);	
																										int serialNo = payrollDao.getSerialNo(sender, payrollMsgRef, type);
																										if(payrollAckFlag){
																											String msgDesc = "Payroll Payment Response";//payrollRequest.getMsgDesc();
																											String resMsgType = "PRRES";
																											//if(logSaveFlag){
																												logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll write File Flag and print Flag writeFileFlag="+writeFileFlag+" printFlag="+printFlag+"\r\n");
																												if(printFlag.equals("1")){
																													String ackFileName = "Payroll_"+payrollMsgRef+"ACK"+fileFormatormat.format(new Date())+".xml";
																													logger.info("(getPayrollResponse) Payroll Ack Flag "+payrollAckFlag +" to write "+dir+ackFileName+"\r\n");
																													File outputFile = new File(dir+ackFileName);
																													if(!outputFile.exists())
																														outputFile.createNewFile();
																													FileOutputStream fop = new FileOutputStream(outputFile);
																													fop.write(bytes);
																													fop.flush();
																													fop.close();
																												}
																												JSONObject headerStatusObj = payrollDao.getHeaderStatus(sender, payrollMsgRef,"WPSPRMSG");
																												String headerStatus = (String) headerStatusObj.get("status");
																												String headerStatusDesc = (String) headerStatusObj.get("desc");
																												if(headerStatus!=null && headerStatus!="" && headerStatus.length()>0){
																													List<PayrollDetailsDto> detailsArr = payrollDao.getPayrollData(sender, payrollMsgRef);
																													if(detailsArr.size()>0){
																														int payrollCount = 1;
																														trnPaymentResMsg.append("<?xml version=\"1.0\"?>\r\n");
																														trnPaymentResMsg.append(" <Message>\r\n");
																														trnPaymentResMsg.append(" <Header>\r\n");
																														trnPaymentResMsg.append(" <Sender>"+receiver+"</Sender>\r\n");
																														trnPaymentResMsg.append(" <Receiver>"+sender+"</Receiver>\r\n");
																														trnPaymentResMsg.append(" <MessageType>"+resMsgType+"</MessageType>\r\n");
																														trnPaymentResMsg.append(" <MessageDescription>"+msgDesc+"</MessageDescription>\r\n");
																														trnPaymentResMsg.append(" <TimeStamp>"+timeStamp+"</TimeStamp>\r\n");
																														trnPaymentResMsg.append(" </Header>\r\n");
																														trnPaymentResMsg.append(" <Body>\r\n");
																														trnPaymentResMsg.append(" <PayrollResponse>\r\n");
																														trnPaymentResMsg.append(" <PayrollMessageRef>"+payrollMsgRef+"</PayrollMessageRef>\r\n");
																														trnPaymentResMsg.append(" <PayrollMessageType>MT100-Payroll</PayrollMessageType>\r\n");
																														StringBuilder detailTrn = new StringBuilder();
																														double totalAmount = 0;
																														for(PayrollDetailsDto payrollDetails:detailsArr){
																															double amount = payrollDetails.getAmount();
																															totalAmount = totalAmount+amount;
																															detailTrn.append(" <PayrollTransactionResponse>\r\n");
																															detailTrn.append(" <SequenceNum>"+payrollDetails.getTransactionRef()+"</SequenceNum>\r\n");
																															detailTrn.append(" <TransactionReference>"+payrollDetails.getTransactionRef()+"</TransactionReference>\r\n");
																															if("REJECT".equals(payrollDetails.getStatus()) || "REJECTED".equals(payrollDetails.getStatus())){
																																detailTrn.append(" <StatusCode>DE</StatusCode>\r\n");
																																detailTrn.append(" <StatusDetail>"+payrollDetails.getStatusDesc()+"</StatusDetail>\r\n");	
																															}else if("SUCCESS".equals(payrollDetails.getStatus())){
																																detailTrn.append(" <StatusCode>OK</StatusCode>\r\n");
																																detailTrn.append(" <StatusDetail>Processed Successfully</StatusDetail>\r\n");	
																															}else if("PENDING".equals(payrollDetails.getStatus())){
																																detailTrn.append(" <StatusCode>OK</StatusCode>\r\n");
																																detailTrn.append(" <StatusDetail>Under Process</StatusDetail>\r\n");	
																															}else{
																																detailTrn.append(" <StatusCode>"+payrollDetails.getStatus()+"</StatusCode>\r\n");
																																detailTrn.append(" <StatusDetail>"+payrollDetails.getStatusDesc()+"</StatusDetail>\r\n");	
																															}																															
																															detailTrn.append(" </PayrollTransactionResponse>\r\n");
																															payrollCount++;
																														}
																														int totalTrnCount = payrollCount-1;
																														String amountStr = BigDecimal.valueOf(totalAmount).toString();
																														trnPaymentResMsg.append(" <PayrollTransactionCount>"+totalTrnCount+"</PayrollTransactionCount>\r\n");
																														trnPaymentResMsg.append(" <PayrollTransactionAmount>"+amountStr+"</PayrollTransactionAmount>\r\n");																														
																														trnPaymentResMsg.append(detailTrn);																														
																														trnPaymentResMsg.append(" </PayrollResponse>\r\n");
																														trnPaymentResMsg.append(" </Body>\r\n");
																														trnPaymentResMsg.append(" <ResponseStatus>\r\n");
																														if("ACCEPTED".equals(headerStatus)){
																															trnPaymentResMsg.append(" <StatusCode>OK</StatusCode>\r\n");
																															trnPaymentResMsg.append(" <StatusDetail>OK</StatusDetail>\r\n");
																														}else if("RECEIVED".equals(headerStatus)){
																															trnPaymentResMsg.append(" <StatusCode>PENDING</StatusCode>\r\n");
																															trnPaymentResMsg.append(" <StatusDetail>"+headerStatusDesc+", Waiting for the Process</StatusDetail>\r\n");
																														}else if("REJECT".equals(headerStatus) || "REJECTED".equals(headerStatus)){
																															trnPaymentResMsg.append(" <StatusCode>FAILED</StatusCode>\r\n");
																															trnPaymentResMsg.append(" <StatusDetail>"+headerStatusDesc+"</StatusDetail>\r\n");																															
																														}else{
																															trnPaymentResMsg.append(" <StatusCode>"+headerStatus+"</StatusCode>\r\n");
																															trnPaymentResMsg.append(" <StatusDetail>"+headerStatusDesc+"</StatusDetail>\r\n");																															
																														}
																														trnPaymentResMsg.append(" </ResponseStatus>\r\n");	
																														trnPaymentResMsg.append(" </Message>");
																													}else{
																														trnPaymentResMsg.append("<?xml version=\"1.0\"?>\r\n");
																														trnPaymentResMsg.append(" <Message>\r\n");
																														trnPaymentResMsg.append(" <Header>\r\n");
																														trnPaymentResMsg.append(" <Sender>"+receiver+"</Sender>\r\n");
																														trnPaymentResMsg.append(" <Receiver>"+sender+"</Receiver>\r\n");
																														trnPaymentResMsg.append(" <MessageType>"+resMsgType+"</MessageType>\r\n");
																														trnPaymentResMsg.append(" <MessageDescription>"+msgDesc+"</MessageDescription>\r\n");
																														trnPaymentResMsg.append(" <TimeStamp>"+timeStamp+"</TimeStamp>\r\n");
																														trnPaymentResMsg.append(" </Header>\r\n");
																														
																														trnPaymentResMsg.append(" <Body>\r\n");
																														
																														trnPaymentResMsg.append(" <PayrollResponse>\r\n");
																														trnPaymentResMsg.append(" <PayrollMessageRef>"+payrollMsgRef+"</PayrollMessageRef>\r\n");
																														trnPaymentResMsg.append(" <PayrollMessageType>MT100-Payroll</PayrollMessageType>\r\n");
																														trnPaymentResMsg.append(" </PayrollResponse>\r\n");
																														
																														trnPaymentResMsg.append(" </Body>\r\n");
																														
																														trnPaymentResMsg.append(" <ResponseStatus>\r\n");
																														if("ACCEPTED".equals(headerStatus)){
																															trnPaymentResMsg.append(" <StatusCode>OK</StatusCode>\r\n");
																															trnPaymentResMsg.append(" <StatusDetail>OK</StatusDetail>\r\n");
																														}else if("RECEIVED".equals(headerStatus)){
																															trnPaymentResMsg.append(" <StatusCode>PENDING</StatusCode>\r\n");
																															trnPaymentResMsg.append(" <StatusDetail>"+headerStatusDesc+", Waiting for the Process</StatusDetail>\r\n");
																														}else if("REJECT".equals(headerStatus) || "REJECTED".equals(headerStatus)){
																															trnPaymentResMsg.append(" <StatusCode>FAILED</StatusCode>\r\n");
																															trnPaymentResMsg.append(" <StatusDetail>"+headerStatusDesc+"</StatusDetail>\r\n");																															
																														}else{
																															trnPaymentResMsg.append(" <StatusCode>"+headerStatus+"</StatusCode>\r\n");
																															trnPaymentResMsg.append(" <StatusDetail>"+headerStatusDesc+"</StatusDetail>\r\n");	
																														}
																														trnPaymentResMsg.append(" </ResponseStatus>\r\n");	
																														trnPaymentResMsg.append(" </Message>");
																														logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll No File exists "+trnPaymentResMsg.toString()+" \r\n");
																													}
																												}else{
																													trnPaymentResMsg.append("<?xml version=\"1.0\"?>\r\n");
																													trnPaymentResMsg.append(" <Message>\r\n");
																													trnPaymentResMsg.append(" <Header>\r\n");
																													trnPaymentResMsg.append(" <Sender>"+receiver+"</Sender>\r\n");
																													trnPaymentResMsg.append(" <Receiver>"+sender+"</Receiver>\r\n");
																													trnPaymentResMsg.append(" <MessageType>"+resMsgType+"</MessageType>\r\n");
																													trnPaymentResMsg.append(" <MessageDescription>"+msgDesc+"</MessageDescription>\r\n");
																													trnPaymentResMsg.append(" <TimeStamp>"+timeStamp+"</TimeStamp>\r\n");
																													trnPaymentResMsg.append(" </Header>\r\n");
																													trnPaymentResMsg.append(" <Body>\r\n");
																													
																													trnPaymentResMsg.append(" <PayrollResponse>\r\n");
																													trnPaymentResMsg.append(" <PayrollMessageRef>"+payrollMsgRef+"</PayrollMessageRef>\r\n");
																													trnPaymentResMsg.append(" <PayrollMessageType>MT100-Payroll</PayrollMessageType>\r\n");
																													trnPaymentResMsg.append(" </PayrollResponse>\r\n");
																													
																													trnPaymentResMsg.append(" </Body>\r\n");
																													trnPaymentResMsg.append(" <ResponseStatus>\r\n");
																													trnPaymentResMsg.append(" <StatusCode>FAILED</StatusCode>\r\n");
																													trnPaymentResMsg.append(" <StatusDetail>No Result Found For the requested Reference Number</StatusDetail>\r\n");
																													trnPaymentResMsg.append(" </ResponseStatus>\r\n");	
																													trnPaymentResMsg.append(" </Message>");
																													logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll No File exists "+trnPaymentResMsg.toString()+" \r\n");
																												}																						
																											/*}else{
																												trnPaymentResMsg.append("<?xml version=\"1.0\"?>\r\n");
																												trnPaymentResMsg.append(" <Message>\r\n");
																												trnPaymentResMsg.append(" <Header>\r\n");
																												trnPaymentResMsg.append(" <Sender>"+receiver+"</Sender>\r\n");
																												trnPaymentResMsg.append(" <Receiver>"+sender+"</Receiver>\r\n");
																												trnPaymentResMsg.append(" <MessageType>"+msgType+"</MessageType>\r\n");
																												trnPaymentResMsg.append(" <MessageDescription>"+msgDesc+"</MessageDescription>\r\n");
																												trnPaymentResMsg.append(" <TimeStamp>"+timeStamp+"</TimeStamp>\r\n");
																												trnPaymentResMsg.append(" </Header>\r\n");
																												trnPaymentResMsg.append(" <Body>\r\n");
																													
																													trnPaymentResMsg.append(" <PayrollResponse>\r\n");
																													trnPaymentResMsg.append(" <PayrollMessageRef>"+payrollMsgRef+"</PayrollMessageRef>\r\n");
																													trnPaymentResMsg.append(" <PayrollMessageType>MT100-Payroll</PayrollMessageType>\r\n");
																													trnPaymentResMsg.append(" </PayrollResponse>\r\n");
																													
																													trnPaymentResMsg.append(" </Body>\r\n");
																												trnPaymentResMsg.append(" <ResponseStatus>\r\n");
																												trnPaymentResMsg.append(" <StatusCode>Fail</StatusCode>\r\n");
																												trnPaymentResMsg.append(" <StatusDetail>Unable to process. Please try after sometime</StatusDetail>\r\n");
																												trnPaymentResMsg.append(" </ResponseStatus>\r\n");	
																												trnPaymentResMsg.append(" </Message>");
																												logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll No File exists "+trnPaymentResMsg.toString()+" \r\n");
																											}	*/																
																										}else{
																											String msgDesc = "Payroll Message Acknowledgement";//payrollRequest.getMsgDesc();
																											String resMsgType = "PRACK";
																											if(!fileRefFlag){
																												PayrollLogDetails payrollLogs = new PayrollLogDetails();
																												payrollLogs.setClientId(sender);
																												payrollLogs.setActivityTime(new Date());
																												payrollLogs.setFileRef(payrollMsgRef);
																												payrollLogs.setMsgType(type);
																												payrollLogs.setSerialNo(serialNo);
																												payrollLogs.setFreeText(sender+" Send Payroll Request having Payroll Ref. "+payrollMsgRef);
																												boolean logSaveFlag = payrollDao.insertDataInPayrollLogs(payrollLogs);
																												if(logSaveFlag){
																													PayrollHeaderDto payrollHeader = new PayrollHeaderDto();
																													payrollHeader.setSender(sender);
																													payrollHeader.setReceiver(receiver);
																													payrollHeader.setMsgType(msgType);
																													payrollHeader.setFileRef(payrollMsgRef);
																													payrollHeader.setValueDate(timeStamp);
																													boolean payrollHeaderSaveFlag = payrollDao.insertDataInPayrollHeader(payrollHeader);
																													if(payrollHeaderSaveFlag){
																														File outputFile = new File(dir+"/B2B_Payroll_"+payrollMsgRef+".xml");
																														if(!outputFile.exists())
																															outputFile.createNewFile();
																														FileOutputStream fop = new FileOutputStream(outputFile);
																														fop.write(bytes);
																														fop.flush();
																														fop.close();
																														trnPaymentResMsg.append("<?xml version=\"1.0\"?>\r\n");
																												 		trnPaymentResMsg.append(" <Message>\r\n");
																														trnPaymentResMsg.append(" <Header>\r\n");
																														trnPaymentResMsg.append(" <Sender>"+receiver+"</Sender>\r\n");
																														trnPaymentResMsg.append(" <Receiver>"+sender+"</Receiver>\r\n");
																														trnPaymentResMsg.append(" <MessageType>"+resMsgType+"</MessageType>\r\n");
																														trnPaymentResMsg.append(" <MessageDescription>"+msgDesc+"</MessageDescription>\r\n");
																												 		trnPaymentResMsg.append(" <TimeStamp>"+timeStamp+"</TimeStamp>\r\n");
																												 		trnPaymentResMsg.append(" </Header>\r\n");
																														trnPaymentResMsg.append(" <Body>\r\n");
																														trnPaymentResMsg.append(" <PayrollMessageAck>\r\n");
																														trnPaymentResMsg.append(" <PayrollMessageRef>"+payrollMsgRef+"</PayrollMessageRef>\r\n");
																												 		trnPaymentResMsg.append(" <PayrollMessageType>"+payrollMsgType+"</PayrollMessageType>\r\n");
																														trnPaymentResMsg.append(" </PayrollMessageAck>\r\n");
																												 		trnPaymentResMsg.append(" </Body>\r\n");
																												 		trnPaymentResMsg.append(" <ResponseStatus>\r\n");
																												 		trnPaymentResMsg.append(" <StatusCode>OK</StatusCode>\r\n");
																												 		trnPaymentResMsg.append(" <StatusDetail>Payroll file received successfully kindly check the payroll enquiry after 30 min</StatusDetail>\r\n");
																												 		trnPaymentResMsg.append(" </ResponseStatus>\r\n");									 		
																												 		trnPaymentResMsg.append(" </Message>");
																												 		logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll Based on writeFileFlag true "+trnPaymentResMsg+" \r\n");
																													}else{
																														trnPaymentResMsg.append("<?xml version=\"1.0\"?>\r\n");
																												 		trnPaymentResMsg.append(" <Message>\r\n");
																														trnPaymentResMsg.append(" <Header>");
																														trnPaymentResMsg.append(" <Sender>"+receiver+"</Sender>\r\n");
																														trnPaymentResMsg.append(" <Receiver>"+sender+"</Receiver>\r\n");
																														trnPaymentResMsg.append(" <MessageType>"+resMsgType+"</MessageType>\r\n");
																														trnPaymentResMsg.append(" <MessageDescription>"+msgDesc+"</MessageDescription>\r\n");
																												 		trnPaymentResMsg.append(" <TimeStamp>"+timeStamp+"</TimeStamp>\r\n");
																												 		trnPaymentResMsg.append(" </Header>\r\n");
																												 		trnPaymentResMsg.append(" <Body>\r\n");
																														
																														trnPaymentResMsg.append(" <PayrollMessageAck>\r\n");
																														trnPaymentResMsg.append(" <PayrollMessageRef>"+payrollMsgRef+"</PayrollMessageRef>\r\n");
																														trnPaymentResMsg.append(" <PayrollMessageType>MT100-Payroll</PayrollMessageType>\r\n");
																														trnPaymentResMsg.append(" </PayrollMessageAck>\r\n");
																														
																														trnPaymentResMsg.append(" </Body>\r\n");
																														trnPaymentResMsg.append(" <ResponseStatus>\r\n");
																														trnPaymentResMsg.append(" <Status>FAILED</Status>\r\n");
																												 		trnPaymentResMsg.append(" <StatusDetail>Unable to Process. Please try after sometime</StatusDetail>\r\n");
																														trnPaymentResMsg.append(" </ResponseStatus>\r\n");		
																												 		trnPaymentResMsg.append(" </Message>");
																												 		logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll Based on writeFileFlag false "+trnPaymentResMsg+" \r\n");
																													}		
																												}else{
																													trnPaymentResMsg.append("<?xml version=\"1.0\"?>\r\n");
																											 		trnPaymentResMsg.append(" <Message>\r\n");
																													trnPaymentResMsg.append(" <Header>");
																													trnPaymentResMsg.append(" <Sender>"+receiver+"</Sender>\r\n");
																													trnPaymentResMsg.append(" <Receiver>"+sender+"</Receiver>\r\n");
																													trnPaymentResMsg.append(" <MessageType>"+resMsgType+"</MessageType>\r\n");
																													trnPaymentResMsg.append(" <MessageDescription>"+msgDesc+"</MessageDescription>\r\n");
																											 		trnPaymentResMsg.append(" <TimeStamp>"+timeStamp+"</TimeStamp>\r\n");
																											 		trnPaymentResMsg.append(" </Header>\r\n");
																											 		trnPaymentResMsg.append(" <Body>\r\n");
																													
																													trnPaymentResMsg.append(" <PayrollMessageAck>\r\n");
																													trnPaymentResMsg.append(" <PayrollMessageRef>"+payrollMsgRef+"</PayrollMessageRef>\r\n");
																													trnPaymentResMsg.append(" <PayrollMessageType>MT100-Payroll</PayrollMessageType>\r\n");
																													trnPaymentResMsg.append(" </PayrollMessageAck>\r\n");
																													
																													trnPaymentResMsg.append(" </Body>\r\n");
																													trnPaymentResMsg.append(" <ResponseStatus>\r\n");
																													trnPaymentResMsg.append(" <StatusCode>FAILED</StatusCode>\r\n");
																											 		trnPaymentResMsg.append(" <StatusDetail>Unable to Process. Please try after sometime</StatusDetail>\r\n");
																													trnPaymentResMsg.append(" </ResponseStatus>\r\n");		
																											 		trnPaymentResMsg.append(" </Message>");
																											 		logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll Based on writeFileFlag false "+trnPaymentResMsg+" \r\n");
																												}																																		
																											}else{
																												trnPaymentResMsg.append("<?xml version=\"1.0\"?>\r\n");
																										 		trnPaymentResMsg.append(" <Message>\r\n");
																												trnPaymentResMsg.append(" <Header>");
																												trnPaymentResMsg.append(" <Sender>"+receiver+"</Sender>\r\n");
																												trnPaymentResMsg.append(" <Receiver>"+sender+"</Receiver>\r\n");
																												trnPaymentResMsg.append(" <MessageType>"+resMsgType+"</MessageType>\r\n");
																												trnPaymentResMsg.append(" <MessageDescription>"+msgDesc+"</MessageDescription>\r\n");
																										 		trnPaymentResMsg.append(" <TimeStamp>"+timeStamp+"</TimeStamp>\r\n");
																										 		trnPaymentResMsg.append(" </Header>\r\n");
																										 		trnPaymentResMsg.append(" <Body>\r\n");
																												
																												trnPaymentResMsg.append(" <PayrollMessageAck>\r\n");
																												trnPaymentResMsg.append(" <PayrollMessageRef>"+payrollMsgRef+"</PayrollMessageRef>\r\n");
																												trnPaymentResMsg.append(" <PayrollMessageType>MT100-Payroll</PayrollMessageType>\r\n");
																												trnPaymentResMsg.append(" </PayrollMessageAck>\r\n");
																												
																												trnPaymentResMsg.append(" </Body>\r\n");
																												trnPaymentResMsg.append(" <ResponseStatus>\r\n");
																												trnPaymentResMsg.append(" <StatusCode>DU</StatusCode>\r\n");
																										 		trnPaymentResMsg.append(" <StatusDetail>Reference Number already Used</StatusDetail>\r\n");
																												trnPaymentResMsg.append(" </ResponseStatus>\r\n");		
																										 		trnPaymentResMsg.append(" </Message>");
																										 		logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll Based on writeFileFlag false "+trnPaymentResMsg+" \r\n");
																											}
																										}
																									}else{
																										trnPaymentResMsg = generateExceptionMessage("Time Stamp Should be today's date");
																										/*if(payrollAckFlag){
																											trnPaymentResMsg = generateExceptionMessage("Time Stamp Should be today's date");
																										}else{
																											trnPaymentResMsg = generateExceptionMessage("Time Stamp Should be tomorrow's date");
																										}*/
																									}													
																								}else{
																									trnPaymentResMsg = generateExceptionMessage("Clent Cerificate is not Mattched");
																								}
																							}
																						}
																					}
																				}
																			}else{
																				trnPaymentResMsg = generateExceptionMessage("Payroll Reference Number should numeric only");
																			}
																		}
																	}else{
																		trnPaymentResMsg = generateExceptionMessage("Payroll Reference Number is missing");
																	}
																}else{
																	trnPaymentResMsg = generateExceptionMessage("Message Description is Mandatory");
																}
															}else{
																if(payrollAckFlag)
																	trnPaymentResMsg = generateExceptionMessage("Payroll Message Type should 'WPSPRREQ'");
																else
																	trnPaymentResMsg = generateExceptionMessage("Payroll Message Type should 'WPSPRMSG'");
															}
														}else{
															trnPaymentResMsg = generateExceptionMessage("Payroll Message Type is missing");
														}
													}
												}
											}
										}
									}else{
										trnPaymentResMsg = generateExceptionMessage("Receiver is always "+sourceBank.substring(0,4));
									}
								}else{
									trnPaymentResMsg = generateExceptionMessage("Receiver is Mandatory");
								}
							}else{
								trnPaymentResMsg = generateExceptionMessage("Sender is Mandatory");
						 	}
						}else{
							trnPaymentResMsg = generateExceptionMessage("Signature is not valid");
						}
					}else{
						trnPaymentResMsg = generateExceptionMessage("Signature is Missing");
					}
				}else{
					trnPaymentResMsg = generateExceptionMessage("Message shouldn't be blank");
				}
			}else{
				trnPaymentResMsg = generateExceptionMessage("No Client Cetificate exported from Webserver");
			}
		}catch (SignatureVerifyException e) {
			logger.error(e.getMessage(),e);
			trnPaymentResMsg = generateExceptionMessage(e.getMessage());
	 		return trnPaymentResMsg.toString();	
		}catch(ParseException e){
			logger.error(e.getMessage(),e);
			trnPaymentResMsg = generateExceptionMessage("Date format should be yyyy-MM-dd'T'HH:mm:ss");
	 		return trnPaymentResMsg.toString();			
		}catch (ClientNotFoundException e) {
			logger.error(e.getMessage(),e);
			trnPaymentResMsg = generateExceptionMessage(e.getMessage());
	 		return trnPaymentResMsg.toString();	
		} catch (DataAccessException e) {
			logger.error(e.getMessage(),e);
			trnPaymentResMsg = generateExceptionMessage(e.getMessage());
			return trnPaymentResMsg.toString();			
		}catch (IOException e) {
			logger.error(e.getMessage(),e);
			trnPaymentResMsg = generateExceptionMessage("Currently B2B Service is not available");
			return trnPaymentResMsg.toString();		
		} catch (TagMissingException e) {
			logger.error(e.getMessage(),e);
			trnPaymentResMsg = generateExceptionMessage(e.getMessage());
			return trnPaymentResMsg.toString();	
		} catch(Exception e){
			logger.error(e.getMessage(),e);
			trnPaymentResMsg = generateExceptionMessage("Currently B2B Service is not available");
			return trnPaymentResMsg.toString();	
		}
		return trnPaymentResMsg.toString();
	}	
	public String getSignedPayrollResponse(X509Certificate cert,byte[] bytes,String dirSignedPayroll,String schemaSignedPayroll,String sourceBank){
		String trnPaymentResMsg = "";
		DateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		ClientDao clientdao = DAOFactory.getClientDao();
		PayrollDao payrollDao = DAOFactory.getPayrollDao();
		String type = "PayrollSigned";
		logger.info("PayrollServiceImpl->(getSignedPayrollResponse) Inside getSignedPayrollResponse");
		try {
			if(cert!=null){
				if(bytes.length>0){
					BigInteger clientSlNo = cert.getSerialNumber();
					String slNoStr = clientSlNo.toString();
					File schemaFile = new File(schemaSignedPayroll);
					logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll Client Serial No: "+slNoStr+" \r\n");
					PayrollRequest payrollRequest = RequestMessageReader.readPayrollSignedRequest(bytes,schemaFile);	
					String sender = payrollRequest.getSender();
					String receiver = payrollRequest.getReceiver();
					String msgType = payrollRequest.getMsgType();
					String timeStamp = payrollRequest.getTimeStamp();
					String payrollMsgRef = payrollRequest.getPayrollMsgRef();
					String signature = payrollRequest.getSignature();
					if(signature!=null && signature!="" && signature.length()>0){
						boolean flag = PKCS7SignVerify.verifySignature(cert,bytes, signature,type);					
						if(flag){
							if(sender!=null && sender!="" && sender.length()>0){
								String registeredSlNo = clientdao.getSerialNo(sender.trim());
								if(registeredSlNo== null || registeredSlNo==""){
									trnPaymentResMsg = generateExceptionMessage("Certificate Serial Number is not Registered").toString(); 
								}else{
									if(slNoStr.trim().equalsIgnoreCase(registeredSlNo.trim())){
										if(receiver!=null && receiver!="" && receiver.length()>0){
											if(receiver.trim().equalsIgnoreCase(sourceBank)){
												if(msgType!=null && msgType!="" && msgType.length()>0){
													if(msgType.trim().equals("PRSIGNED")){		
														if(timeStamp!=null && timeStamp!="" && timeStamp.length()>0){
															Date dateTime = format.parse(timeStamp);
															Calendar c1 = Calendar.getInstance();
															Calendar c2 = Calendar.getInstance();
															c2.setTime(dateTime);
															if (c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)) {
																if(payrollMsgRef!=null && payrollMsgRef!="" && payrollMsgRef.length()>0){
																	boolean refNoCheck = payrollDao.checkReferenceNoExist(sender, payrollMsgRef);
																	if(refNoCheck){
																		File sourcefolder = new File(dirSignedPayroll);	
																		boolean noFileFound = true;
																		for (final File fileEntry : sourcefolder.listFiles()) {
																			String sourceFileName = "";
																			if (!fileEntry.isDirectory()) {
																				sourceFileName = fileEntry.getName();
																				logger.info("Folder conatins FileName : "+sourceFileName);
																				if(sourceFileName.contains(payrollMsgRef)){
																					noFileFound = false;
																					logger.info(" PAYROLL SIGNED FILE FOR  "+sourceFileName + " MATCHED");
																					File signedFile = new File(dirSignedPayroll+"/"+sourceFileName);
																					String signedMessage = readSignedContent(signedFile);																		
																					trnPaymentResMsg = generateSignedMessageResponse(signedMessage);
																					break;
																				}
																			}
																		}
																		if(noFileFound)
																			trnPaymentResMsg = generateExceptionMessage("No Signed file exist for requested reference number").toString();
																	}else{
																		trnPaymentResMsg = generateExceptionMessage("Requested Reference Number is Invalid").toString();
																	}
																}else{
																	trnPaymentResMsg = generateExceptionMessage("Message Reference is Missing").toString();
																}
															}else{
																trnPaymentResMsg = generateExceptionMessage("Time Stamp Should be today's date").toString();
															}
														}else{
															trnPaymentResMsg = generateExceptionMessage("Timestamp is Missing").toString();
														}
														
													}else{
														trnPaymentResMsg = generateExceptionMessage("Payroll Message Type should be 'PRSIGNED'").toString();
													}
												}else{
													trnPaymentResMsg = generateExceptionMessage("Payroll Message Type is missing").toString();
												}
											}else{
												trnPaymentResMsg = generateExceptionMessage("Receiver is always "+sourceBank).toString();
											}
										}else{
											trnPaymentResMsg = generateExceptionMessage("Receiver is Missing").toString();
										}
									}else{
										trnPaymentResMsg = generateExceptionMessage("Clent Cerificate is not Mattched").toString();
									}
								}
							}else{
								trnPaymentResMsg = generateExceptionMessage("Sender is Missing").toString();
						 	}
						}else{
							trnPaymentResMsg = generateExceptionMessage("Signature is not valid").toString();
						}
					}else{
						trnPaymentResMsg = generateExceptionMessage("Signature is Missing").toString();
					}					
				}else{
					trnPaymentResMsg = generateExceptionMessage("Input Message shouldn't be blank").toString();
				}
			}else{
				trnPaymentResMsg = generateExceptionMessage("No Client Cetificate exported from Webserver").toString();
			}
		}catch (SignatureVerifyException e) {
			logger.error(e.getMessage(),e);
			trnPaymentResMsg = generateExceptionMessage(e.getMessage()).toString();
	 		return trnPaymentResMsg.toString();	
		}catch(ParseException e){
			logger.error(e.getMessage(),e);
			trnPaymentResMsg = generateExceptionMessage("Date format should be yyyy-MM-dd'T'HH:mm:ss").toString();
	 		return trnPaymentResMsg.toString();			
		}catch (ClientNotFoundException e) {
			logger.error(e.getMessage(),e);
			trnPaymentResMsg = generateExceptionMessage(e.getMessage()).toString();
	 		return trnPaymentResMsg.toString();	
		} catch (DataAccessException e) {
			logger.error(e.getMessage(),e);
			trnPaymentResMsg = generateExceptionMessage(e.getMessage()).toString();
			return trnPaymentResMsg.toString();			
		}catch (IOException e) {
			logger.error(e.getMessage(),e);
			trnPaymentResMsg = generateExceptionMessage("Server facing technical problem. please try after some time").toString();
			return trnPaymentResMsg.toString();		
		}  catch(TagMissingException e){
			logger.error(e.getMessage(),e);
			trnPaymentResMsg = generateExceptionMessage("Invalid Request Message").toString();
			return trnPaymentResMsg.toString();	
		}catch(Exception e){
			logger.error(e.getMessage(),e);
			trnPaymentResMsg = generateExceptionMessage("Server facing technical problem. please try after some time").toString();
			return trnPaymentResMsg.toString();	
		}
		return trnPaymentResMsg.toString();
	}
 	private StringBuilder generateExceptionMessage(String message){
 		StringBuilder trnPaymentResMsg = new StringBuilder();
 		trnPaymentResMsg.append("<?xml version=\"1.0\"?>\r\n");
 		trnPaymentResMsg.append("<Message>\r\n");
 		trnPaymentResMsg.append("<StatusCode>FAILED</StatusCode>\r\n");
 		trnPaymentResMsg.append("<StatusDetail>"+message+"</StatusDetail>\r\n");
 		trnPaymentResMsg.append("</Message>");
 		return trnPaymentResMsg;
 	}
 	private String generateSignedMessageResponse(String message){
 		StringBuilder trnPaymentResMsg = new StringBuilder();
 		trnPaymentResMsg.append("<?xml version=\"1.0\"?>\r\n");
 		trnPaymentResMsg.append("<Message>\r\n");
 		trnPaymentResMsg.append("<StatusCode>Success</StatusCode>\r\n");
 		trnPaymentResMsg.append("<StatusDetail>OK</StatusDetail>\r\n");
 		trnPaymentResMsg.append("<SignedContent>"+message+"</SignedContent>\r\n");
 		trnPaymentResMsg.append("</Message>");
 		return trnPaymentResMsg.toString();
 	}
 	private String readSignedContent(File file){
		String result = "";
		FileInputStream fileInputStream = null;
		BufferedReader br = null;
		try{
			fileInputStream = new FileInputStream(file);
			br = new BufferedReader(new InputStreamReader(fileInputStream,"UTF-8"));
			String content;
			int count = 0;
			while((content = br.readLine()) != null) {
				if(count>0)
					result = result+"\r\n"+content;
				else
					result += content;
				count++;
			}		
			br.close();
		}catch (Exception e) {
			logger.error(e.getMessage(),e);
			return "";
		}
		return result;
	}
 	public String getPayrollMessageResponse(X509Certificate cert,byte[] bytes,String sourceBank,String dir,String dirRes,String printFlag,boolean payrollAckFlag,File schemaFile){
		String decimalRegExp = "^\\d+(\\.\\d{1,2})?$";
		StringBuilder trnPaymentResMsg = new StringBuilder();
		DateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		DateFormat fileFormatormat = new SimpleDateFormat("ddMMyyyyHHmmss");
		ClientDao clientdao = DAOFactory.getClientDao();
		PayrollDao payrollDao = DAOFactory.getPayrollDao();
		String type = "";
		//String regexAlfaNumeric = "^[a-zA-Z0-9]+$";
		String regex = "\\d+";
		logger.info("PayrollServiceImpl->(getPayrollResponse) Inside getPayrollResponse");
		boolean signedFlag = false;
		try {
			if(cert!=null){
				if(bytes.length>0){
					//logger.info("(PayrollServiceImpl)Orginal Payroll Message Content 1: --Start-- "+new String(bytes)+" --End--");
					//PublicKey publicKey = cert.getPublicKey();
					BigInteger clientSlNo = cert.getSerialNumber();
					String slNoStr = clientSlNo.toString();
					logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll Client Serial No: "+slNoStr+" \r\n");
					PayrollRequest payrollRequest = RequestMessageReader.readPayrollRequest(bytes,schemaFile);	
					//logger.info("(PayrollServiceImpl)Orginal Payroll Message Content 2: --Start-- "+new String(bytes)+" --End--");
					String sender = payrollRequest.getSender();
					String receiver = payrollRequest.getReceiver();
					String msgType = payrollRequest.getMsgType();
					String totalHeaderAmount = payrollRequest.getTotalAmount();
					String msgDescReq = payrollRequest.getMsgDesc();
					String timeStamp = payrollRequest.getTimeStamp();
					String payrollMsgRef = payrollRequest.getPayrollMsgRef();
					String payrollMsgType = "MT100-Payroll";//payrollRequest.getPayrollMsgType();
					String signature = payrollRequest.getSignature();
					String msgDescInput = payrollRequest.getMsgDesc();
					boolean wpsMessageFlag = payrollRequest.isWpsMessage();
					String molEstablishmentId = payrollRequest.getMolEstablishmentId();
					logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll Sender:"+sender+" Receiver:"+receiver+" MsgType:"+msgType+" msgDesc:"+msgDescReq+" \r\n");
					logger.info("PayrollServiceImpl->(getPayrollResponse) timeStamp:"+timeStamp+" Payroll Message Ref:"+payrollMsgRef+" Payroll Message Type:"+payrollMsgType+" \r\n");
					/*File folder = new File(dir);
					File[] listOfFiles = folder.listFiles();
					logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll Folder "+folder.getPath()+"\r\n");
					if(null != listOfFiles){
						logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll Folder files count "+listOfFiles.length+"\r\n");
					}else{
						logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll Folder files count 0\r\n");
					}*/
					//String fileName = "Payroll_"+payrollMsgRef+".xml";
					//String ackFileName = "Payroll_"+payrollMsgRef+"ACK"+fileFormatormat.format(new Date())+".xml";
					boolean writeFileFlag = false;
					Calendar c1 = Calendar.getInstance();
					Calendar c2 = Calendar.getInstance();
					Date dateTime = format.parse(timeStamp);
					c2.setTime(dateTime);
					logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll Type Falg for Ack/Payroll "+payrollAckFlag+" \r\n");
					if(payrollAckFlag)
						type = "PayrollACK";
					else{
						type = "PayrollMessage";
						//c1.add(Calendar.DATE, 1); // add 1 days		
						logger.info("PayrollServiceImpl (getPayrollResponse) After 1 days time stamp "+c1.getTime());
					}logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll Type set as "+type+" \r\n");
					if(!payrollAckFlag && (signature==null || signature=="" || signature.length()==0)){
						trnPaymentResMsg = generateExceptionMessage("Signature is Missing");
					}else{
						if(null!=signature)
							signedFlag = PKCS7SignVerify.verifySignature(cert,bytes, signature,type);					
						if(!signedFlag && !payrollAckFlag){
							trnPaymentResMsg = generateExceptionMessage("Signature is not valid");
						}else{								
							logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll Signature is valid \r\n");
							if(sender!=null && sender!="" && sender.length()>0){
								if(receiver!=null && receiver!="" && receiver.length()>0){
									if(receiver.trim().equals(sourceBank.substring(0,4))){
										if(!wpsMessageFlag && !payrollAckFlag){
											trnPaymentResMsg = generateExceptionMessage("WPSMessage is always True for WPS Payroll");
										}else{
											logger.info("Payroll "+!payrollAckFlag);
											logger.info("MOL Establishment ID is : "+(molEstablishmentId==null || molEstablishmentId=="" || molEstablishmentId.length()==0));
											if(!payrollAckFlag && (molEstablishmentId==null || molEstablishmentId=="" || molEstablishmentId.length()==0)){
												trnPaymentResMsg = generateExceptionMessage("MOL Establishment ID is Missing");
											}else{
												if(!payrollAckFlag && (molEstablishmentId.length()>18 || molEstablishmentId.length()<3)){
													trnPaymentResMsg = generateExceptionMessage("MOL Establishment ID is not valid");
												}else if(!payrollAckFlag && molEstablishmentId.indexOf("-")!=1 && molEstablishmentId.indexOf("-")!=2){
													trnPaymentResMsg = generateExceptionMessage("MOL Establishment ID is not valid");
												}else{
													int index = 0;
													String prefixMolId = "";
													String sufixMolId = "";
													if(!payrollAckFlag){
														index = molEstablishmentId.indexOf("-");
														prefixMolId = molEstablishmentId.substring(0, index);
														sufixMolId = molEstablishmentId.substring(index+1);														
													}
													if(!payrollAckFlag && (prefixMolId.length()>2 || sufixMolId.length()>15)){
														trnPaymentResMsg = generateExceptionMessage("MOL Establishment ID is not valid");
													}else if(!payrollAckFlag && (prefixMolId.length()<1 || sufixMolId.length()<1)){
														trnPaymentResMsg = generateExceptionMessage("MOL Establishment ID is not valid");
													}else if(!payrollAckFlag && (!prefixMolId.matches(regex) || !sufixMolId.matches(regex))){
														trnPaymentResMsg = generateExceptionMessage("MOL Establishment ID is not valid");
													}else{
														if(msgType!=null && msgType!="" && msgType.length()>0){
															if((!payrollAckFlag && msgType.trim().equals("WPSPRMSG")) || (payrollAckFlag && msgType.trim().equals("WPSPRREQ"))){
																if(msgDescInput!=null && msgDescInput!="" && msgDescInput.length()>0){
																	if(payrollMsgRef!=null && payrollMsgRef!="" && payrollMsgRef.length()>0){
																		logger.info("Payroll Ref. Length : "+payrollMsgRef.trim().length());
																		if(!payrollAckFlag && payrollMsgRef.trim().length()>16){
																			trnPaymentResMsg = generateExceptionMessage("Payroll Reference Number should not be more than 16 characters");
																		}else{
																			if(payrollMsgRef.matches(regex)){
																				if(!payrollAckFlag && (totalHeaderAmount==null || totalHeaderAmount=="" || totalHeaderAmount.length()==0)){
																					trnPaymentResMsg = generateExceptionMessage("Payroll Transaction Amount is missing");																
																				}else{
																					if(!payrollAckFlag && Double.parseDouble(totalHeaderAmount)<0){
																						trnPaymentResMsg = generateExceptionMessage("Payroll Transaction Amount value shouldn't be negetive");
																					}else{
																						if(!payrollAckFlag && !totalHeaderAmount.matches(decimalRegExp)){
																							trnPaymentResMsg = generateExceptionMessage("Payroll Transaction Amount decimal value should not more than 2 decimal point");
																						}else{																	
																							String registeredSlNo = clientdao.getSerialNo(sender.trim());
																							logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll registered serial No: "+registeredSlNo+" \r\n");
																							if(registeredSlNo== null || registeredSlNo==""){
																								trnPaymentResMsg = generateExceptionMessage("Certificate Serial Number is not Registered"); 
																							}else{
																								if(slNoStr.trim().equalsIgnoreCase(registeredSlNo.trim())){
																									logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll Signature is valid \r\n");
																									if (c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)) {
																										boolean fileRefFlag = payrollDao.checkReferenceNo(sender, payrollMsgRef);	
																										int serialNo = payrollDao.getSerialNo(sender, payrollMsgRef, type);
																										if(payrollAckFlag){
																											String msgDesc = "Payroll Payment Response";//payrollRequest.getMsgDesc();
																											String resMsgType = "PRRES";
																											//if(logSaveFlag){
																												logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll write File Flag and print Flag writeFileFlag="+writeFileFlag+" printFlag="+printFlag+"\r\n");
																												if(printFlag.equals("1")){
																													String ackFileName = "Payroll_"+payrollMsgRef+"ACK"+fileFormatormat.format(new Date())+".xml";
																													logger.info("(getPayrollResponse) Payroll Ack Flag "+payrollAckFlag +" to write "+dir+ackFileName+"\r\n");
																													File outputFile = new File(dir+ackFileName);
																													if(!outputFile.exists())
																														outputFile.createNewFile();
																													FileOutputStream fop = new FileOutputStream(outputFile);
																													fop.write(bytes);
																													fop.flush();
																													fop.close();
																												}
																												JSONObject headerStatusObj = payrollDao.getHeaderStatus(sender, payrollMsgRef,"WPSPRMSG");
																												String headerStatus = (String) headerStatusObj.get("status");
																												String headerStatusDesc = (String) headerStatusObj.get("desc");
																												if(headerStatus!=null && headerStatus!="" && headerStatus.length()>0){
																													List<PayrollDetailsDto> detailsArr = payrollDao.getPayrollData(sender, payrollMsgRef);
																													if(detailsArr.size()>0){
																														int payrollCount = 1;
																														trnPaymentResMsg.append("<?xml version=\"1.0\"?>\r\n");
																														trnPaymentResMsg.append(" <Message>\r\n");
																														trnPaymentResMsg.append(" <Header>\r\n");
																														trnPaymentResMsg.append(" <Sender>"+receiver+"</Sender>\r\n");
																														trnPaymentResMsg.append(" <Receiver>"+sender+"</Receiver>\r\n");
																														trnPaymentResMsg.append(" <MessageType>"+resMsgType+"</MessageType>\r\n");
																														trnPaymentResMsg.append(" <MessageDescription>"+msgDesc+"</MessageDescription>\r\n");
																														trnPaymentResMsg.append(" <TimeStamp>"+timeStamp+"</TimeStamp>\r\n");
																														trnPaymentResMsg.append(" </Header>\r\n");
																														trnPaymentResMsg.append(" <Body>\r\n");
																														trnPaymentResMsg.append(" <PayrollResponse>\r\n");
																														trnPaymentResMsg.append(" <PayrollMessageRef>"+payrollMsgRef+"</PayrollMessageRef>\r\n");
																														trnPaymentResMsg.append(" <PayrollMessageType>MT100-Payroll</PayrollMessageType>\r\n");
																														StringBuilder detailTrn = new StringBuilder();
																														double totalAmount = 0;
																														for(PayrollDetailsDto payrollDetails:detailsArr){
																															double amount = payrollDetails.getAmount();
																															totalAmount = totalAmount+amount;
																															detailTrn.append(" <PayrollTransactionResponse>\r\n");
																															detailTrn.append(" <SequenceNum>"+payrollDetails.getTransactionRef()+"</SequenceNum>\r\n");
																															if("REJECT".equals(payrollDetails.getStatus()) || "REJECTED".equals(payrollDetails.getStatus())){
																																detailTrn.append(" <StatusCode>DE</StatusCode>\r\n");
																																detailTrn.append(" <StatusDetail>"+payrollDetails.getStatusDesc()+"</StatusDetail>\r\n");	
																															}else if("SUCCESS".equals(payrollDetails.getStatus())){
																																detailTrn.append(" <StatusCode>OK</StatusCode>\r\n");
																																detailTrn.append(" <StatusDetail>Processed Successfully</StatusDetail>\r\n");	
																															}else if("PENDING".equals(payrollDetails.getStatus())){
																																detailTrn.append(" <StatusCode>OK</StatusCode>\r\n");
																																detailTrn.append(" <StatusDetail>Under Process</StatusDetail>\r\n");	
																															}else{
																																detailTrn.append(" <StatusCode>"+payrollDetails.getStatus()+"</StatusCode>\r\n");
																																detailTrn.append(" <StatusDetail>"+payrollDetails.getStatusDesc()+"</StatusDetail>\r\n");	
																															}
																															detailTrn.append(" </PayrollTransactionResponse>\r\n");
																															payrollCount++;
																														}
																														int totalTrnCount = payrollCount-1;
																														String amountStr = BigDecimal.valueOf(totalAmount).toString();
																														trnPaymentResMsg.append(" <PayrollTransactionCount>"+totalTrnCount+"</PayrollTransactionCount>\r\n");
																														trnPaymentResMsg.append(" <PayrollTransactionAmount>"+amountStr+"</PayrollTransactionAmount>\r\n");
																														trnPaymentResMsg.append(detailTrn);
																														trnPaymentResMsg.append(" </PayrollResponse>\r\n");
																														trnPaymentResMsg.append(" </Body>\r\n");
																														trnPaymentResMsg.append(" <ResponseStatus>\r\n");
																														if("ACCEPTED".equals(headerStatus)){
																															trnPaymentResMsg.append(" <StatusCode>OK</StatusCode>\r\n");
																															trnPaymentResMsg.append(" <StatusDetail>OK</StatusDetail>\r\n");
																														}else if("RECEIVED".equals(headerStatus)){
																															trnPaymentResMsg.append(" <StatusCode>PENDING</StatusCode>\r\n");
																															trnPaymentResMsg.append(" <StatusDetail>"+headerStatusDesc+", Waiting for the Process</StatusDetail>\r\n");
																														}else if("REJECT".equals(headerStatus) || "REJECTED".equals(headerStatus)){
																															trnPaymentResMsg.append(" <StatusCode>FAILED</StatusCode>\r\n");
																															trnPaymentResMsg.append(" <StatusDetail>"+headerStatusDesc+"</StatusDetail>\r\n");																															
																														}else{
																															trnPaymentResMsg.append(" <StatusCode>"+headerStatus+"</StatusCode>\r\n");
																															trnPaymentResMsg.append(" <StatusDetail>"+headerStatusDesc+"</StatusDetail>\r\n");																															
																														}
																														trnPaymentResMsg.append(" </ResponseStatus>\r\n");	
																														trnPaymentResMsg.append(" </Message>");
																													}else{
																														trnPaymentResMsg.append("<?xml version=\"1.0\"?>\r\n");
																														trnPaymentResMsg.append(" <Message>\r\n");
																														trnPaymentResMsg.append(" <Header>\r\n");
																														trnPaymentResMsg.append(" <Sender>"+receiver+"</Sender>\r\n");
																														trnPaymentResMsg.append(" <Receiver>"+sender+"</Receiver>\r\n");
																														trnPaymentResMsg.append(" <MessageType>"+resMsgType+"</MessageType>\r\n");
																														trnPaymentResMsg.append(" <MessageDescription>"+msgDesc+"</MessageDescription>\r\n");
																														trnPaymentResMsg.append(" <TimeStamp>"+timeStamp+"</TimeStamp>\r\n");
																														trnPaymentResMsg.append(" </Header>\r\n");
																														trnPaymentResMsg.append(" <Body>\r\n");
																														
																														trnPaymentResMsg.append(" <PayrollResponse>\r\n");
																														trnPaymentResMsg.append(" <PayrollMessageRef>"+payrollMsgRef+"</PayrollMessageRef>\r\n");
																														trnPaymentResMsg.append(" <PayrollMessageType>MT100-Payroll</PayrollMessageType>\r\n");
																														trnPaymentResMsg.append(" </PayrollResponse>\r\n");
																														
																														trnPaymentResMsg.append(" </Body>\r\n");
																														trnPaymentResMsg.append(" <ResponseStatus>\r\n");
																														if("ACCEPTED".equals(headerStatus)){
																															trnPaymentResMsg.append(" <StatusCode>OK</StatusCode>\r\n");
																															trnPaymentResMsg.append(" <StatusDetail>OK</StatusDetail>\r\n");
																														}else if("RECEIVED".equals(headerStatus)){
																															trnPaymentResMsg.append(" <StatusCode>PENDING</StatusCode>\r\n");
																															trnPaymentResMsg.append(" <StatusDetail>"+headerStatusDesc+", Waiting for the Process</StatusDetail>\r\n");
																														}else if("REJECT".equals(headerStatus) || "REJECTED".equals(headerStatus)){
																															trnPaymentResMsg.append(" <StatusCode>FAILED</StatusCode>\r\n");
																															trnPaymentResMsg.append(" <StatusDetail>"+headerStatusDesc+"</StatusDetail>\r\n");																															
																														}else{
																															trnPaymentResMsg.append(" <StatusCode>"+headerStatus+"</StatusCode>\r\n");
																															trnPaymentResMsg.append(" <StatusDetail>"+headerStatusDesc+"</StatusDetail>\r\n");																															
																														}
																														trnPaymentResMsg.append(" </ResponseStatus>\r\n");	
																														trnPaymentResMsg.append(" </Message>");
																														logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll No File exists "+trnPaymentResMsg.toString()+" \r\n");
																													}
																												}else{
																													trnPaymentResMsg.append("<?xml version=\"1.0\"?>\r\n");
																													trnPaymentResMsg.append(" <Message>\r\n");
																													trnPaymentResMsg.append(" <Header>\r\n");
																													trnPaymentResMsg.append(" <Sender>"+receiver+"</Sender>\r\n");
																													trnPaymentResMsg.append(" <Receiver>"+sender+"</Receiver>\r\n");
																													trnPaymentResMsg.append(" <MessageType>"+resMsgType+"</MessageType>\r\n");
																													trnPaymentResMsg.append(" <MessageDescription>"+msgDesc+"</MessageDescription>\r\n");
																													trnPaymentResMsg.append(" <TimeStamp>"+timeStamp+"</TimeStamp>\r\n");
																													trnPaymentResMsg.append(" </Header>\r\n");
																													trnPaymentResMsg.append(" <Body>\r\n");
																													
																													trnPaymentResMsg.append(" <PayrollResponse>\r\n");
																													trnPaymentResMsg.append(" <PayrollMessageRef>"+payrollMsgRef+"</PayrollMessageRef>\r\n");
																													trnPaymentResMsg.append(" <PayrollMessageType>MT100-Payroll</PayrollMessageType>\r\n");
																													trnPaymentResMsg.append(" </PayrollResponse>\r\n");
																													
																													trnPaymentResMsg.append(" </Body>\r\n");
																													trnPaymentResMsg.append(" <ResponseStatus>\r\n");
																													trnPaymentResMsg.append(" <StatusCode>FAILED</StatusCode>\r\n");
																													trnPaymentResMsg.append(" <StatusDetail>No Result Found For the requested Reference Number</StatusDetail>\r\n");
																													trnPaymentResMsg.append(" </ResponseStatus>\r\n");	
																													trnPaymentResMsg.append(" </Message>");
																													logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll No File exists "+trnPaymentResMsg.toString()+" \r\n");
																												}																						
																											/*}else{
																												trnPaymentResMsg.append("<?xml version=\"1.0\"?>\r\n");
																												trnPaymentResMsg.append(" <Message>\r\n");
																												trnPaymentResMsg.append(" <Header>\r\n");
																												trnPaymentResMsg.append(" <Sender>"+receiver+"</Sender>\r\n");
																												trnPaymentResMsg.append(" <Receiver>"+sender+"</Receiver>\r\n");
																												trnPaymentResMsg.append(" <MessageType>"+msgType+"</MessageType>\r\n");
																												trnPaymentResMsg.append(" <MessageDescription>"+msgDesc+"</MessageDescription>\r\n");
																												trnPaymentResMsg.append(" <TimeStamp>"+timeStamp+"</TimeStamp>\r\n");
																												trnPaymentResMsg.append(" </Header>\r\n");
																												trnPaymentResMsg.append(" <Body>\r\n");
																													
																													trnPaymentResMsg.append(" <PayrollResponse>\r\n");
																													trnPaymentResMsg.append(" <PayrollMessageRef>"+payrollMsgRef+"</PayrollMessageRef>\r\n");
																													trnPaymentResMsg.append(" <PayrollMessageType>MT100-Payroll</PayrollMessageType>\r\n");
																													trnPaymentResMsg.append(" </PayrollResponse>\r\n");
																													
																													trnPaymentResMsg.append(" </Body>\r\n");
																												trnPaymentResMsg.append(" <ResponseStatus>\r\n");
																												trnPaymentResMsg.append(" <StatusCode>FAILED</StatusCode>\r\n");
																												trnPaymentResMsg.append(" <StatusDetail>Unable to process. Please try after sometime</StatusDetail>\r\n");
																												trnPaymentResMsg.append(" </ResponseStatus>\r\n");	
																												trnPaymentResMsg.append(" </Message>");
																												logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll No File exists "+trnPaymentResMsg.toString()+" \r\n");
																											}	*/																
																										}else{
																											String msgDesc = "Payroll Message Acknowledgement";//payrollRequest.getMsgDesc();
																											String resMsgType = "PRACK";
																											if(!fileRefFlag){
																												PayrollLogDetails payrollLogs = new PayrollLogDetails();
																												payrollLogs.setClientId(sender);
																												payrollLogs.setActivityTime(new Date());
																												payrollLogs.setFileRef(payrollMsgRef);
																												payrollLogs.setMsgType(type);
																												payrollLogs.setSerialNo(serialNo);
																												payrollLogs.setFreeText(sender+" Send Payroll Request having Payroll Ref. "+payrollMsgRef);
																												boolean logSaveFlag = payrollDao.insertDataInPayrollLogs(payrollLogs);
																												if(logSaveFlag){
																													PayrollHeaderDto payrollHeader = new PayrollHeaderDto();
																													payrollHeader.setSender(sender);
																													payrollHeader.setReceiver(receiver);
																													payrollHeader.setMsgType(msgType);
																													payrollHeader.setFileRef(payrollMsgRef);
																													payrollHeader.setValueDate(timeStamp);
																													boolean payrollHeaderSaveFlag = payrollDao.insertDataInPayrollHeader(payrollHeader);
																													if(payrollHeaderSaveFlag){
																														File outputFile = new File(dir+"/B2B_Payroll_"+payrollMsgRef+".xml");
																														if(!outputFile.exists())
																															outputFile.createNewFile();
																														FileOutputStream fop = new FileOutputStream(outputFile);
																														fop.write(bytes);
																														fop.flush();
																														fop.close();
																														trnPaymentResMsg.append("<?xml version=\"1.0\"?>\r\n");
																												 		trnPaymentResMsg.append(" <Message>\r\n");
																														trnPaymentResMsg.append(" <Header>\r\n");
																														trnPaymentResMsg.append(" <Sender>"+receiver+"</Sender>\r\n");
																														trnPaymentResMsg.append(" <Receiver>"+sender+"</Receiver>\r\n");
																														trnPaymentResMsg.append(" <MessageType>"+resMsgType+"</MessageType>\r\n");
																														trnPaymentResMsg.append(" <MessageDescription>"+msgDesc+"</MessageDescription>\r\n");
																												 		trnPaymentResMsg.append(" <TimeStamp>"+timeStamp+"</TimeStamp>\r\n");
																												 		trnPaymentResMsg.append(" </Header>\r\n");
																														trnPaymentResMsg.append(" <Body>\r\n");
																														trnPaymentResMsg.append(" <PayrollMessageAck>\r\n");
																														trnPaymentResMsg.append(" <PayrollMessageRef>"+payrollMsgRef+"</PayrollMessageRef>\r\n");
																												 		trnPaymentResMsg.append(" <PayrollMessageType>"+payrollMsgType+"</PayrollMessageType>\r\n");
																														trnPaymentResMsg.append(" </PayrollMessageAck>\r\n");
																												 		trnPaymentResMsg.append(" </Body>\r\n");
																												 		trnPaymentResMsg.append(" <ResponseStatus>\r\n");
																												 		trnPaymentResMsg.append(" <StatusCode>OK</StatusCode>\r\n");
																												 		trnPaymentResMsg.append(" <StatusDetail>Payroll File received successfully kindly check the payroll enquiry after 30 min</StatusDetail>\r\n");
																												 		trnPaymentResMsg.append(" </ResponseStatus>\r\n");									 		
																												 		trnPaymentResMsg.append(" </Message>");
																												 		logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll Based on writeFileFlag true "+trnPaymentResMsg+" \r\n");
																													}else{
																														trnPaymentResMsg.append("<?xml version=\"1.0\"?>\r\n");
																												 		trnPaymentResMsg.append(" <Message>\r\n");
																														trnPaymentResMsg.append(" <Header>");
																														trnPaymentResMsg.append(" <Sender>"+receiver+"</Sender>\r\n");
																														trnPaymentResMsg.append(" <Receiver>"+sender+"</Receiver>\r\n");
																														trnPaymentResMsg.append(" <MessageType>"+resMsgType+"</MessageType>\r\n");
																														trnPaymentResMsg.append(" <MessageDescription>"+msgDesc+"</MessageDescription>\r\n");
																												 		trnPaymentResMsg.append(" <TimeStamp>"+timeStamp+"</TimeStamp>\r\n");
																												 		trnPaymentResMsg.append(" </Header>\r\n");
																												 		trnPaymentResMsg.append(" <Body>\r\n");
																														
																														trnPaymentResMsg.append(" <PayrollMessageAck>\r\n");
																														trnPaymentResMsg.append(" <PayrollMessageRef>"+payrollMsgRef+"</PayrollMessageRef>\r\n");
																														trnPaymentResMsg.append(" <PayrollMessageType>MT100-Payroll</PayrollMessageType>\r\n");
																														trnPaymentResMsg.append(" </PayrollMessageAck>\r\n");
																														
																														trnPaymentResMsg.append(" </Body>\r\n");
																														trnPaymentResMsg.append(" <ResponseStatus>\r\n");
																														trnPaymentResMsg.append(" <StatusCode>FAILED</StatusCode>\r\n");
																												 		trnPaymentResMsg.append(" <StatusDetail>Unable to Process. Please try after sometime</StatusDetail>\r\n");
																														trnPaymentResMsg.append(" </ResponseStatus>\r\n");		
																												 		trnPaymentResMsg.append(" </Message>");
																												 		logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll Based on writeFileFlag false "+trnPaymentResMsg+" \r\n");
																													}		
																												}else{
																													trnPaymentResMsg.append("<?xml version=\"1.0\"?>\r\n");
																											 		trnPaymentResMsg.append(" <Message>\r\n");
																													trnPaymentResMsg.append(" <Header>");
																													trnPaymentResMsg.append(" <Sender>"+receiver+"</Sender>\r\n");
																													trnPaymentResMsg.append(" <Receiver>"+sender+"</Receiver>\r\n");
																													trnPaymentResMsg.append(" <MessageType>"+resMsgType+"</MessageType>\r\n");
																													trnPaymentResMsg.append(" <MessageDescription>"+msgDesc+"</MessageDescription>\r\n");
																											 		trnPaymentResMsg.append(" <TimeStamp>"+timeStamp+"</TimeStamp>\r\n");
																											 		trnPaymentResMsg.append(" </Header>\r\n");
																											 		trnPaymentResMsg.append(" <Body>\r\n");
																													
																													trnPaymentResMsg.append(" <PayrollMessageAck>\r\n");
																													trnPaymentResMsg.append(" <PayrollMessageRef>"+payrollMsgRef+"</PayrollMessageRef>\r\n");
																													trnPaymentResMsg.append(" <PayrollMessageType>MT100-Payroll</PayrollMessageType>\r\n");
																													trnPaymentResMsg.append(" </PayrollMessageAck>\r\n");
																													
																													trnPaymentResMsg.append(" </Body>\r\n");
																													trnPaymentResMsg.append(" <ResponseStatus>\r\n");
																													trnPaymentResMsg.append(" <StatusCode>FAILED</StatusCode>\r\n");
																											 		trnPaymentResMsg.append(" <StatusDetail>Unable to Process. Please try after sometime</StatusDetail>\r\n");
																													trnPaymentResMsg.append(" </ResponseStatus>\r\n");		
																											 		trnPaymentResMsg.append(" </Message>");
																											 		logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll Based on writeFileFlag false "+trnPaymentResMsg+" \r\n");
																												}																																		
																											}else{
																												trnPaymentResMsg.append("<?xml version=\"1.0\"?>\r\n");
																										 		trnPaymentResMsg.append(" <Message>\r\n");
																												trnPaymentResMsg.append(" <Header>");
																												trnPaymentResMsg.append(" <Sender>"+receiver+"</Sender>\r\n");
																												trnPaymentResMsg.append(" <Receiver>"+sender+"</Receiver>\r\n");
																												trnPaymentResMsg.append(" <MessageType>"+resMsgType+"</MessageType>\r\n");
																												trnPaymentResMsg.append(" <MessageDescription>"+msgDesc+"</MessageDescription>\r\n");
																										 		trnPaymentResMsg.append(" <TimeStamp>"+timeStamp+"</TimeStamp>\r\n");
																										 		trnPaymentResMsg.append(" </Header>\r\n");
																										 		trnPaymentResMsg.append(" <Body>\r\n");
																												
																												trnPaymentResMsg.append(" <PayrollMessageAck>\r\n");
																												trnPaymentResMsg.append(" <PayrollMessageRef>"+payrollMsgRef+"</PayrollMessageRef>\r\n");
																												trnPaymentResMsg.append(" <PayrollMessageType>MT100-Payroll</PayrollMessageType>\r\n");
																												trnPaymentResMsg.append(" </PayrollMessageAck>\r\n");
																												
																												trnPaymentResMsg.append(" </Body>\r\n");
																												trnPaymentResMsg.append(" <ResponseStatus>\r\n");
																												trnPaymentResMsg.append(" <StatusCode>DU</StatusCode>\r\n");
																										 		trnPaymentResMsg.append(" <StatusDetail>Reference Number already Used</StatusDetail>\r\n");
																												trnPaymentResMsg.append(" </ResponseStatus>\r\n");		
																										 		trnPaymentResMsg.append(" </Message>");
																										 		logger.info("PayrollServiceImpl->(getPayrollResponse) Payroll Based on writeFileFlag false "+trnPaymentResMsg+" \r\n");
																											}
																										}
																									}else{
																										trnPaymentResMsg = generateExceptionMessage("Time Stamp Should be today's date");
																										/*if(payrollAckFlag){
																											trnPaymentResMsg = generateExceptionMessage("Time Stamp Should be today's date");
																										}else{
																											trnPaymentResMsg = generateExceptionMessage("Time Stamp Should be tomorrow's date");
																										}*/
																									}													
																								}else{
																									trnPaymentResMsg = generateExceptionMessage("Clent Cerificate is not Mattched");
																								}
																							}
																						}
																					}
																				}
																			}else{
																				trnPaymentResMsg = generateExceptionMessage("Payroll Reference Number should numeric only");
																			}
																		}
																	}else{
																		trnPaymentResMsg = generateExceptionMessage("Payroll Reference Number is missing");
																	}
																}else{
																	trnPaymentResMsg = generateExceptionMessage("Message Description is Mandatory");
																}
															}else{
																if(payrollAckFlag)
																	trnPaymentResMsg = generateExceptionMessage("Payroll Message Type should 'WPSPRREQ'");
																else
																	trnPaymentResMsg = generateExceptionMessage("Payroll Message Type should 'WPSPRMSG'");
															}
														}else{
															trnPaymentResMsg = generateExceptionMessage("Payroll Message Type is missing");
														}
													}
												}
											}
										}
									}else{
										trnPaymentResMsg = generateExceptionMessage("Receiver is always "+sourceBank.substring(0,4));
									}
								}else{
									trnPaymentResMsg = generateExceptionMessage("Receiver is Mandatory");
								}
							}else{
								trnPaymentResMsg = generateExceptionMessage("Sender is Mandatory");
						 	}
						}
					}
				}else{
					trnPaymentResMsg = generateExceptionMessage("Message shouldn't be blank");
				}
			}else{
				trnPaymentResMsg = generateExceptionMessage("No Client Cetificate exported from Webserver");
			}
		}catch (SignatureVerifyException e) {
			logger.error(e.getMessage(),e);
			trnPaymentResMsg = generateExceptionMessage(e.getMessage());
	 		return trnPaymentResMsg.toString();	
		}catch(ParseException e){
			logger.error(e.getMessage(),e);
			trnPaymentResMsg = generateExceptionMessage("Date format should be yyyy-MM-dd'T'HH:mm:ss");
	 		return trnPaymentResMsg.toString();			
		}catch (ClientNotFoundException e) {
			logger.error(e.getMessage(),e);
			trnPaymentResMsg = generateExceptionMessage(e.getMessage());
	 		return trnPaymentResMsg.toString();	
		} catch (DataAccessException e) {
			logger.error(e.getMessage(),e);
			trnPaymentResMsg = generateExceptionMessage(e.getMessage());
			return trnPaymentResMsg.toString();			
		}catch (IOException e) {
			logger.error(e.getMessage(),e);
			trnPaymentResMsg = generateExceptionMessage("Currently B2B Service is not available");
			return trnPaymentResMsg.toString();		
		} catch (TagMissingException e) {
			logger.error(e.getMessage(),e);
			trnPaymentResMsg = generateExceptionMessage(e.getMessage());
			return trnPaymentResMsg.toString();	
		} catch(Exception e){
			logger.error(e.getMessage(),e);
			trnPaymentResMsg = generateExceptionMessage("Currently B2B Service is not available");
			return trnPaymentResMsg.toString();	
		}
		return trnPaymentResMsg.toString();
	}	
	
}
