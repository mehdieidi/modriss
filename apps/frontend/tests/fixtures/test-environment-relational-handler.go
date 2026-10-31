package main

import (
    "context"
    "encoding/json"
    "errors"

    "github.com/aws/aws-lambda-go/lambda"
    "github.com/aws/aws-sdk-go-v2/aws"
    "github.com/aws/aws-sdk-go-v2/service/rdsdata/types"
    "MODRISS_MODULE_PATH/src/shared"
)

// Evaluation-only handler: tests the generated Data API helper against the modeled database.
// It intentionally accepts SQL from the isolated test harness, never from a public application API.
type input struct {
    SQL string `json:"sql"`
    Parameters map[string]string `json:"parameters"`
    Transaction bool `json:"transaction"`
    Rollback bool `json:"rollback"`
}

func handle(ctx context.Context, event input) (map[string]any, error) {
    store, err := shared.NewRelationalStore(ctx, "MODRISS_STORE_PREFIX")
    if err != nil { return nil, err }
    parameters := make([]types.SqlParameter, 0, len(event.Parameters))
    for name, value := range event.Parameters {
        parameters = append(parameters, types.SqlParameter{Name:aws.String(name), Value:&types.FieldMemberStringValue{Value:value}})
    }
    var result any
    rollback := errors.New("evaluation rollback requested")
    if event.Transaction {
        err = store.WithTransaction(ctx, func(transaction string) error {
            response, failure := store.Execute(ctx, event.SQL, parameters, &transaction)
            result = response
            if failure != nil { return failure }
            if event.Rollback { return rollback }
            return nil
        })
    } else {
        result, err = store.Execute(ctx, event.SQL, parameters, nil)
    }
    if err != nil && !errors.Is(err, rollback) { return nil, err }
    body, err := json.Marshal(map[string]any{"ok":true, "revision":"MODRISS_EVALUATION_REVISION", "rolledBack":errors.Is(err, rollback), "database":store.Database, "result":result})
    if err != nil { return nil, err }
    return map[string]any{"statusCode":200, "body":string(body)}, nil
}

func main() { lambda.Start(handle) }
