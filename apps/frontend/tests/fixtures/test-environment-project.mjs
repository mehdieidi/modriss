// Developer-refined fixture: real Go Lambda runtime, AWS API calls, and SAM resources.
export const files = {
  "go.mod": "module modriss/evaluation\n\ngo 1.23\n",
  "scripts/build.sh": "#!/usr/bin/env bash\nset -euo pipefail\ngo test ./...\nmkdir -p bin/handler\nGOOS=linux GOARCH=amd64 CGO_ENABLED=0 go build -o bin/handler/bootstrap ./src/handler\n",
  "scripts/probe.py": "import boto3, os\nclient = boto3.client('sts', endpoint_url=os.environ['AWS_ENDPOINT_URL'])\nprint(client.get_caller_identity()['Account'])\n",
  "src/handler/main.go": String.raw`package main

import (
  "bytes"
  "encoding/json"
  "fmt"
  "io"
  "net/http"
  "os"
  "strings"
  "time"
)

var client = &http.Client{Timeout: 20 * time.Second}

func aws(operation string, payload any) map[string]any {
  body, _ := json.Marshal(payload)
  req, _ := http.NewRequest("POST", os.Getenv("AWS_ENDPOINT_URL"), bytes.NewReader(body))
  req.Header.Set("Content-Type", "application/x-amz-json-1.0")
  req.Header.Set("X-Amz-Target", "DynamoDB_20120810."+operation)
  req.Header.Set("Authorization", "AWS4-HMAC-SHA256 Credential="+os.Getenv("TEST_ACCOUNT")+"/20260101/us-east-1/dynamodb/aws4_request, SignedHeaders=host, Signature=test")
  response, err := client.Do(req)
  if err != nil { panic(err) }
  defer response.Body.Close()
  var result map[string]any
  json.NewDecoder(response.Body).Decode(&result)
  if response.StatusCode >= 400 { panic(fmt.Sprint(result)) }
  return result
}

func handle(event map[string]any) (any, bool) {
  fmt.Println("evaluation handler called", event)
  if event["type"] == "TOKEN" {
    token, _ := event["authorizationToken"].(string)
    if token == "Bearer evaluation-expired" {
      return map[string]any{"errorMessage":"Unauthorized","errorType":"Unauthorized"}, true
    }
    effect := "Deny"
    if token == "Bearer evaluation-valid" { effect = "Allow" }
    return map[string]any{"principalId":"evaluation-user","policyDocument":map[string]any{
      "Version":"2012-10-17","Statement":[]any{map[string]any{"Action":"execute-api:Invoke","Effect":effect,"Resource":event["methodArn"]}}}}, false
  }
  path, _ := event["path"].(string)
  if rawPath, ok := event["rawPath"].(string); ok { path = rawPath }
  if strings.HasSuffix(path, "/secure") {
    authorization := ""
    if headers, ok := event["headers"].(map[string]any); ok {
      for name, value := range headers { if strings.EqualFold(name, "Authorization") { authorization, _ = value.(string) } }
    }
    status := 401
    if authorization == "Bearer evaluation-valid" { status = 200 }
    if authorization == "Bearer evaluation-forbidden" { status = 403 }
    body, _ := json.Marshal(map[string]any{"authorization":authorization,"ok":status == 200})
    return map[string]any{"statusCode":status,"headers":map[string]string{"Content-Type":"application/json"},"body":string(body)}, false
  }
  if event["fail"] == true { return map[string]any{"errorMessage":"Intentional evaluation failure","errorType":"CaseStudyError"}, true }
  payload := event
  if body, ok := event["body"].(string); ok && body != "" {
    if json.Unmarshal([]byte(body), &payload) != nil { return map[string]any{"statusCode":400,"body":"invalid JSON"}, false }
  }
  if records, ok := event["Records"].([]any); ok {
    for _, value := range records {
      record := value.(map[string]any)
      var message map[string]any
      if body, ok := record["body"].(string); ok { json.Unmarshal([]byte(body), &message) }
      if id, ok := message["id"].(string); ok { aws("PutItem", map[string]any{"TableName":os.Getenv("TABLE_NAME"),"Item":map[string]any{"id":map[string]string{"S":id},"status":map[string]string{"S":"CONSUMED"}}}) }
    }
    return map[string]any{"batchItemFailures":[]any{}}, false
  }
  if id, ok := payload["id"].(string); ok {
    aws("PutItem", map[string]any{"TableName":os.Getenv("TABLE_NAME"),"Item":map[string]any{"id":map[string]string{"S":id},"status":map[string]string{"S":"SAVED"}}})
  }
  result := map[string]any{"received":payload,"ok":true}
  if event["httpMethod"] != nil || event["requestContext"] != nil {
    method, _ := event["httpMethod"].(string)
    if method == "GET" { result["items"] = aws("Scan", map[string]any{"TableName":os.Getenv("TABLE_NAME")})["Items"] }
    body, _ := json.Marshal(result)
    return map[string]any{"statusCode":200,"headers":map[string]string{"Content-Type":"application/json"},"body":string(body)}, false
  }
  return result, false
}

func main() {
  endpoint := "http://"+os.Getenv("AWS_LAMBDA_RUNTIME_API")+"/2018-06-01/runtime/invocation/"
  for {
    response, err := http.Get(endpoint+"next")
    if err != nil { panic(err) }
    data, _ := io.ReadAll(response.Body)
    response.Body.Close()
    var event map[string]any
    json.Unmarshal(data, &event)
    result, failed := handle(event)
    content, _ := json.Marshal(result)
    path := "response"
    if failed { path = "error" }
    request, _ := http.NewRequest("POST",endpoint+response.Header.Get("Lambda-Runtime-Aws-Request-Id")+"/"+path,strings.NewReader(string(content)))
    request.Header.Set("Content-Type","application/json")
    if failed { request.Header.Set("Lambda-Runtime-Function-Error-Type","CaseStudyError") }
    posted, err := http.DefaultClient.Do(request)
    if err != nil { panic(err) }
    posted.Body.Close()
  }
}
`,
  "template.yaml": String.raw`AWSTemplateFormatVersion: '2010-09-09'
Transform: AWS::Serverless-2016-10-31
Description: MODRISS refined evaluation fixture
Resources:
  Role:
    Type: AWS::IAM::Role
    Properties:
      AssumeRolePolicyDocument:
        Version: '2012-10-17'
        Statement:
          - Effect: Allow
            Principal: {Service: [lambda.amazonaws.com, states.amazonaws.com]}
            Action: sts:AssumeRole
      Policies:
        - PolicyName: evaluation
          PolicyDocument:
            Version: '2012-10-17'
            Statement:
              - Effect: Allow
                Action: ['logs:*', 'dynamodb:PutItem', 'dynamodb:Scan', 'lambda:InvokeFunction', 'sqs:ReceiveMessage', 'sqs:DeleteMessage', 'sqs:GetQueueAttributes']
                Resource: '*'
  Orders:
    Type: AWS::DynamoDB::Table
    Properties:
      BillingMode: PAY_PER_REQUEST
      AttributeDefinitions: [{AttributeName: id, AttributeType: S}]
      KeySchema: [{AttributeName: id, KeyType: HASH}]
  Receipts:
    Type: AWS::S3::Bucket
  Queue:
    Type: AWS::SQS::Queue
  Topic:
    Type: AWS::SNS::Topic
  DeliveryQueue:
    Type: AWS::SQS::Queue
    Properties: {VisibilityTimeout: 120}
  TopicDelivery:
    Type: AWS::SNS::Subscription
    Properties:
      Protocol: sqs
      TopicArn: !Ref Topic
      Endpoint: !GetAtt DeliveryQueue.Arn
      RawMessageDelivery: true
  DeliveryPolicy:
    Type: AWS::SQS::QueuePolicy
    Properties:
      Queues: [!Ref DeliveryQueue]
      PolicyDocument:
        Version: '2012-10-17'
        Statement:
          - Effect: Allow
            Principal: {Service: [sns.amazonaws.com, events.amazonaws.com]}
            Action: sqs:SendMessage
            Resource: !GetAtt DeliveryQueue.Arn
  Bus:
    Type: AWS::Events::EventBus
    Properties: {Name: evaluation-bus}
  DeliveryRule:
    Type: AWS::Events::Rule
    Properties:
      EventBusName: !Ref Bus
      EventPattern: {source: [modriss.test]}
      Targets:
        - {Id: Delivery, Arn: !GetAtt DeliveryQueue.Arn, InputPath: '$.detail'}
  Consumer:
    Type: AWS::Serverless::Function
    Properties:
      Runtime: provided.al2023
      Handler: bootstrap
      CodeUri: bin/handler/
      Timeout: 20
      Role: !GetAtt Role.Arn
      Environment:
        Variables:
          TABLE_NAME: !Ref Orders
          TEST_ACCOUNT: !Ref AWS::AccountId
      Events:
        Delivery:
          Type: SQS
          Properties: {Queue: !GetAtt DeliveryQueue.Arn, BatchSize: 1}
  Handler:
    Type: AWS::Serverless::Function
    Properties:
      Runtime: provided.al2023
      Handler: bootstrap
      CodeUri: bin/handler/
      Timeout: 20
      MemorySize: 128
      Role: !GetAtt Role.Arn
      Environment:
        Variables:
          TABLE_NAME: !Ref Orders
          TEST_ACCOUNT: !Ref AWS::AccountId
      Events:
        PostOrders:
          Type: Api
          Properties: {RestApiId: !Ref Api, Path: /orders, Method: POST}
        GetOrders:
          Type: Api
          Properties: {RestApiId: !Ref Api, Path: /orders, Method: GET}
        Echo:
          Type: HttpApi
          Properties: {ApiId: !Ref HttpApi, Path: /echo, Method: POST}
        SecureRest:
          Type: Api
          Properties: {RestApiId: !Ref Api, Path: /secure, Method: GET}
        SecureHttp:
          Type: HttpApi
          Properties: {ApiId: !Ref HttpApi, Path: /secure, Method: GET}
        AuthorizedRest:
          Type: Api
          Properties:
            RestApiId: !Ref Api
            Path: /authorized
            Method: GET
            Auth: {Authorizer: CaseStudyToken}
  Authorizer:
    Type: AWS::Serverless::Function
    Properties:
      Runtime: provided.al2023
      Handler: bootstrap
      CodeUri: bin/handler/
      Timeout: 20
      Role: !GetAtt Role.Arn
  Api:
    Type: AWS::Serverless::Api
    Properties:
      StageName: test
      Auth:
        Authorizers:
          CaseStudyToken:
            FunctionArn: !GetAtt Authorizer.Arn
            Identity: {Header: Authorization, ReauthorizeEvery: 0}
  HttpApi:
    Type: AWS::Serverless::HttpApi
    Properties: {StageName: test}
  HandlerLogs:
    Type: AWS::Logs::LogGroup
    Properties: {LogGroupName: !Join ['', ['/aws/lambda/', !Ref Handler]], RetentionInDays: 7}
  Workflow:
    Type: AWS::StepFunctions::StateMachine
    Properties:
      RoleArn: !GetAtt Role.Arn
      Definition:
        StartAt: Choose
        States:
          Choose:
            Type: Choice
            Choices: [{And: [{Variable: '$.fail', IsPresent: true}, {Variable: '$.fail', BooleanEquals: true}], Next: Rejected}]
            Default: Invoke
          Invoke:
            Type: Task
            Resource: arn:aws:states:::lambda:invoke
            Parameters:
              FunctionName: !GetAtt Handler.Arn
              Payload.$: '$'
            OutputPath: '$.Payload'
            Next: Complete
          Complete: {Type: Succeed}
          Rejected: {Type: Fail, Error: RejectedByScenario, Cause: Intentional evaluation branch}
Outputs:
  Function: {Value: !Ref Handler}
`.replaceAll("\\u0024", "$"),
};
