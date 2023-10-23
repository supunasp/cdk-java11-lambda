package com.supunasp.cdk;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

public class MyLambdaHandler implements RequestHandler<String, String> {

    public String handleRequest(String input, Context context) {
        return "Hello, " + input;
    }
}
