package main

import (
	"context"
	"encoding/json"
	"strings"

	"github.com/aws/aws-lambda-go/lambda"
)

// A completed evaluation handler for testing the generated Cognito API boundary.
func handle(_ context.Context, event map[string]any) (map[string]any, error) {
	forwarded := false
	if headers, ok := event["headers"].(map[string]any); ok {
		for name, value := range headers {
			if strings.EqualFold(name, "Authorization") {
				text, _ := value.(string)
				forwarded = strings.HasPrefix(text, "Bearer ")
			}
		}
	}
	var subject any
	if request, ok := event["requestContext"].(map[string]any); ok {
		if authorizer, ok := request["authorizer"].(map[string]any); ok {
			if claims, ok := authorizer["claims"].(map[string]any); ok {
				subject = claims["sub"]
			}
		}
	}
	body, err := json.Marshal(map[string]any{
		"accepted": true, "bearerHeaderForwarded": forwarded,
		"subject": subject, "query": event["queryStringParameters"],
	})
	if err != nil {
		return nil, err
	}
	return map[string]any{
		"statusCode": 200, "headers": map[string]string{"content-type": "application/json"},
		"body": string(body),
	}, nil
}

func main() {
	lambda.Start(handle)
}
