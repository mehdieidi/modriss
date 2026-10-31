import test from "node:test";
import assert from "node:assert/strict";
import { initialInput, resourceGroup, workflowGraph, workflowStates } from "../js/test-environment-utils.mjs";

test("only executable API roots appear as APIs and storage is grouped separately", () => {
  assert.equal(resourceGroup({ResourceType:"AWS::ApiGateway::RestApi"}), "APIs");
  assert.equal(resourceGroup({ResourceType:"AWS::ApiGateway::Method"}), "Infrastructure");
  assert.equal(resourceGroup({ResourceType:"AWS::DynamoDB::Table"}), "Storage");
  assert.equal(resourceGroup({ResourceType:"AWS::RDS::DBCluster"}), "Storage");
  assert.equal(resourceGroup({ResourceType:"AWS::RDS::DBInstance"}), "Storage");
  assert.equal(resourceGroup({ResourceType:"AWS::StepFunctions::StateMachine"}), "Workflows");
});

test("workflow graph retains choices, retries through Catch, parallel branches, and map states", () => {
  const graph = workflowGraph({ States: {
    Decide: { Type:"Choice", Choices:[{Next:"Parallel"}], Default:"Fail" },
    Parallel: { Type:"Parallel", Branches:[{States:{Child:{Type:"Pass",End:true}}}], Next:"Map", Catch:[{Next:"Fail"}] },
    Map: { Type:"Map", ItemProcessor:{States:{Item:{Type:"Pass",End:true}}}, End:true },
    Fail: { Type:"Fail" },
  }});
  assert.deepEqual(graph.edges.filter(edge => edge.source === "Decide").map(edge => edge.target).sort(), ["Fail", "Parallel"]);
  assert.ok(graph.nodes.some(node => node.id === "Parallel/0/Child"));
  assert.ok(graph.nodes.some(node => node.id === "Map/items/Item"));
  assert.ok(graph.edges.some(edge => edge.source === "Parallel" && edge.target === "Fail"));
});

test("workflow status derives from observed events and retains exact state input/output", () => {
  const states = workflowStates([
    {type:"PassStateEntered",stateEnteredEventDetails:{name:"Prepare",input:'{"id":1}'}},
    {type:"PassStateExited",stateExitedEventDetails:{name:"Prepare",output:'{"ready":true}'}},
    {type:"TaskStateEntered",stateEnteredEventDetails:{name:"Save",input:'{"ready":true}'}},
    {type:"TaskFailed",taskFailedEventDetails:{error:"WriteFailed"}},
  ]);
  assert.equal(states.get("Prepare").status, "succeeded");
  assert.equal(states.get("Prepare").output, '{"ready":true}');
  assert.equal(states.get("Save").status, "failed");
  assert.equal(states.get("Save").error.taskFailedEventDetails.error, "WriteFailed");
});

test("HTTP defaults use a real deployed route and stage, and data writes have appropriate inputs", () => {
  assert.deepEqual(initialInput("request", {routes:[{method:"POST",path:"/orders"}],stages:["test"]}),
    {method:"POST",path:"/orders",stage:"test",query:{},headers:{},body:null});
  assert.ok(initialInput("put", {ResourceType:"AWS::DynamoDB::Table"}).item.id.S);
  assert.ok(initialInput("put", {ResourceType:"AWS::S3::Bucket"}).key);
});

test("SQL inputs use the deployed table and quote identifiers without changing SQL structure", () => {
  assert.deepEqual(initialInput("query", {database:{tables:[{name:'orders"history'}]}}),
    {sql:'SELECT * FROM "orders""history" LIMIT 25',parameters:[]});
  assert.equal(initialInput("query").sql, "SELECT 1");
  assert.ok(initialInput("rollback-transaction").transactionId);
});
