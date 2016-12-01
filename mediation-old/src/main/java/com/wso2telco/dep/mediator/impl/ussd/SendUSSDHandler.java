/*
 *
 *  Copyright (c) 2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * /
 */
package com.wso2telco.dep.mediator.impl.ussd;

import com.wso2telco.core.dbutils.fileutils.FileReader;
import com.wso2telco.dep.mediator.MSISDNConstants;
import com.wso2telco.dep.mediator.OperatorEndpoint;
import com.wso2telco.dep.mediator.internal.Type;
import com.wso2telco.dep.mediator.internal.UID;
import com.wso2telco.dep.mediator.mediationrule.OriginatingCountryCalculatorIDD;
import com.wso2telco.dep.mediator.service.USSDService;
import com.wso2telco.dep.mediator.util.DataPublisherConstants;
import com.wso2telco.dep.mediator.util.FileNames;
import com.wso2telco.dep.mediator.util.HandlerUtils;
import com.wso2telco.dep.oneapivalidation.exceptions.CustomException;
import com.wso2telco.dep.subscriptionvalidator.util.ValidatorUtils;
import org.apache.axis2.AxisFault;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.commons.json.JsonUtil;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.json.JSONObject;
import org.wso2.carbon.apimgt.gateway.handlers.security.APISecurityUtils;
import org.wso2.carbon.apimgt.gateway.handlers.security.AuthenticationContext;
import org.wso2.carbon.utils.CarbonUtils;

import java.io.File;
import java.util.Map;

// TODO: Auto-generated Javadoc

/**
 * The Class SendUSSDHandler.
 */
public class SendUSSDHandler implements USSDHandler {

	/** The log. */
	private Log log = LogFactory.getLog(SendUSSDHandler.class);

	/** The Constant API_TYPE. */
	private static final String API_TYPE = "ussd";

	/** The occi. */
	private OriginatingCountryCalculatorIDD occi;

	/** The executor. */
	private USSDExecutor executor;

	/** The ussdDAO. */
	private USSDService ussdService;

	/**
	 * Instantiates a new send ussd handler.
	 *
	 * @param executor
	 *            the executor
	 */
	public SendUSSDHandler(USSDExecutor executor) {
		occi = new OriginatingCountryCalculatorIDD();
		this.executor = executor;
		ussdService = new USSDService();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.wso2telco.mediator.impl.ussd.USSDHandler#handle(org.apache.synapse.
	 * MessageContext)
	 */
	@Override
	public boolean handle(MessageContext context) throws CustomException, AxisFault, Exception {

		String requestid = UID.getUniqueID(Type.SEND_USSD.getCode(), context, executor.getApplicationid());
		JSONObject jsonBody = executor.getJsonBody();
		FileReader fileReader = new FileReader();
		String file = CarbonUtils.getCarbonConfigDirPath() + File.separator
		              + FileNames.MEDIATOR_CONF_FILE.getFileName();

		String address = jsonBody.getJSONObject("outboundUSSDMessageRequest").getString("address");
		String notifyUrl = jsonBody.getJSONObject("outboundUSSDMessageRequest").getJSONObject("responseRequest")
				.getString("notifyURL");
		String msisdn = address.substring(5);

		Map<String, String> mediatorConfMap = fileReader.readPropertyFile(file);

		/*AuthenticationContext authContext = APISecurityUtils.getAuthenticationContext(context);
        String consumerKey = "";
        String userId="";
        //String operatorId="";
        if (authContext != null) {
            consumerKey = authContext.getConsumerKey();
            userId=authContext.getUsername();
        }*/
		String consumerKey = "";
		String userId = "";
		consumerKey = (String) context.getProperty("CONSUMER_KEY");
		userId = (String) context.getProperty("USER_ID");
		//Integer subscriptionId = ussdService.ussdRequestEntry(notifyUrl ,consumerKey);

		OperatorEndpoint endpoint = null;
        if (ValidatorUtils.getValidatorForSubscriptionFromMessageContext(context).validate(context)) {
            endpoint = occi.getAPIEndpointsByMSISDN(address.replace("tel:", ""), API_TYPE, executor.getSubResourcePath(), false,executor.getValidoperators());
        }
        //operatorId=ussdService.getOperatorIdByOperator(endpoint.getOperator());
        
        Integer subscriptionId = ussdService.ussdRequestEntry(notifyUrl ,consumerKey,endpoint.getOperator(),userId);
        log.info("created subscriptionId  -  " + subscriptionId + " Request ID: " + UID.getRequestID(context));
		
		String subsEndpoint = mediatorConfMap.get("ussdGatewayEndpoint") + subscriptionId;
		log.info("Subsendpoint - " + subsEndpoint + " Request ID: " + UID.getRequestID(context));

		jsonBody.getJSONObject("outboundUSSDMessageRequest").getJSONObject("responseRequest").put("notifyURL",
				subsEndpoint);

		context.setProperty(MSISDNConstants.USER_MSISDN, msisdn);
		/*OperatorEndpoint endpoint = null;
		if (ValidatorUtils.getValidatorForSubscription(context).validate(context)) {
			endpoint = occi.getAPIEndpointsByMSISDN(address.replace("tel:", ""), API_TYPE,
					executor.getSubResourcePath(), false, executor.getValidoperators());
			
			
			OparatorEndPointSearchDTO searchDTO = new OparatorEndPointSearchDTO();
			searchDTO.setApi(APIType.USSD);
			searchDTO.setContext(context);
			searchDTO.setIsredirect(false);
			searchDTO.setMSISDN(address);
			searchDTO.setOperators(executor.getValidoperators());
			searchDTO.setRequestPathURL(executor.getSubResourcePath());

			endpoint = occi.getOperatorEndpoint(searchDTO);
			
		}*/
		String sending_add = endpoint.getEndpointref().getAddress();
		log.info("sending endpoint found: " + sending_add + " Request ID: " + UID.getRequestID(context));

		HandlerUtils.setHandlerProperty(context,this.getClass().getSimpleName());
		HandlerUtils.setEndpointProperty(context,sending_add);
		HandlerUtils.setAuthorizationHeader(context,executor,endpoint);

		/*String responseStr = executor.makeRequest(endpoint, sending_add, jsonBody.toString(), true, context,false);
		executor.handlePluginException(responseStr);
		executor.removeHeaders(context);
		executor.setResponse(context, responseStr);*/
		
		((Axis2MessageContext) context).getAxis2MessageContext().setProperty("messageType", "application/json");
		String transformedJson = jsonBody.toString();
		JsonUtil.newJsonPayload(((Axis2MessageContext) context).getAxis2MessageContext(), transformedJson, true, true);

		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.wso2telco.mediator.impl.ussd.USSDHandler#validate(java.lang.String,
	 * java.lang.String, org.json.JSONObject, org.apache.synapse.MessageContext)
	 */
	@Override
	public boolean validate(String httpMethod, String requestPath, JSONObject jsonBody, MessageContext context)
			throws Exception {
		context.setProperty(DataPublisherConstants.OPERATION_TYPE, 400);
		if (!httpMethod.equalsIgnoreCase("POST")) {
			((Axis2MessageContext) context).getAxis2MessageContext().setProperty("HTTP_SC", 405);
			throw new Exception("Method not allowed");
		}

		return true;
	}
}
