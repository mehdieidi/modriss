# Assistant Live Eval Gate Report

Generated: 2026-07-14T03:16:55.8588979+03:30

| Scenario                      | Level | State     | Seconds | Provider calls | Saved elements | Structural nodes | Visual elements | Visual relationships | Valid | Message                                                                |
| ----------------------------- | ----- | --------- | ------: | -------------: | -------------: | ---------------: | --------------: | -------------------: | ----- | ---------------------------------------------------------------------- |
| source-to-cim-pantry          | CIM   | SUCCEEDED |      88 |              1 |             74 |               51 |               1 |                    1 | True  | Model checkpoint saved.                                                |
| create-cim-library            | CIM   | SUCCEEDED |      66 |              2 |             71 |               38 |               1 |                    1 | True  | Model checkpoint saved.                                                |
| create-pim-serverless         | PIM   | SUCCEEDED |      93 |              2 |             45 |               55 |               1 |                    1 | True  | Model checkpoint saved.                                                |
| create-psm-aws-serverless     | PSM   | FAILED    |      12 |              2 |              0 |                4 |               0 |                    0 | True  | The model provider did not produce any model changes for this request. |
| edit-existing-pim-add-pattern | PIM   | SUCCEEDED |      30 |              2 |              4 |                8 |               1 |                    1 | True  | Model checkpoint saved.                                                |
