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

package com.wso2telco.dep.mediator.impl.payment;

import com.wso2telco.core.dbutils.fileutils.FileReader;
import com.wso2telco.dep.mediator.MSISDNConstants;
import com.wso2telco.dep.mediator.OperatorEndpoint;
import com.wso2telco.dep.mediator.ResponseHandler;
import com.wso2telco.dep.mediator.mediationrule.OriginatingCountryCalculatorIDD;
import com.wso2telco.dep.mediator.service.PaymentService;
import com.wso2telco.dep.mediator.util.FileNames;
import com.wso2telco.dep.mediator.util.HandlerUtils;
import com.wso2telco.dep.oneapivalidation.exceptions.CustomException;
import com.wso2telco.dep.oneapivalidation.service.IServiceValidate;
import com.wso2telco.dep.oneapivalidation.service.impl.payment.ValidateListTransactions;
import com.wso2telco.dep.subscriptionvalidator.util.ValidatorUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.json.JSONArray;
import org.json.JSONObject;
import org.wso2.carbon.utils.CarbonUtils;

import java.io.File;
import java.util.Map;

public class ListTransactionsHandler implements PaymentHandler {
	private Log log = LogFactory.getLog(ListTransactionsHandler.class);
	private static final String API_TYPE = "payment";
	private OriginatingCountryCalculatorIDD occi;
	private ResponseHandler responseHandler;
	private PaymentExecutor executor;
	private PaymentService dbservice;

	public ListTransactionsHandler(PaymentExecutor executor) {
		this.executor = executor;
		occi = new OriginatingCountryCalculatorIDD();
		responseHandler = new ResponseHandler();
		dbservice = new PaymentService();
	}

	@Override
	public boolean validate(String httpMethod, String requestPath,
			JSONObject jsonBody, MessageContext context) throws Exception {
		if (!httpMethod.equalsIgnoreCase("GET")) {
			((Axis2MessageContext) context).getAxis2MessageContext()
			                               .setProperty("HTTP_SC", 405);
			throw new Exception("Method not allowed");
		}

		String[] params = executor.getSubResourcePath().split("/");
		IServiceValidate validator = new ValidateListTransactions();
		validator.validateUrl(requestPath);
		validator.validate(params);

		return true;

	}

	@Override
	public boolean handle(MessageContext context) throws Exception {
		String[] params = executor.getSubResourcePath().split("/");
		context.setProperty(MSISDNConstants.USER_MSISDN, params[1].substring(5));
		context.setProperty(MSISDNConstants.MSISDN, params[1]);
        OperatorEndpoint endpoint = null;
        if (ValidatorUtils.getValidatorForSubscriptionFromMessageContext(context).validate(context)) {
            endpoint = occi.getAPIEndpointsByMSISDN(
                    params[1].replace("tel:", ""), API_TYPE,
                    executor.getSubResourcePath(), true,
                    executor.getValidoperators());
        }

        // set information to the message context, to be used in the sequence
        String sending_add = endpoint.getEndpointref().getAddress() + executor.getSubResourcePath();
        HandlerUtils.setHandlerProperty(context, this.getClass().getSimpleName());
        HandlerUtils.setEndpointProperty(context, sending_add);
        HandlerUtils.setGatewayHost(context);
        HandlerUtils.setAuthorizationHeader(context, executor, endpoint);
        context.setProperty("requestResourceUrl", executor.getResourceUrl());
        return true;
    }

	private String makeListTransactionResponse(String responseStr) {

		String jsonResponse = null;

		try {
			FileReader fileReader = new FileReader();
			String file = CarbonUtils.getCarbonConfigDirPath() + File.separator + FileNames.MEDIATOR_CONF_FILE.getFileName();
			Map<String, String> mediatorConfMap = fileReader.readPropertyFile(file);			
			String ResourceUrlPrefix = mediatorConfMap.get("hubGateway");

			JSONObject jsonObj = new JSONObject(responseStr);
			JSONObject objPaymentTransactionList = jsonObj
					.getJSONObject("paymentTransactionList");

			if (!objPaymentTransactionList.isNull("amountTransaction")) {

				JSONArray amountTransactionArray = objPaymentTransactionList
						.getJSONArray("amountTransaction");
				for (int a = 0; a < amountTransactionArray.length(); a++) {

					JSONObject amountTransaction = (JSONObject) amountTransactionArray
							.get(a);
					String serverReferenceCode = nullOrTrimmed(amountTransaction
							.get("serverReferenceCode").toString());
					amountTransaction.put("resourceURL", ResourceUrlPrefix
							+ executor.getResourceUrl() + "/amount/"
							+ serverReferenceCode);
				}
			}
			objPaymentTransactionList.put("resourceURL", ResourceUrlPrefix
					+ executor.getResourceUrl() + "/amount");
			jsonResponse = jsonObj.toString();
		} catch (Exception e) {

			log.error("Error in formatting list transaction response : "
					+ e.getMessage());
			throw new CustomException("SVC1000", "", new String[] { null });
		}

		log.debug("Formatted list transaction response : " + jsonResponse);
		return jsonResponse;
	}

	/**
	 * Ensure the input value is either a null value or a trimmed string
	 */
	public static String nullOrTrimmed(String s) {
		String rv = null;
		if (s != null && s.trim().length() > 0) {
			rv = s.trim();
		}
		return rv;
	}

}
